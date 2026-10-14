package com.book.mask.personalize;

import android.content.Context;
import android.util.Log;

import com.tencent.mmkv.MMKV;

import java.io.File;

/**
 * 一次性清理已下线功能遗留在本机的数据，避免凭据、缓存与录音文件成为再也无法通过界面清除的孤儿数据：
 * <ul>
 *   <li>听力 / 复述 / 听述题：豆包语音与腾讯口语评测的凭据、复述录音文件、答题记录与相关设置；</li>
 *   <li>推理 / 混合 / 英文阅读题：远程题目缓存与相关设置；</li>
 *   <li>题目类型选择、答题计时、自动截图、电脑任务专注：相关设置项。</li>
 * </ul>
 * 清理成功后写入完成标记，之后每次启动只做一次布尔读取。
 */
public final class LegacyDataCleaner {

    private static final String TAG = "LegacyDataCleaner";
    private static final String KEY_CLEANED = "legacy_features_cleaned_v1";

    /** 已下线功能独占的 MMKV 存储：腾讯口语评测凭据、豆包语音凭据、远程题目缓存。 */
    private static final String[] LEGACY_MMKV_IDS = {
            "soe_config_v1",
            "doubao_tts_config_v1",
            "challenge_question_cache_v1",
    };

    /** 通用设置存储（app_settings）中已下线功能遗留的键。 */
    private static final String[] LEGACY_SETTING_KEYS = {
            "math_question_type",
            "reasoning_difficulty_level",
            "listening_difficulty_level",
            "listening_retelling_difficulty_level",
            "listening_scenario",
            "listening_custom_scenarios",
            "challenge_timer_mode",
            "english_reading_length",
            "retelling_story_length",
            "retelling_display_seconds",
            "retelling_pass_score",
            "voice_challenge_enabled",
            "retelling_answer_records",
            "listening_answer_records",
            "auto_screenshot_enabled",
            "auto_screenshot_last_result",
            "auto_screenshot_last_result_at",
            "auto_screenshot_last_error",
            "computer_focus_server_address",
    };

    /** 复述题录音文件所在目录（位于应用私有 filesDir 下）。 */
    private static final String RETELLING_AUDIO_DIR = "retelling_audio";

    private LegacyDataCleaner() {
    }

    /** 在后台线程执行清理；已清理过则直接返回。须在 MMKV 初始化之后调用。 */
    public static void runOnce(Context context) {
        final MMKV settings = SettingsStorage.open();
        if (settings.getBoolean(KEY_CLEANED, false)) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            if (cleanRemovedFeatureData(appContext, settings)) {
                settings.putBoolean(KEY_CLEANED, true).commit();
                Log.i(TAG, "已清理已下线功能遗留的本地数据");
            }
        }, "legacy-data-cleaner");
        worker.setDaemon(true);
        worker.start();
    }

    /** @return 全部步骤成功才返回 true；失败的步骤下次启动会重试。 */
    private static boolean cleanRemovedFeatureData(Context context, MMKV settings) {
        boolean success = true;
        try {
            settings.removeValuesForKeys(LEGACY_SETTING_KEYS);
        } catch (RuntimeException e) {
            Log.w(TAG, "清理遗留设置项失败", e);
            success = false;
        }
        for (String storageId : LEGACY_MMKV_IDS) {
            try {
                if (MMKV.checkExist(storageId) && !MMKV.removeStorage(storageId)) {
                    Log.w(TAG, "删除遗留存储失败: " + storageId);
                    success = false;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "删除遗留存储异常: " + storageId, e);
                success = false;
            }
        }
        if (!deleteRecursively(new File(context.getFilesDir(), RETELLING_AUDIO_DIR))) {
            Log.w(TAG, "删除复述录音目录失败");
            success = false;
        }
        return success;
    }

    private static boolean deleteRecursively(File target) {
        if (!target.exists()) {
            return true;
        }
        boolean success = true;
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                success &= deleteRecursively(child);
            }
        }
        return target.delete() && success;
    }
}

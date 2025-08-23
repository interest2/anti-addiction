package com.book.mask.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.book.mask.config.Const;
import com.book.mask.setting.RelaxManager;
import com.book.mask.setting.AppSettingsManager;
import com.book.mask.util.ContentUtils;

import org.json.JSONObject;

public class TextFetcher {
    
    private static final String TAG = "TextFetcher";
    private static final String PREF_NAME = "floating_text_cache";
    private static final String PREF_KEY_CACHED_TEXT = "cached_text";
    private static final String PREF_KEY_LAST_UPDATE = "last_update";
    
    private Context context;
    private ExecutorService executorService;
    private Handler mainHandler;
    private SharedPreferences prefs;
    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    
    public interface OnTextFetchListener {
        void onTextFetched(String text);
        void onFetchError(String error);
    }
    
    public TextFetcher(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.relaxManager = new RelaxManager(context);
        this.appSettingsManager = new AppSettingsManager(context);
    }
    
    /**
     * 获取缓存的文字内容
     */
    public String getCachedText() {
        String cachedText = prefs.getString(PREF_KEY_CACHED_TEXT, null);
        if (cachedText != null) {
            return cachedText;
        }
        Log.d(TAG, "没有缓存文字，用默认预置的内容");
        return "别让指尖滑动成为你人生的绊脚石！\n" +
                "APP的诱惑不过是虚幻的糖衣，吞噬的是你的黄金时间！\n" +
                "醒醒吧，自律的缺失，正在将你推向平庸的深渊";
    }
    
    /**
     * 异步获取最新的文字内容
     */
    public void fetchLatestText(OnTextFetchListener listener) {
        Log.d(TAG, "开始获取最新文字内容");
        
        executorService.execute(() -> {
            try {
                String reqResult = httpObtainEncourage();
                String result = reqResult.replaceAll("\\n\\s*\\n", "\n");

                mainHandler.post(() -> {
                    if (result != null) {
                        // 缓存新文字
                        cacheText(result);
                        Log.d(TAG, "获取到新文字");
                        if (listener != null) {
                            listener.onTextFetched(result);
                        }
                    } else {
                        Log.w(TAG, "获取文字失败，使用缓存文字");
                        if (listener != null) {
                            listener.onFetchError("获取文字失败");
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "获取文字时发生异常", e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onFetchError("网络异常: " + e.getMessage());
                    }
                });
            }
        });
    }


    /**
     * http 获取警示文字
     */
    private String httpObtainEncourage() {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            String tag = appSettingsManager.getMotivationTag();
            String encodedTag = java.net.URLEncoder.encode(tag, java.nio.charset.StandardCharsets.UTF_8.name());

            JSONObject reqJson = new JSONObject();
            reqJson.put("tag", encodedTag);
            reqJson.put("devId", androidId);
            reqJson.put("version", packageInfo.versionName);

            String response = ContentUtils.doHttpPost(Const.DOMAIN_URL + Const.LLM_PATH_V2,
                    reqJson.toString(), java.util.Collections.singletonMap("Accept", "application/json"));
            return ContentUtils.parseRespJson(response);

        } catch (Exception e) {
            Log.e(TAG, "HTTP请求异常", e);
            return null;
        }
    }


    /**
     * 缓存文字内容
     */
    private void cacheText(String text) {
        try {
            prefs.edit()
                    .putString(PREF_KEY_CACHED_TEXT, text)
                    .putLong(PREF_KEY_LAST_UPDATE, System.currentTimeMillis())
                    .apply();
            Log.d(TAG, "文字已缓存");
        } catch (Exception e) {
            Log.e(TAG, "缓存文字失败", e);
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
} 
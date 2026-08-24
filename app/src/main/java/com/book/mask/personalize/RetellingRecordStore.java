package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.constant.QuestionConst;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 复述/听述答题记录持久化：以 Gson JSON 数组存于 MMKV，新的在前，按材料模式分别裁剪。
 * 记录与其录音文件（{@link RetellingAudioStore}）同生命周期：裁剪丢弃最旧记录时删除对应录音文件，
 * 清空记录时删除全部录音文件。
 */
public final class RetellingRecordStore {

    private static final String KEY = "retelling_answer_records";

    private final MMKV mmkv;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<ArrayList<RetellingRecord>>() {
    }.getType();
    private final RetellingAudioStore audioStore;

    public RetellingRecordStore(Context context) {
        this.mmkv = SettingsStorage.open();
        this.audioStore = new RetellingAudioStore(context);
    }

    /** 读取全部记录（新的在前）；无记录时返回空列表。 */
    public synchronized List<RetellingRecord> getRecords() {
        String json = mmkv.getString(KEY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<RetellingRecord> list = gson.fromJson(json, listType);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 新增一条记录，按复述/听述各自上限裁剪；被裁剪记录的录音文件一并删除。 */
    public synchronized void addRecord(RetellingRecord record) {
        if (record == null) {
            return;
        }
        List<RetellingRecord> records = getRecords();
        records.add(0, record);
        Map<String, Integer> seen = new HashMap<>();
        for (Iterator<RetellingRecord> iterator = records.iterator(); iterator.hasNext(); ) {
            RetellingRecord current = iterator.next();
            String mode = current != null && current.isListeningRetelling()
                    ? RetellingRecord.STIMULUS_AUDIO
                    : RetellingRecord.STIMULUS_TEXT;
            int count = seen.getOrDefault(mode, 0) + 1;
            seen.put(mode, count);
            int limit = RetellingRecord.STIMULUS_AUDIO.equals(mode)
                    ? QuestionConst.LISTENING_RETELLING_RECORD_MAX
                    : QuestionConst.RETELLING_RECORD_MAX;
            if (count > limit) {
                if (current != null
                        && current.audioPath != null
                        && !current.audioPath.isEmpty()) {
                    audioStore.delete(current.audioPath);
                }
                iterator.remove();
            }
        }
        mmkv.putString(KEY, gson.toJson(records)).commit();
    }

    /** 清空全部记录及对应的录音文件。 */
    public synchronized void clear() {
        mmkv.removeValueForKey(KEY);
        audioStore.deleteAll();
    }
}

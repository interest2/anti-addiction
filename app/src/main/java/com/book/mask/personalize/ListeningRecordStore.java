package com.book.mask.personalize;

import com.book.mask.constant.QuestionConst;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 听力题答题记录持久化：以 Gson JSON 数组存于 MMKV，新的在前，超过上限丢弃最旧。
 */
public final class ListeningRecordStore {

    private static final String KEY = "listening_answer_records";

    private final MMKV mmkv;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<ArrayList<ListeningRecord>>() {
    }.getType();

    public ListeningRecordStore() {
        this.mmkv = SettingsStorage.open();
    }

    /** 读取全部记录（新的在前）；无记录时返回空列表。 */
    public synchronized List<ListeningRecord> getRecords() {
        String json = mmkv.getString(KEY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<ListeningRecord> list = gson.fromJson(json, listType);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 新增一条记录，插入到最前，裁剪到上限后持久化。 */
    public synchronized void addRecord(ListeningRecord record) {
        if (record == null) {
            return;
        }
        List<ListeningRecord> records = getRecords();
        records.add(0, record);
        if (records.size() > QuestionConst.LISTENING_RECORD_MAX) {
            records = new ArrayList<>(records.subList(0, QuestionConst.LISTENING_RECORD_MAX));
        }
        mmkv.putString(KEY, gson.toJson(records)).commit();
    }

    /** 清空全部记录。 */
    public synchronized void clear() {
        mmkv.removeValueForKey(KEY);
    }
}

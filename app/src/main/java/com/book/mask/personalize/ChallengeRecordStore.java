package com.book.mask.personalize;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 非算术题答题记录持久化：以 Gson JSON 数组存于 MMKV，新的在前，超过上限丢弃最旧。
 */
public final class ChallengeRecordStore {

    private static final String KEY = "challenge_answer_records";
    /** 最多保留的记录条数 */
    private static final int MAX_RECORDS = 100;

    private final MMKV mmkv;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<ArrayList<ChallengeRecord>>() {
    }.getType();

    public ChallengeRecordStore() {
        this.mmkv = SettingsStorage.open();
    }

    /** 读取全部记录（新的在前）；无记录时返回空列表。 */
    public synchronized List<ChallengeRecord> getRecords() {
        String json = mmkv.getString(KEY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<ChallengeRecord> list = gson.fromJson(json, listType);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 新增一条记录，插入到最前，裁剪到上限后持久化。 */
    public synchronized void addRecord(ChallengeRecord record) {
        if (record == null) {
            return;
        }
        List<ChallengeRecord> records = getRecords();
        records.add(0, record);
        if (records.size() > MAX_RECORDS) {
            records = new ArrayList<>(records.subList(0, MAX_RECORDS));
        }
        mmkv.putString(KEY, gson.toJson(records)).commit();
    }

    /** 清空全部记录。 */
    public synchronized void clear() {
        mmkv.removeValueForKey(KEY);
    }
}

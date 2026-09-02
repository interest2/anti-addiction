package com.book.mask.personalize;

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
 * 文本类答题记录持久化：以 Gson JSON 数组存于 MMKV，新的在前，超过上限丢弃最旧。
 */
public final class ChallengeRecordStore {

    private static final String KEY = "challenge_answer_records";

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

    /** 新增一条记录，插入到最前，按实际文本题型分别裁剪到上限后持久化。 */
    public synchronized void addRecord(ChallengeRecord record) {
        if (record == null) {
            return;
        }
        List<ChallengeRecord> records = getRecords();
        records.add(0, record);
        trimToPerTypeLimit(records);
        mmkv.putString(KEY, gson.toJson(records)).commit();
    }

    /** 列表新的在前：各文本题型独立保留详情，避免算术题挤掉推理题详情。 */
    private static void trimToPerTypeLimit(List<ChallengeRecord> records) {
        Map<String, Integer> seen = new HashMap<>();
        for (Iterator<ChallengeRecord> it = records.iterator(); it.hasNext(); ) {
            ChallengeRecord record = it.next();
            String type = normalizedType(record.type);
            int count = seen.getOrDefault(type, 0) + 1;
            seen.put(type, count);
            if (count > QuestionConst.CHALLENGE_RECORD_MAX) {
                it.remove();
            }
        }
    }

    /** 旧记录或异常类型统一归入 legacy challenge 分组，避免产生无限多的裁剪分组。 */
    private static String normalizedType(String type) {
        if (AnswerOverviewRecord.TYPE_ARITHMETIC.equals(type)
                || AnswerOverviewRecord.TYPE_REASONING.equals(type)
                || AnswerOverviewRecord.TYPE_MIXED.equals(type)
                || AnswerOverviewRecord.TYPE_ENGLISH_READING.equals(type)) {
            return type;
        }
        return AnswerOverviewRecord.TYPE_CHALLENGE;
    }

    /** 清空全部记录。 */
    public synchronized void clear() {
        mmkv.removeValueForKey(KEY);
    }
}

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
 * 答题「概况」记录持久化：以 Gson JSON 数组存于 MMKV，新的在前。
 * 各题型分别裁剪到 QuestionConst 中 *_OVERVIEW_MAX（详情量的 10 倍），超出丢弃该题型最旧一条。
 */
public final class AnswerOverviewStore {

    private static final String KEY = "answer_overview_records";

    private final MMKV mmkv;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<ArrayList<AnswerOverviewRecord>>() {
    }.getType();

    public AnswerOverviewStore() {
        this.mmkv = SettingsStorage.open();
    }

    /** 读取全部概况记录（新的在前）；无记录时返回空列表。 */
    public synchronized List<AnswerOverviewRecord> getRecords() {
        String json = mmkv.getString(KEY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<AnswerOverviewRecord> list = gson.fromJson(json, listType);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 新增一条概况记录，插入到最前，按题型裁剪到对应上限后持久化。 */
    public synchronized void addRecord(AnswerOverviewRecord record) {
        if (record == null || record.type == null || record.type.isEmpty()) {
            return;
        }
        List<AnswerOverviewRecord> records = getRecords();
        records.add(0, record);
        trimToPerTypeLimit(records);
        mmkv.putString(KEY, gson.toJson(records)).commit();
    }

    private static int maxForType(String type) {
        switch (type) {
            case AnswerOverviewRecord.TYPE_ARITHMETIC:
            case AnswerOverviewRecord.TYPE_CHALLENGE:
                return QuestionConst.CHALLENGE_OVERVIEW_MAX;
            case AnswerOverviewRecord.TYPE_LISTENING:
                return QuestionConst.LISTENING_OVERVIEW_MAX;
            case AnswerOverviewRecord.TYPE_RETELLING:
                return QuestionConst.RETELLING_OVERVIEW_MAX;
            default:
                return 0;
        }
    }

    /** 列表新的在前：按题型从前往后数，超过该题型上限的最旧记录裁掉。 */
    private static void trimToPerTypeLimit(List<AnswerOverviewRecord> records) {
        Map<String, Integer> seen = new HashMap<>();
        for (Iterator<AnswerOverviewRecord> it = records.iterator(); it.hasNext(); ) {
            AnswerOverviewRecord r = it.next();
            int count = seen.getOrDefault(r.type, 0) + 1;
            seen.put(r.type, count);
            if (count > maxForType(r.type)) {
                it.remove();
            }
        }
    }

    /** 清空全部概况记录。 */
    public synchronized void clear() {
        mmkv.removeValueForKey(KEY);
    }
}

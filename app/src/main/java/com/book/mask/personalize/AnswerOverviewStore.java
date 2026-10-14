package com.book.mask.personalize;

import com.book.mask.constant.QuestionConst;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 答题「概况」记录持久化：以 Gson JSON 数组存于 MMKV，新的在前，
 * 超出 QuestionConst.CHALLENGE_OVERVIEW_MAX（详情量的 10 倍）则丢弃最旧一条。
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

    /** 读取算术题概况记录（新的在前）；无记录时返回空列表。历史版本遗留的其他题型记录不返回。 */
    public synchronized List<AnswerOverviewRecord> getRecords() {
        String json = mmkv.getString(KEY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<AnswerOverviewRecord> list = gson.fromJson(json, listType);
            if (list == null) {
                return new ArrayList<>();
            }
            list.removeIf(record ->
                    record == null || !AnswerOverviewRecord.TYPE_ARITHMETIC.equals(record.type));
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 新增一条概况记录，插入到最前，裁剪到上限后持久化；写入时历史遗留的其他题型记录一并丢弃。 */
    public synchronized void addRecord(AnswerOverviewRecord record) {
        if (record == null || !AnswerOverviewRecord.TYPE_ARITHMETIC.equals(record.type)) {
            return;
        }
        List<AnswerOverviewRecord> records = getRecords();
        records.add(0, record);
        while (records.size() > QuestionConst.CHALLENGE_OVERVIEW_MAX) {
            records.remove(records.size() - 1);
        }
        mmkv.putString(KEY, gson.toJson(records)).commit();
    }

    /** 清空全部概况记录。 */
    public synchronized void clear() {
        mmkv.removeValueForKey(KEY);
    }
}

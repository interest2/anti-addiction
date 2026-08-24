package com.book.mask.challenge.common;

import com.book.mask.config.ChallengeType;
import com.tencent.mmkv.MMKV;

/**
 * 远程题目的持久化缓存：按「题型 + 难度档」保存最近一次成功拉取的题目。
 *
 * <p>持久化后，进程重启（无障碍服务重建 / 开机 / 被杀）仍能拿到上次拉到的题，
 * 不必退回内置预置题；远程拉题失败时也自然沿用上次缓存。
 *
 * <p>属运行时数据而非用户配置，因此独立存储、不随备份导出。
 */
final class ChallengeQuestionCache {

    private static final String STORAGE_ID = "challenge_question_cache_v1";

    private final MMKV mmkv = MMKV.mmkvWithID(STORAGE_ID);

    /** @return 该档缓存的题目；从未成功拉取过则返回 null */
    ChallengeQuestionProvider.Question get(ChallengeType type, int reasoningLevel) {
        String key = cacheKey(type, reasoningLevel);
        String content = mmkv.getString(contentKey(key), null);
        String answer = mmkv.getString(answerKey(key), null);
        if (content == null || answer == null) {
            return null;
        }
        return new ChallengeQuestionProvider.Question(content, answer);
    }

    void put(ChallengeType type, int reasoningLevel, ChallengeQuestionProvider.Question question) {
        String key = cacheKey(type, reasoningLevel);
        mmkv.putString(contentKey(key), question.getContent())
                .putString(answerKey(key), question.getAnswer())
                .commit();
    }

    private static String cacheKey(ChallengeType type, int reasoningLevel) {
        return type.name() + ':' + reasoningLevel;
    }

    private static String contentKey(String key) {
        return "question_" + key;
    }

    private static String answerKey(String key) {
        return "answer_" + key;
    }
}

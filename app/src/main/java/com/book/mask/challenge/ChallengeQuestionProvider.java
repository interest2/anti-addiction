package com.book.mask.challenge;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.CloudConst;
import com.book.mask.constant.QuestionConst;
import com.book.mask.setting.ChallengeSettingsManager;
import com.book.mask.util.ArithmeticUtils;
import com.book.mask.util.ContentUtils;

import org.json.JSONObject;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 负责题型选择、题目生成、远程题目请求与缓存。
 */
final class ChallengeQuestionProvider {

    private static final String TAG = "ChallengeProvider";
    private static final String FIRST_Q_MIXED = "小红书社区组织挖红薯：\n"
            + "如果每位主包挖 12 个，地里还剩 75 个；\n"
            + "如果每位主包挖 29 个，就还缺 180 个。\n"
            + "问共有多少位主包参加了挖红薯？";
    private static final String FIRST_A_MIXED = "15";
    private static final String FIRST_Q_REASON = "甲、乙、丙、丁四人参加比赛，名次各不相同。已知：\n" +
            "①甲、乙中恰有一人是第一名；\n" +
            "②若甲是第一名，则丙是第三名；\n" +
            "③若乙是第一名，则丁是第二名；\n" +
            "④丁比丙高一个名次。问谁是第二名\n";
    private static final String FIRST_A_REASON = "丁";

    private static final String FIRST_Q_READING = "Supermarkets were still a California phenomenon; District food shopping "
            + "was done in small groceries, in red-fronted outlets of the Great "
            + "Atlantic & Pacific Tea Company, in open markets, or on pavements."
            + "\n\n"
            + "A.Supermarkets were prevalent in California, while food shopping "
            + "in other districts was typically done in small groceries, open "
            + "markets, or on pavements.\n"
            + "B.California exclusively used open markets for food shopping, "
            + "with supermarkets being a rare phenomenon.\n"
            + "C.Supermarkets were the dominant form of food shopping in all "
            + "districts, with no use of small groceries or open markets.";
    private static final String FIRST_A_READING = "A";

    private static final ConcurrentMap<ChallengeType, Question> REMOTE_CACHE =
            new ConcurrentHashMap<>();

    static {
        REMOTE_CACHE.put(
                ChallengeType.MIXED,
                new Question(FIRST_Q_MIXED, FIRST_A_MIXED));
        REMOTE_CACHE.put(
                ChallengeType.REASONING,
                new Question(FIRST_Q_REASON, FIRST_A_REASON));
        REMOTE_CACHE.put(
                ChallengeType.ENGLISH_READING,
                new Question(FIRST_Q_READING, FIRST_A_READING));
    }

    private final Context context;
    private final ChallengeSettingsManager challengeSettingsManager;
    private final Random random = new Random();

    ChallengeQuestionProvider(Context context) {
        this.context = context;
        this.challengeSettingsManager = new ChallengeSettingsManager(context);
    }

    ChallengeType selectType() {
        ChallengeType configuredType = challengeSettingsManager.getChallengeType();
        if (configuredType != ChallengeType.MIXED) {
            return configuredType;
        }
        return random.nextInt(100) < QuestionConst.MIXED_REMOTE_CHALLENGE_PERCENT
                ? ChallengeType.MIXED
                : ChallengeType.ARITHMETIC;
    }

    Question getQuestion(ChallengeType type) {
        if (type == ChallengeType.ARITHMETIC) {
            return generateArithmeticQuestion();
        }

        Question cachedQuestion = REMOTE_CACHE.get(type);
        if (type == ChallengeType.ENGLISH_READING) {
            fetchLatestEnglishReading();
        } else {
            fetchLatestChallenge(type);
        }

        if (cachedQuestion != null) {
            Log.d(TAG, "使用缓存的" + type.getDisplayName());
            return cachedQuestion;
        }

        Log.w(TAG, "缓存中没有" + type.getDisplayName() + "，使用本地算术题作为备选");
        return generateArithmeticQuestion();
    }

    private Question generateArithmeticQuestion() {
        String difficultyMode = challengeSettingsManager.getMathDifficultyMode();
        String question;
        if ("custom".equals(difficultyMode)) {
            question = ArithmeticUtils.customArithmetic(
                    challengeSettingsManager.getMathAdditionDigits(),
                    challengeSettingsManager.getMathSubtractionDigits(),
                    challengeSettingsManager.getMathMultiplicationMultiplierDigits(),
                    challengeSettingsManager.getMathMultiplicationMultiplicandDigits());
        } else {
            question = ArithmeticUtils.customArithmetic(
                    QuestionConst.ADD_LEN_DEFAULT,
                    QuestionConst.SUB_LEN_DEFAULT,
                    QuestionConst.MUL_FIRST_LEN_DEFAULT,
                    QuestionConst.MUL_SECOND_LEN_DEFAULT);
        }
        return new Question(question, String.valueOf(ArithmeticUtils.getMathAnswer(question)));
    }

    private void fetchLatestChallenge(ChallengeType type) {
        Log.d(TAG, "开始获取最新" + type.getDisplayName());
        new Thread(() -> {
            try {
                String remoteChallenge = httpObtainChallenge(type.getRequestType());
                cacheRemoteQuestion(type, remoteChallenge);
            } catch (Exception e) {
                Log.e(TAG, "获取" + type.getDisplayName() + "时发生异常", e);
            }
        }).start();
    }

    private void fetchLatestEnglishReading() {
        Log.d(TAG, "开始获取最新英文阅读题目");
        new Thread(() -> {
            try {
                String remoteChallenge = httpObtainEnglishReading();
                cacheRemoteQuestion(ChallengeType.ENGLISH_READING, remoteChallenge);
            } catch (Exception e) {
                Log.e(TAG, "获取英文阅读题时发生异常", e);
            }
        }).start();
    }

    private void cacheRemoteQuestion(ChallengeType type, String remoteChallenge) throws Exception {
        if (remoteChallenge == null) {
            Log.w(TAG, "获取" + type.getDisplayName() + "失败");
            return;
        }
        JSONObject jsonResponse = new JSONObject(remoteChallenge);
        Question question = new Question(
                jsonResponse.getString("question"),
                jsonResponse.getString("answer"));
        REMOTE_CACHE.put(type, question);
        Log.d(TAG, type.getDisplayName() + "获取成功并已缓存");
    }

    private String httpObtainChallenge(int type) {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            JSONObject request = new JSONObject();
            request.put("type", type);
            request.put("devId", androidId);
            String response = ContentUtils.doHttpPost(
                    CloudConst.DOMAIN_URL + CloudConst.CHALLENGE,
                    request.toString(),
                    java.util.Collections.singletonMap("Accept", "application/json"));
            return ContentUtils.parseRespJson(response);
        } catch (Exception e) {
            Log.e(TAG, "HTTP请求题目异常", e);
            return null;
        }
    }

    private String httpObtainEnglishReading() {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            int readingLength = challengeSettingsManager.getEnglishReadingLength();
            JSONObject request = new JSONObject();
            request.put("devId", androidId);
            request.put("length", readingLength);
            String response = ContentUtils.doHttpPost(
                    CloudConst.DOMAIN_URL + CloudConst.ENGLISH_READING,
                    request.toString(),
                    java.util.Collections.singletonMap("Accept", "application/json"));
            return ContentUtils.parseRespJson(response);
        } catch (Exception e) {
            Log.e(TAG, "HTTP请求英文阅读题异常", e);
            return null;
        }
    }

    static final class Question {
        private final String content;
        private final String answer;

        Question(String content, String answer) {
            this.content = content;
            this.answer = answer;
        }

        String getContent() {
            return content;
        }

        String getAnswer() {
            return answer;
        }
    }
}

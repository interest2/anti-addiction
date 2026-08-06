package com.book.mask.challenge.listening;

import android.content.Context;
import android.util.Log;

import com.book.mask.constant.QuestionConst;
import com.book.mask.reminder.config.ProviderSecretStore;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;
import com.book.mask.reminder.provider.OpenAiChatClient;
import com.book.mask.reminder.provider.ProviderHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 听力题获取与缓存。
 *
 * <p>维护 currentQuestion / nextQuestion 两级缓存：展示当前题时后台预取下一题；换题时直接使用已预取的下一题。
 *
 * <p>仅在用户配置了非默认的模型服务商（自定义 Provider）时调用该服务商的大模型生成一道「略有难度的中文听力选择题」；
 * 官方默认服务商无此能力，直接使用内置题目池兜底，不应因网络不可用而卡死听力题。
 */
public final class ListeningQuestionRepository {

    private static final String TAG = "ListeningQuestion";
    private static final int MAX_TOKENS = 1_024;
    private static final String QUESTION_ID_CUSTOM = "custom";

    private static final String SYSTEM_PROMPT =
            "你是出题助手。请创作一道有难度的中文听力选择题，难度对标高考听力题。\n"
                    + "要求：\n"
                    + "1. 给出一段约 60-80 字的中文听力原文，信息必须密集：至少包含 2-3 个相互关联的细节"
                    + "（数字、时间顺序、人物关系、因果链、数量、条件限制），且这些细节是作答的唯一依据；\n"
                    + "   注意：语音合成单次文本上限为 80 字，原文务必不超过 80 字；\n"
                    + "2. 给出一个针对关键细节区分或语义理解的中文问题，考「听清+理解」，不考常识、不考背景知识；\n"
                    + "3. 给出 4 个中文选项，其中只有一个是正确选项。\n"
                    + "   每个错误选项必须使用原文中出现过的元素（数字/人物/事件/条件）来构造，"
                    + "通过张冠李戴、数量错位、顺序颠倒、方向反置、过度推断或利用原文中的误解等方式产生干扰，"
                    + "禁止出现与原文无关或明显违背常识的送分选项；\n"
                    + "4. 不要使用 Markdown 或任何解释文字，严格按如下 JSON 格式输出：\n"
                    + "{\"transcript\":\"听力原文\",\"question\":\"问题\",\"options\":[\"选项A\",\"选项B\",\"选项C\",\"选项D\"],\"answer\":\"A\"}\n"
                    + "其中 answer 为正确选项的字母（A/B/C/D）。";

    public static final class Question {
        private final String questionId;
        private final ListeningQuestion question;

        Question(String questionId, ListeningQuestion question) {
            this.questionId = questionId == null ? "" : questionId;
            this.question = question;
        }

        public String getQuestionId() {
            return questionId;
        }

        public ListeningQuestion getQuestion() {
            return question;
        }
    }

    private final Random random = new Random();
    private final ReminderProviderConfigStore configStore;
    private final ProviderSecretStore secretStore;
    private final OpenAiChatClient chatClient;

    private Question currentQuestion;
    private Question nextQuestion;

    public ListeningQuestionRepository(Context context) {
        this.configStore = new ReminderProviderConfigStore();
        this.secretStore = new ProviderSecretStore(context);
        this.chatClient = new OpenAiChatClient(new ProviderHttpClient());
    }

    /**
     * 获取当前要作答的题目；当前无题目时先同步取一题，再后台预取下一题。
     */
    public synchronized Question obtainQuestion() {
        if (currentQuestion == null) {
            currentQuestion = fetchQuestion();
        }
        prefetchNext();
        return currentQuestion;
    }

    /**
     * 当前题已作废（答错换题 / 查看原文），前进到下一题。
     */
    public synchronized Question advance() {
        currentQuestion = nextQuestion != null ? nextQuestion : fetchQuestion();
        nextQuestion = null;
        prefetchNext();
        return currentQuestion;
    }

    private void prefetchNext() {
        if (nextQuestion != null) {
            return;
        }
        new Thread(() -> {
            Question question = fetchQuestion();
            synchronized (ListeningQuestionRepository.this) {
                if (question != null) {
                    nextQuestion = question;
                }
            }
        }, "listening-question-prefetch").start();
    }

    private Question fetchQuestion() {
        Question remote = fetchFromProvider();
        if (remote != null) {
            return remote;
        }
        Log.w(TAG, "听力题接口不可用，使用内置题目池兜底");
        return fetchBuiltin();
    }

    /**
     * 从用户配置的大模型服务商获取听力题。
     *
     * <p>仅当用户配置了非默认（自定义）的模型服务商时可用：官方默认服务商无出题接口，直接返回 null 走内置题目池；
     * 自定义服务商则调用其大模型按 JSON 生成听力选择题。
     */
    private Question fetchFromProvider() {
        ReminderProviderConfig config = configStore.getActiveConfig();
        if (config.isOfficial()) {
            Log.d(TAG, "官方默认服务商下听力题接口不可用，跳过网络获取");
            return null;
        }
        try {
            String apiKey = secretStore.getApiKey(config.getProfileId());
            if (apiKey == null || apiKey.isEmpty()) {
                Log.w(TAG, "自定义 Provider 未配置 API Key，听力题接口不可用");
                return null;
            }
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", SYSTEM_PROMPT));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "请生成一道略有难度的中文听力选择题。"));

            String raw = chatClient.complete(config, apiKey, messages, MAX_TOKENS, 0.8);
            ListeningQuestion question = parseQuestion(raw);
            if (question == null) {
                Log.w(TAG, "听力题接口未返回有效题目");
                return null;
            }
            Log.d(TAG, "听力题获取成功，questionId=" + QUESTION_ID_CUSTOM);
            return new Question(QUESTION_ID_CUSTOM, question);
        } catch (OpenAiChatClient.OpenAiChatException e) {
            Log.w(TAG, "听力题接口请求失败: " + e.getMessage());
            return null;
        } catch (GeneralSecurityException e) {
            Log.w(TAG, "读取 API Key 失败", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "获取听力题异常", e);
            return null;
        }
    }

    private Question fetchBuiltin() {
        QuestionConst.ListeningBuiltinQuestion[] pool = QuestionConst.LISTENING_BUILTIN_QUESTIONS;
        if (pool == null || pool.length == 0) {
            return null;
        }
        QuestionConst.ListeningBuiltinQuestion item = pool[random.nextInt(pool.length)];
        List<String> options = new ArrayList<>();
        for (String option : item.options) {
            options.add(option);
        }
        ListeningQuestion question = new ListeningQuestion(
                item.transcript, item.question, options, item.answer);
        return new Question("builtin", question);
    }

    /** 清理大模型输出中可能夹带的 Markdown 代码块与成对引号，只保留 JSON 正文。 */
    private static String cleanJson(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            } else {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        return text.isEmpty() ? null : text;
    }

    /** 解析大模型按 JSON 返回的听力题；解析失败或字段无效时返回 null。 */
    private static ListeningQuestion parseQuestion(String raw) {
        if (raw == null) {
            return null;
        }
        String json = cleanJson(raw);
        if (json == null) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(json);
            String transcript = obj.optString("transcript", "").trim();
            String questionText = obj.optString("question", "").trim();
            JSONArray optionsArray = obj.optJSONArray("options");
            List<String> options = new ArrayList<>();
            if (optionsArray != null) {
                for (int i = 0; i < optionsArray.length(); i++) {
                    String option = optionsArray.optString(i, "").trim();
                    if (!option.isEmpty()) {
                        options.add(option);
                    }
                }
            }
            String answer = obj.optString("answer", "").trim();
            ListeningQuestion question =
                    new ListeningQuestion(transcript, questionText, options, answer);
            return question.isValid() ? question : null;
        } catch (Exception e) {
            Log.w(TAG, "解析听力题 JSON 失败: " + e.getMessage());
            return null;
        }
    }
}

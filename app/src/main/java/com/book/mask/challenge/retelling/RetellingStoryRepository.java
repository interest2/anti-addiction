package com.book.mask.challenge.retelling;

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
import java.util.Random;

/**
 * 复述题故事获取与缓存。
 *
 * <p>维护 currentStory / nextStory 两级缓存：用户阅读当前故事时后台预取下一篇；失败换题时直接
 * 使用已预取的下一篇。首次网络失败时回退内置故事池，不应因故事接口不可用而卡死复述题。
 *
 * <p>故事接口仅在用户配置了非默认的模型服务商（自定义 Provider）时才可用，并调用该服务商的大模型
 * 生成故事；官方默认服务商无故事接口，直接使用内置故事池兜底。
 */
public final class RetellingStoryRepository {

    private static final String TAG = "RetellingStory";
    private static final int MAX_TOKENS = 768;
    private static final String STORY_ID_CUSTOM = "custom";

    private static final String SYSTEM_PROMPT =
            "你是故事创作助手。请用中文创作一个适合复述训练的寓言或哲理小故事。\n"
                    + "要求：\n"
                    + "1. 情节完整，包含「冲突（主角遇到的问题或困难）→ 行动（主角做了什么，含转折）→ 结局（结果与启发）」三个部分；\n"
                    + "2. 有明确寓意，语言口语化、有画面感，适合朗读；\n"
                    + "3. 不要使用 Markdown、标题、编号或任何解释性文字。\n"
                    + "严格按以下标签输出，每个标签独占一行：\n"
                    + "【故事】\n故事正文\n"
                    + "【冲突】\n一句话概括冲突\n"
                    + "【行动】\n一句话概括行动与转折\n"
                    + "【结局】\n一句话概括结局\n"
                    + "【寓意】\n一句话概括寓意";

    public static final class Story {
        private final String storyId;
        private final String story;
        private final String conflict;
        private final String action;
        private final String outcome;
        private final String moral;

        Story(String storyId, String story) {
            this(storyId, story, null, null, null, null);
        }

        Story(String storyId, String story, String conflict, String action,
              String outcome, String moral) {
            this.storyId = storyId == null ? "" : storyId;
            this.story = story;
            this.conflict = conflict;
            this.action = action;
            this.outcome = outcome;
            this.moral = moral;
        }

        public String getStoryId() {
            return storyId;
        }

        public String getStory() {
            return story;
        }

        public String getConflict() {
            return conflict;
        }

        public String getAction() {
            return action;
        }

        public String getOutcome() {
            return outcome;
        }

        public String getMoral() {
            return moral;
        }
    }

    private final Context context;
    private final Random random = new Random();
    private final ReminderProviderConfigStore configStore;
    private final ProviderSecretStore secretStore;
    private final OpenAiChatClient chatClient;

    private Story currentStory;
    private Story nextStory;

    public RetellingStoryRepository(Context context) {
        this.context = context.getApplicationContext();
        this.configStore = new ReminderProviderConfigStore();
        this.secretStore = new ProviderSecretStore(context);
        this.chatClient = new OpenAiChatClient(new ProviderHttpClient());
    }

    /**
     * 获取当前要阅读的故事；当前无故事时先同步取一篇，再后台预取下一篇。
     */
    public synchronized Story obtainStory(int length) {
        if (currentStory == null) {
            currentStory = fetchStory(length);
        }
        prefetchNext(length);
        return currentStory;
    }

    /**
     * 当前故事已作答完毕（失败换题），前进到下一篇。
     */
    public synchronized Story advance(int length) {
        currentStory = nextStory != null ? nextStory : fetchStory(length);
        nextStory = null;
        prefetchNext(length);
        return currentStory;
    }

    private void prefetchNext(final int length) {
        if (nextStory != null) {
            return;
        }
        new Thread(() -> {
            Story story = fetchStory(length);
            synchronized (RetellingStoryRepository.this) {
                if (story != null) {
                    nextStory = story;
                }
            }
        }, "retelling-story-prefetch").start();
    }

    private Story fetchStory(int length) {
        Story remote = fetchFromProvider(length);
        if (remote != null) {
            return remote;
        }
        Log.w(TAG, "故事接口不可用，使用内置故事池兜底");
        return fetchBuiltin();
    }

    /**
     * 从用户配置的大模型服务商获取故事。
     *
     * <p>仅当用户配置了非默认（自定义）的模型服务商时故事接口才可用：官方默认服务商无故事接口，
     * 直接返回 null 走内置故事池兜底；自定义服务商则调用其大模型生成故事。
     */
    private Story fetchFromProvider(int length) {
        ReminderProviderConfig config = configStore.getActiveConfig();
        if (config.isOfficial()) {
            Log.d(TAG, "官方默认服务商下故事接口不可用，跳过网络获取");
            return null;
        }
        try {
            String apiKey = secretStore.getApiKey(config.getProfileId());
            if (apiKey == null || apiKey.isEmpty()) {
                Log.w(TAG, "自定义 Provider 未配置 API Key，故事接口不可用");
                return null;
            }
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", SYSTEM_PROMPT));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "请创作一个约 " + length + " 字的中文故事。"));

            String raw = chatClient.complete(config, apiKey, messages, MAX_TOKENS, 0.8);
            Story story = parseStory(raw, STORY_ID_CUSTOM);
            if (story == null) {
                Log.w(TAG, "故事接口未返回有效故事");
                return null;
            }
            Log.d(TAG, "故事获取成功，storyId=" + STORY_ID_CUSTOM);
            return story;
        } catch (OpenAiChatClient.OpenAiChatException e) {
            Log.w(TAG, "故事接口请求失败: " + e.getMessage());
            return null;
        } catch (GeneralSecurityException e) {
            Log.w(TAG, "读取 API Key 失败", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "获取故事异常", e);
            return null;
        }
    }

    /** 清理大模型输出中可能夹带的 Markdown 代码块与成对引号，只保留故事正文。 */
    private static String cleanStory(String raw) {
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
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"')
                    || (first == '“' && last == '”')
                    || (first == '\'' && last == '\'')) {
                text = text.substring(1, text.length() - 1).trim();
            }
        }
        return text.isEmpty() ? null : text;
    }

    private Story fetchBuiltin() {
        QuestionConst.RetellingBuiltinStory[] pool = QuestionConst.RETELLING_BUILTIN_STORIES;
        if (pool == null || pool.length == 0) {
            return null;
        }
        QuestionConst.RetellingBuiltinStory item = pool[random.nextInt(pool.length)];
        return new Story("builtin", item.story, item.conflict, item.action, item.outcome, item.moral);
    }

    /** 解析大模型按标签返回的故事，回退到纯正文解析（未带标签时整段当作正文）。 */
    private static Story parseStory(String raw, String storyId) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        String story = cleanStory(extractSection(text, "【故事】"));
        if (story == null) {
            story = cleanStory(text);
        }
        if (story == null) {
            return null;
        }
        return new Story(storyId, story,
                extractSection(text, "【冲突】"),
                extractSection(text, "【行动】"),
                extractSection(text, "【结局】"),
                extractSection(text, "【寓意】"));
    }

    /** 截取某标签后的内容，到下一个标签或结尾为止。 */
    private static String extractSection(String raw, String marker) {
        int index = raw.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int start = index + marker.length();
        String[] markers = {"【故事】", "【冲突】", "【行动】", "【结局】", "【寓意】"};
        int end = raw.length();
        for (String next : markers) {
            int nextIndex = raw.indexOf(next, start);
            if (nextIndex >= 0 && nextIndex < end) {
                end = nextIndex;
            }
        }
        String section = raw.substring(start, end).trim();
        while (section.startsWith("：") || section.startsWith(":")
                || section.startsWith(" ")) {
            section = section.substring(1).trim();
        }
        return section.isEmpty() ? null : section;
    }
}

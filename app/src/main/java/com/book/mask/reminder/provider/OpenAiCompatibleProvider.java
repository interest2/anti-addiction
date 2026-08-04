package com.book.mask.reminder.provider;

import android.util.Log;

import com.book.mask.reminder.ProviderResult;
import com.book.mask.reminder.ReminderProvider;
import com.book.mask.reminder.ReminderRequest;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigValidator;
import com.book.mask.reminder.content.ReminderTextPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public final class OpenAiCompatibleProvider implements ReminderProvider {
    private static final String TAG = "OpenAiCompatibleProvider";
    private static final int RESPONSE_PREVIEW_LENGTH = 500;
    private static final int MAX_TOKENS = 1_024;
    private static final String SYSTEM_PROMPT =
            "你是防沉迷提醒助手。请用有力度的中文，生成一条帮助用户停止刷手机、回到目标的提醒。"
                    + "约 60 字；不要使用Markdown、标题、编号、引号或解释；不要带脏话或辱骂用户";

    private final ReminderProviderConfig config;
    private final String apiKey;
    private final OpenAiChatClient chatClient;

    public OpenAiCompatibleProvider(
            ReminderProviderConfig config,
            String apiKey,
            ProviderHttpClient httpClient) {
        this.config = config;
        this.apiKey = apiKey;
        this.chatClient = new OpenAiChatClient(httpClient);
    }

    @Override
    public ProviderResult generate(ReminderRequest request) {
        ReminderProviderConfigValidator.Error validationError =
                ReminderProviderConfigValidator.validate(config, apiKey);
        if (validationError != null) {
            return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_CONFIG);
        }

        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", buildSystemPrompt(request)));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "用户当前目标：" + request.getMotivationTag()));

            String text = chatClient.complete(config, apiKey, messages, MAX_TOKENS, 0.7);
            text = ReminderTextPolicy.normalize(text);
            return text == null
                    ? invalidResponse("normalize 后为空", null)
                    : ProviderResult.success(text);
        } catch (OpenAiChatClient.OpenAiChatException e) {
            return invalidResponse(e.getMessage(), null);
        } catch (IOException e) {
            Log.w(TAG, "Provider 请求发生网络异常", e);
            return ProviderResponseMapper.fromException(e);
        } catch (JSONException e) {
            return invalidResponse("构造 messages 失败：" + e.getMessage(), null);
        } catch (Exception e) {
            Log.e(TAG, "Provider 请求发生未预期异常", e);
            return ProviderResult.failure(ProviderResult.ErrorCode.INTERNAL);
        }
    }

    private String buildSystemPrompt(ReminderRequest request) {
        if ("默认".equals(request.getStyle())) {
            return SYSTEM_PROMPT;
        }
        if ("自定义".equals(request.getStyle())) {
            return SYSTEM_PROMPT + "回答风格要求：" + request.getCustomStyle() + "。";
        }
        return SYSTEM_PROMPT + "回答风格要求：" + request.getStyle() + "。";
    }

    private ProviderResult invalidResponse(String reason, JSONException exception) {
        String message = "无法解析 Provider 响应，原因=" + reason;
        if (exception == null) {
            Log.w(TAG, message);
        } else {
            Log.w(TAG, message, exception);
        }
        return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE, 0, message);
    }
}

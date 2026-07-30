package com.book.mask.network.reminder.provider;

import android.util.Log;

import com.book.mask.network.reminder.ProviderResult;
import com.book.mask.network.reminder.ReminderProvider;
import com.book.mask.network.reminder.ReminderRequest;
import com.book.mask.network.reminder.config.ReminderProviderConfig;
import com.book.mask.network.reminder.config.ReminderProviderConfigValidator;
import com.book.mask.network.reminder.content.ReminderTextPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class OpenAiCompatibleProvider implements ReminderProvider {
    private static final String TAG = "OpenAiCompatibleProvider";
    private static final int RESPONSE_PREVIEW_LENGTH = 500;
    private static final int MAX_TOKENS = 1_024;
    private static final String SYSTEM_PROMPT =
            "你是防沉迷提醒助手。请用简洁、有力度的中文，生成一条帮助用户停止刷手机、回到目标的提醒。"
                    + "最多三行，总长度不超过120个汉字；不要使用Markdown、标题、编号、引号或解释。";

    private final ReminderProviderConfig config;
    private final String apiKey;
    private final ProviderHttpClient httpClient;

    public OpenAiCompatibleProvider(
            ReminderProviderConfig config,
            String apiKey,
            ProviderHttpClient httpClient) {
        this.config = config;
        this.apiKey = apiKey;
        this.httpClient = httpClient;
    }

    @Override
    public ProviderResult generate(ReminderRequest request) {
        ReminderProviderConfigValidator.Error validationError =
                ReminderProviderConfigValidator.validate(config, apiKey);
        if (validationError != null) {
            return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_CONFIG);
        }

        String responseBody = null;
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", buildSystemPrompt(request)));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "用户当前目标：" + request.getMotivationTag()));

            JSONObject requestJson = new JSONObject();
            requestJson.put("model", config.getModel().trim());
            requestJson.put("messages", messages);
            requestJson.put("max_tokens", MAX_TOKENS);
            requestJson.put("stream", false);
            applyReasoningDisabled(requestJson);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey.trim());

            ProviderHttpClient.HttpResponse response = httpClient.postJson(
                    config.getEndpointUrl().trim(),
                    headers,
                    requestJson.toString());
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                Log.w(TAG, "Provider 请求失败，HTTP " + response.getStatusCode()
                        + "，响应前" + RESPONSE_PREVIEW_LENGTH + "字符="
                        + responsePreview(response.getBody()));
                return ProviderResponseMapper.fromHttpStatus(response.getStatusCode());
            }

            responseBody = response.getBody();
            JSONObject responseJson = new JSONObject(responseBody);
            JSONArray choices = responseJson.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return invalidResponse("missing or empty choices", response.getBody(), null);
            }
            JSONObject message = choices.optJSONObject(0) == null
                    ? null
                    : choices.optJSONObject(0).optJSONObject("message");
            if (message == null) {
                return invalidResponse("missing choices[0].message", response.getBody(), null);
            }
            String text = ReminderTextPolicy.normalize(message.optString("content", ""));
            return text == null
                    ? invalidResponse("missing or empty choices[0].message.content", response.getBody(), null)
                    : ProviderResult.success(text);
        } catch (JSONException e) {
            return invalidResponse("invalid JSON", responseBody, e);
        } catch (IOException e) {
            Log.w(TAG, "Provider 请求发生网络异常", e);
            return ProviderResponseMapper.fromException(e);
        } catch (Exception e) {
            Log.e(TAG, "Provider 请求发生未预期异常", e);
            return ProviderResult.failure(ProviderResult.ErrorCode.INTERNAL);
        }
    }

    private void applyReasoningDisabled(JSONObject requestJson) throws JSONException {
        String presetId = config.getPresetId();
        String model = config.getModel().trim();
        if ("moonshot".equals(presetId) && "kimi-k2.6".equals(model)) {
            requestJson.put("thinking", new JSONObject().put("type", "disabled"));
        } else if ("zhipu".equals(presetId)) {
            requestJson.put("thinking", new JSONObject().put("type", "disabled"));
        } else if ("openai".equals(presetId)) {
            requestJson.put("reasoning_effort", "none");
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

    private ProviderResult invalidResponse(String reason, String responseBody, JSONException exception) {
        String message = "无法解析 Provider 响应，原因=" + reason;
        if (responseBody != null) {
            message += "，响应前" + RESPONSE_PREVIEW_LENGTH + "字符="
                    + responsePreview(responseBody);
        }
        if (exception == null) {
            Log.w(TAG, message);
        } else {
            Log.w(TAG, message, exception);
        }
        return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE);
    }

    private String responsePreview(String responseBody) {
        return responseBody.substring(0, Math.min(responseBody.length(), RESPONSE_PREVIEW_LENGTH));
    }
}

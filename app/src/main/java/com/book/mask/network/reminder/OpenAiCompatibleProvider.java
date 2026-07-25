package com.book.mask.network.reminder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class OpenAiCompatibleProvider implements ReminderProvider {
    private static final String SYSTEM_PROMPT =
            "你是防沉迷提醒助手。请用简洁、有力度但不侮辱用户的中文，生成一条帮助用户停止刷手机、回到目标的提醒。"
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
        String validationError = ReminderProviderConfigValidator.validate(config, apiKey);
        if (validationError != null) {
            return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_CONFIG);
        }

        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", SYSTEM_PROMPT));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "用户当前目标：" + request.getMotivationTag()));

            JSONObject requestJson = new JSONObject();
            requestJson.put("model", config.getModel().trim());
            requestJson.put("messages", messages);
            requestJson.put("max_tokens", 180);
            requestJson.put("temperature", 0.8);

            Map<String, String> headers = Collections.emptyMap();
            if (config.getAuthType() == ReminderProviderConfig.AuthType.BEARER) {
                headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + apiKey.trim());
            }

            ProviderHttpClient.HttpResponse response = httpClient.postJson(
                    config.getEndpointUrl().trim(),
                    headers,
                    requestJson.toString());
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                return ProviderResponseMapper.fromHttpStatus(response.getStatusCode());
            }

            JSONObject responseJson = new JSONObject(response.getBody());
            JSONArray choices = responseJson.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE);
            }
            JSONObject message = choices.optJSONObject(0) == null
                    ? null
                    : choices.optJSONObject(0).optJSONObject("message");
            String text = message == null
                    ? null
                    : ReminderTextPolicy.normalize(message.optString("content", ""));
            return text == null
                    ? ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE)
                    : ProviderResult.success(text);
        } catch (JSONException e) {
            return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE);
        } catch (IOException e) {
            return ProviderResponseMapper.fromException(e);
        } catch (Exception e) {
            return ProviderResult.failure(ProviderResult.ErrorCode.INTERNAL);
        }
    }
}

package com.book.mask.reminder.provider;

import android.util.Log;

import com.book.mask.reminder.config.ReminderProviderConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用 OpenAI 兼容 Chat Completions 客户端。
 *
 * <p>从 {@link OpenAiCompatibleProvider} 中抽出的通用层，供「生成提醒语」与「复述题评分」等
 * 多个业务复用 Provider 配置、API Key 存储、HTTP 客户端、模型选择与推理模式关闭逻辑。
 */
public final class OpenAiChatClient {

    private static final String TAG = "OpenAiChatClient";

    /** 请求失败（HTTP 非 2xx、响应结构异常、内容为空）时抛出，message 携带可读原因。 */
    public static final class OpenAiChatException extends Exception {
        public OpenAiChatException(String message) {
            super(message);
        }
    }

    private final ProviderHttpClient httpClient;

    public OpenAiChatClient(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 执行一次非流式 Chat Completions 请求，返回 {@code choices[0].message.content} 去首尾空白后的文本。
     *
     * @throws IOException        网络异常
     * @throws OpenAiChatException HTTP 状态码异常 / JSON 解析失败 / choices 结构缺失或内容为空
     */
    public String complete(
            ReminderProviderConfig config,
            String apiKey,
            JSONArray messages,
            int maxTokens,
            double temperature) throws IOException, OpenAiChatException {
        JSONObject requestJson;
        try {
            requestJson = new JSONObject();
            requestJson.put("model", config.getModel().trim());
            requestJson.put("messages", messages);
            requestJson.put("max_tokens", maxTokens);
            requestJson.put("temperature", temperature);
            requestJson.put("stream", false);
            applyReasoningDisabled(config, requestJson);
        } catch (JSONException e) {
            throw new OpenAiChatException("构造请求失败：" + e.getMessage());
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey.trim());

        ProviderHttpClient.HttpResponse response = httpClient.postJson(
                config.getEndpointUrl().trim(),
                headers,
                requestJson.toString());
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new OpenAiChatException(
                    "HTTP " + response.getStatusCode()
                            + "，响应前200字符=" + preview(response.getBody()));
        }

        try {
            JSONObject responseJson = new JSONObject(response.getBody());
            JSONArray choices = responseJson.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new OpenAiChatException("响应缺少 choices");
            }
            JSONObject message = choices.optJSONObject(0) == null
                    ? null
                    : choices.optJSONObject(0).optJSONObject("message");
            if (message == null) {
                throw new OpenAiChatException("响应缺少 choices[0].message");
            }
            String text = message.optString("content", "");
            if (text.trim().isEmpty()) {
                throw new OpenAiChatException("响应 content 为空");
            }
            return text.trim();
        } catch (JSONException e) {
            throw new OpenAiChatException("解析响应失败：" + e.getMessage()
                    + "，响应前200字符=" + preview(response.getBody()));
        }
    }

    private static void applyReasoningDisabled(ReminderProviderConfig config, JSONObject requestJson)
            throws JSONException {
        String presetId = config.getPresetId();
        String model = config.getModel().trim();
        if ("deepseek".equals(presetId)) {
            requestJson.put("thinking", new JSONObject().put("type", "disabled"));
        } else if ("moonshot".equals(presetId) && "kimi-k2.6".equals(model)) {
            requestJson.put("thinking", new JSONObject().put("type", "disabled"));
        } else if ("zhipu".equals(presetId)) {
            requestJson.put("thinking", new JSONObject().put("type", "disabled"));
        } else if ("openai".equals(presetId)) {
            requestJson.put("reasoning_effort", "none");
        }
    }

    private static String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.substring(0, Math.min(body.length(), 200));
    }
}

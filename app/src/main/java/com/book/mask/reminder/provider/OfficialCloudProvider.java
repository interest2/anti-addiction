package com.book.mask.reminder.provider;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.provider.Settings;
import android.util.Log;

import com.book.mask.constant.CloudConst;
import com.book.mask.reminder.ProviderResult;
import com.book.mask.reminder.ReminderProvider;
import com.book.mask.reminder.ReminderRequest;
import com.book.mask.reminder.content.ReminderTextPolicy;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.Collections;

public final class OfficialCloudProvider implements ReminderProvider {
    private static final String TAG = "OfficialCloudProvider";
    private static final int RESPONSE_PREVIEW_LENGTH = 500;

    private final Context context;
    private final ProviderHttpClient httpClient;

    public OfficialCloudProvider(Context context, ProviderHttpClient httpClient) {
        this.context = context.getApplicationContext();
        this.httpClient = httpClient;
    }

    @Override
    @SuppressLint("HardwareIds")
    public ProviderResult generate(ReminderRequest request) {
        String responseBody = null;
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);

            JSONObject requestJson = new JSONObject();
            requestJson.put("tag", request.getMotivationTag());
            requestJson.put("style", request.getStyle());
            requestJson.put("customStyle", request.getCustomStyle());
            requestJson.put("devId", androidId);
            requestJson.put("version", packageInfo.versionName);

            ProviderHttpClient.HttpResponse response = httpClient.postJson(
                    CloudConst.DOMAIN_URL + CloudConst.LLM_PATH_V2,
                    Collections.emptyMap(),
                    requestJson.toString());
            responseBody = response.getBody();
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                String preview = responsePreview(responseBody);
                Log.w(TAG, "官方云请求失败，HTTP " + response.getStatusCode()
                        + "，响应前" + RESPONSE_PREVIEW_LENGTH + "字符=" + preview);
                return ProviderResponseMapper.fromHttpStatus(response.getStatusCode(), preview);
            }

            JSONObject responseJson = new JSONObject(responseBody);
            int status = responseJson.optInt("status", -1);
            if (status != 0) {
                return invalidResponse("服务端返回失败 status=" + status, responseBody);
            }
            String text = ReminderTextPolicy.normalize(responseJson.optString("data", ""));
            return text == null
                    ? invalidResponse("返回 data 为空", responseBody)
                    : ProviderResult.success(text);
        } catch (JSONException e) {
            return invalidResponse("JSON 解析失败，原因=" + e.getMessage(), responseBody, e);
        } catch (IOException e) {
            Log.w(TAG, "官方云请求发生网络异常", e);
            return ProviderResponseMapper.fromException(e);
        } catch (Exception e) {
            Log.e(TAG, "官方云请求发生未预期异常", e);
            return ProviderResult.failure(ProviderResult.ErrorCode.INTERNAL);
        }
    }

    private ProviderResult invalidResponse(String reason, String responseBody) {
        return invalidResponse(reason, responseBody, null);
    }

    private ProviderResult invalidResponse(String reason, String responseBody, JSONException exception) {
        String message = "无法解析官方云响应，原因=" + reason;
        if (responseBody != null) {
            message += "，响应前" + RESPONSE_PREVIEW_LENGTH + "字符=" + responsePreview(responseBody);
        }
        if (exception == null) {
            Log.w(TAG, message);
        } else {
            Log.w(TAG, message, exception);
        }
        return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE, 0, message);
    }

    private String responsePreview(String responseBody) {
        return responseBody.substring(0, Math.min(responseBody.length(), RESPONSE_PREVIEW_LENGTH));
    }
}

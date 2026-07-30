package com.book.mask.network.reminder.provider;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.provider.Settings;

import com.book.mask.constant.CloudConst;
import com.book.mask.network.reminder.ProviderResult;
import com.book.mask.network.reminder.ReminderProvider;
import com.book.mask.network.reminder.ReminderRequest;
import com.book.mask.network.reminder.content.ReminderTextPolicy;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.Collections;

public final class OfficialCloudProvider implements ReminderProvider {
    private final Context context;
    private final ProviderHttpClient httpClient;

    public OfficialCloudProvider(Context context, ProviderHttpClient httpClient) {
        this.context = context.getApplicationContext();
        this.httpClient = httpClient;
    }

    @Override
    @SuppressLint("HardwareIds")
    public ProviderResult generate(ReminderRequest request) {
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
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                return ProviderResponseMapper.fromHttpStatus(response.getStatusCode());
            }

            JSONObject responseJson = new JSONObject(response.getBody());
            if (responseJson.optInt("status", -1) != 0) {
                return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE);
            }
            String text = ReminderTextPolicy.normalize(responseJson.optString("data", ""));
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

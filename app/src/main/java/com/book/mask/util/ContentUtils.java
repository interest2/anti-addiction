package com.book.mask.util;

import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Random;

public class ContentUtils {
    private static final String TAG = "ContentUtils";

    /**
     * 通用POST请求方法
     * @param urlString 请求地址
     * @param bodyJson 请求体（JSON字符串，可为null）
     * @param headers 请求头（可为null）
     * @return 响应内容字符串
     */
    public static String doHttpPost(String urlString, String bodyJson, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (bodyJson != null) {
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyJson.getBytes("UTF-8"));
                }
            }
            int responseCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        }catch(Exception e){
            Log.e(TAG, "网络调用失败", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 通用 GET 请求方法。
     */
    public static String doHttpGet(String urlString, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(90000);
            conn.setDoInput(true);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP 请求失败，状态码: " + responseCode);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String parseRespJson(String response) throws JSONException {
        if (response == null || response.trim().isEmpty()) {
            Log.w(TAG, "响应内容为空或null");
            return null;
        }
        org.json.JSONObject jsonResponse = new org.json.JSONObject(response);
        if (jsonResponse.has("status")) {
            int status = jsonResponse.getInt("status");
            if (status == 0) {
                String text = jsonResponse.optString("data", "");
                if (!text.isEmpty()) {
                    return text;
                }
            } else {
                String msg = jsonResponse.optString("msg", "未知错误");
                Log.w(TAG, "服务器返回错误状态: " + status + ", 消息: " + msg);
            }
        } else {
            Log.w(TAG, "响应中缺少status字段");
        }
        Log.w(TAG, "响应格式无效或无文字内容");
        return null;
    }
}

package com.book.baisc.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingTextFetcher {
    
    private static final String TAG = "FloatingTextFetcher";
    private static final String PREF_NAME = "floating_text_cache";
    private static final String PREF_KEY_CACHED_TEXT = "cached_text";
    private static final String PREF_KEY_LAST_UPDATE = "last_update";
    
    // 配置接口地址
    private static final String API_URL = "https://www.ratetend.com:5001/antiAddict/llm";
    
    private Context context;
    private ExecutorService executorService;
    private Handler mainHandler;
    private SharedPreferences prefs;
    
    public interface OnTextFetchListener {
        void onTextFetched(String text);
        void onFetchError(String error);
    }
    
    public FloatingTextFetcher(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取缓存的文字内容
     */
    public String getCachedText() {
        String cachedText = prefs.getString(PREF_KEY_CACHED_TEXT, null);
        if (cachedText != null) {
            Log.d(TAG, "获取到缓存文字: " + cachedText);
            return cachedText;
        }
        return "小红书应用正在运行"; // 默认文字
    }
    
    /**
     * 获取上次更新时间
     */
    public long getLastUpdateTime() {
        return prefs.getLong(PREF_KEY_LAST_UPDATE, 0);
    }
    
    /**
     * 异步获取最新的文字内容
     */
    public void fetchLatestText(OnTextFetchListener listener) {
        Log.d(TAG, "开始获取最新文字内容");
        
        executorService.execute(() -> {
            try {
                String result = performHttpRequest();
                
                mainHandler.post(() -> {
                    if (result != null) {
                        // 缓存新文字
                        cacheText(result);
                        Log.d(TAG, "获取到新文字: " + result);
                        if (listener != null) {
                            listener.onTextFetched(result);
                        }
                    } else {
                        Log.w(TAG, "获取文字失败，使用缓存文字");
                        if (listener != null) {
                            listener.onFetchError("获取文字失败");
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "获取文字时发生异常", e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onFetchError("网络异常: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    /**
     * 执行HTTP请求
     */
    private String performHttpRequest() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL + "?tag=找工作");
            connection = (HttpURLConnection) url.openConnection();
            
            // 设置请求方法和属性
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoInput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            // GET请求不需要发送请求体，参数已在URL中
            
            // 获取响应
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String responseBody = response.toString();
                    Log.d(TAG, "HTTP响应内容: " + responseBody);
                    
                    // 解析响应
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    // 检查status状态
                    if (jsonResponse.has("status")) {
                        int status = jsonResponse.getInt("status");
                        if (status == 0) {
                            // 从data字段提取文字内容
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
            } else {
                Log.w(TAG, "HTTP请求失败，响应码: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "HTTP请求异常", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 缓存文字内容
     */
    private void cacheText(String text) {
        try {
            prefs.edit()
                    .putString(PREF_KEY_CACHED_TEXT, text)
                    .putLong(PREF_KEY_LAST_UPDATE, System.currentTimeMillis())
                    .apply();
            Log.d(TAG, "文字已缓存: " + text);
        } catch (Exception e) {
            Log.e(TAG, "缓存文字失败", e);
        }
    }
    
    /**
     * 检查是否需要更新文字（可选的优化）
     */
    public boolean shouldUpdateText() {
        long lastUpdate = getLastUpdateTime();
        long now = System.currentTimeMillis();
        long timeDiff = now - lastUpdate;
        
        // 如果距离上次更新超过5分钟，则需要更新
        return timeDiff > 5 * 60 * 1000;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
} 
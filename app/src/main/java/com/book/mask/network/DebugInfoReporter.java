package com.book.mask.network;

import android.content.Context;
import android.util.Log;

import com.book.mask.config.Const;
import com.book.mask.config.Share;
import com.book.mask.util.ContentUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 调试信息上报器
 * 负责收集调试信息并定期上报到云端
 */
public class DebugInfoReporter {
    
    private static final String TAG = "DebugInfoReporter";
    
    private Context context;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private boolean isReporting = false;
    
    public DebugInfoReporter(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * 开始定期上报调试信息
     * @param intervalSeconds 上报间隔（秒）
     */
    public void startPeriodicReporting(int intervalSeconds) {
        if (isReporting) {
            Log.w(TAG, "调试信息上报已在进行中，跳过重复启动");
            return;
        }
        
        Log.d(TAG, "开始定期上报调试信息，间隔: " + intervalSeconds + "秒");
        isReporting = true;
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                reportDebugInfo();
            } catch (Exception e) {
                Log.e(TAG, "定期上报调试信息失败", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 停止定期上报
     */
    public void stopPeriodicReporting() {
        if (!isReporting) {
            Log.w(TAG, "调试信息上报未在进行中");
            return;
        }
        
        Log.d(TAG, "停止定期上报调试信息");
        isReporting = false;
        scheduler.shutdown();
    }
    
    /**
     * 立即上报一次调试信息
     */
    public void reportDebugInfo() {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "网络不可用，跳过调试信息上报");
            return;
        }
        
        executor.execute(() -> {
            try {
                JSONObject debugInfo = collectDebugInfo();
                sendDebugInfo(debugInfo);
            } catch (Exception e) {
                Log.e(TAG, "上报调试信息失败", e);
            }
        });
    }
    
    /**
     * 收集调试信息
     */
    private JSONObject collectDebugInfo() throws JSONException {
        JSONObject debugInfo = new JSONObject();
        
        // 时间戳
        long currentTime = System.currentTimeMillis();
        debugInfo.put("timestamp", currentTime);
        debugInfo.put("reportTime", formatTime(currentTime));
        
        // 基本调试信息
        debugInfo.put("lastEventType", Share.lastEventType);
        debugInfo.put("lastEventTime", Share.lastEventTime);
        debugInfo.put("lastEventTimeFormatted", Share.lastEventTime > 0 ? formatTime(Share.lastEventTime) : "无");
        debugInfo.put("findTextInNodeTime", Share.findTextInNodeTime);
        debugInfo.put("findTextInNodeTimeFormatted", Share.findTextInNodeTime > 0 ? formatTime(Share.findTextInNodeTime) : "无");
        
        // 调试时间戳变量
        debugInfo.put("h0", Share.h0);
        debugInfo.put("h0Formatted", Share.h0 > 0 ? formatTime(Share.h0) : "无");
        debugInfo.put("h1", Share.h1);
        debugInfo.put("h1Formatted", Share.h1 > 0 ? formatTime(Share.h1) : "无");
        debugInfo.put("h7", Share.h7);
        debugInfo.put("h7Formatted", Share.h7 > 0 ? formatTime(Share.h7) : "无");
        debugInfo.put("h8", Share.h8);
        debugInfo.put("h8Formatted", Share.h8 > 0 ? formatTime(Share.h8) : "无");
        
        // checkTextContentOptimized 方法调试变量
        debugInfo.put("currentInterface", Share.currentInterface);
        debugInfo.put("forceCheck", Share.forceCheck);
        
        // 当前APP信息
        JSONObject currentAppInfo = new JSONObject();
        if(Share.currentApp!=null){
            currentAppInfo.put("appName", Share.currentApp.getAppName());
            currentAppInfo.put("packageName", Share.currentApp.getPackageName());
        }else {
            currentAppInfo.put("packageName", Share.packageName);
        }
        currentAppInfo.put("className", Share.className);

        debugInfo.put("currentApp", currentAppInfo);

        Log.d(TAG, "收集调试信息完成: " + debugInfo.toString());
        return debugInfo;
    }
    
    /**
     * 格式化时间戳
     */
    private String formatTime(long timestamp) {
        if (timestamp == 0) return "无";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return formatter.format(new Date(timestamp));
    }
    
    /**
     * 发送调试信息到云端
     */
    private void sendDebugInfo(JSONObject debugInfo) throws IOException {
        try {
            String response = ContentUtils.doHttpPost(
                Const.DOMAIN_URL + Const.DEBUG_PATH, // 使用专门的调试接口
                debugInfo.toString(),
                java.util.Collections.singletonMap("Content-Type", Const.CONTENT_TYPE)
            );
            Log.d(TAG, "调试信息上报响应: " + response);
            onReportSuccess(response);
        } catch (IOException e) {
            Log.e(TAG, "调试信息网络请求失败", e);
            onReportFailure("网络异常: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 检查网络是否可用
     */
    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        
        android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
    
    /**
     * 上报成功回调
     */
    private void onReportSuccess(String response) {
        Log.i(TAG, "调试信息上报成功");
        // 可以在这里添加成功后的处理逻辑
    }
    
    /**
     * 上报失败回调
     */
    private void onReportFailure(String error) {
        Log.w(TAG, "调试信息上报失败: " + error);
        // 可以在这里添加失败后的处理逻辑，比如重试机制
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopPeriodicReporting();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
} 
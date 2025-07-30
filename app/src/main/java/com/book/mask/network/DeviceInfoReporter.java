package com.book.mask.network;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

import com.book.mask.config.Const;
import com.book.mask.util.ContentUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备信息上报器
 * 负责收集设备信息并上报到云端
 */
public class DeviceInfoReporter {
    
    private static final String TAG = "DeviceInfoReporter";
    
    private static final int TIMEOUT_CONNECT = 10000; // 10秒连接超时
    private static final int TIMEOUT_READ = 15000; // 15秒读取超时
    
    private Context context;
    private ExecutorService executor;
    
    public DeviceInfoReporter(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 上报设备信息
     */
    public void reportDeviceInfo() {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "网络不可用，跳过设备信息上报");
            return;
        }
        
        executor.execute(() -> {
            try {
                JSONObject deviceInfo = collectDeviceInfo();
                sendDeviceInfo(deviceInfo);
            } catch (Exception e) {
                Log.e(TAG, "上报设备信息失败", e);
            }
        });
    }
    
    /**
     * 收集设备信息
     */
    private JSONObject collectDeviceInfo() throws JSONException {
        JSONObject deviceInfo = new JSONObject();
        
        // 基本设备信息
        deviceInfo.put("brand", Build.BRAND); // 品牌
        deviceInfo.put("model", Build.MODEL); // 型号
        deviceInfo.put("device", Build.DEVICE); // 设备代号

        // 系统版本信息
        deviceInfo.put("androidVersion", Build.VERSION.RELEASE); // Android版本
        deviceInfo.put("sdkVersion", Build.VERSION.SDK_INT); // SDK版本
        deviceInfo.put("cpuAbi", Build.CPU_ABI); // CPU架构
        
        // 唯一标识符
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceInfo.put("androidId", androidId);
        
        // 应用信息
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            deviceInfo.put("appVersion", packageInfo.versionName);
            deviceInfo.put("appVersionCode", packageInfo.versionCode);
            deviceInfo.put("appPackage", packageInfo.packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "获取应用信息失败", e);
        }
        
        // 网络信息
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null) {
                deviceInfo.put("networkType", networkInfo.getTypeName());
                deviceInfo.put("networkConnected", networkInfo.isConnected());
            }
        }
        
        // 时间戳
        deviceInfo.put("timestamp", System.currentTimeMillis());
        
        Log.d(TAG, "收集设备信息完成: " + deviceInfo.toString());
        return deviceInfo;
    }
    
    /**
     * 获取序列号（处理不同Android版本的权限问题）
     */
    private String getSerialNumber() {
        String serialNumber = "unknown";
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上需要READ_PHONE_STATE权限
                if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) 
                    == PackageManager.PERMISSION_GRANTED) {
                    serialNumber = Build.getSerial();
                } else {
                    // 没有权限，使用替代方案
                    serialNumber = "no_permission_" + Build.FINGERPRINT.hashCode();
                }
            } else {
                // Android 8.0以下可以直接获取
                serialNumber = Build.SERIAL;
            }
        } catch (Exception e) {
            Log.w(TAG, "获取序列号失败，使用备用方案", e);
            // 使用设备指纹作为备用方案
            serialNumber = "fallback_" + Build.FINGERPRINT.hashCode();
        }
        
        return serialNumber;
    }
    
    /**
     * 发送设备信息到云端
     */
    private void sendDeviceInfo(JSONObject deviceInfo) throws IOException {
        try {
            String response = ContentUtils.doHttpPost(
                Const.DOMAIN_URL + Const.REPORT_PATH,
                deviceInfo.toString(),
                java.util.Collections.singletonMap("Content-Type", Const.CONTENT_TYPE)
            );
            Log.d(TAG, "设备信息上报响应: " + response);
            onReportSuccess(response);
        } catch (IOException e) {
            Log.e(TAG, "网络请求失败", e);
            onReportFailure("网络异常: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 检查网络是否可用
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
    
    /**
     * 获取应用版本
     */
    private String getAppVersion() {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
    
    /**
     * 上报成功回调
     */
    private void onReportSuccess(String response) {
        Log.i(TAG, "设备信息上报成功");
        // 可以在这里添加成功后的处理逻辑
    }
    
    /**
     * 上报失败回调
     */
    private void onReportFailure(String error) {
        Log.w(TAG, "设备信息上报失败: " + error);
        // 可以在这里添加失败后的处理逻辑，比如重试机制
    }
    
    /**
     * 设置云端接口地址
     */
    public static void setReportUrl(String url) {
        // 这里可以添加动态设置接口地址的逻辑
        Log.d(TAG, "设置上报接口地址: " + url);
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
} 
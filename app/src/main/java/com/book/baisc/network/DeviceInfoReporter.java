package com.book.baisc.network;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

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
    
    // 云端接口配置
    private static final String REPORT_URL = "https://www.ratetend.com:5001/antiAddict/report"; // 请替换为实际的接口地址
    private static final String CONTENT_TYPE = "application/json";
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
        deviceInfo.put("manufacturer", Build.MANUFACTURER); // 制造商
        deviceInfo.put("device", Build.DEVICE); // 设备代号

        // 系统版本信息
        deviceInfo.put("androidVersion", Build.VERSION.RELEASE); // Android版本
        deviceInfo.put("sdkVersion", Build.VERSION.SDK_INT); // SDK版本
        deviceInfo.put("buildId", Build.ID); // 构建ID
        deviceInfo.put("buildTime", Build.TIME); // 构建时间
        
        // 硬件信息
        deviceInfo.put("hardware", Build.HARDWARE); // 硬件信息
        deviceInfo.put("cpuAbi", Build.CPU_ABI); // CPU架构
        
        // 序列号（处理权限问题）
        String serialNumber = getSerialNumber();
        deviceInfo.put("serialNumber", serialNumber);
        
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
        HttpURLConnection connection = null;
        BufferedWriter writer = null;
        BufferedReader reader = null;
        
        try {
            // 创建连接
            URL url = new URL(REPORT_URL);
            connection = (HttpURLConnection) url.openConnection();
            
            // 设置请求方法和属性
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", CONTENT_TYPE);
            connection.setRequestProperty("User-Agent", "AndroidApp/" + getAppVersion());
            connection.setConnectTimeout(TIMEOUT_CONNECT);
            connection.setReadTimeout(TIMEOUT_READ);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            
            // 发送数据
            writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            writer.write(deviceInfo.toString());
            writer.flush();
            
            // 读取响应
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                Log.d(TAG, "设备信息上报成功: " + response.toString());
                onReportSuccess(response.toString());
            } else {
                Log.w(TAG, "设备信息上报失败，响应码: " + responseCode);
                onReportFailure("HTTP错误: " + responseCode);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "网络请求失败", e);
            onReportFailure("网络异常: " + e.getMessage());
            throw e;
        } finally {
            // 关闭资源
            try {
                if (writer != null) writer.close();
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (IOException e) {
                Log.w(TAG, "关闭连接资源失败", e);
            }
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
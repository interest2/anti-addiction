package com.book.mask.lifecycle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.book.mask.config.Const;
import com.book.mask.floating.FloatService;

public class ServiceKeepAliveManager {
    
    private static final String TAG = "ServiceKeepAlive";
    private Context context;
    private Handler handler;
    private BroadcastReceiver screenReceiver;
    private boolean isRegistered = false;
    
    public interface OnServiceStateListener {
        void onScreenUnlocked();
        void onUserPresent();
        void onServiceNeedRestart();
    }
    
    private OnServiceStateListener listener;
    
    public ServiceKeepAliveManager(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        initScreenReceiver();
    }
    
    public void setOnServiceStateListener(OnServiceStateListener listener) {
        this.listener = listener;
    }
    
    public void startKeepAlive() {
        if (!isRegistered) {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_USER_PRESENT);
                filter.addAction(Intent.ACTION_BOOT_COMPLETED);
                
                context.registerReceiver(screenReceiver, filter);
                isRegistered = true;
                Log.d(TAG, "保活机制已启动");
            } catch (Exception e) {
                Log.e(TAG, "启动保活机制失败", e);
            }
        }
    }
    
    public void stopKeepAlive() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(screenReceiver);
                isRegistered = false;
                Log.d(TAG, "保活机制已停止");
            } catch (Exception e) {
                Log.e(TAG, "停止保活机制失败", e);
            }
        }
    }
    
    private void initScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "收到系统广播: " + action);
                
                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    handleUserPresent();
                } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    handleBootCompleted();
                }
            }
        };
    }
    
    private void handleUserPresent() {
        handler.postDelayed(() -> {
            if (!FloatService.isServiceRunning()) {
                Log.w(TAG, "检测到AccessibilityService未运行，尝试恢复");
                if (listener != null) {
                    listener.onServiceNeedRestart();
                }
            } else {
                if (listener != null) {
                    listener.onUserPresent();
                }
            }
        }, 2000);
    }
    
    private void handleBootCompleted() {
        handler.postDelayed(() -> {
            if (listener != null) {
                listener.onServiceNeedRestart();
            }
        }, 5000);
    }
    
    public void startPeriodicCheck() {
        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!FloatService.isServiceRunning()) {
                    Log.w(TAG, "定期检查发现AccessibilityService未运行");
                    if (listener != null) {
                        listener.onServiceNeedRestart();
                    }
                }
                handler.postDelayed(this, Const.CHECK_SERVICE_RUNNING_DELAY);
            }
        };
        handler.postDelayed(checkRunnable, Const.CHECK_SERVICE_RUNNING_DELAY);
    }
} 
package com.book.baisc;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

public class AppLifecycleObserver implements DefaultLifecycleObserver {

    private Context context;

    public AppLifecycleObserver(Context context) {
        this.context = context;
        // 注册生命周期观察者
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        android.util.Log.d("AppLifecycleObserver", "应用生命周期观察者已初始化");
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        // 应用进入前台
        android.util.Log.d("AppLifecycleObserver", "应用进入前台");
        // 悬浮窗功能现在由AccessibilityService自动管理，无需手动启动
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // 应用进入后台  
        android.util.Log.d("AppLifecycleObserver", "应用进入后台");
        // 悬浮窗功能现在由AccessibilityService自动管理，无需手动启动
    }
} 
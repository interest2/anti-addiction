package com.book.mask;

import android.app.Application;
import android.util.Log;

import com.book.mask.config.CustomAppManager;
import com.tencent.mmkv.MMKV;

/**
 * 进程级初始化入口，保证 Activity、无障碍服务和壁纸服务共享同一套已加载配置。
 */
public class MaskApplication extends Application {

    private static final String TAG = "MaskApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        String rootDir = MMKV.initialize(this);
        Log.d(TAG, "MMKV initialized, root: " + rootDir);
        CustomAppManager.initialize(this);
    }
}

package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;

import androidx.annotation.RequiresApi;

import com.book.mask.personalize.AutoScreenshotSettings;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** 每 10 秒通过无障碍服务截取一次当前屏幕，并把图片保存到系统相册。 */
public final class AutoScreenshotManager {
    private static final String TAG = "AutoScreenshot";
    public static final long SCREENSHOT_INTERVAL_MS = 10_000L;
    public static final int MIN_SUPPORTED_SDK = Build.VERSION_CODES.P;
    private static final String SCREENSHOT_DIRECTORY = "Pictures/AntiAddiction";

    private final AccessibilityService service;
    private final AutoScreenshotSettings settings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService imageWriter = Executors.newSingleThreadExecutor();

    private boolean running;
    private volatile boolean screenshotInProgress;

    private final Runnable screenshotTask = new Runnable() {
        @Override
        public void run() {
            if (!running || !settings.isEnabled()) {
                return;
            }
            mainHandler.postDelayed(this, SCREENSHOT_INTERVAL_MS);
            captureScreen();
        }
    };

    public AutoScreenshotManager(AccessibilityService service) {
        this.service = service;
        this.settings = new AutoScreenshotSettings();
    }

    /** 按最新持久化开关重新启动或停止定时任务。 */
    public void refresh() {
        mainHandler.removeCallbacks(screenshotTask);
        running = settings.isEnabled();
        if (running) {
            mainHandler.postDelayed(screenshotTask, SCREENSHOT_INTERVAL_MS);
            Log.i(TAG, "自动截图已开启，10 秒后执行首次截图");
        } else {
            Log.i(TAG, "自动截图已关闭");
        }
    }

    public void stop() {
        running = false;
        mainHandler.removeCallbacks(screenshotTask);
        imageWriter.shutdown();
    }

    private void captureScreen() {
        if (screenshotInProgress) {
            recordFailure("上一次截图仍在保存");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            screenshotInProgress = true;
            captureWithScreenshotApi();
            return;
        }
        if (Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK) {
            boolean accepted = service.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
            if (accepted) {
                settings.recordSuccess(System.currentTimeMillis());
                Log.i(TAG, "系统截图动作执行成功");
            } else {
                recordFailure("系统拒绝了截图请求");
            }
            return;
        }
        recordFailure("当前系统版本不支持自动截图");
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void captureWithScreenshotApi() {
        service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainHandler::post,
                new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(AccessibilityService.ScreenshotResult result) {
                        // 服务可能在截图请求发出后、回调返回前就被销毁（imageWriter 已 shutdown）；
                        // 此时不再提交保存任务，但必须显式关闭 HardwareBuffer，否则会泄漏。
                        try {
                            imageWriter.execute(() -> saveScreenshot(result));
                        } catch (RejectedExecutionException e) {
                            Log.w(TAG, "服务已销毁，丢弃本次截图");
                            result.getHardwareBuffer().close();
                            screenshotInProgress = false;
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        screenshotInProgress = false;
                        recordFailure(describeScreenshotError(errorCode));
                    }
                });
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void saveScreenshot(AccessibilityService.ScreenshotResult result) {
        Bitmap screenshot = null;
        HardwareBuffer buffer = result.getHardwareBuffer();
        try {
            Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (hardwareBitmap == null) {
                throw new IOException("无法读取截图像素");
            }
            screenshot = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (screenshot == null) {
                throw new IOException("无法转换截图格式");
            }
            saveToGallery(screenshot);
            settings.recordSuccess(System.currentTimeMillis());
            Log.i(TAG, "截图已保存到 " + SCREENSHOT_DIRECTORY);
        } catch (Exception e) {
            Log.e(TAG, "保存截图失败", e);
            recordFailure("保存到相册失败");
        } finally {
            if (screenshot != null) {
                screenshot.recycle();
            }
            buffer.close();
            screenshotInProgress = false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void saveToGallery(Bitmap screenshot) throws IOException {
        String fileName = "AntiAddiction_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date())
                + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, SCREENSHOT_DIRECTORY);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        ContentResolver resolver = service.getContentResolver();
        Uri collection = MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri imageUri = resolver.insert(collection, values);
        if (imageUri == null) {
            throw new IOException("无法创建相册图片");
        }

        boolean saved = false;
        try (OutputStream outputStream = resolver.openOutputStream(imageUri, "w")) {
            if (outputStream == null || !screenshot.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("无法写入截图文件");
            }
            saved = true;
        } finally {
            if (!saved) {
                resolver.delete(imageUri, null, null);
            }
        }

        ContentValues readyValues = new ContentValues();
        readyValues.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(imageUri, readyValues, null, null);
    }

    private void recordFailure(String error) {
        settings.recordFailure(System.currentTimeMillis(), error);
        Log.w(TAG, "自动截图失败: " + error);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static String describeScreenshotError(int errorCode) {
        switch (errorCode) {
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS:
                return "无障碍截图权限不可用";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT:
                return "截图请求过于频繁";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY:
                return "无法识别当前屏幕";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW:
                return "当前窗口无法截图";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW:
                return "当前页面禁止截图";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR:
            default:
                return "系统截图服务出错（" + errorCode + "）";
        }
    }
}

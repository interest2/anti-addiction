package com.book.mask.lifecycle;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * 保活用动态壁纸：把用户自选的照片作为桌面壁纸显示。
 * <p>
 * 设为桌面壁纸后，system_server 会绑定本服务，进程被归入 wallpaper 优先级组，
 * 省电模式 / 一键清理下更不易被回收；即便进程仍被杀，系统也会重新绑定把进程拉起，
 * 无障碍服务随之恢复。因此本服务<b>必须与 {@code FloatService} 同进程</b>
 * （清单里不得为它声明 android:process），否则保活对无障碍服务没有意义。
 * <p>
 * 这是概率上的改善而非免疫：部分厂商的深度冻结会连壁纸一起冻，个别 ROM 在省电模式下
 * 直接停用第三方动态壁纸。
 */
public class WallpaperKeepAliveService extends WallpaperService {

    private static final String TAG = "WallpaperKeepAlive";

    /** 照片缺失或解码失败时的兜底底色，绝不让引擎因异常崩溃而被系统换回默认壁纸。 */
    private static final int FALLBACK_COLOR = 0xFF1A1A1A;

    /**
     * 系统当前的壁纸是否正是本服务。
     * <p>
     * 以桌面壁纸为准，另在 Android 13+ 补查锁屏：个别 ROM 的壁纸确认界面措辞混乱，
     * 可能把动态壁纸挂到锁屏而非桌面，而保活只看服务有没有被系统绑定，挂在哪边都算数。
     */
    public static boolean isActive(Context context) {
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            if (isSelf(context, manager.getWallpaperInfo())) {
                return true;
            }
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && isSelf(context, manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK));
        } catch (Exception e) {
            Log.w(TAG, "读取当前壁纸信息失败", e);
            return false;
        }
    }

    private static boolean isSelf(Context context, WallpaperInfo info) {
        return info != null
                && context.getPackageName().equals(info.getPackageName())
                && WallpaperKeepAliveService.class.getName().equals(info.getServiceName());
    }

    /**
     * 第三方应用无法静默替换壁纸，只能跳到系统预览页由用户确认。
     *
     * @return 供 {@code startActivity} 使用的意图，按系统支持程度从直接预览退到壁纸选择器
     */
    public static Intent buildApplyIntent(Context context) {
        Intent preview = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        preview.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(context, WallpaperKeepAliveService.class)
        );
        return preview;
    }

    public static Intent buildChooserIntent() {
        return new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
    }

    @Override
    public Engine onCreateEngine() {
        return new KeepAliveEngine();
    }

    private class KeepAliveEngine extends Engine
            implements WallpaperImageStore.OnImageChangedListener {

        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        private int surfaceWidth;
        private int surfaceHeight;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
            WallpaperImageStore.addListener(this);
        }

        @Override
        public void onDestroy() {
            WallpaperImageStore.removeListener(this);
            mainHandler.removeCallbacksAndMessages(null);
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceWidth = width;
            surfaceHeight = height;
            drawFrame();
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                drawFrame();
            }
        }

        @Override
        public void onWallpaperImageChanged() {
            // 换图来自界面所在的后台线程，绘制须回到主线程
            mainHandler.post(this::drawFrame);
        }

        /**
         * 静态壁纸只在表面变化 / 重新可见 / 换图时各画一次，不做动画，
         * 既避免真的耗电，也避免被系统判定为耗电应用而适得其反。
         */
        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            if (holder == null || surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }

            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    draw(canvas);
                }
            } catch (Throwable t) {
                Log.e(TAG, "绘制壁纸失败", t);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.w(TAG, "提交壁纸画面失败", e);
                    }
                }
            }
        }

        private void draw(Canvas canvas) {
            canvas.drawColor(FALLBACK_COLOR);
            // 位图由所有引擎共用，此处不得 recycle
            Bitmap bitmap = WallpaperImageStore.loadBitmap(getApplicationContext());
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            canvas.drawBitmap(bitmap, buildCenterCropMatrix(bitmap), paint);
        }

        private Matrix buildCenterCropMatrix(Bitmap bitmap) {
            float scale = Math.max(
                    (float) surfaceWidth / bitmap.getWidth(),
                    (float) surfaceHeight / bitmap.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(
                    (surfaceWidth - bitmap.getWidth() * scale) / 2f,
                    (surfaceHeight - bitmap.getHeight() * scale) / 2f);
            return matrix;
        }
    }
}

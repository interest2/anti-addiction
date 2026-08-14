package com.book.mask.lifecycle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 壁纸保活所用照片的本地存储。
 * <p>
 * 用户所选照片会被降采样、按 EXIF 摆正方向后复制到应用私有目录，壁纸引擎只读这份私有文件：
 * 一是 SAF 的 Uri 授权重启后即失效，引擎届时读不到原图；二是照片全程不出本机，不经任何网络。
 * <p>
 * 解码与压缩这类重活刻意放在 UI 进程（调用方在后台线程执行），壁纸引擎只读已压到屏幕尺寸的成品，
 * 因为壁纸进程内存配额很紧，而壁纸引擎一旦 OOM 崩溃，系统会把桌面换回默认壁纸，保活随之失效。
 */
public final class WallpaperImageStore {

    private static final String TAG = "WallpaperImageStore";

    private static final String DIR_NAME = "wallpaper";
    private static final String IMAGE_FILE_NAME = "wallpaper.jpg";
    private static final String TEMP_FILE_NAME = "wallpaper.tmp";
    private static final int JPEG_QUALITY = 90;
    private static final int SOLID_COLOR_SIZE = 64;
    private static final int SOLID_COLOR_QUALITY = 100;

    /** 桌面 / 锁屏 / 预览可能同时存在多个壁纸引擎，共用同一份位图，避免各自解码一份。 */
    private static SoftReference<Bitmap> cachedBitmap = new SoftReference<>(null);
    private static long cachedFileStamp;

    private static final CopyOnWriteArrayList<OnImageChangedListener> LISTENERS =
            new CopyOnWriteArrayList<>();

    public interface OnImageChangedListener {
        void onWallpaperImageChanged();
    }

    private WallpaperImageStore() {
    }

    public static File getImageFile(Context context) {
        return new File(context.getFilesDir(), DIR_NAME + File.separator + IMAGE_FILE_NAME);
    }

    public static boolean hasImage(Context context) {
        File file = getImageFile(context);
        return file.isFile() && file.length() > 0;
    }

    public static void addListener(OnImageChangedListener listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(OnImageChangedListener listener) {
        LISTENERS.remove(listener);
    }

    /**
     * 把用户所选照片处理后写入应用私有目录。耗时操作，须在后台线程调用。
     */
    public static void saveFromUri(Context context, Uri source) throws IOException {
        Bitmap bitmap = decodeScaled(context, source);
        try {
            writeBitmap(context, bitmap, JPEG_QUALITY);
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * 存一张纯色壁纸。纯色无需大图，存成小方块、由绘制时裁切放大即可，
     * 壁纸进程几乎不占内存。
     */
    public static void saveSolidColor(Context context, int color) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(
                SOLID_COLOR_SIZE, SOLID_COLOR_SIZE, Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(color);
            writeBitmap(context, bitmap, SOLID_COLOR_QUALITY);
        } finally {
            bitmap.recycle();
        }
    }

    public static void clear(Context context) {
        File file = getImageFile(context);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "删除壁纸照片失败");
        }
        onImageFileChanged();
    }

    /**
     * 供壁纸引擎读取当前壁纸位图，返回值由所有引擎共用，调用方不得 recycle。
     *
     * @return 未选照片或解码失败时返回 null，由调用方绘制兜底画面
     */
    public static synchronized Bitmap loadBitmap(Context context) {
        File file = getImageFile(context);
        if (!file.isFile() || file.length() <= 0) {
            return null;
        }

        long stamp = file.lastModified() * 31 + file.length();
        Bitmap cached = cachedBitmap.get();
        if (cached != null && !cached.isRecycled() && stamp == cachedFileStamp) {
            return cached;
        }

        Bitmap bitmap = decodeFile(context, file);
        cachedBitmap = new SoftReference<>(bitmap);
        cachedFileStamp = stamp;
        return bitmap;
    }

    private static void writeBitmap(Context context, Bitmap bitmap, int quality) throws IOException {
        File imageFile = getImageFile(context);
        File dir = imageFile.getParentFile();
        if (dir == null) {
            throw new IOException("壁纸目录路径无效");
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("无法创建壁纸目录");
        }

        File tempFile = new File(dir, TEMP_FILE_NAME);
        try {
            try (OutputStream out = new FileOutputStream(tempFile)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw new IOException("图片压缩失败");
                }
            }
            // 先写临时文件再整体替换，避免壁纸引擎读到只写了一半的文件
            replaceFile(tempFile, imageFile);
        } finally {
            tempFile.delete();
        }
        onImageFileChanged();
    }

    private static void replaceFile(File tempFile, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("无法覆盖已有壁纸照片");
        }
        if (!tempFile.renameTo(target)) {
            throw new IOException("无法保存壁纸照片");
        }
    }

    private static void onImageFileChanged() {
        synchronized (WallpaperImageStore.class) {
            cachedBitmap = new SoftReference<>(null);
            cachedFileStamp = 0L;
        }
        for (OnImageChangedListener listener : LISTENERS) {
            try {
                listener.onWallpaperImageChanged();
            } catch (Exception e) {
                // 单个监听器出错不得影响其余监听器与调用方
                Log.w(TAG, "通知壁纸刷新失败", e);
            }
        }
    }

    private static Bitmap decodeScaled(Context context, Uri source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = openStream(context, source)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("照片尺寸无效");
        }

        Point target = getTargetSize(context);
        int rotation = readRotation(context, source);
        // 旋转 90 / 270 度后宽高互换，降采样的目标尺寸需同步互换才算得准
        boolean swapped = rotation % 180 != 0;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                swapped ? target.y : target.x,
                swapped ? target.x : target.y
        );

        Bitmap decoded;
        try (InputStream in = openStream(context, source)) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) {
            throw new IOException("照片解码失败");
        }
        return transform(decoded, rotation, target);
    }

    private static Bitmap decodeFile(Context context, File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            Point target = getTargetSize(context);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = computeSampleSize(
                    bounds.outWidth, bounds.outHeight, target.x, target.y);
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            // 连 OutOfMemoryError 一并兜住：宁可画兜底色，也不能让壁纸引擎崩溃
            Log.e(TAG, "解码壁纸照片失败", t);
            return null;
        }
    }

    /**
     * 按 EXIF 摆正方向，并缩到「恰好覆盖屏幕」的尺寸（小图不放大，交给绘制时裁切填充）。
     */
    private static Bitmap transform(Bitmap source, int rotation, Point target) {
        boolean swapped = rotation % 180 != 0;
        int rotatedWidth = swapped ? source.getHeight() : source.getWidth();
        int rotatedHeight = swapped ? source.getWidth() : source.getHeight();
        float scale = Math.min(1f, Math.max(
                (float) target.x / rotatedWidth,
                (float) target.y / rotatedHeight));
        if (rotation == 0 && scale >= 1f) {
            return source;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        matrix.postScale(scale, scale);
        Bitmap result = Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (result != source) {
            source.recycle();
        }
        return result;
    }

    private static int computeSampleSize(int width, int height, int targetWidth, int targetHeight) {
        int sampleSize = 1;
        while (width / (sampleSize * 2) >= targetWidth
                && height / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /**
     * 以屏幕尺寸为准。横向滚动壁纸所需宽度可达屏幕两倍，但那会让壁纸进程多占一倍内存，
     * 此处取舍为宁可滚动时略有拉伸，也不冒 OOM 的风险。
     */
    private static Point getTargetSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return new Point(Math.max(metrics.widthPixels, 1), Math.max(metrics.heightPixels, 1));
    }

    private static int readRotation(Context context, Uri source) {
        try (InputStream in = openStream(context, source)) {
            int orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "读取照片方向失败，按未旋转处理", e);
            return 0;
        }
    }

    private static InputStream openStream(Context context, Uri source) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(source);
        if (in == null) {
            throw new IOException("无法读取所选照片");
        }
        return in;
    }
}

package com.book.mask.personalize;

import android.content.Context;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 复述题答题录音文件的存储管理。
 *
 * <p>把录音会话产生的 float[] PCM（16kHz / 单声道 / 16bit）写为 WAV 文件，存于应用私有目录
 * {@code filesDir/retelling_audio/}（无需存储权限、不被媒体库扫描）。录音文件与
 * {@link RetellingRecord} 同生命周期：记录被裁剪或清空时同步删除，见 {@link RetellingRecordStore}。
 */
public final class RetellingAudioStore {

    private static final String TAG = "RetellingAudio";
    private static final int SAMPLE_RATE = 16000;
    private static final String DIR_NAME = "retelling_audio";

    private final Context context;

    public RetellingAudioStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private File audioDir() {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "创建录音目录失败: " + dir.getAbsolutePath());
        }
        return dir;
    }

    /**
     * 校验并解析录音文件：仅接受本目录下的文件名（防路径穿越），文件不存在返回 null。
     */
    public File resolve(String fileName) {
        if (fileName == null || fileName.isEmpty()
                || fileName.contains("/") || fileName.contains("\\")) {
            return null;
        }
        File file = new File(audioDir(), fileName);
        return file.isFile() ? file : null;
    }

    /**
     * 把 float[] PCM 采样写为 WAV 文件（16kHz / 16bit / 单声道），返回文件名。
     * 采样为空或写入失败返回 null，失败时清理半截文件。
     */
    public String save(float[] samples, long timestamp) {
        if (samples == null || samples.length == 0) {
            return null;
        }
        String name = "retelling_" + timestamp + ".wav";
        File target = new File(audioDir(), name);
        try {
            DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(target)));
            try {
                writeWavHeader(dos, samples.length * 2);
                for (float sample : samples) {
                    int value = (int) (sample * 32768f);
                    value = Math.max(-32768, Math.min(32767, value));
                    // WAV 为小端字节序，writeShort 写大端，先翻转
                    dos.writeShort(Short.reverseBytes((short) value));
                }
            } finally {
                dos.close();
            }
            Log.d(TAG, "已保存录音文件: " + name + " (" + samples.length + " 采样)");
            return name;
        } catch (Exception e) {
            Log.e(TAG, "保存录音文件失败", e);
            target.delete();
            return null;
        }
    }

    /** 写入 44 字节标准 PCM WAV 头。 */
    private static void writeWavHeader(DataOutputStream dos, int dataSize) throws IOException {
        int byteRate = SAMPLE_RATE * 2;
        dos.writeBytes("RIFF");
        dos.writeInt(Integer.reverseBytes(36 + dataSize));
        dos.writeBytes("WAVE");
        dos.writeBytes("fmt ");
        dos.writeInt(Integer.reverseBytes(16));
        dos.writeShort(Short.reverseBytes((short) 1)); // PCM
        dos.writeShort(Short.reverseBytes((short) 1)); // 单声道
        dos.writeInt(Integer.reverseBytes(SAMPLE_RATE));
        dos.writeInt(Integer.reverseBytes(byteRate));
        dos.writeShort(Short.reverseBytes((short) 2)); // 块对齐 = 声道数 × 位深/8
        dos.writeShort(Short.reverseBytes((short) 16)); // 位深
        dos.writeBytes("data");
        dos.writeInt(Integer.reverseBytes(dataSize));
    }

    /** 删除单个录音文件；文件不存在视为成功。 */
    public boolean delete(String fileName) {
        File file = resolve(fileName);
        if (file == null) {
            return true;
        }
        boolean deleted = file.delete();
        if (!deleted) {
            Log.w(TAG, "删除录音文件失败: " + file.getName());
        }
        return deleted;
    }

    /** 删除全部录音文件。 */
    public void deleteAll() {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                Log.w(TAG, "删除录音文件失败: " + file.getName());
            }
        }
    }
}

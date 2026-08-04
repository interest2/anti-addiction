package com.book.mask.challenge.retelling;

import android.content.Context;

/**
 * lite 版语音识别占位实现：不打包 sherpa-onnx AAR，也不内置 ASR 模型。
 *
 * <p>与 full 版同名同包，供 main 源集代码正常引用；始终不可用，复述题前置校验
 * （{@code SettingsDialogManager.checkRetellingPreflight()}）会据此提示「ASR 模型缺失」，
 * 不会触碰缺失的 native 库，也不会崩溃。
 */
public final class SherpaOnnxTranscriber implements SpeechTranscriber {

    private static volatile SherpaOnnxTranscriber instance;

    public static SherpaOnnxTranscriber getInstance(Context context) {
        if (instance == null) {
            synchronized (SherpaOnnxTranscriber.class) {
                if (instance == null) {
                    instance = new SherpaOnnxTranscriber(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /** 进程退出或彻底不再使用时释放单例。 */
    public static void releaseInstance() {
        instance = null;
    }

    private SherpaOnnxTranscriber(Context context) {
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public TranscriptionResult transcribe(float[] samples, int sampleRate) {
        return TranscriptionResult.failure("ASR 模型缺失，请使用完整版");
    }

    @Override
    public void release() {
    }
}

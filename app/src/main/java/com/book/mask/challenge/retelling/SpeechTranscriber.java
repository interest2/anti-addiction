package com.book.mask.challenge.retelling;

/**
 * 本地语音转文字抽象。当前实现为 Sherpa-ONNX 离线识别，识别过程无需联网。
 */
public interface SpeechTranscriber {

    /**
     * 对一段 PCM 浮点采样执行识别（应在工作线程调用）。
     */
    TranscriptionResult transcribe(float[] samples, int sampleRate);

    /**
     * 模型是否就绪（已加载、可识别）。
     */
    boolean isReady();

    /**
     * 释放底层识别器资源，APP 退出或会话销毁时调用。
     */
    void release();
}

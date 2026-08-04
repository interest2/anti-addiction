package com.book.mask.challenge.retelling;

/**
 * 语音识别结果。成功时携带识别文本；失败时携带错误说明（如 ASR 模型缺失、识别失败）。
 */
public final class TranscriptionResult {

    private final boolean success;
    private final String text;
    private final String errorMessage;

    private TranscriptionResult(boolean success, String text, String errorMessage) {
        this.success = success;
        this.text = text;
        this.errorMessage = errorMessage;
    }

    public static TranscriptionResult success(String text) {
        return new TranscriptionResult(true, text == null ? "" : text, null);
    }

    public static TranscriptionResult failure(String errorMessage) {
        return new TranscriptionResult(false, "", errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getText() {
        return text;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

package com.book.mask.challenge.retelling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 语音识别结果。成功时携带识别文本及可选的 token 时间范围；失败时携带错误说明。
 */
public final class TranscriptionResult {

    private final boolean success;
    private final String text;
    private final String errorMessage;
    private final List<TimedToken> timedTokens;

    private TranscriptionResult(
            boolean success,
            String text,
            String errorMessage,
            List<TimedToken> timedTokens) {
        this.success = success;
        this.text = text;
        this.errorMessage = errorMessage;
        this.timedTokens = timedTokens == null || timedTokens.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(timedTokens));
    }

    public static TranscriptionResult success(String text) {
        return success(text, Collections.emptyList());
    }

    public static TranscriptionResult success(String text, List<TimedToken> timedTokens) {
        return new TranscriptionResult(true, text == null ? "" : text, null, timedTokens);
    }

    public static TranscriptionResult failure(String errorMessage) {
        return new TranscriptionResult(false, "", errorMessage, Collections.emptyList());
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

    public List<TimedToken> getTimedTokens() {
        return timedTokens;
    }

    public boolean hasTimedTokens() {
        return !timedTokens.isEmpty();
    }

    /** 单个识别 token 在本次输入音频中的起止时间。 */
    public static final class TimedToken {
        private final String text;
        private final float startSeconds;
        private final float endSeconds;

        public TimedToken(String text, float startSeconds, float endSeconds) {
            this.text = text == null ? "" : text;
            this.startSeconds = startSeconds;
            this.endSeconds = Math.max(startSeconds, endSeconds);
        }

        public String getText() {
            return text;
        }

        public float getStartSeconds() {
            return startSeconds;
        }

        public float getEndSeconds() {
            return endSeconds;
        }
    }
}

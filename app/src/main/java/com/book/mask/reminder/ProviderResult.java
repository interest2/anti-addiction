package com.book.mask.reminder;

public final class ProviderResult {
    public enum ErrorCode {
        NONE,
        INVALID_CONFIG,
        AUTHENTICATION,
        RATE_LIMIT,
        SERVER,
        TIMEOUT,
        NETWORK,
        INVALID_RESPONSE,
        CANCELLED,
        INTERNAL
    }

    private final String text;
    private final ErrorCode errorCode;
    private final int httpStatus;
    private final String failureMessage;

    private ProviderResult(String text, ErrorCode errorCode, int httpStatus, String failureMessage) {
        this.text = text;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.failureMessage = failureMessage;
    }

    public static ProviderResult success(String text) {
        return new ProviderResult(text, ErrorCode.NONE, 0, null);
    }

    public static ProviderResult failure(ErrorCode errorCode) {
        return failure(errorCode, 0, null);
    }

    public static ProviderResult failure(ErrorCode errorCode, int httpStatus) {
        return failure(errorCode, httpStatus, null);
    }

    /** 诊断字段：失败原因 + 响应体预览，仅供日志排查，勿展示给用户。 */
    public static ProviderResult failure(ErrorCode errorCode, int httpStatus, String failureMessage) {
        return new ProviderResult(null, errorCode, httpStatus, failureMessage);
    }

    public boolean isSuccess() {
        return errorCode == ErrorCode.NONE && text != null && !text.trim().isEmpty();
    }

    public String getText() {
        return text;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String toUserMessage() {
        switch (errorCode) {
            case INVALID_CONFIG:
                return "Provider 配置无效";
            case AUTHENTICATION:
                return appendStatus("认证失败，请检查 API Key");
            case RATE_LIMIT:
                return appendStatus("请求过于频繁或额度不足");
            case SERVER:
                return appendStatus("Provider 服务暂时不可用");
            case TIMEOUT:
                return "连接 Provider 超时";
            case NETWORK:
                return "无法连接 Provider，请检查网络和地址";
            case INVALID_RESPONSE:
                return appendStatus("Provider 返回了无法识别的内容");
            case CANCELLED:
                return "请求已取消";
            case INTERNAL:
                return "生成提醒时发生内部错误";
            default:
                return "";
        }
    }

    private String appendStatus(String message) {
        return httpStatus > 0 ? message + "（HTTP " + httpStatus + "）" : message;
    }
}

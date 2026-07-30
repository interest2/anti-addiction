package com.book.mask.network.reminder;

public final class ReminderRequest {
    private final String motivationTag;
    private final String style;
    private final String customStyle;

    public ReminderRequest(String motivationTag, String style, String customStyle) {
        this.motivationTag = motivationTag == null ? "" : motivationTag.trim();
        this.style = style == null || style.trim().isEmpty() ? "默认" : style.trim();
        this.customStyle = "自定义".equals(this.style) && customStyle != null
                ? customStyle.trim()
                : "";
    }

    public String getMotivationTag() {
        return motivationTag;
    }

    public String getStyle() {
        return style;
    }

    public String getCustomStyle() {
        return customStyle;
    }
}

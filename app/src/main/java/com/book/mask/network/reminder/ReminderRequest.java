package com.book.mask.network.reminder;

public final class ReminderRequest {
    private final String motivationTag;

    public ReminderRequest(String motivationTag) {
        this.motivationTag = motivationTag == null ? "" : motivationTag.trim();
    }

    public String getMotivationTag() {
        return motivationTag;
    }
}

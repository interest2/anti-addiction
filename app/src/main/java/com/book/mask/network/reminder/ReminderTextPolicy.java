package com.book.mask.network.reminder;

public final class ReminderTextPolicy {
    public static final int PROMPT_VERSION = 1;
    private static final int MAX_TEXT_LENGTH = 300;

    private ReminderTextPolicy() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\n\\s*\\n", "\n");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_TEXT_LENGTH).trim();
        }
        return normalized;
    }
}

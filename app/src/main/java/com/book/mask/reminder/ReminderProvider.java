package com.book.mask.reminder;

public interface ReminderProvider {
    ProviderResult generate(ReminderRequest request);
}

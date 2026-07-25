package com.book.mask.network.reminder;

public interface ReminderProvider {
    ProviderResult generate(ReminderRequest request);
}

package com.book.mask.personalize;

import com.tencent.mmkv.MMKV;

final class SettingsStorage {

    private static final String STORAGE_ID = "app_settings";

    private SettingsStorage() {
    }

    static MMKV open() {
        return MMKV.mmkvWithID(STORAGE_ID);
    }
}

package com.book.mask.network.reminder.content;

import com.book.mask.network.reminder.config.ReminderProviderConfig;
import com.tencent.mmkv.MMKV;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ReminderTextCache {
    public static final String DEFAULT_REMINDER =
            "别让指尖滑动成为你人生的绊脚石！\n"
                    + "APP的诱惑不过是虚幻的糖衣，吞噬的是你的黄金时间！\n"
                    + "醒醒吧，自律的缺失，正在将你推向平庸的深渊！";

    private static final String STORAGE_ID = "reminder_text_cache_v2";
    private static final String LEGACY_STORAGE_ID = "floating_text_cache";
    private static final String LEGACY_TEXT_KEY = "cached_text";
    private static final String LEGACY_UPDATED_KEY = "last_update";

    private final MMKV mmkv = MMKV.mmkvWithID(STORAGE_ID);

    public String buildKey(
            ReminderProviderConfig config,
            String motivationTag,
            String style,
            String customStyle) {
        String source = config.cacheIdentity()
                + "|" + motivationTag
                + "|style:" + style
                + "|customStyle:" + customStyle
                + "|prompt:" + ReminderTextPolicy.PROMPT_VERSION;
        return sha256(source);
    }

    public String getText(
            ReminderProviderConfig config,
            String motivationTag,
            String style,
            String customStyle) {
        String key = buildKey(config, motivationTag, style, customStyle);
        String text = mmkv.getString(textKey(key), null);
        if (text == null && config.isOfficial() && isDefaultStyle(style, customStyle)) {
            migrateLegacyOfficialCache(key);
            text = mmkv.getString(textKey(key), null);
        }
        return text;
    }

    long getUpdatedAt(
            ReminderProviderConfig config,
            String motivationTag,
            String style,
            String customStyle) {
        String key = buildKey(config, motivationTag, style, customStyle);
        if (!mmkv.containsKey(textKey(key))
                && config.isOfficial()
                && isDefaultStyle(style, customStyle)) {
            migrateLegacyOfficialCache(key);
        }
        return mmkv.getLong(updatedKey(key), 0);
    }

    public void put(
            ReminderProviderConfig config,
            String motivationTag,
            String style,
            String customStyle,
            String text) {
        String key = buildKey(config, motivationTag, style, customStyle);
        mmkv.putString(textKey(key), text)
                .putLong(updatedKey(key), System.currentTimeMillis())
                .commit();
    }

    private boolean isDefaultStyle(String style, String customStyle) {
        return "默认".equals(style) && (customStyle == null || customStyle.isEmpty());
    }

    private synchronized void migrateLegacyOfficialCache(String targetKey) {
        if (mmkv.containsKey(textKey(targetKey))) {
            return;
        }
        MMKV legacy = MMKV.mmkvWithID(LEGACY_STORAGE_ID);
        String legacyText = ReminderTextPolicy.normalize(legacy.getString(LEGACY_TEXT_KEY, null));
        if (legacyText == null) {
            return;
        }
        long updatedAt = legacy.getLong(LEGACY_UPDATED_KEY, System.currentTimeMillis());
        mmkv.putString(textKey(targetKey), legacyText)
                .putLong(updatedKey(targetKey), updatedAt)
                .commit();
    }

    private static String textKey(String key) {
        return "text_" + key;
    }

    private static String updatedKey(String key) {
        return "updated_" + key;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

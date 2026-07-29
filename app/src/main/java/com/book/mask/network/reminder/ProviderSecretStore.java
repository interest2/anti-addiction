package com.book.mask.network.reminder;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class ProviderSecretStore {
    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String KEY_ALIAS_PREFIX = "reminder_provider_key_v2_";
    private static final String FILE_PREFIX = "reminder_provider_secret_v2_";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int FILE_VERSION = 1;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final Context context;

    public ProviderSecretStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void saveApiKey(String profileId, String apiKey)
            throws GeneralSecurityException {
        AtomicFile secretFile = secretFile(profileId);
        if (apiKey == null || apiKey.isEmpty()) {
            deleteApiKey(profileId);
            return;
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(profileId));
        byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();

        FileOutputStream output = null;
        try {
            output = secretFile.startWrite();
            DataOutputStream dataOutput = new DataOutputStream(output);
            dataOutput.writeInt(FILE_VERSION);
            dataOutput.writeInt(iv.length);
            dataOutput.write(iv);
            dataOutput.writeInt(encrypted.length);
            dataOutput.write(encrypted);
            dataOutput.flush();
            secretFile.finishWrite(output);
            output = null;
        } catch (Exception e) {
            if (output != null) {
                secretFile.failWrite(output);
            }
            throw new GeneralSecurityException("API Key 保存失败", e);
        }
    }

    public synchronized String getApiKey(String profileId) throws GeneralSecurityException {
        AtomicFile secretFile = secretFile(profileId);
        if (!secretFile.getBaseFile().exists()) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(secretFile.openRead())) {
            int version = input.readInt();
            if (version != FILE_VERSION) {
                throw new GeneralSecurityException("API Key 存储版本不受支持");
            }
            int ivLength = input.readInt();
            if (ivLength < 12 || ivLength > 32) {
                throw new GeneralSecurityException("API Key 存储内容无效");
            }
            byte[] iv = new byte[ivLength];
            input.readFully(iv);
            int encryptedLength = input.readInt();
            if (encryptedLength <= 0 || encryptedLength > 16 * 1024) {
                throw new GeneralSecurityException("API Key 存储内容无效");
            }
            byte[] encrypted = new byte[encryptedLength];
            input.readFully(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getExistingKey(profileId),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("API Key 读取失败", e);
        }
    }

    public synchronized boolean hasApiKey(String profileId) {
        return secretFile(profileId).getBaseFile().exists();
    }

    public synchronized void deleteApiKey(String profileId) {
        secretFile(profileId).delete();
        try {
            KeyStore keyStore = loadKeyStore();
            String alias = keyAlias(profileId);
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias);
            }
        } catch (GeneralSecurityException ignored) {
            // 删除配置不应因系统密钥库异常而阻塞。
        }
    }

    private SecretKey getOrCreateKey(String profileId) throws GeneralSecurityException {
        KeyStore keyStore = loadKeyStore();
        String alias = keyAlias(profileId);
        java.security.Key existing = keyStore.getKey(alias, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_NAME);
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build());
        return keyGenerator.generateKey();
    }

    private SecretKey getExistingKey(String profileId) throws GeneralSecurityException {
        java.security.Key key = loadKeyStore().getKey(keyAlias(profileId), null);
        if (!(key instanceof SecretKey)) {
            throw new GeneralSecurityException("API Key 加密密钥不存在，请重新输入");
        }
        return (SecretKey) key;
    }

    private KeyStore loadKeyStore() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_NAME);
            keyStore.load(null);
            return keyStore;
        } catch (Exception e) {
            throw new GeneralSecurityException("系统密钥库不可用", e);
        }
    }

    private AtomicFile secretFile(String profileId) {
        return new AtomicFile(new File(
                context.getNoBackupFilesDir(),
                FILE_PREFIX + profileKey(profileId)));
    }

    private static String keyAlias(String profileId) {
        return KEY_ALIAS_PREFIX + profileKey(profileId);
    }

    private static String profileKey(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            throw new IllegalArgumentException("profileId 不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(profileId.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

package com.book.mask.reminder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.book.mask.reminder.config.ProviderSecretStore;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;
import com.book.mask.reminder.config.ReminderProviderConfigValidator;
import com.book.mask.reminder.content.ReminderTextCache;
import com.book.mask.reminder.provider.OfficialCloudProvider;
import com.book.mask.reminder.provider.OpenAiCompatibleProvider;
import com.book.mask.reminder.provider.ProviderHttpClient;
import com.book.mask.personalize.AppSettingsManager;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ReminderTextRepository {
    private static final String TAG = "ReminderTextRepository";

    public interface Callback {
        void onSuccess(String text);

        void onError(ProviderResult result);
    }

    @SuppressLint("StaticFieldLeak")
    private static volatile ReminderTextRepository instance;

    private final Context context;
    private final ReminderProviderConfigStore configStore;
    private final ProviderSecretStore secretStore;
    private final AppSettingsManager appSettingsManager;
    private final ReminderTextCache cache;
    private final ProviderHttpClient httpClient;
    private final ProviderHttpClient testHttpClient;
    private final ExecutorService requestExecutor;
    private final ExecutorService testExecutor;
    private final Handler mainHandler;
    private final Object requestLock = new Object();
    private final Object testLock = new Object();

    private Future<?> inFlightFuture;
    private String inFlightKey;
    private long inFlightGeneration;
    private List<Callback> inFlightCallbacks = new ArrayList<>();
    private Future<?> testFuture;
    private long testGeneration;

    public static ReminderTextRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ReminderTextRepository.class) {
                if (instance == null) {
                    instance = new ReminderTextRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private ReminderTextRepository(Context context) {
        this.context = context;
        configStore = new ReminderProviderConfigStore();
        secretStore = new ProviderSecretStore(context);
        appSettingsManager = new AppSettingsManager(context);
        cache = new ReminderTextCache();
        httpClient = new ProviderHttpClient();
        testHttpClient = new ProviderHttpClient();
        requestExecutor = Executors.newSingleThreadExecutor();
        testExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public String getCachedText() {
        ReminderProviderConfig config = configStore.getActiveConfig();
        ReminderRequest request = createCurrentRequest();
        String cachedText = cache.getText(
                config,
                request.getMotivationTag(),
                request.getStyle(),
                request.getCustomStyle());
        return cachedText == null ? ReminderTextCache.DEFAULT_REMINDER : cachedText;
    }

    public void fetchLatestText(Callback callback) {
        ReminderProviderConfig config = configStore.getActiveConfig();
        ReminderRequest request = createCurrentRequest();
        String requestKey = cache.buildKey(
                config,
                request.getMotivationTag(),
                request.getStyle(),
                request.getCustomStyle());

        List<Callback> replacedCallbacks = null;
        long generation;
        synchronized (requestLock) {
            if (requestKey.equals(inFlightKey) && inFlightFuture != null) {
                if (callback != null) {
                    inFlightCallbacks.add(callback);
                }
                Log.d(TAG, "复用进行中的提醒请求 reqId=" + inFlightGeneration
                        + ", " + describeRequest(request));
                return;
            }

            if (inFlightFuture != null) {
                Log.d(TAG, "提醒请求参数已变更，取消旧请求");
                inFlightFuture.cancel(true);
                httpClient.cancelActiveRequests();
                replacedCallbacks = inFlightCallbacks;
            }
            inFlightGeneration++;
            generation = inFlightGeneration;
            inFlightKey = requestKey;
            inFlightCallbacks = new ArrayList<>();
            if (callback != null) {
                inFlightCallbacks.add(callback);
            }
            Log.d(TAG, "开始新的提醒请求 reqId=" + generation
                    + ", " + describeRequest(request));
            inFlightFuture = requestExecutor.submit(
                    () -> generate(generation, requestKey, config, request));
        }
        postErrors(replacedCallbacks, ProviderResult.failure(ProviderResult.ErrorCode.CANCELLED));
    }

    public void testCustomProvider(
            ReminderProviderConfig config,
            String apiKey,
            Callback callback) {
        ReminderProviderConfigValidator.Error validationError =
                ReminderProviderConfigValidator.validate(config, apiKey);
        if (validationError != null) {
            postError(callback, ProviderResult.failure(ProviderResult.ErrorCode.INVALID_CONFIG));
            return;
        }
        ReminderRequest request = createCurrentRequest();
        synchronized (testLock) {
            testGeneration++;
            long generation = testGeneration;
            if (testFuture != null) {
                testFuture.cancel(true);
                testHttpClient.cancelActiveRequests();
            }
            testFuture = testExecutor.submit(() -> {
                ReminderProvider provider = new OpenAiCompatibleProvider(
                        config,
                        apiKey,
                        testHttpClient);
                ProviderResult result = provider.generate(request);
                synchronized (testLock) {
                    if (generation != testGeneration) {
                        return;
                    }
                    testFuture = null;
                }
                if (result.isSuccess()) {
                    postSuccess(callback, result.getText());
                } else {
                    postError(callback, result);
                }
            });
        }
    }

    public void cancelProviderTest() {
        synchronized (testLock) {
            testGeneration++;
            if (testFuture != null) {
                testFuture.cancel(true);
                testFuture = null;
            }
            testHttpClient.cancelActiveRequests();
        }
    }

    public void onConfigurationChanged() {
        cancelPendingFetch();
    }

    public void cancelPendingFetch() {
        List<Callback> callbacks;
        synchronized (requestLock) {
            inFlightGeneration++;
            if (inFlightFuture != null) {
                inFlightFuture.cancel(true);
            }
            httpClient.cancelActiveRequests();
            callbacks = inFlightCallbacks;
            inFlightFuture = null;
            inFlightKey = null;
            inFlightCallbacks = new ArrayList<>();
        }
        postErrors(callbacks, ProviderResult.failure(ProviderResult.ErrorCode.CANCELLED));
    }

    private void generate(
            long generation,
            String requestKey,
            ReminderProviderConfig config,
            ReminderRequest request) {
        ProviderResult result = createProvider(config).generate(request);
        List<Callback> callbacks;
        synchronized (requestLock) {
            if (generation != inFlightGeneration || !requestKey.equals(inFlightKey)) {
                return;
            }
            if (!config.cacheIdentity().equals(configStore.getActiveConfig().cacheIdentity())) {
                result = ProviderResult.failure(ProviderResult.ErrorCode.CANCELLED);
            } else if (result.isSuccess()) {
                cache.put(
                        config,
                        request.getMotivationTag(),
                        request.getStyle(),
                        request.getCustomStyle(),
                        result.getText());
            }
            callbacks = inFlightCallbacks;
            inFlightFuture = null;
            inFlightKey = null;
            inFlightCallbacks = new ArrayList<>();
        }

        if (result.isSuccess()) {
            Log.d(TAG, "提醒请求完成并已写入缓存 reqId=" + generation
                    + ", " + describeRequest(request)
                    + ", resp[" + previewText(result.getText()) + "]");
            for (Callback callback : callbacks) {
                postSuccess(callback, result.getText());
            }
        } else {
            Log.w(TAG, "提醒请求失败 reqId=" + generation
                    + ", errorCode=" + result.getErrorCode()
                    + ", httpStatus=" + result.getHttpStatus());
            postErrors(callbacks, result);
        }
    }

    private ReminderRequest createCurrentRequest() {
        return new ReminderRequest(
                appSettingsManager.getMotivationTag(),
                appSettingsManager.getReminderStyle(),
                appSettingsManager.getReminderCustomStyle());
    }

    /** 请求日志用：拼出目标(tag)与风格字段，风格为自定义时附带自定义描述。 */
    private static String describeRequest(ReminderRequest request) {
        String style = request.getStyle();
        String customStyle = request.getCustomStyle();
        if (customStyle != null && !customStyle.isEmpty()) {
            style = style + "(" + customStyle + ")";
        }
        return "tag=" + request.getMotivationTag() + ", style=" + style;
    }

    /** 请求日志用：只打印响应文字的长度与首尾各 5 个字，换行折成空格，避免刷屏与泄露全文。 */
    private static String previewText(String text) {
        if (text == null) {
            return "null";
        }
        int length = text.length();
        String head = text.substring(0, Math.min(5, length)).replace("\n", " ");
        String tail = text.substring(Math.max(0, length - 5)).replace("\n", " ");
        return "len=" + length + ", head=" + head + ", tail=" + tail;
    }

    private ReminderProvider createProvider(ReminderProviderConfig config) {
        if (config.isOfficial()) {
            return new OfficialCloudProvider(context, httpClient);
        }
        try {
            return new OpenAiCompatibleProvider(
                    config,
                    secretStore.getApiKey(config.getProfileId()),
                    httpClient);
        } catch (GeneralSecurityException e) {
            return request -> ProviderResult.failure(ProviderResult.ErrorCode.INTERNAL);
        }
    }

    private void postSuccess(Callback callback, String text) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(text));
        }
    }

    private void postError(Callback callback, ProviderResult result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(result));
        }
    }

    private void postErrors(List<Callback> callbacks, ProviderResult result) {
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            for (Callback callback : callbacks) {
                callback.onError(result);
            }
        });
    }
}

package com.book.mask.network.reminder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.book.mask.personalize.AppSettingsManager;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ReminderTextRepository {
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
        String tag = appSettingsManager.getMotivationTag();
        String cachedText = cache.getText(config, tag);
        return cachedText == null ? ReminderTextCache.DEFAULT_REMINDER : cachedText;
    }

    public void fetchLatestText(Callback callback) {
        ReminderProviderConfig config = configStore.getActiveConfig();
        String tag = appSettingsManager.getMotivationTag();
        String requestKey = cache.buildKey(config, tag);
        String cachedText = cache.getText(config, tag);

        if (isFresh(config, tag, cachedText)) {
            postSuccess(callback, cachedText);
            return;
        }

        List<Callback> replacedCallbacks = null;
        long generation;
        synchronized (requestLock) {
            if (requestKey.equals(inFlightKey) && inFlightFuture != null) {
                if (callback != null) {
                    inFlightCallbacks.add(callback);
                }
                return;
            }

            if (inFlightFuture != null) {
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
            inFlightFuture = requestExecutor.submit(
                    () -> generate(generation, requestKey, config, tag));
        }
        postErrors(replacedCallbacks, ProviderResult.failure(ProviderResult.ErrorCode.CANCELLED));
    }

    public void testCustomProvider(
            ReminderProviderConfig config,
            String apiKey,
            Callback callback) {
        String validationError = ReminderProviderConfigValidator.validate(config, apiKey);
        if (validationError != null) {
            postError(callback, ProviderResult.failure(ProviderResult.ErrorCode.INVALID_CONFIG));
            return;
        }
        String tag = appSettingsManager.getMotivationTag();
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
                ProviderResult result = provider.generate(new ReminderRequest(tag));
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
            String tag) {
        ProviderResult result = createProvider(config).generate(new ReminderRequest(tag));
        List<Callback> callbacks;
        synchronized (requestLock) {
            if (generation != inFlightGeneration || !requestKey.equals(inFlightKey)) {
                return;
            }
            if (!config.cacheIdentity().equals(configStore.getActiveConfig().cacheIdentity())) {
                result = ProviderResult.failure(ProviderResult.ErrorCode.CANCELLED);
            } else if (result.isSuccess()) {
                cache.put(config, tag, result.getText());
            }
            callbacks = inFlightCallbacks;
            inFlightFuture = null;
            inFlightKey = null;
            inFlightCallbacks = new ArrayList<>();
        }

        if (result.isSuccess()) {
            for (Callback callback : callbacks) {
                postSuccess(callback, result.getText());
            }
        } else {
            postErrors(callbacks, result);
        }
    }

    private ReminderProvider createProvider(ReminderProviderConfig config) {
        if (config.isOfficial()) {
            return new OfficialCloudProvider(context, httpClient);
        }
        try {
            String apiKey = config.getAuthType() == ReminderProviderConfig.AuthType.BEARER
                    ? secretStore.getApiKey()
                    : null;
            return new OpenAiCompatibleProvider(config, apiKey, httpClient);
        } catch (GeneralSecurityException e) {
            return request -> ProviderResult.failure(ProviderResult.ErrorCode.INTERNAL);
        }
    }

    private boolean isFresh(
            ReminderProviderConfig config,
            String tag,
            String cachedText) {
        int intervalMinutes = config.getRefreshIntervalMinutes();
        if (cachedText == null || intervalMinutes <= 0) {
            return false;
        }
        long ageMillis = System.currentTimeMillis() - cache.getUpdatedAt(config, tag);
        return ageMillis >= 0 && ageMillis < intervalMinutes * 60_000L;
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

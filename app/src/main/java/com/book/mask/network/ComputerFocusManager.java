package com.book.mask.network;

import android.util.Log;

import com.book.mask.personalize.ComputerFocusSettings;

import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** 每 10 秒读取电脑端 time-divider 状态；网络失败时采用放行策略。 */
public final class ComputerFocusManager {
    private static final String TAG = "ComputerFocus";
    private static final long POLL_INTERVAL_SECONDS = 10;
    private static final long REQUEST_TIMEOUT_SECONDS = 4;

    private final ComputerFocusSettings settings;
    private final OkHttpClient httpClient;
    private final AtomicBoolean activeTask = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;
    private String lastOutcome = "";

    public ComputerFocusManager() {
        settings = new ComputerFocusSettings();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    public synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "computer-focus-poller");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(
                this::pollSafely,
                0,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public synchronized void refreshNow() {
        activeTask.set(false);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(this::pollSafely);
        }
    }

    public boolean hasActiveTask() {
        return activeTask.get();
    }

    private void pollSafely() {
        String endpoint;
        try {
            endpoint = settings.getStatusEndpoint();
        } catch (Exception e) {
            updateState(false, "invalid", "电脑任务专注地址无效", e);
            return;
        }

        if (endpoint.isEmpty()) {
            updateState(false, "disabled", "电脑任务专注未配置", null);
            return;
        }

        Request request;
        try {
            request = new Request.Builder().url(endpoint).get().build();
        } catch (Exception e) {
            updateState(false, "invalid", "电脑任务专注地址无效", e);
            return;
        }

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                updateState(false, "http_" + response.code(),
                        "电脑任务专注接口返回 HTTP " + response.code(), null);
                return;
            }
            JSONObject payload = new JSONObject(response.body().string());
            if (!payload.has("hasActiveTask")) {
                throw new IllegalStateException("响应缺少 hasActiveTask");
            }
            boolean hasActiveTask = payload.getBoolean("hasActiveTask");
            updateState(hasActiveTask, hasActiveTask ? "active" : "idle",
                    hasActiveTask ? "电脑存在进行中任务" : "电脑当前无进行中任务", null);
        } catch (Exception e) {
            updateState(false, "unreachable", "电脑任务专注接口暂时不可用", e);
        }
    }

    private synchronized void updateState(boolean active, String outcome,
                                          String message, Throwable error) {
        activeTask.set(active);
        if (outcome.equals(lastOutcome)) {
            return;
        }
        lastOutcome = outcome;
        if (error == null) {
            Log.i(TAG, message);
        } else {
            Log.w(TAG, message + ": " + error.getMessage());
        }
    }

    public synchronized void stop() {
        activeTask.set(false);
        httpClient.dispatcher().cancelAll();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}

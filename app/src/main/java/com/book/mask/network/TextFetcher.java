package com.book.mask.network;

import android.content.Context;
import android.util.Log;

import com.book.mask.reminder.ProviderResult;
import com.book.mask.reminder.ReminderTextRepository;

public class TextFetcher {
    private static final String TAG = "TextFetcher";

    private final ReminderTextRepository repository;

    public interface OnTextFetchListener {
        void onTextFetched(String text);

        void onFetchError(String error);
    }

    public TextFetcher(Context context) {
        repository = ReminderTextRepository.getInstance(context);
    }

    public String getCachedText() {
        return repository.getCachedText();
    }

    public void prefetchLatestText() {
        repository.fetchLatestText(null);
    }

    public void fetchLatestText(OnTextFetchListener listener) {
        repository.fetchLatestText(new ReminderTextRepository.Callback() {
            @Override
            public void onSuccess(String text) {
                Log.d(TAG, "提醒文字获取成功");
                if (listener != null) {
                    listener.onTextFetched(text);
                }
            }

            @Override
            public void onError(ProviderResult result) {
                Log.w(TAG, "提醒文字获取失败，errorCode=" + result.getErrorCode()
                        + ", httpStatus=" + result.getHttpStatus());
                if (listener != null) {
                    listener.onFetchError(result.toUserMessage());
                }
            }
        });
    }

    public void onProviderConfigurationChanged() {
        repository.onConfigurationChanged();
    }

    public void cleanup() {
        repository.cancelPendingFetch();
    }
}

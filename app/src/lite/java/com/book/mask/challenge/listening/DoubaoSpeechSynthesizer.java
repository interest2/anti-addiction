package com.book.mask.challenge.listening;

import android.content.Context;

import java.io.File;
import java.util.List;

/**
 * lite 版豆包语音合成占位实现：不打包火山引擎 Speech SDK。
 *
 * <p>与 full 版同名同包，供主源集的听力题调用链正常编译。始终通知不可用，
 * {@link ListeningAudioPlayer} 随后回退播放内置占位音频；切换到 full 变体即可使用真实合成。
 */
public final class DoubaoSpeechSynthesizer {

    public interface Callback {
        void onAudioReady(List<File> audioFiles);

        void onError(String message);

        void onUnavailable(String reason);
    }

    public DoubaoSpeechSynthesizer(Context context) {
    }

    public void synthesize(String text, Callback callback) {
        if (callback != null) {
            callback.onUnavailable("lite 版未内置豆包语音服务");
        }
    }

    public void stop() {
    }

    public void destroy() {
    }
}

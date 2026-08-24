package com.book.mask.challenge.retelling.preflight;

import android.content.Context;

import com.book.mask.network.AppConfigManager;
import com.book.mask.personalize.DoubaoTtsConfigManager;

/** 听述题前置检查：在复述答题链路的要求之上，再检查真实 TTS 运行时与凭据。 */
public final class ListeningRetellingPreflight {

    private ListeningRetellingPreflight() {
    }

    public static String check(Context context) {
        if (!AppConfigManager.isListeningRetellingAvailable()) {
            return "当前版本未内置听述题所需的语音合成与识别组件";
        }
        if (!new DoubaoTtsConfigManager(context).isConfigured()) {
            return "未配置豆包语音合成，请先在题目类型设置中配置";
        }
        return RetellingPreflight.check(context);
    }
}

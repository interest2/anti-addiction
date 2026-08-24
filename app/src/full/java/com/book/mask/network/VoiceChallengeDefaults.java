package com.book.mask.network;

/**
 * full 版内置豆包语音合成 SDK 与 Sherpa-ONNX ASR，语音题默认可用，
 * 不再依赖服务端逐设备授权（{@code showVoiceChallenge}）。
 */
final class VoiceChallengeDefaults {

    static final boolean AVAILABLE_BY_DEFAULT = true;
    static final boolean HAS_LISTENING_RETELLING_RUNTIME = true;

    private VoiceChallengeDefaults() {
    }
}

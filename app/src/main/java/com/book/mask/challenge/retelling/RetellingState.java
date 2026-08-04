package com.book.mask.challenge.retelling;

/**
 * 复述题状态机。单靠 boolean 无法表达「正在识别 / 正在评分 / 已通过 / 已失败」等互斥阶段，
 * 明确状态可避免异步回调乱序时界面错乱。
 */
enum RetellingState {
    /** 正在加载故事（可能走网络或内置池兜底） */
    LOADING_STORY,
    /** 限时阅读原文 */
    READING,
    /** 原文已清空，可开始录音 */
    READY_TO_RECORD,
    /** 录音中（透明 Activity 前台） */
    RECORDING,
    /** 正在本地语音识别 */
    TRANSCRIBING,
    /** 正在大模型评分 */
    SCORING,
    /** 达标，可关闭悬浮窗 */
    PASSED,
    /** 未达标，换新故事重来 */
    FAILED,
    /** 异常（模型缺失 / 评分失败等），提示后可重试或取消 */
    ERROR
}

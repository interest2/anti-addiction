package com.book.mask.challenge;

/**
 * 答题会话通用契约。文本类题目（算术 / 推理 / 英文阅读）与复述题各自实现。
 */
public interface ChallengeSession {

    /** 用户主动取消：释放资源并按需回调上层（不判通过）。 */
    void cancel();

    /** 界面销毁 / 窗口关闭：静默释放资源，不回调上层。 */
    void destroy();

    boolean isActive();
}

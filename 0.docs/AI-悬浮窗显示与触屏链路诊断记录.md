# 悬浮窗显示与触屏链路诊断记录

## 用途

本文档记录悬浮窗从“请求显示”到“首帧绘制、帧提交、实际收到触屏”的临时诊断方案。

诊断代码已从生产代码中移除。以后遇到悬浮窗显示延迟、点击穿透或短时重入性能回退时，可按本文重新加入；完成排查后应再次移除。

SystemUI 的隐藏和恢复分支不接入这些埋点，避免改变其既有逻辑。

## 需要区分的时间点

建议统一使用 `SystemClock.elapsedRealtimeNanos()` 记录起点，并输出相对毫秒数。

1. `请求显示`
   - 新建窗口：进入实际显示流程时记录，必须早于布局 inflate 和内容初始化。
   - 短时重入：进入保留窗口恢复流程时记录。
2. 显示 API 返回
   - 新建窗口：`WindowManager.addView()` 返回。
   - 透明窗口恢复：`WindowManager.updateViewLayout()` 返回。
   - `INVISIBLE` 回退恢复：`setVisibility(View.VISIBLE)` 返回。
3. `OnPreDraw`
   - 首次准备绘制的时刻。
4. `OnDraw`
   - 首次进入绘制阶段的时刻。
5. `FrameCommit`
   - Android 10及以上通过 `ViewTreeObserver.registerFrameCommitCallback()` 记录。
   - 表示本次硬件渲染帧已提交，不等同于屏幕像素已经被人眼看到。
6. `首次收到 ACTION_DOWN`
   - 在悬浮窗根布局的 `dispatchTouchEvent()` 入口记录。
   - 只用于证明某次真实触屏已经分发给悬浮窗，不代表输入拦截开始生效的精确时刻。

## 推荐日志格式

```text
显示链路测量[新建窗口] 请求显示: +0.01ms
显示链路测量[新建窗口] WindowManager.addView 返回: +17.43ms
显示链路测量[新建窗口] OnPreDraw: +39.23ms
显示链路测量[新建窗口] OnDraw: +40.29ms
显示链路测量[新建窗口] FrameCommit: +50.45ms
显示链路测量[新建窗口] 首次收到 ACTION_DOWN（实际用户触屏时刻）: +118.42ms
```

短时重入使用来源名称 `短时重入恢复`，透明窗口恢复的 API 阶段建议写为：

```text
updateViewLayout(恢复显示和触屏) 返回
```

## 实现要点

### 1. 帧监听注册时机

新建窗口时，必须在 `WindowManager.addView()` 返回后立即注册 `OnPreDraw`、`OnDraw` 和 `FrameCommit`。

不要在 `addView()` 之前保存并长期使用 `ViewTreeObserver`。View 挂载到窗口时观察器可能被合并或替换，旧观察器会失效，容易造成一次性监听无法正确移除。`addView()`返回后仍处于主线程同一调用栈，正常情况下不会错过第一次 traversal。

短时重入时，在 `updateViewLayout()` 或 `setVisibility()` 返回后立即注册。

### 2. 一次性监听和失效保护

- `OnPreDraw`第一次回调时立即移除自身。
- `OnDraw`不能在绘制回调内部直接修改监听列表，使用 `view.post()`移除，并用布尔值防止重复输出。
- 每轮测量增加 generation；回调执行时只有 generation 仍匹配才输出。
- 隐藏窗口、移除窗口、显示失败或开始新一轮测量时，取消旧测量并移除尚未执行的监听器。
- `FrameCommit`没有公开的对应取消方法，依靠 generation 忽略过期回调。

### 3. 首次触屏观测

临时把 `floating_window_layout.xml` 的根节点由 `RelativeLayout`替换为自定义根布局，例如：

```java
public class FloatingRootLayout extends RelativeLayout {
    private Runnable actionDownObserver;

    public FloatingRootLayout(Context context) {
        super(context);
    }

    public FloatingRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatingRootLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setActionDownObserver(Runnable observer) {
        actionDownObserver = observer;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && actionDownObserver != null) {
            actionDownObserver.run();
        }
        return super.dispatchTouchEvent(event);
    }
}
```

必须继续返回 `super.dispatchTouchEvent(event)` 的结果，不能消费、修改或重新派发事件，否则测量本身会改变触屏行为。

## 已测数据

### 新建窗口，较慢样本

| 阶段 | 累计耗时 |
|---|---:|
| `addView`返回 | 45.60ms |
| `OnPreDraw` | 91.62ms |
| `OnDraw` | 102.82ms |
| `FrameCommit` | 127.27ms |
| 首次`ACTION_DOWN` | 275.63ms |

### 原`INVISIBLE/VISIBLE`短时重入

| 阶段 | 累计耗时 |
|---|---:|
| `setVisibility(VISIBLE)`相关流程返回 | 11.31ms |
| 恢复流程完成 | 17.53ms |
| `OnPreDraw` | 40.82ms |
| `OnDraw` | 41.27ms |
| `FrameCommit` | 52.87ms |

### 透明且不可触屏窗口短时重入

离开目标 APP 时，将保留窗口设置为 `alpha=0`并加入`FLAG_NOT_TOUCHABLE`；重入时通过一次`updateViewLayout()`恢复原透明度和 Flags。

| 阶段 | 累计耗时 |
|---|---:|
| 临时隐藏完成 | 3.05ms |
| `updateViewLayout`恢复返回 | 0.41ms |
| 恢复流程完成 | 0.64ms |
| `OnPreDraw` | 9.11ms |
| `OnDraw` | 9.18ms |
| 首次`ACTION_DOWN` | 137.61ms |

该样本没有收到`FrameCommit`回调。可能原因是窗口复用了已有 Surface 缓冲，透明度和触屏属性通过窗口合成事务恢复，没有产生新的硬件渲染帧；不能仅凭缺少该日志判断显示失败。

### 新建窗口，较快样本

| 阶段 | 累计耗时 |
|---|---:|
| `addView`返回 | 17.43ms |
| `OnPreDraw` | 39.23ms |
| `OnDraw` | 40.29ms |
| `FrameCommit` | 50.45ms |
| 首次`ACTION_DOWN` | 118.42ms |

新建窗口耗时受进程、资源和系统窗口状态影响较大，不应只用单次样本判断优化效果。

## 日志判读规则

- 原有“悬浮窗显示成功/恢复完成”只表示应用侧 API 调用或 Java流程结束，不代表首帧已经提交。
- `请求显示 → 显示 API 返回`主要反映布局、内容初始化和窗口调用的同步耗时。
- `显示 API 返回 → OnPreDraw`主要反映等待窗口 traversal/下一帧调度的时间。
- `OnPreDraw → OnDraw → FrameCommit`反映绘制和渲染提交阶段。
- `首次 ACTION_DOWN`受用户实际点击时刻影响，不能直接当作窗口就绪耗时。
- 如果目标 APP 已响应点击，但悬浮窗尚未记录`ACTION_DOWN`，只能说明那次触屏没有分发给悬浮窗；应用侧埋点无法单独确定底层 InputDispatcher 在哪一毫秒切换了输入目标。
- 荣耀系统的`onTaskVisibilityChanged`是系统窗口日志，与悬浮窗显示链路没有稳定的耗时对应关系，不用于计算悬浮窗显示耗时。
- 350ms防抖只暂停包名、页面关键词和轮询检测，不负责显示窗口或拦截触屏。

## 恢复诊断代码的检查清单

1. 新增仅观测触屏、不改变分发结果的根布局。
2. 临时替换 XML 根节点，并在 inflate 后安装`ACTION_DOWN`观察回调。
3. 为新建窗口和短时重入分别记录请求起点。
4. 在显示 API 返回后注册一次性帧监听。
5. Android 10及以上注册`FrameCommit`。
6. 在隐藏、销毁、失败和新测量开始时清理旧状态。
7. 确认 SystemUI 分支没有接入或修改。
8. 执行`testDebugUnitTest`和`assembleDebug`。
9. 排查完成后删除自定义根布局、XML替换、帧监听字段/方法和诊断日志。

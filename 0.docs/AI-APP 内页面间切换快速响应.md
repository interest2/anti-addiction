# APP 内页面间切换快速响应

（本文已按当前 `src.zip` 源码核对更新）

## 1. 当前状态

该方案**尚未合入当前源码**。当前实现仅监听：

- `TYPE_WINDOW_STATE_CHANGED`
- `TYPE_WINDOW_CONTENT_CHANGED`

`FloatService.onServiceConnected()` 当前设置：

```java
info.eventTypes = TYPE_WINDOW_STATE_CHANGED | TYPE_WINDOW_CONTENT_CHANGED;
info.flags = FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
```

源码中不存在：

- `TYPE_VIEW_CLICKED` 处理分支。
- `FLAG_REPORT_VIEW_IDS`。
- `TriggerControlManager`。
- `onTriggerControlClicked()` 回调。

因此，同一 APP 内从非目标页面切回首页时，仍主要依赖内容变化事件、200ms 防抖和节点树关键词扫描。当前“先显示、后检测”只在**确认进入目标包名**时生效，不能直接覆盖同包名内部页面跳转。

## 2. 目标

将判定信号从“页面文字已经渲染完成”提前到“用户点击了通往目标页面的控件”：

```text
第一次：点击 → 页面渲染 → 关键词命中 → 学习该控件
以后：点击已学习控件 → 先恢复/显示遮罩 → 延后关键词校验
```

关键词检测仍是最终真值；点击信号仅用于抢先响应。

## 3. 原方案需要修正的关键点

### 3.1 不能用 `Share.isFloatingWindowVisible == false` 判断“隐藏→显示”

当前暖窗口机制中：

- 页面离开后窗口可能透明且不可触摸。
- `Share.isFloatingWindowVisible` 仍为 `true`。

所以它表达的是“窗口仍挂载”，不是“用户可见”。若继续使用：

```java
floatingVisibleBeforeCheck == false
```

作为学习条件，恰好会漏掉最重要的“非目标页 → 目标页、暖窗口恢复”场景。

更可靠的学习条件应基于**页面逻辑状态变化**：

```text
检测前 Share.getAppState(app) == "not target"
检测后关键词命中 target
最近存在同包名的新鲜点击
```

这与窗口是否挂载无关。

### 3.2 快速通道不能简单要求“窗口当前未显示”

当前 `showFloatingWindow(app)` 会先调用 `tryResumeFromPageTransition(app)`。因此：

- 暖窗口虽然 `isFloatingWindowVisible == true`，仍需要调用 `showFloatingWindow()` 才能恢复。
- 快速通道应允许“窗口已挂载但处于 `PAGE_TRANSITION` 暂停”的情况。

建议由 `FloatingWindowManager` 暴露明确状态，例如：

```java
boolean isActuallyShown();
boolean isSuspendedForPageTransition();
```

不要继续复用含义模糊的 `isFloatingWindowVisible`。

### 3.3 100ms 后一次强制检测可能过早

点击底栏后，目标页面文字未必在 100ms 内可读。若立即强制检测并在未命中时隐藏，可能形成新的“显示→隐藏→再显示”闪现。

建议把快速校验分成两层：

1. 首次轻量校验：跨过首帧后执行。
2. 最终校验：等待内容事件防抖完成，或在最大宽限期后强制执行。

可复用现有常量：

- `SHOW_BEFORE_CONTENT_CHECK_DELAY_MS = 100ms`
- `CONTENT_CHECK_DEBOUNCE_MS = 200ms`
- `CONTENT_CHECK_MAX_WAIT_MS = 500ms`

但最好新增独立的 `TRIGGER_CONTROL_VERIFY_GRACE_MS`，避免把包名进入和页面点击的时序绑死。

## 4. 推荐实现

### 4.1 开启点击事件

在 `FloatService.onServiceConnected()` 中保留现有 Flags，并追加：

```java
info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        | AccessibilityEvent.TYPE_VIEW_CLICKED;

info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
```

不能用新值覆盖掉 `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`。

XML 中也可以同步声明，但当前运行时会调用 `setServiceInfo()`，最终应以动态配置为准。

### 4.2 点击签名

推荐签名：

```text
viewIdResourceName + "#" + className + "#" + text/contentDescription
```

构造规则：

- `viewId`：`event.getSource().getViewIdResourceName()`。
- `className`：`event.getClassName()`，为空则取 source。
- `text`：先取 `event.getText()`，为空回退 `contentDescription`。
- `viewId` 与文本都为空：放弃学习，回退关键词检测。

资源 ID 可能随应用升级变化；可见文本也可能因语言、灰度版本变化。签名应被视为启发式标识，而非永久稳定主键。

### 4.3 记录最近点击

`AppStateManager` 保存一组短期内存状态：

```text
lastClickedPackage
lastClickedSignature
lastClickedAtElapsedRealtime
lastClickedGeneration
```

使用 `SystemClock.elapsedRealtime()`，不要用系统墙钟，避免用户修改时间影响新鲜度判断。

点击新鲜度可先取 4 秒，但应集中放入 `Const`。

### 4.4 学习条件

在关键词扫描前保存：

```java
String stateBeforeCheck = Share.getAppState(currentActiveApp);
```

仅在以下条件全部满足时记录：

- 扫描结果命中目标关键词。
- `stateBeforeCheck` 为 `not target`。
- 最近点击包名等于当前应用包名。
- 点击未过期。
- 点击发生后没有切换到其他包名。
- 当前不处于休闲时刻、答题或包名切换复核。

记录后清除该次点击归因，防止一个点击被重复学习。

不建议把“首次进入 APP，状态为 null → target”用于学习；那可能把启动页或自动跳转前的无关点击误记为首页入口。

### 4.5 持久化

新增 `TriggerControlManager`：

- 独立 MMKV ID，例如 `trigger_controls`。
- 结构：包名 → 有序签名集合。
- 每个应用最多 30 条。
- 重复签名不重复写入。
- 超限淘汰最旧项。

建议额外保存：

- 记录时间。
- 目标应用版本号。
- 可选的命中次数/失败次数。

应用升级后可降低旧签名可信度，或按失败次数淘汰，避免长期积累失效控件。

### 4.6 快速通道

收到 `TYPE_VIEW_CLICKED` 后：

1. 构造并保存最近点击。
2. 判断是否命中已学习签名。
3. 前置守卫通过后，调用监听方请求显示/恢复窗口。
4. 安排页面校验。

建议守卫：

- 当前包名是受支持且已启用应用。
- 当前页面逻辑状态不是 `target`，避免重复抢显。
- 非手动解禁。
- 非休闲时刻。
- 非答题界面。
- 非包名切换复核。
- 非悬浮窗显示包名防抖引发的异常上下文。

不要把“窗口已挂载”直接作为拒绝条件；暖窗口恢复正需要通过该路径。

### 4.7 校验与纠错

快速显示后仍必须以关键词为准：

```text
点击命中
    ↓
先 showFloatingWindow(app)
    ↓
等待首帧与页面内容事件
    ↓
强制或防抖扫描
    ├─ 命中：保持显示
    └─ 未命中：重新进入 PAGE_TRANSITION 暂停
```

未命中时宜调用现有 `onAppStateChanged(false)` 链路，让窗口进入暖态，而不是直接 `removeView()`。

为了防止早期假阴性，可在宽限期内满足任一条件后再最终隐藏：

- 已收到该包名的内容变化事件并完成防抖扫描。
- 已达到最终校验超时。

## 5. 数据流

```text
TYPE_VIEW_CLICKED
    ↓
构造签名并保存最近点击
    ├─ 未学习：等待常规关键词扫描
    └─ 已学习：先显示/恢复暖窗口
                    ↓
          内容变化事件 + 防抖扫描
                    ↓
       not target → target 且点击新鲜？
           ├─ 是：记录/强化签名
           └─ 否：不学习
                    ↓
            最终关键词校验
           ├─ target：保持
           └─ not target：暂停隐藏
```

## 6. 边界

- 某些应用不发送可靠的 `TYPE_VIEW_CLICKED`。
- Compose、自绘控件或 WebView 可能拿不到稳定 viewId/text。
- 同一控件可能根据当前状态进入不同页面。
- 底栏文字可能因语言或 A/B 测试变化。
- 页面跳转失败时会误抢显，必须依赖最终校验收回。
- 存储的控件文字属于本地 UI 元数据，不应上传。

任一环节失败时，都应无条件回退到当前关键词检测流程。

## 7. 预计改动文件

| 文件                    | 改动                                                         |
| ----------------------- | ------------------------------------------------------------ |
| `FloatService`          | 监听 `TYPE_VIEW_CLICKED`；追加 `FLAG_REPORT_VIEW_IDS`；实现快速显示回调 |
| `AppStateManager`       | 路由点击事件；保存最近点击；学习、命中与校验调度             |
| `FloatingWindowManager` | 暴露“实际显示/暖窗口暂停”状态，避免继续误用挂载状态          |
| `TriggerControlManager` | 新增 MMKV 持久化与淘汰策略                                   |
| `Const`                 | 点击新鲜度、最终校验宽限期等常量                             |
| 单元测试                | 覆盖学习条件、暖窗口状态、过期点击和误判纠正                 |

## 8. 验收标准

- 首次点击首页：仍靠关键词检测，成功后学习。
- 第二次点击同控件：遮罩早于首页内容稳定出现。
- 暖窗口状态：能直接恢复，不因 `Share.isFloatingWindowVisible=true` 被拦截。
- 点击后跳转失败：宽限期后自动收回遮罩。
- 手动解禁/休闲时刻：点击不会抢显。
- 应用升级导致签名变化：自动回退常规检测，不影响主流程。
- 服务重启：已学习签名仍可使用。

|      |      |
| --- | --- |
|      |      |
|      |      |
|      |      |
|      |      |

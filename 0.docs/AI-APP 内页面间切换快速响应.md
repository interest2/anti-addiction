# APP 内页面间切换快速响应

本文档的技术方案（主要由 AI 撰写）已初步实现、验证，但暂未合入 master 代码。

## 1. 背景与目标

当前判定“是否为目标页面（如小红书首页）”依赖 **文字关键词检测**：等无障碍抛出 `TYPE_WINDOW_CONTENT_CHANGED`，防抖后递归遍历整棵节点树，匹配到关键词才显示悬浮窗遮罩。

这条链路的固有延迟在于——必须等目标页**内容渲染出来、文字可读**之后才能判定，因此从“用户点击进入首页”到“遮罩出现”之间存在肉眼可见的空窗期，用户能先看到一部分信息流。

**目标**：在 APP 内页面切换（如点击底栏“首页”）时更快地弹出遮罩。核心思路是把判定信号从“页面内容已就绪”提前到“用户点击了会进入目标页的控件”这一瞬间。

## 2. 核心思路

利用无障碍的 `TYPE_VIEW_CLICKED` 事件做**控件级学习**：

1. **记录**：用户点击某控件后，若随后的文字检测把悬浮窗由“隐藏 → 显示”（即确认进入目标页），就把该控件登记为该 APP 的“触发控件”。
2. **快速响应**：下次点击同一控件时，**先直接显示悬浮窗**，再跑文字检测确认；若确认并非目标页（误判），再把刚显示的悬浮窗隐藏。

即：第一次靠文字检测“学会”哪个控件通往首页，之后靠点击信号抢先遮罩，文字检测退化为**事后校验**而非**事前门槛**。

## 3. 关键技术点

### 3.1 开启点击事件与 viewId 上报

在 [`FloatService`](../app/src/main/java/com/book/mask/floating/FloatService.java) 的 `onServiceConnected()` 中扩展无障碍配置：

- `eventTypes` 增加 `TYPE_VIEW_CLICKED`。
- `flags` 增加 `FLAG_REPORT_VIEW_IDS`——否则 `AccessibilityNodeInfo.getViewIdResourceName()` 恒返回 null，拿不到控件资源 id。

### 3.2 控件签名

被点击控件需要一个**跨会话稳定**的标识。方案：从点击事件构造签名

```
signature = viewIdResourceName + "#" + className + "#" + text
```

- `viewId` 取自 `event.getSource().getViewIdResourceName()`（用完 `recycle()`）。
- `text` 取 `event.getText()`（多段以空格拼接），为空时回退 `event.getContentDescription()`。
- 三者中 viewId 与 text **全为空**则判定“无可识别特征”，返回 null，跳过学习与快速通道，安全回退到原有的纯文字检测流程。

对底栏“首页”这类控件，即使 release 包把 viewId 混淆成 `id/a1b2`，其可见文本 `首页` 仍稳定，签名依然可用。

### 3.3 学习时机（“隐藏 → 显示”的转变才记录）

在文字检测方法 `checkTextContentOptimized()` 中：

- 进入时先快照 `floatingVisibleBeforeCheck = Share.isFloatingWindowVisible`。
- 当检测判定为目标页时，仅在满足以下**全部**条件才登记触发控件：
  - `floatingVisibleBeforeCheck == false`：本次是“隐藏→显示”的转变，即这次点击**确实触发了遮罩出现**（避免在遮罩已显示时把无关点击误记）。
  - 存在最近一次点击且**同包名**：`lastClickedPackage == currentPackageName`。
  - **新鲜度**：`now - lastClickedAt <= VIEW_CLICK_TRIGGER_FRESHNESS_MS`（建议 4000ms），确保这次目标页确实由该点击引发，而非无关的自动/定时触发。
- 登记后清空 `lastClickedSignature`，防止一次点击被后续无关检测重复归因。

### 3.4 快速响应通道（点击即遮罩 + 事后校验）

在 `handleViewClicked()` 中，命中已登记触发控件时：

1. 前置守卫（任一不满足则退回常规流程，不抢先显示）：非检测暂停期、非悬浮窗显示防抖期、悬浮窗当前未显示、该 APP 未被手动隐藏/解禁中、无数学题验证进行中。
2. 先通过监听回调 `onTriggerControlClicked(app)` 立即 `showFloatingWindow(app)`。
3. 延后 `SHOW_BEFORE_CONTENT_CHECK_DELAY_MS`（跨过首帧，避免阻塞式检测把首帧绘制一起卡住）再调用 `checkTextContentOptimized(true)` **强制检测**：
   - 确认为目标页 → 悬浮窗保持。
   - 判定非目标页（误判）→ 走 `onAppStateChanged(false)` 把悬浮窗降级/隐藏，纠正误爆。

> 用 `forceCheck=true` 的原因：常规检测只在“状态发生变化”时才通知监听方。快速通道下悬浮窗已被抢先显示，若此时页面状态与上次相同（如都为“not target”），非强制模式不会发出隐藏通知，导致误显示的遮罩无法收回。强制模式保证一定回调，从而在误判时可靠隐藏。

### 3.5 触发控件的持久化

新增 `TriggerControlManager`（可仿 [`PackageLogManager`](../app/src/main/java/com/book/mask/config/PackageLogManager.java) 写法）：

- MMKV 独立 mmapID（如 `trigger_controls`），存 **包名 → 控件签名集合** 的 JSON（Gson 序列化）。
- 提供 `isTrigger(pkg, sig)` 与 `record(pkg, sig)`；每个 APP 的集合设上限（如 30）并按插入顺序淘汰，防止无限增长。
- 持久化的意义：无障碍服务会被系统回收重启，内存态会丢失学习结果；落盘后跨会话有效，功能才真正“越用越快”。

## 4. 数据流

```text
用户点击控件
    ↓ TYPE_VIEW_CLICKED
handleViewClicked：构造签名、记录 lastClicked(签名/包名/时间戳)
    ├─ 命中已知触发控件？
    │      是 → 先 showFloatingWindow → 延迟后 forceCheck 校验（误判则隐藏）
    │      否 → 不抢先，等常规链路
    ↓
（页面渲染）TYPE_WINDOW_CONTENT_CHANGED → 防抖 → checkTextContentOptimized
    ↓ 目标页 且 悬浮窗此前隐藏 且 存在新鲜同包点击
maybeLearnTriggerControl → TriggerControlManager.record(包名, 签名) 落盘
```

## 5. 边界与安全回退

- **控件无可识别特征**（viewId、text 全空）：签名为 null，跳过学习与快速通道，退回原纯文字检测。
- **误判纠正**：快速通道显示后必有一次强制检测兜底，非目标页会被隐藏，不会长期误遮。
- **解禁 / 手动隐藏 / 数学题期间**：快速通道直接跳过，交由既有逻辑处理，不破坏解禁体验。
- **跨 APP 串扰**：学习与快速通道均要求 `lastClickedPackage`／当前包名与事件包名一致，加上新鲜度窗口，避免 A 应用的点击影响 B 应用。
- **兼容性**：整套机制是对现有关键词检测的**增量增强**，任一环节缺失（拿不到 viewId、控件未学习、事件缺失）都能平滑退回到原有行为。

## 6. 涉及改动点小结

| 文件 | 改动 |
| --- | --- |
| `FloatService` | eventTypes 加 `TYPE_VIEW_CLICKED`；flags 加 `FLAG_REPORT_VIEW_IDS`；监听接口实现新增 `onTriggerControlClicked` → 显示悬浮窗 |
| `AppStateManager` | 监听接口加 `onTriggerControlClicked`；路由 `TYPE_VIEW_CLICKED` → `handleViewClicked`；新增 `buildClickSignature`、`maybeLearnTriggerControl`；`checkTextContentOptimized` 内快照悬浮窗可见性并在目标页转变时学习 |
| `TriggerControlManager`（新增） | MMKV 持久化 包名→触发控件签名集合，提供 `isTrigger` / `record` |
| `Const` | 新增 `VIEW_CLICK_TRIGGER_FRESHNESS_MS`（点击到目标页确认的新鲜度窗口） |

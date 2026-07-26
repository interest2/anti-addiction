已梳理完毕。以下是近一周（07-22 ~ 07-25）围绕**「离开 APP 时悬浮窗处理 + 闪现问题」**的代码演进脉络，按时间和主题串联。

## 背景（改动前的旧逻辑）
`handleWindowStateChanged` 里用 `lastWindowCheckTime < 350ms` 粗暴忽略短时窗口切换；离开支持的 APP 就直接 `Share.clearAppState` + `removeView()` 销毁悬浮窗。问题：过渡动画/多任务切换会误触发隐藏与重显 → 闪现，左滑还能借"短暂隐藏"作弊。

## 阶段一（07-22）：重构入口 + 引入过渡状态机

**1. `e11fd05` 修复左滑作弊** — AppStateManager 大重构（+311/-136）
- 收敛出统一入口 `handleObservedPackage(packageName, source)`，`handleWindowStateChanged`/轮询都汇入。
- SystemUI 走"延迟确认 + 暂停/恢复"（`suspendedForSystemUi`、`onSystemUiSuspensionChanged`），左滑进 SystemUI 不再立即隐藏，暖态雏形出现，堵住左滑作弊。

**2. `434fa33` 修复退出闪现 + 多任务重回未及时遮挡** — 核心状态机诞生
- 新增 `PackageHideTransition` 三段状态机：`WAITING_FOR_INITIAL_CHECK → PAUSED_AFTER_EARLY_RETURN → MONITORING_DIRECT_REENTRY`，配套新类 `PackageTransitionTiming`（计算复核/重入窗口）。
- 离开目标：`startPackageHideTransition` 先隐藏，`p=300ms` 后 `confirmPackageAtInitialCheck` 首次复核；动画时长内直接重入原 APP 走快速复用路径。
- 关键防闪现：`isPackageDetectionPaused()` 在过渡期间**拦截内容/关键词检测**，杜绝动画期误检测。
- 常量重整：删 `PackageConfirmationMode`/`DEFAULT_APP_STATE_DEBOUNCE_MS`，改为 `PACKAGE_TRANSITION_*`（动画时长 a=1000、首次复核 p=300 等）。

**3. `30b7ad5` 抖音"先显示再检测"** — 针对抖音显示慢
- 新增回调 `onTargetPackageEnteredBeforeContentCheck`。从非目标包名进入抖音时先显示悬浮窗、再检测关键词（快速路径）；用 `enteredFromSystemUi` 标记避免从 SystemUI 返回时误抢显。

## 阶段二（07-24）：泛化快速路径 + 暖窗口复用

**4. `5aed5b4` 防抖逻辑重构**（铺垫）。

**5. `7cf5ad5` 先显示再检测推广到所有目标 APP** — 把抖音专属的 `shouldShowDouyinBeforeContentCheck` 泛化为 `shouldShowBeforeContentCheck`。

**6. `5dfe3f0` 多任务返回场景资源复用** — 离开由"销毁"改"降级暖态"
- 新增 `confirmCurrentAppLeftKeepingWarmWindow` + 回调 `onTargetPackageTransitionLeft`：离开目标不再 `removeView`，而是保留 Window 资源降级暖态（alpha=0 + NOT_TOUCHABLE）。
- 复核期 + `PACKAGE_TRANSITION_WINDOW_REUSE_MS`(2000ms) 内返回可直接复用暖窗口，减少重显延迟和触屏空档。

## 阶段三（07-25）：跨 APP 复用及其副作用修复

**7. `39ef709` 跨 APP 窗口资源复用** — `FloatingWindowManager` 支持跨不同 APP 复用同一 Window。

**8. `5c17b2d` 修复非目标页面退出重进闪现** — 快速路径的副作用
- 问题：若离开时页面本就不是目标页（仅因未命中关键词而隐藏），下次进入"先显示再检测"会先闪一下。
- 解决：`lastDetectionMissingTargetWord` 记录离开时是否"仅因缺关键词而隐藏"，`rememberNextEntryDisplayOrder` 据此写 `detectBeforeShowOnNextEntry`；这类 APP 下次进入改走"先检测再决定是否显示"，绕开快速路径。

**9. `f553bc3` 修复跨 APP 复用导致解禁期间也弹窗**
- `stillInHidePeriod()` 由 `getAppRemainingTime` 改为 `relaxManager.getRecordedRemainingTime`（持久化关闭时间+间隔）。原因：暖窗口跨 APP 复用时 `Share.isFloatingWindowVisible` 残留 true，令 `getAppRemainingTime` 返回 0 哨兵 → 误判超时重弹。

## 一条主线总结
`删除即隐藏` → `延迟确认(暖态雏形，堵左滑)` → `过渡状态机 + 暂停检测(治过渡期闪现)` → `先显示再检测(治显示慢)` → `暖窗口降级+复用(治重显延迟/空档)` → `跨APP复用` → 收尾修两个复用副作用（非目标页闪现、解禁期误弹）。核心权衡始终是**「先显示再检测」的低延迟 vs 抢先显示带来的闪现**，靠"记录离开时页面状态 + 过渡期暂停检测 + 暖窗口复用"三招平衡。

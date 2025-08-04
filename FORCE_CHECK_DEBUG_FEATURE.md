# ForceCheck 触发时间调试功能

## 功能概述

在调试悬浮窗中新增了 `forceCheck` 条件触发时间的显示功能，用于帮助开发者了解 `FloatService.checkTextContentOptimized` 方法中 `if (!forceCheck)` 条件的触发情况。

## 实现细节

### 1. Share.java 新增静态变量

```java
public static long forceCheckTriggerTime = 0; // forceCheck条件触发时间戳
```

### 2. FloatService.java 修改

在 `checkTextContentOptimized` 方法的第331行（`if (!forceCheck)` 条件内）添加了时间戳记录：

```java
if (!forceCheck) {
    // 更新调试信息中的forceCheck触发时间
    Share.forceCheckTriggerTime = System.currentTimeMillis();
    Share.setAppState(currentActiveApp, currentInterface);
    Log.d(TAG, "界面变化检测: " + currentInterface + " (APP: " + appName + ")");
}
```

### 3. DebugFloatingWindowManager.java 修改

在 `updateDebugContent` 方法中添加了 forceCheck 触发时间的显示：

```java
// forceCheck条件触发时间
if (Share.forceCheckTriggerTime > 0) {
    String forceCheckTimeText = timeFormatter.format(new Date(Share.forceCheckTriggerTime));
    debugInfo.append("🔍 forceCheck触发: ").append(forceCheckTimeText).append("\n");
} else {
    debugInfo.append("🔍 forceCheck触发: 无\n");
}
```

## 显示效果

调试悬浮窗中会显示：
- 🔍 forceCheck触发: HH:mm:ss (当条件触发时)
- 🔍 forceCheck触发: 无 (当条件未触发时)

## 技术说明

1. **触发条件**: 当 `forceCheck` 为 `false` 且界面状态发生变化时触发
2. **时间格式**: 使用 `HH:mm:ss` 格式显示
3. **更新频率**: 每2秒更新一次调试信息
4. **持久性**: 时间戳会保持到下次触发或应用重启

## 使用场景

- 调试界面状态检测逻辑
- 监控 `forceCheck` 参数的触发频率
- 分析应用状态变化的时间模式
- 排查悬浮窗显示/隐藏的时机问题

## 测试方法

1. 启动应用并开启调试悬浮窗
2. 切换到支持的应用（如小红书、知乎、抖音）
3. 观察调试悬浮窗中的 "🔍 forceCheck触发" 信息
4. 在不同应用间切换，观察触发时间的变化 
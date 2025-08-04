# HX 时间戳调试功能

## 功能概述

在调试悬浮窗中新增了显示 `Share.h0`、`Share.h1`、`Share.h6`、`Share.h7`、`Share.h8` 时间戳变量的功能，用于实时监控代码中特定位置的执行时间。

## 实现细节

### 1. Share.java 变量定义

在 `Share.java` 中定义了 5 个调试时间戳变量：

```java
// 调试时间戳变量
public static long h0 = 0; // 调试时间戳 h0
public static long h1 = 0; // 调试时间戳 h1
public static long h6 = 0; // 调试时间戳 h6
public static long h7 = 0; // 调试时间戳 h7
public static long h8 = 0; // 调试时间戳 h8
```

### 2. FloatService.java 中的时间戳设置

在 `FloatService.java` 的不同方法中设置了这些时间戳：

- `Share.h0 = System.currentTimeMillis();` - 在特定方法中设置
- `Share.h1 = System.currentTimeMillis();` - 在特定方法中设置
- `Share.h6 = System.currentTimeMillis();` - 在特定方法中设置
- `Share.h7 = System.currentTimeMillis();` - 在特定方法中设置
- `Share.h8 = System.currentTimeMillis();` - 在特定方法中设置

### 3. 调试悬浮窗显示

在 `DebugFloatingWindowManager.java` 的 `updateDebugContent()` 方法中新增了显示逻辑：

```java
// 调试时间戳变量
debugInfo.append("🔧 调试时间戳:\n");
if (Share.h0 > 0) {
    String h0TimeText = timeFormatter.format(new Date(Share.h0));
    debugInfo.append("  h0: ").append(h0TimeText).append("\n");
}
if (Share.h1 > 0) {
    String h1TimeText = timeFormatter.format(new Date(Share.h1));
    debugInfo.append("  h1: ").append(h1TimeText).append("\n");
}
if (Share.h6 > 0) {
    String h6TimeText = timeFormatter.format(new Date(Share.h6));
    debugInfo.append("  h6: ").append(h6TimeText).append("\n");
}
if (Share.h7 > 0) {
    String h7TimeText = timeFormatter.format(new Date(Share.h7));
    debugInfo.append("  h7: ").append(h7TimeText).append("\n");
}
if (Share.h8 > 0) {
    String h8TimeText = timeFormatter.format(new Date(Share.h8));
    debugInfo.append("  h8: ").append(h8TimeText).append("\n");
}
```

## 显示格式

调试悬浮窗中会以以下格式显示时间戳：

```
🔧 调试时间戳:
  h0: 14:30:25
  h1: 14:30:26
  h6: 14:30:27
  h7: 14:30:28
  h8: 14:30:29
```

## 使用说明

1. 启动应用并启用无障碍服务
2. 在设置页面点击"切换调试悬浮窗"按钮
3. 调试悬浮窗会显示在屏幕底部，高度为屏幕的1/2
4. 时间戳信息会每2秒自动更新一次
5. 只有被设置过的时间戳（值大于0）才会显示

## 技术特点

- **实时更新**: 时间戳信息每2秒自动刷新
- **条件显示**: 只显示已设置的时间戳，避免显示无意义的"0"值
- **格式化显示**: 使用 `HH:mm:ss` 格式显示时间，便于阅读
- **层级控制**: 调试悬浮窗置顶显示，不会被主悬浮窗遮挡

## 调试价值

这些时间戳变量可以帮助开发者：

1. **追踪执行流程**: 了解代码中不同位置的执行顺序
2. **性能分析**: 分析各个方法之间的时间间隔
3. **问题定位**: 快速定位可能的问题点
4. **实时监控**: 在应用运行时实时观察关键节点的执行情况

## 注意事项

- 时间戳变量是静态的，在应用重启后会重置
- 调试悬浮窗需要 `SYSTEM_ALERT_WINDOW` 权限
- 建议在调试完成后关闭调试悬浮窗以节省资源 
# 事件时间调试功能

## 功能概述

在调试悬浮窗中新增了无障碍事件的时间显示功能，可以实时查看最后接收到的事件类型和触发时间。

## 新增功能

### 1. 事件类型显示
- **显示内容**: 最后接收到的无障碍事件类型
- **格式**: 事件名称 + 事件ID，例如 "WINDOW_STATE_CHANGED (32)"
- **更新频率**: 每2秒自动更新

### 2. 事件触发时间
- **显示内容**: 最后接收到事件的时间戳
- **格式**: HH:mm:ss 格式
- **更新频率**: 每2秒自动更新

## 技术实现

### 1. 数据存储
在 `Share.java` 中新增了两个静态变量：
```java
public static int lastEventType = 0; // 最后接收到的事件类型
public static long lastEventTime = 0; // 最后接收到事件的时间戳
```

### 2. 数据更新
在 `FloatService.java` 的 `onAccessibilityEvent` 方法中：
```java
// 更新调试信息中的事件类型和时间戳
Share.lastEventType = event.getEventType();
Share.lastEventTime = System.currentTimeMillis();
```

### 3. 显示逻辑
在 `DebugFloatingWindowManager.java` 中：
```java
// 无障碍事件类型
String eventTypeText = getEventTypeText(Share.lastEventType);
debugInfo.append("📡 事件类型: ").append(eventTypeText).append("\n");

// 事件触发时间
if (Share.lastEventTime > 0) {
    String eventTimeText = timeFormatter.format(new Date(Share.lastEventTime));
    debugInfo.append("⏱️ 事件时间: ").append(eventTimeText).append("\n");
} else {
    debugInfo.append("⏱️ 事件时间: 无\n");
}
```

## 支持的事件类型

### 常用事件类型
- `VIEW_CLICKED (1)` - 视图点击事件
- `VIEW_LONG_CLICKED (2)` - 视图长按事件
- `VIEW_SELECTED (4)` - 视图选择事件
- `VIEW_FOCUSED (8)` - 视图获得焦点事件
- `VIEW_TEXT_CHANGED (16)` - 文本变化事件
- `WINDOW_STATE_CHANGED (32)` - 窗口状态变化事件
- `WINDOW_CONTENT_CHANGED (2048)` - 窗口内容变化事件

### 其他事件类型
- `VIEW_SCROLLED (128)` - 视图滚动事件
- `VIEW_ACCESSIBILITY_FOCUSED (32768)` - 无障碍焦点事件
- `VIEW_ACCESSIBILITY_FOCUS_CLEARED (65536)` - 无障碍焦点清除事件
- `ANNOUNCEMENT (16384)` - 公告事件
- 等等...

## 使用场景

### 1. 调试无障碍服务
- 查看无障碍服务是否正常工作
- 监控事件接收频率
- 分析事件触发时机

### 2. 性能分析
- 观察事件处理延迟
- 监控事件接收间隔
- 分析系统响应时间

### 3. 问题排查
- 确认无障碍服务是否接收到事件
- 验证事件类型是否正确
- 检查事件触发时间是否合理

## 显示效果

调试悬浮窗中会显示如下信息：
```
⏰ 当前时间: 14:30:25
📡 事件类型: WINDOW_STATE_CHANGED (32)
⏱️ 事件时间: 14:30:24

📱 当前APP: 微信
📦 包名: com.tencent.mm
🎯 目标词: 微信
📊 APP状态: target
🚫 手动隐藏: 否
⏱️ 隐藏时间: 14:25:10

🪟 主悬浮窗: 显示
🔧 调试悬浮窗: 显示

⏰ 时间间隔: 300秒
🎯 宽松模式: 否

💾 内存使用:
   已用: 45.2 MB
   可用: 123.8 MB
   总计: 169.0 MB
   最大: 256.0 MB
```

## 注意事项

1. **时间精度**: 事件时间精确到秒，与当前时间使用相同的时间格式
2. **更新频率**: 事件信息每2秒更新一次，不是实时更新
3. **初始状态**: 应用启动时如果没有接收到事件，会显示"无"
4. **性能影响**: 新增功能对性能影响很小，只是简单的变量存储和显示

## 故障排除

### 问题1: 事件类型显示"UNKNOWN"
**可能原因**: 接收到未知类型的事件
**解决方法**: 这是正常现象，表示接收到系统未定义的事件类型

### 问题2: 事件时间显示"无"
**可能原因**: 无障碍服务未启动或未接收到事件
**解决方法**: 
1. 检查无障碍服务是否已启动
2. 尝试切换应用触发事件
3. 重启无障碍服务

### 问题3: 事件信息不更新
**可能原因**: 调试悬浮窗更新机制异常
**解决方法**:
1. 点击调试悬浮窗的"刷新"按钮
2. 重新开启调试悬浮窗
3. 重启应用

## 开发说明

### 添加新的事件类型
如果需要支持新的事件类型，可以在 `getEventTypeText()` 方法中添加新的case：

```java
case android.view.accessibility.AccessibilityEvent.NEW_EVENT_TYPE:
    return "NEW_EVENT_TYPE (event_id)";
```

### 修改时间格式
如果需要修改时间显示格式，可以修改 `timeFormatter` 的定义：

```java
private static final SimpleDateFormat timeFormatter = 
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
``` 
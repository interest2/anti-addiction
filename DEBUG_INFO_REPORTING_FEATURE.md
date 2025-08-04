# 调试信息定期上报功能

## 功能概述

实现了每2秒自动上报 debugInfo 内容到云端接口的功能，用于实时监控应用运行状态和调试信息。

## 实现细节

### 1. DebugInfoReporter 类

创建了新的 `DebugInfoReporter` 类，负责收集和上报调试信息：

#### 主要功能：
- **定期上报**：支持设置上报间隔，默认每2秒上报一次
- **完整调试信息收集**：收集所有 Share 类中的调试变量
- **网络状态检查**：上报前检查网络连接状态
- **异步处理**：使用线程池处理网络请求，避免阻塞主线程

#### 收集的调试信息包括：
- **时间戳信息**：
  - `lastEventType`：最后接收到的事件类型
  - `lastEventTime`：最后接收到事件的时间戳
  - `findTextInNodeTime`：forceCheck条件触发时间戳
  - `h0`, `h1`, `h7`, `h8`：调试时间戳变量

- **界面状态信息**：
  - `currentInterface`：当前界面状态
  - `forceCheck`：是否强制检查

- **APP信息**：
  - 当前活跃APP的名称、包名、类名
  - APP状态（target/not target）
  - 隐藏时间戳
  - 手动隐藏状态

- **悬浮窗状态**：
  - `isFloatingWindowVisible`：悬浮窗是否显示

- **设备信息**：
  - 设备品牌、型号
  - Android版本、SDK版本
  - 应用版本信息

### 2. FloatService 集成

在 `FloatService` 中集成了调试信息上报器：

#### 初始化：
```java
// 初始化调试信息上报器并开始定期上报
debugInfoReporter = new DebugInfoReporter(this);
debugInfoReporter.startPeriodicReporting(2); // 每2秒上报一次
```

#### 资源清理：
```java
// 释放调试信息上报器资源
if (debugInfoReporter != null) {
    debugInfoReporter.release();
    debugInfoReporter = null;
}
```

### 3. 接口配置

在 `Const.java` 中添加了调试接口路径：
```java
public static final String DEBUG_PATH = "/debug"; // 调试信息上报接口
```

### 4. 上报数据格式

上报的JSON数据格式示例：
```json
{
  "timestamp": 1703123456789,
  "reportTime": "2023-12-21 10:30:56.789",
  "lastEventType": 32,
  "lastEventTime": 1703123456000,
  "lastEventTimeFormatted": "2023-12-21 10:30:56.000",
  "findTextInNodeTime": 1703123456500,
  "findTextInNodeTimeFormatted": "2023-12-21 10:30:56.500",
  "h0": 1703123456000,
  "h0Formatted": "2023-12-21 10:30:56.000",
  "h1": 1703123456100,
  "h1Formatted": "2023-12-21 10:30:56.100",
  "h7": 1703123456500,
  "h7Formatted": "2023-12-21 10:30:56.500",
  "h8": 1703123456600,
  "h8Formatted": "2023-12-21 10:30:56.600",
  "currentInterface": "target",
  "forceCheck": false,
  "currentApp": {
    "appName": "微信",
    "packageName": "com.tencent.mm",
    "className": "com.tencent.mm.ui.LauncherUI",
    "appState": "target",
    "hiddenTimestamp": 1703123450000,
    "hiddenTimestampFormatted": "2023-12-21 10:30:50.000",
    "isManuallyHidden": false
  },
  "isFloatingWindowVisible": true,
  "deviceInfo": {
    "brand": "Xiaomi",
    "model": "Mi 10",
    "androidVersion": "11",
    "sdkVersion": 30,
    "appVersion": "1.0.0",
    "appVersionCode": 1
  }
}
```

## 使用方法

### 启动上报
服务启动时自动开始每2秒上报一次调试信息。

### 停止上报
服务销毁时自动停止上报并释放资源。

### 手动上报
可以通过调用 `debugInfoReporter.reportDebugInfo()` 立即上报一次调试信息。

## 网络要求

- 需要网络连接
- 上报前会检查网络状态
- 网络不可用时跳过上报，不会影响应用正常运行

## 性能考虑

- 使用线程池处理网络请求，避免阻塞主线程
- 网络请求超时设置为10秒连接、15秒读取
- 上报失败时记录日志，不影响应用正常运行
- 定期上报使用 `ScheduledExecutorService`，性能稳定

## 日志输出

调试信息上报器会输出详细的日志信息：
- 启动/停止上报的日志
- 网络请求成功/失败的日志
- 收集的调试信息内容日志

## 云端接口

调试信息会上报到：`https://www.ratetend.com:5001/antiAddict/debug`

接口要求：
- 方法：POST
- Content-Type：application/json
- 请求体：调试信息JSON字符串 
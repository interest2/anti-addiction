# 设备信息上报功能使用说明

## 功能概述

设备信息上报功能会自动收集设备的基本信息并通过HTTPS接口上报到云端服务器，用于统计分析、设备管理或数据收集。

## 收集的设备信息

### 基本设备信息
- **品牌 (brand)**: 设备品牌，如 "Samsung", "Xiaomi"
- **型号 (model)**: 设备型号，如 "SM-G9730", "MI 11"
- **制造商 (manufacturer)**: 制造商名称
- **设备代号 (device)**: 设备内部代号
- **产品名称 (product)**: 产品名称

### 系统版本信息
- **Android版本 (android_version)**: 如 "11", "12"
- **SDK版本 (sdk_version)**: API级别，如 30, 31
- **构建ID (build_id)**: 系统构建ID
- **构建时间 (build_time)**: 系统构建时间戳

### 硬件信息
- **硬件信息 (hardware)**: 硬件平台信息
- **主板信息 (board)**: 主板型号
- **CPU架构 (cpu_abi)**: CPU架构，如 "arm64-v8a"

### 唯一标识符
- **序列号 (serial_number)**: 设备序列号（处理权限问题）
- **Android ID (android_id)**: 系统唯一标识符

### 应用信息
- **应用版本 (app_version)**: 应用版本号
- **应用版本码 (app_version_code)**: 应用版本码
- **应用包名 (app_package)**: 应用包名

### 网络信息
- **网络类型 (network_type)**: 当前网络类型
- **网络连接状态 (network_connected)**: 网络连接状态

### 时间戳
- **时间戳 (timestamp)**: 上报时间戳

## 配置说明

### 1. 修改上报接口地址

在 `DeviceInfoReporter.java` 中修改 `REPORT_URL` 常量：

```java
private static final String REPORT_URL = "https://your-api-domain.com/device/report";
```

### 2. 设置请求头

可以在 `sendDeviceInfo` 方法中添加自定义请求头：

```java
connection.setRequestProperty("Authorization", "Bearer your-token");
connection.setRequestProperty("X-API-Key", "your-api-key");
```

### 3. 自定义上报逻辑

可以重写 `onReportSuccess` 和 `onReportFailure` 方法来处理上报结果：

```java
private void onReportSuccess(String response) {
    Log.i(TAG, "设备信息上报成功");
    // 添加成功后的处理逻辑
}

private void onReportFailure(String error) {
    Log.w(TAG, "设备信息上报失败: " + error);
    // 添加失败后的处理逻辑，如重试机制
}
```

## 上报时机

### 自动上报
- 应用启动时（MainActivity.onCreate）
- 无障碍服务启动时（FloatingAccessibilityService.onServiceConnected）

### 手动上报
- 点击"测试悬浮窗功能"按钮时会触发测试上报

## 权限要求

### 必需权限
- `android.permission.INTERNET`: 网络访问权限
- `android.permission.ACCESS_NETWORK_STATE`: 网络状态权限

### 可选权限
- `android.permission.READ_PHONE_STATE`: 读取设备序列号（Android 8.0+）

## 数据格式

上报的数据格式为JSON，示例：

```json
{
  "brand": "Samsung",
  "model": "SM-G9730",
  "manufacturer": "samsung",
  "device": "star2qltechn",
  "product": "star2qltechn",
  "android_version": "11",
  "sdk_version": 30,
  "build_id": "RP1A.200720.012",
  "build_time": 1607081441000,
  "hardware": "exynos9820",
  "board": "exynos9820",
  "cpu_abi": "arm64-v8a",
  "serial_number": "no_permission_123456789",
  "android_id": "a1b2c3d4e5f6g7h8",
  "app_version": "1.0",
  "app_version_code": 1,
  "app_package": "com.book.mask",
  "network_type": "WIFI",
  "network_connected": true,
  "timestamp": 1673856000000
}
```

## 安全考虑

### 隐私保护
- 序列号获取会检查权限，无权限时使用替代方案
- 不会收集个人敏感信息
- 所有数据通过HTTPS加密传输

### 网络安全
- 使用HTTPS协议确保数据传输安全
- 设置合理的连接和读取超时时间
- 处理网络异常情况

## 测试方法

### 1. 查看日志
通过Android Studio的Logcat查看上报日志：
```
Tag: DeviceInfoReporter
Level: Debug/Info/Warning/Error
```

### 2. 网络抓包
使用Charles、Fiddler等工具抓取HTTPS请求

### 3. 服务端验证
检查服务端是否收到设备信息数据

## 常见问题

### Q: 上报失败怎么办？
A: 检查网络连接、服务端接口是否正常、请求格式是否正确

### Q: 如何获取真实序列号？
A: 需要申请READ_PHONE_STATE权限，并在运行时请求用户授权

### Q: 如何添加重试机制？
A: 在onReportFailure方法中添加延迟重试逻辑

### Q: 如何定期上报？
A: 使用Timer或Handler定期调用reportDeviceInfo()方法

## 扩展功能

### 1. 添加更多设备信息
```java
// 电池信息
BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
int batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

// 存储信息
StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
long totalBytes = statFs.getTotalBytes();
long availableBytes = statFs.getAvailableBytes();
```

### 2. 添加位置信息（需要位置权限）
```java
LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
// 获取位置信息
```

### 3. 添加应用使用统计
```java
UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
// 获取应用使用统计
```

## 版本更新日志

### v1.0
- 基本设备信息收集
- HTTPS上报功能
- 权限处理
- 网络状态检查
- 异步处理
- 资源管理

---

**注意**: 请根据实际需求和隐私政策合规要求使用此功能。 
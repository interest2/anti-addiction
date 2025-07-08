# 📡 本地调试配置说明

## 🚫 问题描述

从Android 9（API level 28）开始，默认禁止明文HTTP流量，只允许HTTPS。在本地调试时会遇到错误：
```
Cleartext HTTP traffic to localhost not permitted
```

## ✅ 解决方案

我们已经为您配置了网络安全策略，允许以下地址使用HTTP：

### 📍 允许的本地调试地址
- `localhost` - 本地主机
- `127.0.0.1` - 本地回环地址
- `10.0.2.2` - Android模拟器中的宿主机地址
- `192.168.1.1` - 常见路由器地址
- `192.168.0.1` - 另一个常见路由器地址

## 🔧 自定义配置

如果您的调试服务器使用其他IP地址，请修改 `app/src/main/res/xml/network_security_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">192.168.1.1</domain>
        <domain includeSubdomains="true">192.168.0.1</domain>
        <!-- 添加您的自定义IP地址 -->
        <domain includeSubdomains="true">您的IP地址</domain>
    </domain-config>
</network-security-config>
```

## 🛠️ 常见调试场景

### 1. 使用Android模拟器
- 服务器地址：`http://10.0.2.2:端口号`
- 模拟器会自动将 `10.0.2.2` 映射到宿主机的 `127.0.0.1`

### 2. 使用真机调试
- 确保手机和电脑在同一WiFi网络
- 使用电脑的局域网IP地址，如：`http://192.168.1.100:端口号`
- 需要在网络安全配置中添加该IP地址

### 3. 使用localhost
- 服务器地址：`http://localhost:端口号` 或 `http://127.0.0.1:端口号`
- 仅适用于模拟器或特殊网络配置

## 📝 DeviceInfoReporter配置

当前设备信息上报器配置：
```java
// 位置：app/src/main/java/com/book/baisc/network/DeviceInfoReporter.java
private static final String REPORT_URL = "http://localhost/device/report";
```

根据您的调试环境修改此URL：
```java
// 示例配置
private static final String REPORT_URL = "http://10.0.2.2:8080/device/report"; // 模拟器
private static final String REPORT_URL = "http://192.168.1.100:8080/device/report"; // 真机
private static final String REPORT_URL = "http://localhost:3000/device/report"; // 本地服务
```

## 🔍 调试技巧

### 1. 查看网络日志
在 `DeviceInfoReporter.java` 中已经添加了详细日志：
```bash
# 查看日志
adb logcat -s DeviceInfoReporter
```

### 2. 测试网络连接
```java
// 在代码中添加网络测试
Log.d("NetworkTest", "尝试连接: " + REPORT_URL);
```

### 3. 验证IP地址
```bash
# 查看电脑IP地址
ipconfig  # Windows
ifconfig  # macOS/Linux
```

## ⚠️ 注意事项

### 1. 仅用于调试
- 此配置仅用于本地调试
- 生产环境应该使用HTTPS

### 2. 安全考虑
- 只有指定的域名/IP可以使用HTTP
- 其他所有连接仍然要求HTTPS

### 3. 网络防火墙
- 确保本地防火墙允许相应端口
- 某些公司网络可能有限制

## 🚀 快速测试

1. **启动本地服务器**
   ```bash
   # 例如使用Python启动简单HTTP服务器
   python -m http.server 8080
   ```

2. **修改URL配置**
   ```java
   private static final String REPORT_URL = "http://10.0.2.2:8080/device/report";
   ```

3. **重新编译运行**
   ```bash
   ./gradlew clean assembleDebug
   ```

4. **查看日志验证**
   ```bash
   adb logcat -s DeviceInfoReporter
   ```

---

**配置完成日期**: 2025-01-08  
**适用版本**: Android 9+ (API 28+)  
**状态**: ✅ 已配置完成 
# 悬浮窗动态文字 - 真实API集成

## 功能概述
悬浮窗现在集成了真实的API服务，从线上平台获取动态文字内容。

## API配置

### 接口信息
- **API地址**: `https://www.ratetend.com:5001/antiAddict/llm`
- **请求方法**: GET
- **查询参数**: `tag=找工作`
- **完整URL**: `https://www.ratetend.com:5001/antiAddict/llm?tag=找工作`

### 响应格式
```json
{
  "status": 200,
  "msg": "success",
  "data": "实际的文字内容"
}
```

### ResponseVo格式
```java
public class ResponseVo<T> {
    private Integer status;
    private String msg;
    private T data;
}
```

## 代码修改

### 1. API地址配置
```java
private static final String API_URL = "https://www.ratetend.com:5001/antiAddict/llm";
```

### 2. 请求方法修改
- 从POST改为GET请求
- 查询参数直接添加到URL中
- 移除请求体相关代码

### 3. JSON解析修改
```java
// 检查status状态
if (jsonResponse.has("status")) {
    int status = jsonResponse.getInt("status");
    if (status == 200) {
        // 从data字段提取文字内容
        String text = jsonResponse.optString("data", "");
        if (!text.isEmpty()) {
            return text;
        }
    } else {
        String msg = jsonResponse.optString("msg", "未知错误");
        Log.w(TAG, "服务器返回错误状态: " + status + ", 消息: " + msg);
    }
}
```

## 使用方法

### 1. 不需要本地服务器
由于使用真实的API服务，不再需要启动本地测试服务器。

### 2. 测试功能
1. 确保设备有网络连接
2. 启动Android应用
3. 进入小红书的"发现"页面
4. 悬浮窗会显示从API获取的动态文字内容

### 3. 网络要求
- 需要HTTPS连接支持
- 确保网络安全配置允许访问外部API

## 错误处理

### 常见错误状态
- `status: 200` - 成功
- `status: 400` - 请求参数错误
- `status: 500` - 服务器内部错误
- `status: 其他` - 根据msg字段显示具体错误

### 调试日志
```
FloatingTextFetcher: 开始获取最新文字内容
FloatingTextFetcher: HTTP响应码: 200
FloatingTextFetcher: HTTP响应内容: {"status":200,"msg":"success","data":"文字内容"}
FloatingTextFetcher: 获取到新文字: 文字内容
```

## 缓存机制

### 智能缓存
- 首次显示使用默认文字
- 成功获取后缓存到本地
- 网络失败时使用缓存文字
- 每次显示悬浮窗时异步更新

### 缓存策略
- 缓存有效期：5分钟
- 存储位置：SharedPreferences
- 自动清理：应用卸载时清理

## 性能优化

### 网络请求优化
- 连接超时：5秒
- 读取超时：10秒
- 异步执行：不阻塞UI线程
- 错误重试：自动容错处理

### 内存管理
- 及时释放网络连接
- 清理不必要的资源
- 优化JSON解析

## 注意事项

1. **网络权限**：应用需要网络访问权限
2. **HTTPS支持**：确保应用支持HTTPS连接
3. **网络稳定性**：网络不稳定时会使用缓存文字
4. **API可用性**：依赖外部API服务的稳定性
5. **数据使用**：每次获取文字会产生网络流量

## 故障排除

### 网络连接问题
```
检查网络连接
确认API服务可访问
查看应用网络权限
```

### 解析失败问题
```
检查响应格式是否正确
确认status字段存在
验证data字段内容
```

### 缓存问题
```
清理应用缓存
检查SharedPreferences
重启应用重新获取
```

## 扩展功能

### 自定义tag参数
修改URL中的tag参数来获取不同类型的文字内容：
```java
URL url = new URL(API_URL + "?tag=学习");
URL url = new URL(API_URL + "?tag=工作");
URL url = new URL(API_URL + "?tag=健康");
```

### 多语言支持
可以在请求中添加语言参数：
```java
URL url = new URL(API_URL + "?tag=找工作&lang=zh");
```

### 自定义缓存时间
修改缓存有效期：
```java
// 将5分钟改为其他时间
return timeDiff > 10 * 60 * 1000; // 10分钟
```

这样的集成方式让应用能够获取真实的、动态的文字内容，提供更好的用户体验。 
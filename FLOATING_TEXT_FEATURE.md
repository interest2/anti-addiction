# 悬浮窗动态文字功能

## 功能概述
悬浮窗现在支持显示动态文字内容，通过HTTP接口从平台获取最新的文字信息。每次显示悬浮窗时，会先显示缓存的文字内容，然后异步获取最新的文字并更新显示。

## 主要特性

### 1. 智能缓存机制
- 首次显示时使用默认文字
- 后续显示时优先使用缓存的文字内容
- 异步获取最新文字后更新显示

### 2. 容错处理
- 网络请求失败时继续使用缓存文字
- 提供默认文字内容作为备用
- 详细的错误日志记录

### 3. 高效的网络请求
- 使用单独的线程执行HTTP请求
- 5秒连接超时，10秒读取超时
- 自动重试和错误处理

### 4. 数据缓存
- 使用SharedPreferences存储缓存数据
- 记录最后更新时间
- 支持缓存有效期检查

## 技术实现

### 核心类
- `FloatingTextFetcher`: 负责HTTP请求和文字缓存
- `FloatingAccessibilityService`: 集成动态文字功能

### API接口
- **接口地址**: `http://10.0.2.2:8003/api/floating-text`
- **请求方法**: POST
- **请求格式**: JSON
- **响应格式**: JSON

### 请求示例
```json
{
  "requestType": "getFloatingText",
  "timestamp": 1703123456789
}
```

### 响应示例
```json
{
  "success": true,
  "text": "🌅 早上好！新的一天开始了\n当前时间: 09:15:30\n记得保护眼睛👀",
  "timestamp": "2023-12-21T09:15:30.123456",
  "message": "文字内容获取成功"
}
```

## 使用方法

### 1. 启动测试服务器
```bash
# 方法1: 使用批处理脚本
start_floating_text_server.bat

# 方法2: 直接运行Python脚本
python floating_text_test_server.py
```

### 2. 服务器配置
- 默认端口: 8003 (自动查找可用端口)
- Android访问地址: `http://10.0.2.2:8003`
- 本地访问地址: `http://localhost:8003`

### 3. 应用使用
1. 确保测试服务器正在运行
2. 启动Android应用
3. 进入小红书的"发现"页面
4. 悬浮窗会显示动态文字内容

## 文字内容生成

### 动态内容规则
- 根据当前时间生成不同的问候语
- 显示实时时间信息
- 随机显示健康提示

### 时间段分类
- 00:00-06:00: 夜深了，注意休息哦 🌙
- 06:00-12:00: 早上好！新的一天开始了 🌅
- 12:00-18:00: 下午好！适度使用手机 ☀️
- 18:00-24:00: 晚上好！放松一下吧 🌆

### 健康提示
- 记得保护眼睛👀
- 适当休息很重要💪
- 保持良好作息😊
- 多喝水有益健康💧
- 户外活动更健康🌿

## 调试和故障排除

### 常见问题

1. **网络连接失败**
   - 检查测试服务器是否启动
   - 确认端口未被占用
   - 检查网络配置

2. **文字不更新**
   - 查看日志输出
   - 检查缓存是否正常
   - 确认HTTP请求成功

3. **服务器启动失败**
   - 检查Python环境
   - 确认端口可用
   - 查看错误日志

### 日志标识
- `FloatingTextFetcher`: 文字获取相关日志
- `FloatingAccessibility`: 悬浮窗服务日志

### 调试命令
```bash
# 查看端口占用
netstat -an | findstr :8003

# 测试API接口
curl -X POST http://10.0.2.2:8003/api/floating-text \
  -H "Content-Type: application/json" \
  -d '{"requestType": "getFloatingText", "timestamp": 1703123456789}'
```

## 扩展功能

### 自定义API地址
修改 `FloatingTextFetcher.java` 中的 `API_URL` 常量:
```java
private static final String API_URL = "http://your-server.com/api/floating-text";
```

### 自定义缓存策略
修改 `shouldUpdateText()` 方法中的时间间隔:
```java
// 将5分钟改为其他时间
return timeDiff > 5 * 60 * 1000;
```

### 自定义文字格式
修改 `updateFloatingWindowContentWithText()` 方法:
```java
String content = text;
// 添加自定义格式化逻辑
content = formatCustomText(content);
```

## 注意事项

1. **网络权限**: 确保应用有网络访问权限
2. **性能影响**: HTTP请求在后台线程执行，不会阻塞UI
3. **数据使用**: 每次获取文字会产生少量网络流量
4. **缓存管理**: 定期清理缓存可能需要手动实现
5. **服务器稳定性**: 生产环境需要更稳定的服务器部署

## 后续优化

1. **添加文字格式化功能**
2. **支持图片和富文本内容**
3. **增加更多个性化选项**
4. **优化缓存策略**
5. **增强错误处理机制**

这个功能为悬浮窗提供了动态内容展示能力，让用户体验更加个性化和实时化。 
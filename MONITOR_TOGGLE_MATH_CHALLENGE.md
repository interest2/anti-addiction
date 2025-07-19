# 监测开关算术题验证功能

## 功能描述

当用户点击关闭屏蔽开关时，会弹出一个算术题验证弹窗，用户必须答对算术题才能成功关闭屏蔽。

## 功能特性

### 触发条件
- **开启监测**：直接执行，无需验证
- **关闭屏蔽**：需要算术题验证

### 算术题类型
- **加法**：三位数加法（100-999）
- **减法**：三位数减法（确保结果为正数）
- **乘法**：两位数乘法（11-19）

### 验证流程
1. 用户点击关闭屏蔽开关
2. 弹出算术题验证弹窗
3. 用户输入答案
4. 验证答案正确性
5. 答对：关闭屏蔽并显示成功提示
6. 答错：显示错误提示，生成新题目

## 技术实现

### 核心方法

#### HomeFragment.java - onMonitorToggle方法
```java
@Override
public void onMonitorToggle(Object app, boolean isEnabled) {
    String packageName = getPackageName(app);
    if (packageName != null) {
        // 如果要关闭屏蔽，需要算术题验证
        if (!isEnabled) {
            showMathChallengeForMonitorToggle(app, packageName);
        } else {
            // 开启监测直接执行
            settingsManager.setAppMonitoringEnabled(packageName, isEnabled);
            // 显示提示
            String status = isEnabled ? "已开启监测" : "已关闭屏蔽";
            Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show();
        }
    }
}
```

#### 算术题验证弹窗
```java
private void showMathChallengeForMonitorToggle(Object app, String packageName) {
    // 创建算术题验证弹窗
    View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_math_challenge, null);
    
    // 生成算术题
    String question = generateMathQuestion();
    final int[] correctAnswer = {getMathAnswer(question)};
    
    // 设置弹窗和事件处理
    // ...
}
```

### 算术题生成逻辑

#### 复用现有逻辑
```java
private String generateMathQuestion() {
    Random random = new Random();
    int operationType = random.nextInt(3); // 0: 加, 1: 减, 2: 乘
    int num1, num2;
    String operator;
    
    switch (operationType) {
        case 0: // 加法 - 三位数
            num1 = random.nextInt(900) + 100; // 100-999
            num2 = random.nextInt(900) + 100; // 100-999
            operator = "+";
            break;
        case 1: // 减法 - 三位数
            num1 = random.nextInt(800) + 200; // 200-999
            num2 = random.nextInt(num1 - 200) + 100;
            operator = "-";
            break;
        case 2: // 乘法 - 30以内
            num1 = random.nextInt(19) + 11; // 11-19
            num2 = random.nextInt(19) + 11; // 11-19
            operator = "×";
            break;
        default:
            num1 = random.nextInt(900) + 100;
            num2 = random.nextInt(900) + 100;
            operator = "+";
    }
    
    return num1 + " " + operator + " " + num2 + " = ?";
}
```

### 布局文件

#### dialog_math_challenge.xml
```xml
<LinearLayout>
    <!-- 标题 -->
    <TextView
        android:text="🔢 回答算术题才能关闭屏蔽"
        android:textSize="16sp"
        android:textStyle="bold" />
    
    <!-- 算术题 -->
    <TextView
        android:id="@+id/tv_math_question"
        android:textSize="24sp"
        android:textColor="#2196F3"
        android:textStyle="bold" />
    
    <!-- 输入区域 -->
    <LinearLayout android:orientation="horizontal">
        <EditText
            android:id="@+id/et_math_answer"
            android:hint="输入答案"
            android:inputType="numberSigned"
            android:maxLength="6" />
        
        <Button
            android:id="@+id/btn_submit_answer"
            android:text="确定" />
    </LinearLayout>
    
    <!-- 结果提示 -->
    <TextView
        android:id="@+id/tv_math_result"
        android:visibility="gone" />
    
    <!-- 取消按钮 -->
    <Button
        android:id="@+id/btn_cancel_close"
        android:text="取消" />
</LinearLayout>
```

## 用户体验

### 交互流程
1. **用户操作**：点击APP卡片上的监测开关（关闭状态）
2. **弹窗显示**：算术题验证弹窗出现
3. **题目展示**：显示随机生成的算术题
4. **用户输入**：在输入框中输入答案
5. **答案验证**：
   - **正确**：显示"✅ 答案正确！"，1秒后关闭弹窗并关闭屏蔽
   - **错误**：显示"❌ 答案错误，请重新计算"，1秒后生成新题目
6. **取消操作**：点击取消按钮，弹窗关闭，开关状态恢复

### 输入方式
- **键盘输入**：支持数字键盘输入
- **回车提交**：按回车键直接提交答案
- **按钮提交**：点击确定按钮提交答案

### 错误处理
- **空输入**：提示"⚠️ 请输入答案"
- **无效数字**：提示"⚠️ 请输入有效数字"
- **答案错误**：提示"❌ 答案错误，请重新计算"

## 功能特点

### 安全性
- **强制验证**：关闭屏蔽必须通过算术题验证
- **防误操作**：避免用户意外关闭屏蔽
- **难度适中**：算术题难度适中，不会过于困难

### 用户体验
- **即时反馈**：答案验证结果立即显示
- **自动刷新**：答错后自动生成新题目
- **状态恢复**：取消操作后恢复开关状态

### 技术优势
- **代码复用**：复用现有的算术题生成逻辑
- **一致性**：与悬浮窗算术题验证保持一致
- **可扩展**：易于添加新的验证方式

## 与现有功能的对比

### 悬浮窗算术题验证
- **触发条件**：点击悬浮窗关闭按钮
- **验证目的**：关闭悬浮窗
- **界面样式**：悬浮窗内嵌验证界面

### 监测开关算术题验证
- **触发条件**：点击监测开关（关闭）
- **验证目的**：关闭APP监测
- **界面样式**：独立弹窗验证界面

### 共同特点
- **算术题类型**：相同的生成逻辑
- **验证机制**：相同的答案验证流程
- **用户体验**：一致的交互方式

## 技术细节

### 变量处理
- **Lambda表达式**：使用数组包装变量以支持在lambda中修改
- **状态管理**：正确处理弹窗状态和开关状态同步

### 事件处理
- **按钮事件**：确定、取消按钮的事件处理
- **键盘事件**：回车键提交答案
- **焦点管理**：自动获得输入框焦点

### 界面更新
- **实时更新**：验证结果实时显示
- **状态同步**：开关状态与验证结果同步
- **列表刷新**：验证成功后更新APP列表显示 
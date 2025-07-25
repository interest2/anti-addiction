# 防沉迷APP新主界面功能说明

## 🎯 功能概述

新的主界面采用卡片式设计，为每个支持的APP显示独立的信息卡片，提供更直观的用户体验。

## 📱 支持的APP

- 小红书
- 支付宝
- 知乎
- 微信
- 抖音
- 哔哩哔哩

## 🎨 界面特性

### 1. APP卡片显示
- 每个APP显示为一个独立的卡片
- 卡片包含：
  - APP名称
  - 剩余时长倒计时（绿色表示可用，红色表示不可用）
  - 宽松模式剩余次数

### 2. 点击卡片功能
- 点击任意APP卡片会弹出设置弹窗
- 弹窗标题：`[APP名称] - 单次解禁时长`
- 弹窗内容：显示"请选择解禁模式："
- 提供三个按钮：
  - **严格模式**：设置严格的时间限制
  - **宽松模式**：设置宽松的时间限制
  - **取消**：关闭弹窗

### 3. 实时更新
- 剩余时长倒计时每秒更新
- 宽松模式剩余次数实时显示
- 卡片状态根据APP可用性动态变化

## 🔧 技术实现

### 新增文件
- `item_app_card.xml`：APP卡片布局文件
- `AppCardAdapter.java`：RecyclerView适配器

### 修改文件
- `fragment_home.xml`：主界面布局，使用RecyclerView显示APP卡片
- `HomeFragment.java`：实现卡片点击事件和倒计时更新
- `SettingsDialogManager.java`：将`showTimeSettingDialogForApp`方法改为public
- `MainActivity.java`：更新广播接收器以支持新的UI更新机制

### 依赖添加
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.cardview:cardview:1.0.0`

## 🚀 使用流程

1. 打开应用，查看所有支持的APP卡片
2. 点击任意APP卡片
3. 在弹出的对话框中选择"严格模式"或"宽松模式"
4. 在后续的时长选择对话框中选择具体的时间间隔
5. 设置完成后，卡片会实时显示剩余时长和宽松模式次数

## 📊 状态指示

- **绿色文字**：APP可以自由使用
- **红色文字**：APP正在倒计时中，需要等待
- **宽松模式剩余次数**：显示今日还可以使用宽松模式的次数

## 🔄 兼容性

新界面完全兼容原有的设置逻辑，所有功能保持不变，只是改变了展示方式，提供了更好的用户体验。 
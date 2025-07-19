# 拟物风格监测开关

## 设计特点

### 开关样式
- **拟物风格**：类似iOS开关的经典设计
- **圆角矩形**：12dp圆角，现代感十足
- **尺寸**：32dp × 18dp，比例协调

### 状态设计

#### 开启状态（绿色）
```
┌─────────────────┐
│ ████████ ●      │
└─────────────────┘
```
- **背景**：绿色 (#4CAF50)
- **圆点**：白色圆点位于右端
- **含义**：监测已开启

#### 关闭状态（白色）
```
┌─────────────────┐
│ ● ████████      │
└─────────────────┘
```
- **背景**：白色背景 + 灰色边框
- **圆点**：灰色圆点位于左端
- **含义**：监测已关闭

## 技术实现

### Drawable结构
```xml
<selector>
    <!-- 开启状态 -->
    <item android:state_checked="true">
        <layer-list>
            <!-- 绿色背景 -->
            <item>
                <shape android:shape="rectangle">
                    <solid android:color="#4CAF50" />
                    <corners android:radius="12dp" />
                </shape>
            </item>
            <!-- 右端白色圆点 -->
            <item android:gravity="end" android:right="2dp">
                <shape android:shape="oval">
                    <solid android:color="#FFFFFF" />
                    <size android:width="10dp" android:height="10dp" />
                </shape>
            </item>
        </layer-list>
    </item>
    
    <!-- 关闭状态 -->
    <item android:state_checked="false">
        <layer-list>
            <!-- 白色背景 -->
            <item>
                <shape android:shape="rectangle">
                    <solid android:color="#FFFFFF" />
                    <stroke android:width="1dp" android:color="#CCCCCC" />
                    <corners android:radius="12dp" />
                </shape>
            </item>
            <!-- 左端灰色圆点 -->
            <item android:gravity="start" android:left="2dp">
                <shape android:shape="oval">
                    <solid android:color="#CCCCCC" />
                    <size android:width="10dp" android:height="10dp" />
                </shape>
            </item>
        </layer-list>
    </item>
</selector>
```

### 布局配置
```xml
<ToggleButton
    android:id="@+id/toggle_monitor"
    android:layout_width="32dp"
    android:layout_height="18dp"
    android:layout_alignParentEnd="true"
    android:layout_alignParentTop="true"
    android:background="@drawable/toggle_switch_background"
    android:textOff=""
    android:textOn=""
    android:checked="false" />
```

## 视觉效果

### 设计优势
1. **直观易懂**：圆点位置清晰表示开关状态
2. **现代美观**：拟物风格符合用户习惯
3. **尺寸合适**：不会过于突出，也不会太小难以点击
4. **颜色协调**：绿色表示活跃，白色表示非活跃

### 用户体验
- **状态清晰**：一眼就能看出开关状态
- **操作友好**：点击区域适中，易于操作
- **视觉反馈**：状态变化有明显的视觉差异
- **一致性**：与系统开关风格保持一致

## 功能特点

- **实时切换**：点击立即切换状态
- **状态保存**：开关状态自动保存到SharedPreferences
- **功能关联**：开关状态直接影响APP监测行为
- **默认设置**：小红书默认开启，其他APP默认关闭 
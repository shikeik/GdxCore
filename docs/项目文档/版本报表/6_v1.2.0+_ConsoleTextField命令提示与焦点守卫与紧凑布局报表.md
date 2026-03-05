# GdxCore v1.2.0+ ConsoleTextField 命令提示 & 焦点守卫 & DebugConsole 紧凑布局报表

> 版本: 6.0  
> 日期: 2026-03-05  
> 分支: main  
> 涉及版本: 1.2.0 (增量更新, mavenLocal)

---

## 一、概述

本次对 GdxCore 进行三项增强：

1. **ConsoleTextField 命令提示能力** — 新增 `onTextChanged` 文本变化回调 + `onTabPressed` Tab 键回调，外部可监听输入变化实现实时命令提示和 Tab 补全。
2. **ConsoleTextField 焦点守卫** — 新增静态焦点追踪机制 (`isAnyFocused()`)，任何 ConsoleTextField 获得键盘焦点时自动注册，游戏层可据此禁用 WASD 等全局按键处理。
3. **DebugConsole 紧凑布局** — 大幅缩减安全边距、头部高度、标签按钮宽度、内容内边距、拖拽条高度，减少屏幕空间浪费。
4. **CommandRegistry API 扩展** — 新增 `getCommandInfo(name)` 和 `getAll()` 方法，支持命令提示面板查询。

---

## 二、文件变更统计

### 修改文件 (3 个)

| 文件 | 改动量 | 说明 |
|------|--------|------|
| `ui/widget/ConsoleTextField.java` | +50 | 新增 `onTextChanged`/`onTabPressed` 回调; 静态焦点计数器 `focusedCount` + `isAnyFocused()`; FocusListener 自动追踪; Tab/keyTyped 事件处理 |
| `log/CommandRegistry.java` | +18 | 新增 `getCommandInfo(name)` 和 `getAll()` 方法 |
| `log/DebugConsole.java` | ±12 | SAFE_PAD 30→8, panelHeight 400→300, MIN_HEIGHT 100→80, header 40→26, tab 按钮 80→52, 内容 pad 10→3, 拖拽条 14→8, 关闭按钮 50→30 |

### 总计

- **修改**: +80 / -12 行
- **涉及文件**: 3 个

---

## 三、ConsoleTextField 命令提示能力

### 3.1 新增 API

```java
// 文本内容每次改变时触发（keyTyped 后通过 postRunnable 延迟到下一帧确保文本已更新）
void setOnTextChanged(Consumer<String> callback)

// Tab 键按下时触发（参数为当前文本）
void setOnTabPressed(Consumer<String> callback)
```

### 3.2 使用场景

ChatOverlay 全屏聊天输入栏监听 `onTextChanged`：
- 检测 `/` 前缀 → 调用 `CommandRegistry.getSuggestions()` → 灰色命令提示
- `onTabPressed` → 循环补全匹配的命令名

---

## 四、焦点守卫机制

### 4.1 设计

```java
// 静态计数器: 任何 ConsoleTextField 的内部 VisTextField 获得焦点时 +1，失去焦点时 -1
private static int focusedCount = 0;

// 外部查询（如 InputManager）
public static boolean isAnyFocused()  // focusedCount > 0
```

### 4.2 工作原理

- 构造时添加 `FocusListener` 到内部 `VisTextField`
- `keyboardFocusChanged(focused=true)` → `focusedCount++`
- `keyboardFocusChanged(focused=false)` → `focusedCount--`（最低为 0）
- 游戏的 `InputManager.getAxis()` / `isKeyJustPressed()` / `isKeyPressed()` 在 `isAnyFocused()` 时跳过键盘轮询

### 4.3 受影响的按键

| 场景 | 正常 | 焦点时 |
|------|------|--------|
| WASD 移动 | 生效 | 跳过 |
| 方向键移动 | 生效 | 跳过 |
| 快捷键 (E/I/T 等) | 生效 | 跳过 |
| 手柄摇杆 | 生效 | 不受影响 |
| 虚拟摇杆 | 生效 | 不受影响 |

---

## 五、DebugConsole 紧凑布局

### 5.1 布局参数对比

| 参数 | 旧值 | 新值 | 说明 |
|------|------|------|------|
| SAFE_PAD | 30 | 8 | 面板左右及 FPS 按钮边距 |
| panelHeight | 400 | 300 | 默认面板高度 |
| MIN_HEIGHT | 100 | 80 | 最小拖拽高度 |
| header height | 40 | 26 | 头部栏高度 |
| tab button width | 80 | 52 | INTRO/LOG/INFO 按钮宽度 |
| close button width | 50 | 30 | X 关闭按钮宽度 |
| header pad | 5 | 2 | 头部内边距 |
| content pad | 10 | 3 | 内容区域内边距 |
| resizeHandleHeight | 14 | 8 | 底部拖拽条高度 |
| tab padRight | 5 | 2 | 标签按钮右间距 |

### 5.2 视觉效果

- 面板更贴合屏幕边缘（8px 边距 vs 30px）
- 头部栏紧凑（26px 高度），按钮更小但仍可点击
- 内容区域占比增大，信息密度提升
- 默认面板高度适中（300px），不过度遮挡游戏画面

---

## 六、CommandRegistry API 扩展

```java
// 获取指定命令的元信息（用于显示 usage/description）
public static CommandInfo getCommandInfo(String name)

// 获取所有已注册命令的元信息列表（用于命令提示面板）
public static List<CommandInfo> getAll()
```

---

## 七、编译验证

- GdxCore `:core:compileJava` → BUILD SUCCESSFUL
- GdxCore `publishToMavenLocal` → BUILD SUCCESSFUL (1.2.0)
- SandTank `:core:compileJava :netcode:compileJava` → BUILD SUCCESSFUL

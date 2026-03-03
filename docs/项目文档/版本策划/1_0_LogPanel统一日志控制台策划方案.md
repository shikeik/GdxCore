# LogPanel 统一日志控制台策划方案

> 创建日期: 2026-03-03  
> 状态: **策划阶段**  
> 关联项目: GdxCore (log 模块), SandTank, MagicDungeon2  
> 参考: Unity IngameDebugConsole (2.6k★), UE OutputLog, StrongJoshua/libgdx-inGameConsole

---

## 一、背景与目标

### 1.1 现状问题

当前 GdxCore 的日志展示存在以下问题:

1. **多套重复面板并存**:
   - `DebugConsole` (log 包) — DLog 自带的独立 Stage 覆盖层，有 INTRO/LOG/INFO 三个标签页
   - `IDEConsole` (ui/widget 包) — 可嵌入游戏 UI 的折叠式日志面板
   - 各游戏项目中还有零散自建的日志展示面板

2. **日志存储为纯字符串**: `DLog.logMessages` 是 `List<String>`，tag/level 信息在存入时已被拼进格式化文本，无法结构化筛选

3. **无筛选能力**: 所有日志无差别展示，引擎底层日志与游戏内容日志混在一起，信息密度低

4. **无命令输入**: 没有运行时命令执行能力，调试需要重新编译

### 1.2 目标

- **统一**: 用一个通用 `LogPanel` 组件替代所有日志展示面板
- **可筛选**: 支持按 Level (DEBUG/INFO/WARN/ERROR) 和 Tag 过滤日志
- **可嵌入**: `LogPanel` 是一个标准 VisTable，可放入任何 UI 布局
- **可交互**: 底部命令输入栏，支持运行时命令注册与执行
- **向后兼容**: 不改动任何现有 `DLog.logT()` 调用，不动 tag 体系

---

## 二、业界调研

### 2.1 Unity — IngameDebugConsole (事实标准)

| 功能 | 说明 |
|------|------|
| Level 过滤 | Info/Warning/Error 三按钮，各带计数徽标 |
| 文本搜索 | 搜索栏实时筛选 (变相支持 tag 过滤) |
| 折叠模式 | 相同日志合并，显示重复次数 |
| 命令系统 | 注解注册 + 自动补全 + 命令历史(↑↓) |
| 回收列表 | 虚拟化滚动，万条日志不卡 |
| 结构化条目 | `DebugLogEntry { logString, stackTrace, logType, count }` |
| 最大日志限制 | 超限自动清理最旧条目 |
| Popup 模式 | 收起后小气泡显示新增日志数 |

**关键点**: Unity 没有 tag 过滤，靠搜索栏替代。

### 2.2 UE — OutputLog

- Level 过滤 + **Category 过滤** (等同于 tag 下拉多选)
- 文本搜索
- 命令系统 (`~` 键)

### 2.3 结论

我们的方案取 Unity 的 UI 交互设计 + UE 的 Category(tag) 过滤，再保留现有 DebugConsole 的特色功能 (抽屉动画、INTRO/INFO 标签页、拖拽调整大小)。

---

## 三、架构设计

### 3.1 模块划分

```
GdxCore/core/src/main/java/com/goldsprite/gdengine/log/
├── DLog.java                  ← [修改] logMessages 类型改为 List<LogEntry>
├── LogEntry.java              ← [新增] 结构化日志条目
├── LogPanel.java              ← [新增] 通用日志展示面板 (VisTable)
├── CommandRegistry.java       ← [新增] 运行时命令注册中心
├── DebugConsole.java          ← [修改] LOG 标签页改为内嵌 LogPanel
└── (IDEConsole 废弃或改为 LogPanel 的简化包装)
```

### 3.2 类图概览

```
┌─────────────────────────────────────────────────────┐
│                      DLog                           │
│  ─────────────────────────────────────────────────  │
│  + logEntries: List<LogEntry>   // [改] 结构化存储   │
│  + logMessages: List<String>    // [保留] 兼容旧代码  │
│  + dispatch(level, tag, values) // 分发给 LogOutput  │
│  + registerLogOutput(output)    // 注册输出端         │
└──────────┬──────────────────────────────────────────┘
           │ GdxUiOutput 同时写入 logEntries + logMessages
           ▼
┌─────────────────────────────────────────────────────┐
│                   LogEntry                          │
│  ─────────────────────────────────────────────────  │
│  + level: Level          // DEBUG/INFO/WARN/ERROR   │
│  + tag: String           // "Netcode", "Combat"...  │
│  + time: String          // "12:03:05:123"          │
│  + message: String       // 原始消息文本             │
│  + formatted: String     // 带颜色标记的完整文本     │
└─────────────────────────────────────────────────────┘
           │ 被 LogPanel 读取和筛选
           ▼
┌─────────────────────────────────────────────────────┐
│              LogPanel extends VisTable               │
│  ─────────────────────────────────────────────────  │
│  - searchField: VisTextField    // 搜索栏            │
│  - levelFilters: EnumSet<Level> // 当前启用的等级     │
│  - tagFilter: Set<String>       // 当前选中的 tag     │
│  - showAllTags: boolean         // 是否显示全部 tag   │
│  - logLabel: VisLabel           // 日志显示区         │
│  - logScroll: ScrollPane        // 滚动容器           │
│  - commandInput: VisTextField   // 命令输入栏         │
│  - autoScroll: boolean          // 自动滚到底部       │
│  ─────────────────────────────────────────────────  │
│  + setDefaultTagFilter(tags...) // 预设默认 tag 筛选  │
│  + setFilterGroups(groups...)   // 配置筛选分组       │
│  + refresh()                    // 按当前筛选刷新显示  │
└─────────────────────────────────────────────────────┘
           │ 可被嵌入
           ▼
┌─────────────────────────────────────────────────────┐
│         DebugConsole extends Group                   │
│  ─────────────────────────────────────────────────  │
│  - logPanel: LogPanel    // [改] LOG 标签页用 LogPanel│
│  - introScroll           // [保留] INTRO 标签页      │
│  - infoScroll            // [保留] INFO 标签页       │
│  - 抽屉动画 / FPS按钮 / 拖拽调整大小  // [全部保留]  │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│                CommandRegistry                       │
│  ─────────────────────────────────────────────────  │
│  + register(name, desc, handler) // 注册命令         │
│  + execute(input): String        // 执行命令并返回结果│
│  + getSuggestions(prefix): List  // 自动补全建议      │
│  + getHelp(): String             // 列出所有命令      │
│  ─────────────────────────────────────────────────  │
│  内置命令:                                           │
│    help    — 列出所有已注册命令                       │
│    clear   — 清空日志                                │
│    filter  — 切换 tag 过滤 (filter +Netcode -Combat) │
│    level   — 切换 level 过滤 (level warn error)      │
└─────────────────────────────────────────────────────┘
```

---

## 四、详细设计

### 4.1 LogEntry — 结构化日志条目

```java
package com.goldsprite.gdengine.log;

/** 结构化日志条目，存储 tag/level 等元信息以支持筛选 */
public class LogEntry {
    public DLog.Level level;
    public String tag;
    public String time;
    public String message;
    public String formatted;  // 带 [RED]/[CYAN] 等颜色标记的完整文本

    public LogEntry(DLog.Level level, String tag, String time, String message, String formatted) {
        this.level = level;
        this.tag = tag;
        this.time = time;
        this.message = message;
        this.formatted = formatted;
    }

    /** 是否匹配搜索词 (大小写不敏感) */
    public boolean matchesSearch(String term) {
        if (term == null || term.isEmpty()) return true;
        String lower = term.toLowerCase();
        return tag.toLowerCase().contains(lower) || message.toLowerCase().contains(lower);
    }
}
```

### 4.2 DLog 修改 — 新增结构化存储

```java
// DLog.java 新增字段
public static List<LogEntry> logEntries = new CopyOnWriteArrayList<>();

// GdxUiOutput.onLog() 修改
@Override
public void onLog(Level level, String tag, String msg) {
    String time = formatTime("HH:mm:ss:SSS");
    String formatted = /* 现有格式化逻辑不变 */;

    // 同时写入两个列表 (logMessages 保持兼容)
    logMessages.add(formatted);
    logEntries.add(new LogEntry(level, tag, time, msg, formatted));

    // 同步清理
    if (logEntries.size() > maxLogsCache) {
        int removeCount = maxLogsCache / 10;
        logMessages.subList(0, logMessages.size() - maxLogsCache).clear();
        logEntries.subList(0, logEntries.size() - maxLogsCache).clear();
    }
}
```

**向后兼容**: `logMessages` (List\<String\>) 保持不变，旧代码无需修改。

### 4.3 LogPanel — 通用日志展示组件

#### UI 布局

```
┌────────────────────────────────────────────────────┐
│ 🔍 [搜索框______________________]                   │  搜索栏
│ [All] [ℹ 12] [⚠ 3] [❌ 1]   [Tags ▼] [AutoScroll] │  过滤工具栏
├────────────────────────────────────────────────────┤
│ [12:03:05] [Combat] 玩家攻击命中                     │
│ [12:03:06] [Quest] 任务完成: 收集10个石头             │  日志区 (ScrollPane)
│ [12:03:07] [Netcode] 收到同步包 #1234               │
│ ...                                                 │
├────────────────────────────────────────────────────┤
│ > [命令输入________________________] [发送]          │  命令输入栏
└────────────────────────────────────────────────────┘
```

#### 核心 API

```java
package com.goldsprite.gdengine.log;

public class LogPanel extends VisTable {

    /** 创建默认 LogPanel (显示全部日志) */
    public LogPanel() { ... }

    /** 设置默认 tag 过滤 (只显示指定 tag 的日志) */
    public void setDefaultTagFilter(String... tags) { ... }

    /** 设置 tag 分组 (在 Tags 下拉菜单中显示为分组) */
    public void setFilterGroups(FilterGroup... groups) { ... }

    /** 刷新方法，在 act() 中按帧率限制调用 */
    @Override
    public void act(float delta) { ... }

    // --- 过滤组定义 ---
    public static class FilterGroup {
        public String name;       // "游戏", "引擎", "网络"
        public String[] tags;     // 该组包含的 tag 列表

        public FilterGroup(String name, String... tags) {
            this.name = name;
            this.tags = tags;
        }
    }
}
```

#### 筛选逻辑

```java
/** 根据当前筛选条件过滤日志 */
private List<LogEntry> getFilteredEntries() {
    List<LogEntry> result = new ArrayList<>();
    for (LogEntry entry : DLog.logEntries) {
        // 1. Level 过滤
        if (!levelFilters.contains(entry.level)) continue;
        // 2. Tag 过滤
        if (!showAllTags && !tagFilter.contains(entry.tag)) continue;
        // 3. 搜索词过滤
        if (!entry.matchesSearch(searchTerm)) continue;
        result.add(entry);
    }
    return result;
}
```

### 4.4 DebugConsole 修改 — 保留特色、嵌入 LogPanel

| 保留 | 改动 |
|------|------|
| ✅ 抽屉动画 (Lerp 滑入滑出) | LOG 标签页: logLabel + logScroll → 替换为 LogPanel 实例 |
| ✅ FPS 按钮 + 收起/展开状态切换 | createPanel(): 移除旧的 logLabel/logScroll 创建逻辑 |
| ✅ INTRO 标签页 | showTab(): LOG 标签改为展示 logPanel |
| ✅ INFO 标签页 (Monitor 数据) | refreshData(): 移除旧的 logLabel 更新 (LogPanel 自己管) |
| ✅ 拖拽调整大小 | — |
| ✅ AutoScroll 按钮 | 移至 LogPanel 内部管理 |
| ✅ Clear 按钮 | 移至 LogPanel 内部管理 |

```java
// DebugConsole.createPanel() 中的关键修改:
// Before:
//   logLabel = new VisLabel("", "small");
//   logScroll = new HoverFocusScrollPane(logLabel);
// After:
//   logPanel = new LogPanel();

// showTab(logScroll) → showTab(logPanel)
```

### 4.5 CommandRegistry — 运行时命令系统

```java
package com.goldsprite.gdengine.log;

public class CommandRegistry {
    private static final Map<String, CommandInfo> commands = new LinkedHashMap<>();

    /** 注册命令 */
    public static void register(String name, String description, CommandHandler handler) {
        commands.put(name.toLowerCase(), new CommandInfo(name, description, handler));
    }

    /** 执行命令字符串，返回结果文本 */
    public static String execute(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        String name = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        CommandInfo cmd = commands.get(name);
        if (cmd == null) return "未知命令: " + name + "，输入 help 查看所有命令";
        return cmd.handler.execute(args);
    }

    /** 获取自动补全建议 */
    public static List<String> getSuggestions(String prefix) { ... }

    // --- 内部类型 ---
    @FunctionalInterface
    public interface CommandHandler {
        String execute(String args);
    }

    public static class CommandInfo {
        public String name, description;
        public CommandHandler handler;
        public CommandInfo(String name, String description, CommandHandler handler) { ... }
    }
}
```

**内置命令** (在 LogPanel 构造时自动注册):

| 命令 | 说明 | 示例 |
|------|------|------|
| `help` | 列出所有已注册命令 | `help` |
| `clear` | 清空日志 | `clear` |
| `filter` | 增减 tag 过滤 | `filter +Netcode -Combat` |
| `level` | 设置显示的日志等级 | `level warn error` |
| `search` | 设置搜索关键词 | `search 连接失败` |

**游戏项目自定义命令** 示例:

```java
// 在游戏项目中注册
CommandRegistry.register("tp", "传送到指定坐标", args -> {
    String[] parts = args.split(" ");
    float x = Float.parseFloat(parts[0]);
    float y = Float.parseFloat(parts[1]);
    player.setPosition(x, y);
    return "已传送到 (" + x + ", " + y + ")";
});

CommandRegistry.register("spawn", "生成实体", args -> {
    EntityFactory.spawn(args);
    return "已生成: " + args;
});
```

### 4.6 IDEConsole 处理

**方案**: 废弃 `IDEConsole`，改为 `LogPanel` 的轻量包装。

```java
// 游戏 UI 中嵌入日志面板 (替代原 IDEConsole):
LogPanel gameLog = new LogPanel();
gameLog.setDefaultTagFilter("Game", "Combat", "Quest", "NPC");
bottomBar.add(gameLog).growX().height(200);
```

如需保留 IDEConsole 的折叠行为，可包装为:

```java
// 可选: 保留 IDEConsole 外壳，内部使用 LogPanel
public class IDEConsole extends VisTable {
    private LogPanel logPanel;
    // ... 保留折叠/展开动画，内容区替换为 logPanel
}
```

---

## 五、消费端使用示例

### 5.1 DebugConsole 中 (自动生效)

无需修改，DebugConsole 内部的 LOG 标签页自动升级为带筛选的 LogPanel。

### 5.2 游戏内嵌入

```java
// 创建只看游戏日志的面板
LogPanel gameLog = new LogPanel();
gameLog.setDefaultTagFilter("Game", "Combat", "Quest");
gameLog.setFilterGroups(
    new FilterGroup("战斗", "Combat", "Damage", "Skill"),
    new FilterGroup("任务", "Quest", "NPC", "Dialog"),
    new FilterGroup("网络", "Netcode", "Lobby", "Sync")
);
myGameUI.add(gameLog).grow();
```

### 5.3 注册自定义命令

```java
// 在游戏初始化时注册
CommandRegistry.register("god", "切换无敌模式", args -> {
    player.toggleGodMode();
    return "无敌模式: " + (player.isGodMode() ? "开启" : "关闭");
});

CommandRegistry.register("give", "给予物品", args -> {
    String[] parts = args.split(" ", 2);
    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
    inventory.addItem(parts[0], amount);
    return "已添加 " + amount + "x " + parts[0];
});
```

---

## 六、实施计划

### Phase 0 — 数据层改造 (P0)

| 步骤 | 内容 | 文件 |
|------|------|------|
| 0.1 | 新建 `LogEntry` 类 | `log/LogEntry.java` |
| 0.2 | DLog 新增 `logEntries` 列表 | `log/DLog.java` |
| 0.3 | GdxUiOutput 同时写入 logEntries + logMessages | `log/DLog.java` |

### Phase 1 — LogPanel 组件 (P0)

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1.1 | 新建 `LogPanel` (VisTable) 基础布局 | `log/LogPanel.java` |
| 1.2 | 实现 Level 过滤 (4 个按钮 + 带计数) | `log/LogPanel.java` |
| 1.3 | 实现 Tag 过滤 (下拉菜单 + 多选) | `log/LogPanel.java` |
| 1.4 | 实现搜索栏 | `log/LogPanel.java` |
| 1.5 | 实现自动滚动 + 手动锁定 | `log/LogPanel.java` |

### Phase 2 — 命令系统 (P0)

| 步骤 | 内容 | 文件 |
|------|------|------|
| 2.1 | 新建 `CommandRegistry` | `log/CommandRegistry.java` |
| 2.2 | LogPanel 底部命令输入栏 + 内置命令 | `log/LogPanel.java` |

### Phase 3 — DebugConsole 集成 (P1)

| 步骤 | 内容 | 文件 |
|------|------|------|
| 3.1 | DebugConsole 的 LOG 标签页替换为 LogPanel | `log/DebugConsole.java` |
| 3.2 | 移除 DebugConsole 中冗余的 logLabel/logScroll/AutoScroll/Clear | `log/DebugConsole.java` |
| 3.3 | 保留 INTRO/INFO/抽屉动画/FPS按钮/拖拽 不变 | — |

### Phase 4 — IDEConsole 替代 (P1)

| 步骤 | 内容 | 文件 |
|------|------|------|
| 4.1 | 评估 IDEConsole 使用点，替换为 LogPanel | `ui/widget/IDEConsole.java` |
| 4.2 | 游戏项目中的零散日志面板统一迁移 | 各游戏项目 |

### Phase 5 — 增强功能 (P2, 后续迭代)

| 步骤 | 内容 |
|------|------|
| 5.1 | 命令自动补全弹出菜单 |
| 5.2 | 命令历史 (↑↓ 键翻阅) |
| 5.3 | 折叠模式 (相同日志合并 + 计数) |
| 5.4 | 列表虚拟化 (仅在日志量极大时性能优化) |
| 5.5 | 日志导出到文件 |

---

## 七、兼容性与风险

| 风险 | 缓解措施 |
|------|----------|
| `logMessages` (List\<String\>) 被外部直接引用 | 保留不删，logEntries 与 logMessages 双写同步 |
| CopyOnWriteArrayList 性能 (频繁写入 logEntries) | 当前 maxLogsCache=100，可接受；后续可改为 CircularBuffer |
| VisUI 主题兼容 | LogPanel 使用 VisUI 标准组件，不依赖特殊主题 |
| 命令输入框抢焦点 | LogPanel 可配置是否显示命令栏 (`showCommandInput = false`) |
| 游戏项目未注册 FilterGroup | LogPanel 默认 showAllTags=true，不配置也能工作 |

---

## 八、修改文件清单

### GdxCore (log 模块)

| 操作 | 文件 | 说明 |
|------|------|------|
| **新增** | `log/LogEntry.java` | 结构化日志条目 |
| **新增** | `log/LogPanel.java` | 通用日志展示面板 |
| **新增** | `log/CommandRegistry.java` | 运行时命令注册中心 |
| **修改** | `log/DLog.java` | 新增 logEntries 字段，GdxUiOutput 双写 |
| **修改** | `log/DebugConsole.java` | LOG 标签页改用 LogPanel |
| **废弃** | `ui/widget/IDEConsole.java` | 由 LogPanel 替代 |

### 消费端 (SandTank / MagicDungeon2)

| 操作 | 说明 |
|------|------|
| 可选 | 注册自定义 FilterGroup 和 Command |
| 可选 | 替换自建的日志面板为 LogPanel |

发布后消费端更新 GdxCore 依赖版本即可获得新功能，无需强制改动。

# GdxCore v0.9.2 云端房间自注册 & 日志级别过滤 & MC 风格 ChatOverlay — 开发报表

> 版本: 3.0  
> 日期: 2026-03-04  
> 分支: main (df822ad →)  
> 涉及版本: 0.9.2

---

## 一、概述

本次开发为 GdxCore 引擎库添加三大能力：

1. **纯服务器云端房间自注册** — `HeadlessGameServer` 启动时自动通过 `PresenceLobbyManager` 注册到 Supabase Presence 云大厅，玩家加入/离开自动更新在线人数与房间状态。
2. **DLog 全局/按标签日志级别过滤** — 为 `DLog` 新增 `globalMinLevel` 和 `tagMinLevels` 机制，`ServerConsole` 新增 `loglevel` 命令实现运行时按需调整日志输出。
3. **MC 风格 ChatOverlay** — 替代 `DebugConsole` 的 CHAT 标签页，新增独立的 `ChatOverlay` 组件，采用 Minecraft 风格的半透明消息覆盖层 + 按键触发输入框。

---

## 二、文件变更统计

### 新增文件 (1 个)

| 文件 | 模块 | 行数 | 说明 |
|------|------|------|------|
| `chat/ChatOverlay.java` | core | 476 | MC 风格半透明聊天覆盖层 (消息淡出/输入模式/历史翻阅/反射调用 drawRect) |

### 修改文件 (5 个)

| 文件 | 改动量 | 说明 |
|------|--------|------|
| `log/DLog.java` | +66 | 全局/按标签日志级别过滤 (`globalMinLevel` / `tagMinLevels` / `dispatch()` 前置检查) |
| `log/DebugConsole.java` | -4 | 移除 CHAT 标签页 (字段/按钮/布局/实例化 4 处) |
| `netcode/headless/HeadlessGameServer.java` | +133 | 云端房间自注册 (`initCloudLobby` / `publishServerRoom` / `updateLobbyPlayerCount`) + `configureLogLevel` |
| `netcode/headless/ServerConfig.java` | +19 | 新增 `enableLobby` / `roomName` 字段及 CLI 参数 (`--no-lobby` / `--room-name`) |
| `netcode/headless/ServerConsole.java` | +45 | 新增 `loglevel` 命令 (全局/按标签/reset) |

### 总计

- **新增**: 476 行  
- **修改**: +263 / -4 行  
- **涉及文件**: 6 个 (1 新增 + 5 修改)

---

## 三、架构设计

### 3.1 云端房间自注册流程

```
HeadlessGameServer.create()
    │
    ├─ configureLogLevel()        ← 根据 ServerConfig 设置 DLog 级别
    │
    └─ initCloudLobby()
         │
         ├─ PresenceLobbyManager.connect()
         │       ↓ onJoined 回调
         ├─ publishServerRoom()
         │       ├─ PublicIPResolver.resolve()  ← 异步获取公网 IP
         │       └─ lobbyManager.publishRoom(roomInfo)
         │
         └─ 连接监听器 (onPlayerConnected / onPlayerDisconnected)
                 └─ updateLobbyPlayerCount()
                         ├─ 更新 playerCount
                         ├─ 设置 status: "waiting" / "full"
                         └─ lobbyManager.updateRoom(roomInfo)
```

### 3.2 DLog 日志级别过滤

```
DLog.dispatch(level, tag, msg)
    │
    ├─ 查找有效级别: tagMinLevels.get(tag) ?? globalMinLevel
    ├─ if level.ordinal() < minLevel.ordinal() → return (过滤)
    ├─ 黑名单检查 (tagBlacklistEnabled)
    └─ 正常分发到各输出端
```

新增 API：

| 方法 | 说明 |
|------|------|
| `setGlobalLogLevel(Level)` | 设置全局最低日志级别 |
| `getGlobalLogLevel()` | 获取当前全局级别 |
| `setTagLogLevel(tag, Level)` | 为指定标签设置独立级别 |
| `getEffectiveLogLevel(tag)` | 查询某标签的有效级别 |
| `clearTagLogLevels()` | 清除所有按标签覆盖 |
| `parseLevel(String)` | 字符串解析为 Level 枚举 |

### 3.3 MC 风格 ChatOverlay

```
ChatOverlay (implements ChatChannel.MessageListener)
    │
    ├─ 消息显示
    │   ├─ 最近 10 条消息，底部对齐
    │   ├─ 7 秒可见 + 1.5 秒淡出
    │   ├─ 半透明黑色背景条 (alpha 0.45)
    │   └─ 按消息类型着色 (白色/黄色/青色)
    │
    ├─ 输入模式 (T / Enter 激活)
    │   ├─ 底部输入框 (半透明黑色背景 alpha 0.55)
    │   ├─ pollTypedCharacters() 轮询键盘输入
    │   ├─ ↑↓ 翻阅输入历史
    │   ├─ Enter 发送 → ChatChannel.processInput()
    │   └─ Escape 取消
    │
    └─ 渲染
        ├─ render(SpriteBatch, BitmapFont, Viewport)
        └─ drawFilledRect() 反射调用 NeonBatch.drawRect
```

---

## 四、ServerConsole 新增命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `loglevel` | 显示当前全局日志级别 | `loglevel` |
| `loglevel <级别>` | 设置全局最低级别 | `loglevel WARN` |
| `loglevel <级别> <标签>` | 为指定标签设置级别 | `loglevel DEBUG Netcode` |
| `loglevel reset` | 清除所有按标签覆盖 | `loglevel reset` |
| `loglevel reset <标签>` | 清除指定标签覆盖 | `loglevel reset Lobby` |

---

## 五、ServerConfig 新增字段

| 字段 | CLI 参数 | Properties 键 | 默认值 | 说明 |
|------|----------|---------------|--------|------|
| `enableLobby` | `--no-lobby` | `server.enable-lobby` | `true` | 是否启用云端大厅注册 |
| `roomName` | `--room-name <名称>` | `server.room-name` | `"Dedicated Server"` | 房间显示名称 |

---

## 六、遗留与后续

| 项目 | 优先级 | 说明 |
|------|--------|------|
| ChatOverlay 网络同步 | P1 | 聊天消息通过 RPC 在客户端间同步 |
| ChatOverlay 触控支持 | P2 | Android 端输入框需弹出软键盘 |
| 房间密码/白名单 | P2 | 云大厅房间加密接入 |
| DLog 日志级别持久化 | P3 | 保存到 server.properties 重启生效 |

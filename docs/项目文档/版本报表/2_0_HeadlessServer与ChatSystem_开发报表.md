# GdxCore v0.9.1+ 无头服务器框架 & 聊天指令系统 — 开发报表

> 版本: 2.0  
> 日期: 2026-03-03  
> 分支: main (c223168 → a9ab1e3)  
> 涉及版本: 0.9.1 → 0.9.2 (未发版)

---

## 一、概述

本次开发为 GdxCore 引擎库新增两大框架级功能：

1. **Headless 无头服务器框架** — 提供 `HeadlessGameServer` / `ServerConfig` / `ServerConsole` 三件套，让下游游戏项目（如 SandTank）可以零图形开销运行纯命令行 Dedicated Server。
2. **游戏聊天/指令系统 (MC 风格)** — 新增 `ChatMessage` / `ChatChannel` / `GameChatPanel` 消息总线与 UI 组件，并将 `ServerConsole` 和 `DebugConsole` 接入，实现跨 GUI/Headless 的统一聊天与 `/命令` 路由。

设计参考：
- Headless 框架参考 Netty/Minecraft Dedicated Server 的生命周期管理
- 聊天系统参考 Minecraft 的 `/say`、`/tp` 输入路由模型

---

## 二、开发阶段与提交记录

| 阶段 | 提交哈希 | 提交信息 |
|------|----------|----------|
| Headless 框架 | 30c0d72 | feat: 新增 headless 无头服务器框架 (HeadlessGameServer/ServerConfig/ServerConsole) |
| 聊天指令系统 | a9ab1e3 | feat: 游戏聊天/指令系统 (MC风格) - ChatMessage/ChatChannel/GameChatPanel/ServerConsole集成/DebugConsole CHAT标签页 |

---

## 三、文件变更统计

### 新增文件 (6 个)

| 文件 | 模块 | 行数 | 说明 |
|------|------|------|------|
| `chat/ChatMessage.java` | core | 87 | 消息数据模型 (CHAT/SYSTEM/COMMAND_RESULT 三类型 + 工厂方法 + 格式化输出) |
| `chat/ChatChannel.java` | core | 236 | 单例消息总线 (processInput 路由 / DLog 同步 / MessageListener / NetworkSender / CommandExecutor) |
| `log/GameChatPanel.java` | core | 300 | 游戏聊天面板 UI (VisTable 架构 / 自动滚动 / 命令历史 / Markup 着色 / ESC 焦点切换) |
| `netcode/headless/HeadlessGameServer.java` | netcode | 270 | 抽象基类 (ApplicationListener 生命周期 / 固定 tick 循环 / 5 个子类回调) |
| `netcode/headless/ServerConfig.java` | netcode | 156 | 配置加载器 (默认值 → server.properties → --命令行参数 三级覆盖) |
| `netcode/headless/ServerConsole.java` | netcode | 198 | 服务器控制台 (stdin 非阻塞读取 / 内置命令表 / ChatChannel 桥接 / `/` 前缀路由) |

### 修改文件 (2 个)

| 文件 | 改动量 | 说明 |
|------|--------|------|
| `log/DebugConsole.java` | +4 | 新增 CHAT 标签页 (INTRO/LOG/CHAT/INFO 四页) |
| `build.gradle` | +9 | 接入 GdxGradle 插件，启用 bumpPatch 等版本管理任务 |

### 总计

- **新增**: 1247 行
- **修改**: +13 行
- **净增**: +1260 行
- **涉及文件**: 8 个 (6 新增 + 2 修改)

---

## 四、测试覆盖

### 新增测试 (本次报表配套 TDD 验证)

| 测试类 | 用例数 | 通过 | 覆盖范围 |
|--------|--------|------|----------|
| ChatMessageTest | 10 | 10 | 工厂方法 (chat/system/commandResult)、格式化输出、toString、null 安全性 |
| ChatChannelTest | 13 | 13 | processInput 路由 (聊天/命令/say/空输入)、postMessage、监听器回调、网络发送器、消息上限裁剪、getRecentMessages |
| ServerConfigTest | 6 | 6 | 默认值、单参数/多参数覆盖、未知参数容错、toString |
| **合计** | **29** | **29** | **100% 通过率** |

> 测试位于 SandTank/tests 模块（SandTank 依赖 GdxCore，测试通过依赖链验证 GdxCore 源码）

---

## 五、架构设计

### Headless 无头服务器框架

```
┌─────────────────────────────────────────────┐
│           HeadlessGameServer (抽象基类)       │
│  create() → registerPrefabs() → initGame()  │
│  render() → heartbeat → onServerTick()      │
│           → manager.tick() → tickReliable()  │
│  5 个抽象回调: registerPrefabs / initGame   │
│    / onServerTick / onPlayerConnected       │
│    / onPlayerDisconnected                   │
└──────────────────┬──────────────────────────┘
                   │
     ┌─────────────┼──────────────┐
     ▼             ▼              ▼
ServerConfig   ServerConsole   NetworkManager
(三级配置)     (stdin + 命令)  (netcode 核心)
```

### 聊天/指令消息总线

```
用户输入 ─┬─ "你好"          ─→ ChatChannel.processInput()
          ├─ "/say 公告"      ─→   ├─ 聊天消息 → postMessage() → DLog + 监听器
          └─ "/tp 100 200"   ─→   └─ CommandExecutor.execute() → COMMAND_RESULT
                                          │
                    ┌─────────────────────┼─────────────────┐
                    ▼                     ▼                  ▼
              GameChatPanel         ServerConsole        DebugConsole
              (GUI 客户端)          (Headless 服务端)    (CHAT 标签页)
```

---

## 六、ServerConsole 内置命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `status` | 显示服务器状态 (在线人数/tick/运行时间) | `status` |
| `list` | 列出在线玩家 ID | `list` |
| `stop` | 安全关闭服务器 | `stop` |
| `help` | 列出所有可用命令 | `help` |
| `/say <消息>` | 发送服务器聊天消息 (通过 ChatChannel) | `/say 服务器即将维护` |
| `/<命令>` | 路由到 ChatChannel 的 CommandExecutor | `/tp 100 200` |

---

## 七、消费端使用

### 实现 Dedicated Server (下游游戏项目)

```java
public class MyDedicatedServer extends HeadlessGameServer {
    @Override
    protected void registerPrefabs(NetworkManager manager) {
        manager.registerPrefab("player", MyPlayerFactory::create);
    }

    @Override
    protected void onServerTick(float delta, NetworkManager manager) {
        gameLogic.tick(delta);
    }

    @Override
    protected void onPlayerConnected(int clientId, NetworkManager manager) {
        // 5 步标准序列可委托给 TankServerHandler 模式
    }
}
```

### 集成聊天系统

```java
// GUI 客户端 — 直接嵌入 GameChatPanel
GameChatPanel chatPanel = new GameChatPanel();
stage.addActor(chatPanel);

// 设置命令执行器
ChatChannel.get().setCommandExecutor(cmd -> CommandRegistry.execute(cmd));

// 设置网络发送器
ChatChannel.get().setNetworkSender(msg -> myTank.sendServerRpc("rpcChat", msg));
```

---

## 八、遗留与后续

| 项目 | 优先级 | 说明 |
|------|--------|------|
| ChatMessage 网络序列化 | P1 | 当前 NetworkSender 接口已预留，需实现具体的 RPC 序列化 |
| 聊天消息持久化 | P2 | 将聊天记录写入日志文件 |
| 服务器权限系统 | P2 | 区分 OP/普通玩家的命令权限 |
| GameChatPanel 美化 | P3 | 消息气泡样式、表情包支持 |
| Headless 热重载 | P3 | 运行时 reload 配置/地图 |

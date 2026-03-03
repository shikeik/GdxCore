package com.goldsprite.gdengine.netcode.headless;

import java.util.Collections;
import java.util.Set;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.goldsprite.gdengine.log.DLog;
import com.goldsprite.gdengine.netcode.NetworkConnectionListener;
import com.goldsprite.gdengine.netcode.NetworkManager;
import com.goldsprite.gdengine.netcode.ReliableUdpTransport;
import com.goldsprite.gdengine.netcode.UdpSocketTransport;

/**
 * 通用无头（Headless）服务器骨架。
 * <p>
 * 子类只需实现 5 个游戏回调即可运行 Dedicated Server，无需关心网络初始化和主循环驱动。
 * <p>
 * <b>生命周期</b>（由 {@code HeadlessApplication} 驱动）：
 * <ol>
 *   <li>{@link #create()} — 初始化 Transport / NetworkManager / ServerConsole → 调用子类
 *       {@link #registerPrefabs} / {@link #initGameLogic}</li>
 *   <li>{@link #render()} — 每帧调用：心跳检测 → {@link #onServerTick} → 网络同步 → 可靠层重传</li>
 *   <li>{@link #dispose()} — 优雅关闭网络、控制台</li>
 * </ol>
 *
 * <b>使用方式</b>（在下游项目 Launcher 中）：
 * <pre>
 * ServerConfig config = ServerConfig.load(args);
 * HeadlessApplicationConfiguration haConfig = new HeadlessApplicationConfiguration();
 * haConfig.updatesPerSecond = config.tickRate;
 * new HeadlessApplication(new MyDedicatedServer(config), haConfig);
 * </pre>
 */
public abstract class HeadlessGameServer extends ApplicationAdapter {

    // ── 配置 ──
    protected ServerConfig config;

    // ── 网络层 ──
    protected UdpSocketTransport rawTransport;
    protected ReliableUdpTransport transport;
    protected NetworkManager manager;

    // ── 控制台 ──
    protected ServerConsole console;

    // ── 关闭标志 ──
    private volatile boolean shutdownRequested = false;

    // ══════════════════════════════════════════
    //  构造
    // ══════════════════════════════════════════

    public HeadlessGameServer(ServerConfig config) {
        this.config = config;
    }

    // ══════════════════════════════════════════
    //  抽象方法 — 子类实现游戏特定逻辑
    // ══════════════════════════════════════════

    /**
     * 注册所有预制体工厂到 NetworkManager。
     * <p>示例: {@code manager.registerPrefab(TANK_PREFAB_ID, TankSandboxUtils.createTankFactory());}
     */
    protected abstract void registerPrefabs(NetworkManager manager);

    /**
     * 初始化游戏逻辑（地图生成、物理系统等），在 Transport 启动之后调用。
     */
    protected abstract void initGameLogic(ServerConfig config);

    /**
     * 每帧服务器 tick（在心跳检测之后、网络同步之前调用）。
     * <p>子类在此驱动游戏逻辑（移动、碰撞、死亡判定等）。
     *
     * @param delta 本帧间隔（秒）
     * @param manager NetworkManager 实例
     */
    protected abstract void onServerTick(float delta, NetworkManager manager);

    /**
     * 新玩家连接。子类应在此：
     * <ol>
     *   <li>补发已有实体 + 状态快照</li>
     *   <li>为新玩家 Spawn 实体</li>
     *   <li>广播同步数据（如地图种子）</li>
     * </ol>
     *
     * @param clientId 新连接的客户端 ID
     * @param manager  NetworkManager 实例
     */
    protected abstract void onPlayerConnected(int clientId, NetworkManager manager);

    /**
     * 玩家断开连接。子类应在此清理业务层映射。
     * <p>注意: NetworkManager 已自动调用 {@code despawnByOwner(clientId)} 清理网络对象。
     *
     * @param clientId 断开的客户端 ID
     */
    protected abstract void onPlayerDisconnected(int clientId);

    /**
     * 返回当前在线玩家数量（供控制台 status 命令使用）。
     */
    public abstract int getOnlinePlayerCount();

    /**
     * 返回当前在线玩家 ID 集合（供控制台 players/kick 命令使用）。
     * 默认返回空集，子类应覆写。
     */
    public Set<Integer> getOnlinePlayerIds() {
        return Collections.emptySet();
    }

    // ══════════════════════════════════════════
    //  Application 生命周期
    // ══════════════════════════════════════════

    @Override
    public void create() {
        printBanner();

        // 1. 初始化网络传输层
        rawTransport = new UdpSocketTransport(true); // isServer = true
        transport = new ReliableUdpTransport(rawTransport);

        // 2. 初始化 NetworkManager
        manager = new NetworkManager();
        manager.setTickRate(config.tickRate);
        manager.setTransport(transport);

        // 3. 注册连接/断开事件监听
        manager.setConnectionListener(new NetworkConnectionListener() {
            @Override
            public void onClientConnected(int clientId) {
                // 排到主线程执行
                Gdx.app.postRunnable(() -> {
                    if (getOnlinePlayerCount() >= config.maxPlayers) {
                        DLog.logWarnT("Server", "玩家数已满 (" + config.maxPlayers + ")，拒绝 Client #" + clientId);
                        // TODO: 发送拒绝包并断开
                        return;
                    }
                    DLog.logT("Server", "客户端连接 #" + clientId);
                    onPlayerConnected(clientId, manager);
                });
            }

            @Override
            public void onClientDisconnected(int clientId) {
                // 此回调在 NetworkManager.tickInternal() 主线程中触发
                DLog.logT("Server", "客户端断开 #" + clientId);
                onPlayerDisconnected(clientId);
            }
        });

        // 4. 子类注册预制体
        registerPrefabs(manager);

        // 5. 启动服务器监听
        transport.startServer(config.port);

        // 6. 子类初始化游戏逻辑
        initGameLogic(config);

        // 7. 启动控制台线程
        console = new ServerConsole(this);
        console.start();

        DLog.logT("Server", "服务器已启动 | 端口: " + config.port
                + " | TickRate: " + config.tickRate + "Hz"
                + " | 最大玩家: " + config.maxPlayers);
    }

    @Override
    public void render() {
        if (shutdownRequested) {
            performShutdown();
            return;
        }

        float delta = Gdx.graphics.getDeltaTime();

        // ── 心跳超时检测 ──
        if (transport != null) {
            transport.checkHeartbeatTimeouts((long) (config.timeoutSec * 1000));
        }

        // ── 子类游戏逻辑 tick ──
        onServerTick(delta, manager);

        // ── 网络同步（累加器模式，按 tickRate 自动控制频率） ──
        manager.tick(delta);

        // ── 可靠层超时重传 ──
        transport.tickReliable();
    }

    @Override
    public void dispose() {
        if (console != null) {
            console.shutdown();
        }
        if (transport != null) {
            try {
                transport.disconnect();
            } catch (Exception e) {
                DLog.logWarn("Server", "关闭 Transport 时异常: " + e.getMessage());
            }
        }
        DLog.logT("Server", "服务器已关闭");
    }

    // ══════════════════════════════════════════
    //  公共 API
    // ══════════════════════════════════════════

    /** 获取服务器配置 */
    public ServerConfig getConfig() {
        return config;
    }

    /** 获取 NetworkManager */
    public NetworkManager getManager() {
        return manager;
    }

    /** 获取 Transport */
    public ReliableUdpTransport getTransport() {
        return transport;
    }

    /** 请求优雅关闭（可从控制台线程安全调用） */
    public void requestShutdown() {
        shutdownRequested = true;
    }

    /**
     * 踢出指定玩家（从网络层 despawn + 触发子类清理）。
     * 可由控制台命令或业务逻辑调用。
     */
    public void kickPlayer(int clientId) {
        if (manager != null) {
            manager.despawnByOwner(clientId);
        }
        onPlayerDisconnected(clientId);
        DLog.logT("Server", "已踢出玩家 #" + clientId);
    }

    // ══════════════════════════════════════════
    //  内部方法
    // ══════════════════════════════════════════

    /** 执行优雅关闭流程 */
    private void performShutdown() {
        DLog.logT("Server", "正在优雅关闭...");
        dispose();
        Gdx.app.exit();
    }

    /** 打印启动 Banner */
    private void printBanner() {
        System.out.println("========================================");
        System.out.println("  Dedicated Server");
        System.out.println("  端口: " + config.port + " | TickRate: " + config.tickRate + "Hz");
        System.out.println("  输入 'help' 查看可用命令");
        System.out.println("========================================");
    }
}

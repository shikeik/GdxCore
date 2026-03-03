package com.goldsprite.gdengine.netcode.headless;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;

import com.goldsprite.gdengine.log.DLog;

/**
 * 服务器命令行控制台（守护线程）。
 * <p>
 * 从 stdin 循环读取管理员指令并分发到已注册的处理器。
 * 内置命令: status, players, kick, stop, help。
 * 子类可通过 {@link #registerCommand(String, String, Consumer)} 注册自定义命令。
 */
public class ServerConsole extends Thread {

    /** 命令处理器: 命令名 → 处理函数（参数为去除命令名后的剩余字符串） */
    private final Map<String, CommandEntry> commands = new HashMap<>();

    /** 关联的服务器实例（用于内置命令的状态查询） */
    private final HeadlessGameServer server;

    /** 控制台运行标志 */
    private volatile boolean running = true;

    /** 服务器启动时间（毫秒） */
    private final long startTimeMs;

    public ServerConsole(HeadlessGameServer server) {
        super("ServerConsole");
        setDaemon(true); // 守护线程，JVM 退出时自动终止
        this.server = server;
        this.startTimeMs = System.currentTimeMillis();

        // 注册内置命令
        registerBuiltinCommands();
    }

    /**
     * 注册自定义命令。
     *
     * @param name        命令名称（全小写）
     * @param description 命令说明（显示在 help 中）
     * @param handler     处理函数，参数为命令名之后的剩余文本
     */
    public void registerCommand(String name, String description, Consumer<String> handler) {
        commands.put(name.toLowerCase(), new CommandEntry(description, handler));
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            try {
                if (!scanner.hasNextLine()) {
                    // stdin 被关闭（如非交互环境），静默退出
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                // 分离命令名和参数
                String[] parts = line.split("\\s+", 2);
                String cmd = parts[0].toLowerCase();
                String argsStr = parts.length > 1 ? parts[1] : "";

                CommandEntry entry = commands.get(cmd);
                if (entry != null) {
                    entry.handler.accept(argsStr);
                } else {
                    System.out.println("未知命令: " + cmd + "。输入 'help' 查看可用命令。");
                }
            } catch (Exception e) {
                if (running) {
                    DLog.logWarn("Console", "命令处理异常: " + e.getMessage());
                }
            }
        }
        scanner.close();
    }

    /** 停止控制台线程 */
    public void shutdown() {
        running = false;
        this.interrupt();
    }

    // ══════════════ 内置命令 ══════════════

    private void registerBuiltinCommands() {

        registerCommand("help", "显示所有可用命令", args -> {
            System.out.println("═══════ 可用命令 ═══════");
            for (Map.Entry<String, CommandEntry> e : commands.entrySet()) {
                System.out.printf("  %-12s %s%n", e.getKey(), e.getValue().description);
            }
            System.out.println("════════════════════════");
        });

        registerCommand("status", "显示服务器状态（玩家数、内存、运行时长）", args -> {
            long uptimeMs = System.currentTimeMillis() - startTimeMs;
            long sec = (uptimeMs / 1000) % 60;
            long min = (uptimeMs / 1000 / 60) % 60;
            long hr = uptimeMs / 1000 / 3600;
            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long maxMB = rt.maxMemory() / (1024 * 1024);

            int playerCount = server.getOnlinePlayerCount();
            int maxPlayers = server.getConfig().maxPlayers;

            System.out.printf("  在线玩家: %d/%d%n", playerCount, maxPlayers);
            System.out.printf("  已运行: %02d:%02d:%02d%n", hr, min, sec);
            System.out.printf("  内存: %dMB / %dMB%n", usedMB, maxMB);
            System.out.printf("  TickRate: %d Hz%n", server.getConfig().tickRate);
        });

        registerCommand("players", "列出所有在线玩家", args -> {
            java.util.Set<Integer> playerIds = server.getOnlinePlayerIds();
            if (playerIds.isEmpty()) {
                System.out.println("  当前无玩家在线");
                return;
            }
            System.out.println("  ── 在线玩家 ──");
            for (int id : playerIds) {
                System.out.printf("  #%d%n", id);
            }
        });

        registerCommand("kick", "踢出指定玩家 (用法: kick <clientId>)", args -> {
            if (args.isEmpty()) {
                System.out.println("  用法: kick <clientId>");
                return;
            }
            try {
                int clientId = Integer.parseInt(args.trim());
                server.kickPlayer(clientId);
                System.out.println("  已踢出玩家 #" + clientId);
            } catch (NumberFormatException e) {
                System.out.println("  无效的 clientId: " + args);
            }
        });

        registerCommand("stop", "优雅关闭服务器", args -> {
            System.out.println("  正在优雅关闭服务器...");
            server.requestShutdown();
        });
    }

    // ══════════════ 内部类 ══════════════

    /** 命令注册表条目 */
    private static class CommandEntry {
        final String description;
        final Consumer<String> handler;

        CommandEntry(String description, Consumer<String> handler) {
            this.description = description;
            this.handler = handler;
        }
    }
}

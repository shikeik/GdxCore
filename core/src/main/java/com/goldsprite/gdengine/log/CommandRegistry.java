package com.goldsprite.gdengine.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时命令注册表 — 支持在游戏内通过控制台执行自定义命令。
 * <p>
 * 内置命令在 {@link LogPanel} 构造时自动注册。
 * 游戏项目可通过 {@link #register(String, String, CommandHandler)} 添加自定义命令。
 *
 * <pre>
 * // 示例: 注册自定义命令
 * CommandRegistry.register("tp", "传送到指定坐标 (tp x y)", args -> {
 *     String[] parts = args.split(" ");
 *     float x = Float.parseFloat(parts[0]);
 *     float y = Float.parseFloat(parts[1]);
 *     player.setPosition(x, y);
 *     return "已传送到 (" + x + ", " + y + ")";
 * });
 * </pre>
 */
public class CommandRegistry {

	private static final Map<String, CommandInfo> commands = new LinkedHashMap<>();

	/** 命令处理器函数接口 */
	@FunctionalInterface
	public interface CommandHandler {
		/**
		 * 执行命令并返回结果文本。
		 *
		 * @param args 命令参数 (不含命令名本身)
		 * @return 执行结果文本, 会作为日志输出到 LogPanel
		 */
		String execute(String args);
	}

	/** 命令元信息 */
	public static class CommandInfo {
		public final String name;
		public final String description;
		public final CommandHandler handler;

		public CommandInfo(String name, String description, CommandHandler handler) {
			this.name = name;
			this.description = description;
			this.handler = handler;
		}
	}

	// ============================================================
	//  注册 / 执行
	// ============================================================

	/**
	 * 注册命令。同名命令会被覆盖。
	 *
	 * @param name        命令名 (大小写不敏感, 存储为小写)
	 * @param description 命令描述 (help 中展示)
	 * @param handler     命令处理器
	 */
	public static void register(String name, String description, CommandHandler handler) {
		if (name == null || name.trim().isEmpty()) return;
		commands.put(name.trim().toLowerCase(), new CommandInfo(name.trim(), description, handler));
	}

	/**
	 * 执行命令字符串, 返回结果文本。
	 *
	 * @param input 完整命令字符串, 如 "filter +Combat -Quest"
	 * @return 执行结果文本
	 */
	public static String execute(String input) {
		if (input == null || input.trim().isEmpty()) {
			return "";
		}
		String trimmed = input.trim();
		String[] parts = trimmed.split("\\s+", 2);
		String name = parts[0].toLowerCase();
		String args = parts.length > 1 ? parts[1] : "";

		CommandInfo cmd = commands.get(name);
		if (cmd == null) {
			return "[WARN] 未知命令: " + parts[0] + " — 输入 help 查看所有命令";
		}

		try {
			return cmd.handler.execute(args);
		} catch (Exception e) {
			return "[ERROR] 命令执行异常: " + e.getMessage();
		}
	}

	/**
	 * 获取以 prefix 开头的命令名列表 (用于自动补全)。
	 *
	 * @param prefix 前缀 (大小写不敏感)
	 * @return 匹配的命令名列表
	 */
	public static List<String> getSuggestions(String prefix) {
		List<String> result = new ArrayList<>();
		String lower = (prefix == null) ? "" : prefix.toLowerCase();
		for (CommandInfo info : commands.values()) {
			if (info.name.toLowerCase().startsWith(lower)) {
				result.add(info.name);
			}
		}
		return result;
	}

	/**
	 * 获取帮助文本 (列出所有已注册命令)。
	 */
	public static String getHelp() {
		if (commands.isEmpty()) return "暂无已注册命令";
		StringBuilder sb = new StringBuilder("=== 可用命令 ===\n");
		for (CommandInfo info : commands.values()) {
			sb.append("  ").append(info.name).append(" — ").append(info.description).append("\n");
		}
		return sb.toString().trim();
	}

	/**
	 * 判断是否已注册指定命令。
	 */
	public static boolean hasCommand(String name) {
		if (name == null) return false;
		return commands.containsKey(name.trim().toLowerCase());
	}

	/**
	 * 移除已注册命令。
	 */
	public static void unregister(String name) {
		if (name == null) return;
		commands.remove(name.trim().toLowerCase());
	}

	/**
	 * 清空所有已注册命令 (一般仅用于测试)。
	 */
	public static void clearAll() {
		commands.clear();
	}

	/**
	 * 获取已注册命令数量。
	 */
	public static int getCommandCount() {
		return commands.size();
	}
}

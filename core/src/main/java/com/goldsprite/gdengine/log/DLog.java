package com.goldsprite.gdengine.log;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Logger;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.goldsprite.gdengine.GDEngineConfig;
import com.goldsprite.gdengine.PlatformProfile;
import com.goldsprite.gdengine.screens.ScreenManager;

public class DLog {
	public static final String passStr = "Y";
	private static final float LOGICAL_SHORT = 540f;
	private static final float LOGICAL_LONG = 960f;
	public static boolean singleMode = false;
	public static String singleTag = "Default";
	// [新增] 黑白名单模式控制
	public static boolean isBlackListMode = true; // 默认黑名单模式
	public static List<String> blackList = new CopyOnWriteArrayList<>(); // 黑名单列表
	public static String[] showTags = {
		"Default Y",
		"拦截 N",

		//GDEngine
		"ToastUI Y",
		"ZipDownLoader Y",

		// Test
		"TEST Y",
		"VERIFY Y",
		"Test1 Y",
		"VisualCheck Y",
	};

	/** 运行时动态注册的白名单条目（供下游项目注入业务标签） */
	private static final List<String> extraShowTags = new CopyOnWriteArrayList<>();

	/**
	 * 动态添加白名单标签（格式: "TagName Y" 或 "TagName N"）。
	 * <p>应在 initUI() 之前调用。
	 */
	public static void addShowTag(String tagLine) {
		if (tagLine != null && !tagLine.isEmpty()) extraShowTags.add(tagLine);
	}

	public static String LOG_TAG = GDEngineConfig.getProjectName();
	private static final Logger logger = new Logger(LOG_TAG);

	// 数据层 (构造时即可用)
	public static List<String> logMessages = new CopyOnWriteArrayList<>();
	/** 结构化日志条目列表，供 LogPanel 筛选使用 */
	public static List<LogEntry> logEntries = new CopyOnWriteArrayList<>();
	public static boolean showDebugUI = true;
	public static boolean shortcuts = true;
	static int maxLogsCache = 100;
	// 视口配置
	static float scl = 2.5f;
	private static DLog instance;
	private static final List<String> logInfos = new CopyOnWriteArrayList<>();

	private static final List<LogOutput> outputs = new CopyOnWriteArrayList<>();

	static {
		blackList.add("拦截");
		// 大厅心跳/线路级别日志默认拦截，避免每30秒刷屏
		blackList.add("PhoenixHB");
		blackList.add("PhoenixWire");

		// [新增] 注册默认输出端
		registerLogOutput(new StandardOutput());
		registerLogOutput(new GdxUiOutput());
	}

	public DebugConsole console;
	// 表现层 (延后初始化)
	private Stage stage;

	// [修改] 构造函数只做最基础的数据准备，绝对不碰 UI
	public DLog() {
		instance = this;
	}

	public static DLog getInstance() {
		if (instance == null) new DLog();
		return instance;
	}

	public static List<String> getAllLogs() {
		return logMessages;
	}

	public static List<String> getLogs() {
		getInstance();
		return logMessages;
	}

	/** 获取结构化日志条目列表 (供 LogPanel 筛选使用) */
	public static List<LogEntry> getLogEntries() {
		return logEntries;
	}

	/** 清空所有日志 (同步清理 logMessages + logEntries) */
	public static void clearAllLogs() {
		logMessages.clear();
		logEntries.clear();
	}

	// --- 数据接口 ---

	public static String getInfoString() {
		StringBuilder sb = new StringBuilder();
		sb.append(GDEngineConfig.getProjectName()).append(": V").append(GDEngineConfig.getVersion());
		sb.append("\nHeap: ").append(Gdx.app.getJavaHeap() / 1024 / 1024).append("MB");
		sb.append("\nFPS: ").append(Gdx.graphics.getFramesPerSecond());

		getInstance();
		if (!logInfos.isEmpty()) {
			sb.append("\n--- Monitors ---\n");
			getInstance();
			sb.append(String.join("\n", logInfos));
		}
		return sb.toString();
	}

	public static void clearInfo() {
		if (getInstance() != null) {
			getInstance();
			logInfos.clear();
		}
	}

	// [新增] 日志等级枚举
	public enum Level {
		DEBUG, INFO, WARN, ERROR
	}

	// ── 日志等级过滤 ──
	/** 全局最低日志等级 (低于此等级的消息将被丢弃)。默认 DEBUG = 全部通过。 */
	private static volatile Level globalMinLevel = Level.DEBUG;
	/** 按标签设置的最低日志等级覆盖 (优先级高于全局等级) */
	private static final Map<String, Level> tagMinLevels = new ConcurrentHashMap<>();

	/**
	 * 设置全局最低日志等级。
	 * 低于此等级的日志将被丢弃（ERROR &gt; WARN &gt; INFO &gt; DEBUG）。
	 * @param level 最低等级，null 则重置为 DEBUG
	 */
	public static void setGlobalLogLevel(Level level) {
		globalMinLevel = (level != null) ? level : Level.DEBUG;
	}

	/** 获取当前全局最低日志等级 */
	public static Level getGlobalLogLevel() {
		return globalMinLevel;
	}

	/**
	 * 为指定标签设置最低日志等级（覆盖全局等级）。
	 * @param tag   日志标签
	 * @param level 最低等级，null 则移除该标签的覆盖（回退到全局等级）
	 */
	public static void setTagLogLevel(String tag, Level level) {
		if (tag == null) return;
		if (level != null) {
			tagMinLevels.put(tag, level);
		} else {
			tagMinLevels.remove(tag);
		}
	}

	/** 获取指定标签的有效最低日志等级（优先取标签覆盖，否则返回全局等级） */
	public static Level getEffectiveLogLevel(String tag) {
		Level tagLevel = tagMinLevels.get(tag);
		return (tagLevel != null) ? tagLevel : globalMinLevel;
	}

	/** 清除所有按标签的等级覆盖 */
	public static void clearTagLogLevels() {
		tagMinLevels.clear();
	}

	/**
	 * 从字符串解析日志等级 (不区分大小写)。
	 * @return 解析结果，无法识别时返回 null
	 */
	public static Level parseLevel(String str) {
		if (str == null || str.isEmpty()) return null;
		try {
			return Level.valueOf(str.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// [新增] 日志输出接口
	@FunctionalInterface
	public interface LogOutput {
		void onLog(Level level, String tag, String msg);
	}

	/**
	 * 注册额外的日志输出端 (如 Android Logcat, iOS SystemLog, Server)
	 */
	public static void registerLogOutput(LogOutput output) {
		if (output != null && !outputs.contains(output)) {
			outputs.add(output);
		}
	}

	/**
	 * 移除已注册的日志输出端
	 */
	public static void removeLogOutput(LogOutput output) {
		if (output != null) {
			outputs.remove(output);
		}
	}

	/**
	 * 移除所有特定类型的日志输出端
	 * @param outputClass 输出端类对象
	 */
	public static <T extends LogOutput> void removeLogOutput(Class<T> outputClass) {
		outputs.removeIf(output -> outputClass.isInstance(output));
	}

	// --- 默认输出端实现 ---

	/**
	 * 标准控制台输出 (System.out / System.err)
	 * 使用 ANSI 转义码在 IDE 控制台和终端中显示颜色
	 */
	public static class StandardOutput implements LogOutput {
		// ANSI 转义码常量
		private static final String RESET  = "\033[0m";
		private static final String RED    = "\033[91m";   // 亮红
		private static final String GREEN  = "\033[92m";   // 亮绿（PASS）
		private static final String YELLOW = "\033[93m";   // 亮黄（WARN）
		private static final String CYAN   = "\033[96m";   // 亮青（INFO）
		private static final String WHITE  = "\033[97m";   // 亮白（DEBUG）
		private static final String ORANGE = "\033[38;5;208m"; // 256色橙色
		private static final String GRAY   = "\033[90m";   // 暗灰（时间戳）

		// 是否启用 ANSI 颜色（Android Logcat 不支持）
		private static final boolean ANSI_ENABLED = !isAndroidRuntime();

		private static boolean isAndroidRuntime() {
			try {
				Class.forName("android.os.Build");
				return true;
			} catch (ClassNotFoundException e) {
				return false;
			}
		}

		@Override
		public void onLog(Level level, String tag, String msg) {
			String time = formatTime("HH:mm:ss:SSS");

			if (!ANSI_ENABLED) {
				// Android 等不支持 ANSI 的平台：纯文本输出
				String plain = String.format("[%s] [%s] [%s] %s", level.name(), time, tag, msg);
				if (level == Level.ERROR) {
					System.err.println(plain);
				} else {
					System.out.println(plain);
				}
				return;
			}

			// 根据级别选择颜色
			String levelColor;
			String levelLabel;
			switch (level) {
				case ERROR:
					levelColor = RED;
					levelLabel = "ERROR";
					break;
				case WARN:
					levelColor = YELLOW;
					levelLabel = "WARN ";
					break;
				case INFO:
					levelColor = CYAN;
					levelLabel = "INFO ";
					break;
				default:
					levelColor = WHITE;
					levelLabel = "DEBUG";
					break;
			}

			// Tag 特殊着色
			String tagColor = levelColor;
			if ("UserProject".equals(tag)) {
				tagColor = ORANGE;
			} else if ("AutoTest".equals(tag)) {
				// AutoTest 的 PASS/FAIL 已通过 level 区分，Tag 用绿色突出
				tagColor = GREEN;
			}

			// 组装带 ANSI 颜色的消息: [灰色时间] [彩色级别] [Tag颜色Tag] 消息
			String fullMsg = String.format("%s[%s]%s %s[%s]%s %s[%s]%s %s%s%s",
				GRAY, time, RESET,
				levelColor, levelLabel, RESET,
				tagColor, tag, RESET,
				levelColor, msg, RESET);

			if (level == Level.ERROR) {
				System.err.println(fullMsg);
			} else {
				System.out.println(fullMsg);
			}
		}
	}

	/**
	 * 游戏内 UI 控制台输出
	 */
	private static class GdxUiOutput implements LogOutput {
		@Override
		public void onLog(Level level, String tag, String msg) {
			String time = formatTime("HH:mm:ss:SSS");
			String fullMsg = String.format("[%s] [%s] %s", time, tag, msg);

			// 颜色逻辑与 StandardOutput 保持一致，供 VisUI Label 解析
			if (level == Level.ERROR) {
				fullMsg = "[RED]" + fullMsg;
			} else {
				if ("UserProject".equals(tag)) {
					fullMsg = "[ORANGE]" + fullMsg;
				}

				if (level == Level.WARN) {
					fullMsg = "[YELLOW]" + fullMsg;
				} else if (level == Level.INFO) {
					fullMsg = "[CYAN]" + fullMsg;
				} else {
					fullMsg = "[WHITE]" + fullMsg;
				}
			}

			// 双写: 旧的纯字符串列表 (向后兼容) + 新的结构化条目列表
			logMessages.add(fullMsg);
			logEntries.add(new LogEntry(level, tag, time, msg, fullMsg));

			// 限制缓存大小，防止内存溢出
			if (logMessages.size() > maxLogsCache) {
				logMessages.subList(0, logMessages.size() - maxLogsCache).clear();
				logEntries.subList(0, logEntries.size() - maxLogsCache).clear();
			}
		}
	}

	// --- 统一分发逻辑 ---

	private static void dispatch(Level level, String tag, Object... values) {
		// 0. 日志等级过滤（低于有效最低等级的消息直接丢弃）
		Level minLevel = getEffectiveLogLevel(tag);
		if (level.ordinal() < minLevel.ordinal()) {
			return;
		}

		// 1. 黑白名单检查
		if (banTag(tag)) {
			// 如果被拦截，且是拦截模式下的特殊显示，则走特定逻辑 (仅针对 logT/DEBUG)
			// 为了简化，这里仅对 DEBUG 级别且开启了"拦截 Y"的情况做特殊处理
			// 但考虑到逻辑统一，如果被 ban，应该直接返回
			// 原有逻辑中有个 "拦截 Y" 的特殊分支
			if (level == Level.DEBUG && showTags[1].equals("拦截 Y")) {
				// 递归调用自身，但 Tag 变为 "拦截"，可能会绕过 banTag (因为 "拦截" 在白名单里?)
				// 检查 static 块: blackList.add("拦截"); -> 意味着 "拦截" 也会被 ban?
				// 原有逻辑：banTag("拦截") -> return blackList.contains("拦截") -> true.
				// 所以原有逻辑其实是死循环或者无效？
				// 等等，原有逻辑：
				// if (banTag(tag)) {
				//     if (showTags[1].equals("拦截 Y"))
				//         logT("拦截", "被拦截的: " + formatString(values));
				//     return;
				// }
				// 如果 "拦截" 也在黑名单里，那 logT("拦截") 也会进来然后 return。
				// 除非 "拦截" 不在黑名单。但 static 块里加了。
				// 假设用户知道自己在做什么，这里我们暂时保留这个特殊逻辑的意图，
				// 但为了防止递归栈溢出，我们直接输出到 System.out 调试一下，或者忽略。
				// 鉴于这是一个复杂的遗留逻辑，我们暂且只做 return。
			}
			return;
		}

		// 2. 格式化内容
		String content = formatString(values);

		// 3. 分发给所有输出端
		for (LogOutput output : outputs) {
			output.onLog(level, tag, content);
		}
	}

	public static void log(Object... values) {
		logT("Default", values);
	}

	public static void logT(String tag, Object... values) {
		dispatch(Level.DEBUG, tag, values);
	}

	public static void logErr(Object... values) {
		logErrT("Default", values);
	}

	public static void logErrT(String tag, Object... values) {
		dispatch(Level.ERROR, tag, values);
	}

	// [新增] WARN 级别
	public static void logWarn(Object... values) {
		logWarnT("Default", values);
	}

	public static void logWarnT(String tag, Object... values) {
		dispatch(Level.WARN, tag, values);
	}

	// [新增] INFO 级别 (流式日志，区别于 Monitor 的 info)
	public static void logInfo(Object... values) {
		logInfoT("Default", values);
	}

	public static void logInfoT(String tag, Object... values) {
		dispatch(Level.INFO, tag, values);
	}

	// Monitor 专用接口 (保持不变)
	public static void info(Object... values) {
		infoT("Default", values);
	}

	public static void infoT(String tag, Object... values) {
		if (banTag(tag)) return;

		String msg = String.format("[%s] %s", tag, formatString(values));

		logInfos.add(msg);
	}

	public static boolean banTag(String tag) {
		if (singleMode) {
			return !singleTag.equals(tag);
		}

		// [修改] 黑名单模式逻辑
		if (isBlackListMode) {
			// 如果在黑名单中，则拦截 (return true)
			// 否则放行 (return false)
			return blackList.contains(tag);
		}

		// 白名单模式逻辑 (原有 + 动态注册)
		for (String tagInfo : showTags) {
			String[] splits = tagInfo.split(" ");
			if (splits.length < 2) continue;
			if (splits[0].equals(tag)) return !passStr.equals(splits[1]);
		}
		for (String tagInfo : extraShowTags) {
			String[] splits = tagInfo.split(" ");
			if (splits.length < 2) continue;
			if (splits[0].equals(tag)) return !passStr.equals(splits[1]);
		}

		return true; // 白名单模式下，未找到则默认拦截
	}

	/**
	 * 格式化当前时间（Java 8+ 推荐）
	 *
	 * @param pattern 时间格式，如 "HH:mm:ss:SSS"
	 * @return 格式化后的时间字符串
	 */
	public static String formatTime(String pattern) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
		// 强制使用上海时区 (GMT+8)，解决部分环境(如Termux)下时区不正确的问题
		return LocalTime.now(ZoneId.of("Asia/Shanghai")).format(formatter);
	}

	public static String formatString(Object... values) {
		String msg;
		if (values.length == 1) {
			msg = String.valueOf(values[0]);
		} else {
			try {
				String format = String.valueOf(values[0]);
				Object[] args = Arrays.copyOfRange(values, 1, values.length);
				msg = String.format(format, args);
			} catch (Exception e) {
				msg = values[0] + " <FmtErr> " + Arrays.toString(Arrays.copyOfRange(values, 1, values.length));
			}
		}
		return msg;
	}

	public static void setIntros(String text) {
		// [修改] 增加判空，防止在 initUI 之前调用导致的崩溃
		if (getInstance().console != null) {
			getInstance().console.setIntros(text);
		}
	}

	/**
	 * 获取 DLog 内部的 Stage (用于 UI 遮挡检测等).
	 * @return DLog 的 Stage，未初始化时返回 null
	 */
	public Stage getStage() {
		return stage;
	}

	/**
	 * [新增] 显式 UI 初始化方法
	 * 必须在 VisUIHelper.loadWithChineseFont() 之后调用
	 */
	public void initUI() {
		if (stage != null) return; // 防止重复初始化

		// [修改] 初始默认横屏
		stage = new Stage(new ExtendViewport(LOGICAL_LONG * scl, LOGICAL_SHORT * scl));

		console = new DebugConsole();
		stage.addActor(console);

		registerInput();

		// 打印一条调试信息验证顺序
//		log("DebugUI UI Initialized.");
	}

	private void registerInput() {
		Gdx.app.postRunnable(() -> {
			try {
				ScreenManager sm = ScreenManager.getInstance();
				if (sm != null && sm.getImp() != null) {
					sm.getImp().addProcessor(0, stage);
//					log("DebugUI Input Registered at Top.");
				} else {
//					log("Warning: ScreenManager not ready for DebugUI input.");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	public void render() {
		// [修改] 如果 UI 还没初始化，直接跳过渲染，但数据收集依然正常工作
		if (!showDebugUI || stage == null) return;

		stage.getViewport().apply(true);
		stage.act();
		stage.draw();
	}

	public void resize(int w, int h) {
		if (stage == null) return;

		scl = PlatformProfile.get().ideConsoleViewportScale;
		ExtendViewport vp = (ExtendViewport) stage.getViewport();
		if (h > w) {
			vp.setMinWorldWidth(LOGICAL_SHORT * scl);
			vp.setMinWorldHeight(LOGICAL_LONG * scl);
		} else {
			vp.setMinWorldWidth(LOGICAL_LONG * scl);
			vp.setMinWorldHeight(LOGICAL_SHORT * scl);
		}
		vp.update(w, h, true);
	}

	public void dispose() {
		if (stage != null) stage.dispose();
		if (console != null) console.dispose();
	}
}

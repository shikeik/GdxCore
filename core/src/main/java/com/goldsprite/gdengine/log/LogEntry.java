package com.goldsprite.gdengine.log;

/**
 * 结构化日志条目，存储 tag/level 等元信息以支持筛选。
 * <p>
 * 与 {@link DLog#logMessages} (纯 String) 并行存在，
 * 供 LogPanel 等新组件进行结构化过滤。
 */
public class LogEntry {
	public DLog.Level level;
	public String tag;
	public String time;
	public String message;
	/** 带 [RED]/[CYAN] 等颜色标记的完整文本，兼容旧渲染逻辑 */
	public String formatted;

	public LogEntry(DLog.Level level, String tag, String time, String message, String formatted) {
		this.level = level;
		this.tag = tag;
		this.time = time;
		this.message = message;
		this.formatted = formatted;
	}

	/** 是否匹配搜索词 (大小写不敏感，匹配 tag 或 message) */
	public boolean matchesSearch(String term) {
		if (term == null || term.isEmpty()) return true;
		String lower = term.toLowerCase();
		return tag.toLowerCase().contains(lower) || message.toLowerCase().contains(lower);
	}

	@Override
	public String toString() {
		return String.format("[%s] [%s] [%s] %s", level, time, tag, message);
	}
}

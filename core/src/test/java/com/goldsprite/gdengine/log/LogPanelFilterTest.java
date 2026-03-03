package com.goldsprite.gdengine.log;

import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * LogPanel 筛选逻辑 单元测试。
 * <p>
 * 由于 LogPanel extends VisTable 需要 OpenGL 上下文才能构造 UI，
 * 这里通过直接操作 DLog.logEntries 来测试核心筛选方法。
 * 使用反射绕过 UI 构造，或用辅助测试子类。
 * <p>
 * 实际方案: 将筛选逻辑提取到静态方法进行测试,
 * 或直接用 LogPanel 的 getFilteredEntries() — 但需要构造实例.
 * 由于 VisTable 构造需要 VisUI.load() 等, 此处采用直接测试 LogEntry 过滤逻辑的方式,
 * 确保筛选算法正确.
 */
public class LogPanelFilterTest {

	@Before
	public void setUp() {
		DLog.clearAllLogs();
		DLog.logEntries.clear(); // 确保干净
	}

	// --------- 等级过滤 ---------

	@Test
	public void 等级过滤_只显示ERROR() {
		addEntry(DLog.Level.DEBUG, "A", "debug msg");
		addEntry(DLog.Level.INFO, "A", "info msg");
		addEntry(DLog.Level.WARN, "A", "warn msg");
		addEntry(DLog.Level.ERROR, "A", "error msg");

		EnumSet<DLog.Level> levels = EnumSet.of(DLog.Level.ERROR);
		List<LogEntry> result = filter(levels, true, null, "");
		assertEquals(1, result.size());
		assertEquals(DLog.Level.ERROR, result.get(0).level);
	}

	@Test
	public void 等级过滤_多个等级() {
		addEntry(DLog.Level.DEBUG, "A", "d");
		addEntry(DLog.Level.INFO, "A", "i");
		addEntry(DLog.Level.WARN, "A", "w");
		addEntry(DLog.Level.ERROR, "A", "e");

		EnumSet<DLog.Level> levels = EnumSet.of(DLog.Level.WARN, DLog.Level.ERROR);
		List<LogEntry> result = filter(levels, true, null, "");
		assertEquals(2, result.size());
	}

	@Test
	public void 等级过滤_全部() {
		addEntry(DLog.Level.DEBUG, "A", "d");
		addEntry(DLog.Level.INFO, "A", "i");
		addEntry(DLog.Level.WARN, "A", "w");
		addEntry(DLog.Level.ERROR, "A", "e");

		EnumSet<DLog.Level> levels = EnumSet.allOf(DLog.Level.class);
		List<LogEntry> result = filter(levels, true, null, "");
		assertEquals(4, result.size());
	}

	// --------- Tag 过滤 ---------

	@Test
	public void tag过滤_showAllTags() {
		addEntry(DLog.Level.DEBUG, "Combat", "hit");
		addEntry(DLog.Level.DEBUG, "Quest", "done");
		addEntry(DLog.Level.DEBUG, "Netcode", "sync");

		List<LogEntry> result = filter(EnumSet.allOf(DLog.Level.class), true, null, "");
		assertEquals(3, result.size());
	}

	@Test
	public void tag过滤_指定tag() {
		addEntry(DLog.Level.DEBUG, "Combat", "hit");
		addEntry(DLog.Level.DEBUG, "Quest", "done");
		addEntry(DLog.Level.DEBUG, "Netcode", "sync");

		java.util.Set<String> tags = new java.util.HashSet<>();
		tags.add("Combat");
		tags.add("Netcode");
		List<LogEntry> result = filter(EnumSet.allOf(DLog.Level.class), false, tags, "");
		assertEquals(2, result.size());
		assertTrue(result.stream().allMatch(e -> e.tag.equals("Combat") || e.tag.equals("Netcode")));
	}

	// --------- 搜索过滤 ---------

	@Test
	public void 搜索过滤_匹配message() {
		addEntry(DLog.Level.DEBUG, "A", "玩家血量不足");
		addEntry(DLog.Level.DEBUG, "A", "怪物刷新完毕");
		addEntry(DLog.Level.DEBUG, "A", "玩家经验增加");

		List<LogEntry> result = filter(EnumSet.allOf(DLog.Level.class), true, null, "玩家");
		assertEquals(2, result.size());
	}

	@Test
	public void 搜索过滤_匹配tag() {
		addEntry(DLog.Level.DEBUG, "Network", "msg1");
		addEntry(DLog.Level.DEBUG, "Audio", "msg2");

		List<LogEntry> result = filter(EnumSet.allOf(DLog.Level.class), true, null, "net");
		assertEquals(1, result.size());
		assertEquals("Network", result.get(0).tag);
	}

	// --------- 组合过滤 ---------

	@Test
	public void 组合过滤_等级加Tag加搜索() {
		addEntry(DLog.Level.DEBUG, "Combat", "玩家攻击命中");
		addEntry(DLog.Level.ERROR, "Combat", "伤害计算错误");
		addEntry(DLog.Level.DEBUG, "Quest", "任务完成");
		addEntry(DLog.Level.ERROR, "Quest", "任务数据异常");

		java.util.Set<String> tags = new java.util.HashSet<>();
		tags.add("Combat");
		List<LogEntry> result = filter(
			EnumSet.of(DLog.Level.ERROR), false, tags, "错误"
		);
		assertEquals(1, result.size());
		assertEquals("伤害计算错误", result.get(0).message);
	}

	@Test
	public void 空搜索词_不过滤() {
		addEntry(DLog.Level.DEBUG, "A", "msg");
		List<LogEntry> result = filter(EnumSet.allOf(DLog.Level.class), true, null, "");
		assertEquals(1, result.size());
	}

	// ============================================================
	//  辅助方法 — 模拟 LogPanel.getFilteredEntries() 的纯逻辑
	// ============================================================

	private void addEntry(DLog.Level level, String tag, String message) {
		String formatted = String.format("[%s] [%s] %s", level, tag, message);
		DLog.logEntries.add(new LogEntry(level, tag, "00:00:00:000", message, formatted));
	}

	/**
	 * 纯逻辑版 getFilteredEntries(), 不依赖 VisUI
	 */
	private List<LogEntry> filter(
		EnumSet<DLog.Level> levels, boolean showAllTags,
		java.util.Set<String> tagFilter, String searchTerm
	) {
		List<LogEntry> source = DLog.getLogEntries();
		List<LogEntry> result = new java.util.ArrayList<>();
		for (LogEntry entry : source) {
			if (!levels.contains(entry.level)) continue;
			if (!showAllTags && (tagFilter == null || !tagFilter.contains(entry.tag))) continue;
			if (!entry.matchesSearch(searchTerm)) continue;
			result.add(entry);
		}
		return result;
	}
}

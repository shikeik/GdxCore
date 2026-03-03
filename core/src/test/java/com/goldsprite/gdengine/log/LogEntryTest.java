package com.goldsprite.gdengine.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * LogEntry 结构化日志条目 单元测试。
 * 覆盖：构造函数、matchesSearch、toString。
 */
public class LogEntryTest {

	// --------- 构造 & 字段 ---------

	@Test
	public void 构造函数_字段赋值正确() {
		LogEntry entry = new LogEntry(
			DLog.Level.ERROR, "NetWork", "12:00:00:000", "连接超时", "[RED][12:00:00:000] [NetWork] 连接超时"
		);

		assertEquals(DLog.Level.ERROR, entry.level);
		assertEquals("NetWork", entry.tag);
		assertEquals("12:00:00:000", entry.time);
		assertEquals("连接超时", entry.message);
		assertEquals("[RED][12:00:00:000] [NetWork] 连接超时", entry.formatted);
	}

	// --------- matchesSearch ---------

	@Test
	public void matchesSearch_空字符串_返回true() {
		LogEntry entry = makeEntry("Default", "hello");
		assertTrue(entry.matchesSearch(""));
	}

	@Test
	public void matchesSearch_null_返回true() {
		LogEntry entry = makeEntry("Default", "hello");
		assertTrue(entry.matchesSearch(null));
	}

	@Test
	public void matchesSearch_匹配message() {
		LogEntry entry = makeEntry("Default", "玩家血量不足");
		assertTrue(entry.matchesSearch("血量"));
	}

	@Test
	public void matchesSearch_匹配tag() {
		LogEntry entry = makeEntry("NetWork", "请求超时");
		assertTrue(entry.matchesSearch("net")); // 大小写不敏感
	}

	@Test
	public void matchesSearch_不匹配_返回false() {
		LogEntry entry = makeEntry("AI", "寻路完成");
		assertFalse(entry.matchesSearch("网络"));
	}

	@Test
	public void matchesSearch_大小写不敏感() {
		LogEntry entry = makeEntry("MyTag", "Hello World");
		assertTrue(entry.matchesSearch("HELLO"));
		assertTrue(entry.matchesSearch("hello"));
		assertTrue(entry.matchesSearch("MYTAG"));
	}

	// --------- toString ---------

	@Test
	public void toString_格式正确() {
		LogEntry entry = new LogEntry(
			DLog.Level.WARN, "AI", "10:30:00:123", "路径不可达", ""
		);
		String s = entry.toString();
		assertEquals("[WARN] [10:30:00:123] [AI] 路径不可达", s);
	}

	// --------- 辅助方法 ---------

	private LogEntry makeEntry(String tag, String message) {
		return new LogEntry(DLog.Level.DEBUG, tag, "00:00:00:000", message, "");
	}
}

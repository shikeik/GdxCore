package com.goldsprite.gdengine.log;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * DLog 数据层 单元测试。
 * 覆盖：dispatch 双写 logEntries、缓存清理、clearAllLogs、getLogEntries。
 * 注意：DLog static 块会注册 StandardOutput + GdxUiOutput，
 *       StandardOutput 输出到 System.out/err (JUnit 下正常)，
 *       GdxUiOutput 负责写入 logMessages + logEntries。
 */
public class DLogDataLayerTest {

	@Before
	public void setUp() {
		// 每个测试前清空日志，避免互相影响
		DLog.clearAllLogs();
	}

	// --------- 双写验证 ---------

	@Test
	public void dispatch_结构化条目同步写入() {
		DLog.log("测试消息");

		List<LogEntry> entries = DLog.getLogEntries();
		assertFalse("logEntries 不应为空", entries.isEmpty());

		LogEntry last = entries.get(entries.size() - 1);
		assertEquals(DLog.Level.DEBUG, last.level);
		assertEquals("Default", last.tag);
		assertEquals("测试消息", last.message);
		assertNotNull(last.time);
		assertNotNull(last.formatted);
	}

	@Test
	public void dispatch_logMessages和logEntries数量一致() {
		DLog.log("msg1");
		DLog.logErr("msg2");
		DLog.logWarn("msg3");
		DLog.logInfo("msg4");

		assertEquals("logMessages 与 logEntries 数量应一致",
			DLog.logMessages.size(), DLog.logEntries.size());
	}

	@Test
	public void dispatch_不同级别正确记录() {
		DLog.logT("TestTag", "debug消息");
		DLog.logErrT("TestTag", "error消息");
		DLog.logWarnT("TestTag", "warn消息");
		DLog.logInfoT("TestTag", "info消息");

		List<LogEntry> entries = DLog.getLogEntries();
		// TestTag 不在黑名单中，所以 4 条都应写入
		assertTrue("至少应有 4 条日志", entries.size() >= 4);

		// 取最后 4 条验证
		int base = entries.size() - 4;
		assertEquals(DLog.Level.DEBUG, entries.get(base).level);
		assertEquals(DLog.Level.ERROR, entries.get(base + 1).level);
		assertEquals(DLog.Level.WARN, entries.get(base + 2).level);
		assertEquals(DLog.Level.INFO, entries.get(base + 3).level);
	}

	@Test
	public void dispatch_tag正确传递() {
		DLog.logT("MyModule", "hello");
		LogEntry last = lastEntry();
		assertEquals("MyModule", last.tag);
	}

	// --------- 黑名单拦截 ---------

	@Test
	public void dispatch_黑名单tag不写入() {
		// "拦截" 在默认黑名单中
		DLog.clearAllLogs();
		DLog.logT("拦截", "这条应该被拦截");

		assertTrue("黑名单 tag 不应写入 logEntries", DLog.logEntries.isEmpty());
		assertTrue("黑名单 tag 不应写入 logMessages", DLog.logMessages.isEmpty());
	}

	// --------- 缓存上限 ---------

	@Test
	public void dispatch_超过maxLogsCache时自动清理() {
		int max = DLog.maxLogsCache;
		// 写入 max + 20 条
		for (int i = 0; i < max + 20; i++) {
			DLog.log("msg" + i);
		}

		assertTrue("logMessages 数量不应超过 maxLogsCache",
			DLog.logMessages.size() <= max);
		assertTrue("logEntries 数量不应超过 maxLogsCache",
			DLog.logEntries.size() <= max);
		assertEquals("logMessages 与 logEntries 数量应保持一致",
			DLog.logMessages.size(), DLog.logEntries.size());
	}

	// --------- clearAllLogs ---------

	@Test
	public void clearAllLogs_同步清空两个列表() {
		DLog.log("msg1");
		DLog.log("msg2");
		assertFalse(DLog.logMessages.isEmpty());
		assertFalse(DLog.logEntries.isEmpty());

		DLog.clearAllLogs();
		assertTrue("clearAllLogs 后 logMessages 应为空", DLog.logMessages.isEmpty());
		assertTrue("clearAllLogs 后 logEntries 应为空", DLog.logEntries.isEmpty());
	}

	// --------- getLogEntries ---------

	@Test
	public void getLogEntries_返回同一引用() {
		assertSame(DLog.logEntries, DLog.getLogEntries());
	}

	// --------- matchesSearch 集成 ---------

	@Test
	public void logEntries_支持搜索过滤() {
		DLog.logT("NetWork", "连接成功");
		DLog.logT("AI", "寻路完成");
		DLog.logT("NetWork", "数据包丢失");

		long networkCount = DLog.getLogEntries().stream()
			.filter(e -> e.matchesSearch("NetWork"))
			.count();
		assertTrue("搜索 NetWork 应至少匹配 2 条", networkCount >= 2);

		long aiCount = DLog.getLogEntries().stream()
			.filter(e -> e.matchesSearch("寻路"))
			.count();
		assertTrue("搜索 '寻路' 应至少匹配 1 条", aiCount >= 1);
	}

	// --------- 辅助 ---------

	private LogEntry lastEntry() {
		List<LogEntry> entries = DLog.getLogEntries();
		return entries.get(entries.size() - 1);
	}
}

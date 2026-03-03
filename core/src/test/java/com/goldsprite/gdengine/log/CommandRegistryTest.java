package com.goldsprite.gdengine.log;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * CommandRegistry 命令注册表 单元测试。
 * 覆盖: 注册、执行、补全建议、help、异常处理、大小写。
 */
public class CommandRegistryTest {

	@Before
	public void setUp() {
		CommandRegistry.clearAll();
	}

	// --------- 注册与执行 ---------

	@Test
	public void register_执行返回结果() {
		CommandRegistry.register("hello", "打招呼", args -> "你好, " + args);
		String result = CommandRegistry.execute("hello 世界");
		assertEquals("你好, 世界", result);
	}

	@Test
	public void register_无参数时args为空串() {
		CommandRegistry.register("ping", "测试连通性", args -> "pong:" + args.length());
		String result = CommandRegistry.execute("ping");
		assertEquals("pong:0", result);
	}

	@Test
	public void execute_未知命令返回提示() {
		String result = CommandRegistry.execute("notexist");
		assertTrue(result.contains("未知命令"));
	}

	@Test
	public void execute_空输入返回空串() {
		assertEquals("", CommandRegistry.execute(""));
		assertEquals("", CommandRegistry.execute(null));
		assertEquals("", CommandRegistry.execute("   "));
	}

	// --------- 大小写 ---------

	@Test
	public void execute_命令名大小写不敏感() {
		CommandRegistry.register("Test", "测试", args -> "OK");
		assertEquals("OK", CommandRegistry.execute("test"));
		assertEquals("OK", CommandRegistry.execute("TEST"));
		assertEquals("OK", CommandRegistry.execute("Test"));
	}

	// --------- 同名覆盖 ---------

	@Test
	public void register_同名覆盖() {
		CommandRegistry.register("cmd", "v1", args -> "v1");
		CommandRegistry.register("cmd", "v2", args -> "v2");
		assertEquals("v2", CommandRegistry.execute("cmd"));
		assertEquals(1, CommandRegistry.getCommandCount());
	}

	// --------- 异常处理 ---------

	@Test
	public void execute_handler异常被捕获() {
		CommandRegistry.register("crash", "会崩", args -> { throw new RuntimeException("boom"); });
		String result = CommandRegistry.execute("crash");
		assertTrue(result.contains("命令执行异常"));
		assertTrue(result.contains("boom"));
	}

	// --------- 补全建议 ---------

	@Test
	public void getSuggestions_前缀匹配() {
		CommandRegistry.register("filter", "过滤", args -> "");
		CommandRegistry.register("find", "查找", args -> "");
		CommandRegistry.register("help", "帮助", args -> "");

		List<String> suggestions = CommandRegistry.getSuggestions("fi");
		assertEquals(2, suggestions.size());
		assertTrue(suggestions.contains("filter"));
		assertTrue(suggestions.contains("find"));
	}

	@Test
	public void getSuggestions_空前缀返回全部() {
		CommandRegistry.register("a", "", args -> "");
		CommandRegistry.register("b", "", args -> "");
		List<String> all = CommandRegistry.getSuggestions("");
		assertEquals(2, all.size());
	}

	// --------- help ---------

	@Test
	public void getHelp_列出所有命令() {
		CommandRegistry.register("help", "列出命令", args -> "");
		CommandRegistry.register("clear", "清空", args -> "");
		String help = CommandRegistry.getHelp();
		assertTrue(help.contains("help"));
		assertTrue(help.contains("clear"));
		assertTrue(help.contains("列出命令"));
	}

	@Test
	public void getHelp_空注册表() {
		String help = CommandRegistry.getHelp();
		assertTrue(help.contains("暂无"));
	}

	// --------- hasCommand / unregister ---------

	@Test
	public void hasCommand_和unregister() {
		CommandRegistry.register("test", "测试", args -> "");
		assertTrue(CommandRegistry.hasCommand("test"));
		assertTrue(CommandRegistry.hasCommand("TEST")); // 大小写不敏感

		CommandRegistry.unregister("test");
		assertFalse(CommandRegistry.hasCommand("test"));
	}

	@Test
	public void clearAll_清空全部() {
		CommandRegistry.register("a", "", args -> "");
		CommandRegistry.register("b", "", args -> "");
		assertEquals(2, CommandRegistry.getCommandCount());
		CommandRegistry.clearAll();
		assertEquals(0, CommandRegistry.getCommandCount());
	}
}

package com.goldsprite.gdengine.log;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.goldsprite.gdengine.PlatformProfile;
import com.goldsprite.gdengine.ui.widget.HoverFocusScrollPane;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * 统一日志控制台面板 — 可嵌入 DebugConsole 或独立使用。
 * <p>
 * 功能:
 * <ul>
 *   <li>搜索栏 (大小写不敏感, 实时过滤 tag + message)</li>
 *   <li>等级过滤按钮 (DEBUG / INFO / WARN / ERROR 独立切换)</li>
 *   <li>Tag 过滤 (All 模式 / 手动选中)</li>
 *   <li>自动滚动到底部</li>
 *   <li>清空日志</li>
 * </ul>
 *
 * @see LogEntry
 * @see DLog
 */
public class LogPanel extends VisTable {

	// ---- 筛选状态 ----
	private final EnumSet<DLog.Level> levelFilters = EnumSet.allOf(DLog.Level.class);
	private final Set<String> tagFilter = new LinkedHashSet<>();
	private boolean showAllTags = true;
	private String searchTerm = "";

	// ---- UI 组件 ----
	private VisTextField searchField;
	private final VisTextButton[] levelBtns = new VisTextButton[DLog.Level.values().length];
	private VisTextButton tagToggleBtn;
	private VisLabel logLabel;
	private HoverFocusScrollPane logScroll;
	private VisTextButton autoScrollBtn;
	private boolean autoScroll = true;

	// ---- 刷新控制 ----
	private float refreshTimer = 0;
	private static final float REFRESH_INTERVAL = 1 / 30f; // 30 FPS 刷新
	private int lastEntryCount = -1; // 用于检测新日志
	private boolean filterDirty = true; // 筛选条件变更标志

	// ---- Tag 面板 ----
	private VisTable tagPanel;
	private boolean tagPanelVisible = false;

	// ---- 命令输入 ----
	private VisTextField commandField;
	private final List<String> commandHistory = new ArrayList<>();
	private int historyIndex = -1;

	public LogPanel() {
		buildUI();
		registerBuiltinCommands();
	}

	// ============================================================
	//  UI 构建
	// ============================================================

	private void buildUI() {
		// 第1行: 搜索栏
		buildSearchRow();
		// 第2行: 过滤工具栏 (等级按钮 + Tag + AutoScroll + Clear)
		buildFilterRow();
		// 第3行: Tag 展开面板 (默认隐藏)
		buildTagPanel();
		// 第4行: 日志显示区
		buildLogArea();
		// 第5行: 命令输入 (Phase 2 占位)
		buildCommandRow();
	}

	// ---------- 搜索栏 ----------

	private void buildSearchRow() {
		VisTable row = new VisTable();
		VisLabel icon = new VisLabel("Search:", "small");
		searchField = new VisTextField("");
		searchField.setMessageText("搜索 tag 或 message ...");
		searchField.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				searchTerm = searchField.getText();
				filterDirty = true;
			}
		});
		row.add(icon).padLeft(4).padRight(4);
		row.add(searchField).growX().height(28);
		add(row).growX().pad(2).row();
	}

	// ---------- 过滤工具栏 ----------

	private void buildFilterRow() {
		VisTable row = new VisTable();

		// 等级过滤按钮
		DLog.Level[] levels = DLog.Level.values();
		Color[] levelColors = { Color.WHITE, Color.CYAN, Color.YELLOW, Color.RED };
		for (int i = 0; i < levels.length; i++) {
			final DLog.Level lvl = levels[i];
			VisTextButton btn = new VisTextButton(lvl.name());
			btn.setColor(levelColors[i]);
			btn.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					toggleLevel(lvl, btn);
				}
			});
			levelBtns[i] = btn;
			row.add(btn).padRight(3).minWidth(55);
		}

		row.add().expandX(); // 弹性间距

		// Tag 下拉切换
		tagToggleBtn = new VisTextButton("Tags: All");
		tagToggleBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				toggleTagPanel();
			}
		});
		row.add(tagToggleBtn).padRight(6).minWidth(80);

		// AutoScroll
		autoScrollBtn = new VisTextButton("Auto: ON");
		autoScrollBtn.setColor(Color.GREEN);
		autoScrollBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				autoScroll = !autoScroll;
				autoScrollBtn.setText(autoScroll ? "Auto: ON" : "Auto: OFF");
				autoScrollBtn.setColor(autoScroll ? Color.GREEN : Color.GRAY);
			}
		});
		row.add(autoScrollBtn).padRight(6).minWidth(70);

		// Clear
		VisTextButton clearBtn = new VisTextButton("Clear");
		clearBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				DLog.clearAllLogs();
				logLabel.setText("");
				filterDirty = true;
			}
		});
		row.add(clearBtn).padRight(2).minWidth(50);

		add(row).growX().pad(2).row();
	}

	// ---------- Tag 面板 ----------

	private void buildTagPanel() {
		tagPanel = new VisTable();
		tagPanel.setVisible(false);
		add(tagPanel).growX().pad(0).row();
	}

	/** 切换 Tag 筛选面板的显隐 */
	private void toggleTagPanel() {
		tagPanelVisible = !tagPanelVisible;
		rebuildTagPanel();
		tagPanel.setVisible(tagPanelVisible);
		// 更新按钮高度的cell
		Cell<?> cell = getCell(tagPanel);
		if (cell != null) {
			cell.height(tagPanelVisible ? 0 : 0); // 自适应
		}
		invalidateHierarchy();
	}

	/** 重建 Tag 面板中的按钮列表 */
	private void rebuildTagPanel() {
		tagPanel.clearChildren();
		if (!tagPanelVisible) return;

		// 收集当前所有出现过的 tag
		Set<String> allTags = new LinkedHashSet<>();
		for (LogEntry entry : DLog.getLogEntries()) {
			allTags.add(entry.tag);
		}

		// "All" 按钮
		VisTextButton allBtn = new VisTextButton("All");
		allBtn.setColor(showAllTags ? Color.GREEN : Color.GRAY);
		allBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				showAllTags = true;
				tagFilter.clear();
				filterDirty = true;
				rebuildTagPanel();
				updateTagToggleBtnText();
			}
		});
		tagPanel.add(allBtn).padRight(3).padBottom(2);

		// 各 tag 按钮
		for (String tag : allTags) {
			boolean selected = !showAllTags && tagFilter.contains(tag);
			VisTextButton btn = new VisTextButton(tag);
			btn.setColor(showAllTags ? Color.LIGHT_GRAY : (selected ? Color.GREEN : Color.GRAY));
			btn.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					onTagButtonClicked(tag);
				}
			});
			tagPanel.add(btn).padRight(3).padBottom(2);
		}

		tagPanel.row();
	}

	private void onTagButtonClicked(String tag) {
		if (showAllTags) {
			// 从 All 模式切换到单 tag 模式
			showAllTags = false;
			tagFilter.clear();
			tagFilter.add(tag);
		} else {
			if (tagFilter.contains(tag)) {
				tagFilter.remove(tag);
				if (tagFilter.isEmpty()) {
					showAllTags = true; // 全部取消 → 回到 All
				}
			} else {
				tagFilter.add(tag);
			}
		}
		filterDirty = true;
		rebuildTagPanel();
		updateTagToggleBtnText();
	}

	private void updateTagToggleBtnText() {
		if (showAllTags) {
			tagToggleBtn.setText("Tags: All");
		} else {
			tagToggleBtn.setText("Tags: " + tagFilter.size());
		}
	}

	// ---------- 日志区 ----------

	private void buildLogArea() {
		logLabel = new VisLabel("", "small");
		logLabel.setWrap(true);
		logLabel.setFontScale(PlatformProfile.get().logPanelFontScale);
		logScroll = new HoverFocusScrollPane(logLabel);
		logScroll.setFadeScrollBars(false);
		add(logScroll).grow().pad(2).row();
	}

	// ---------- 命令输入栏 ----------

	private void buildCommandRow() {
		VisTable row = new VisTable();

		VisLabel prompt = new VisLabel(">", "small");
		commandField = new VisTextField("");
		commandField.setMessageText("输入命令 (help 查看帮助) ...");

		// 回车执行命令
		commandField.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
			@Override
			public boolean keyDown(InputEvent event, int keycode) {
				if (keycode == com.badlogic.gdx.Input.Keys.ENTER) {
					executeCommandInput();
					return true;
				}
				// 上箭头: 历史记录上翻
				if (keycode == com.badlogic.gdx.Input.Keys.UP) {
					navigateHistory(-1);
					return true;
				}
				// 下箭头: 历史记录下翻
				if (keycode == com.badlogic.gdx.Input.Keys.DOWN) {
					navigateHistory(1);
					return true;
				}
				return false;
			}
		});

		VisTextButton sendBtn = new VisTextButton("Send");
		sendBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				executeCommandInput();
			}
		});

		row.add(prompt).padLeft(4).padRight(4);
		row.add(commandField).growX().height(28);
		row.add(sendBtn).padLeft(4).padRight(4).minWidth(50);

		add(row).growX().pad(2).row();
	}

	/** 执行命令输入栏中的文本 */
	private void executeCommandInput() {
		String input = commandField.getText().trim();
		if (input.isEmpty()) return;

		// 记入历史
		commandHistory.add(input);
		historyIndex = commandHistory.size(); // 指向末尾之后

		// 执行
		String result = CommandRegistry.execute(input);

		// 将命令和结果输出到日志
		DLog.logInfoT("Console", "> " + input);
		if (result != null && !result.isEmpty()) {
			DLog.logInfoT("Console", result);
		}

		commandField.setText("");
		filterDirty = true;
	}

	/** 浏览历史命令 (direction: -1上翻, +1下翻) */
	private void navigateHistory(int direction) {
		if (commandHistory.isEmpty()) return;
		historyIndex += direction;
		if (historyIndex < 0) historyIndex = 0;
		if (historyIndex >= commandHistory.size()) {
			historyIndex = commandHistory.size();
			commandField.setText("");
			return;
		}
		commandField.setText(commandHistory.get(historyIndex));
		commandField.setCursorPosition(commandField.getText().length());
	}

	// ---------- 内置命令注册 ----------

	private void registerBuiltinCommands() {
		CommandRegistry.register("help", "列出所有已注册命令", args -> CommandRegistry.getHelp());

		CommandRegistry.register("clear", "清空日志", args -> {
			DLog.clearAllLogs();
			if (logLabel != null) logLabel.setText("");
			filterDirty = true;
			return "日志已清空";
		});

		CommandRegistry.register("filter", "Tag 过滤 (filter +Tag1 -Tag2 / filter all)", args -> {
			if (args.trim().equalsIgnoreCase("all")) {
				showAllTags = true;
				tagFilter.clear();
				updateTagToggleBtnText();
				filterDirty = true;
				return "已重置为显示全部 Tag";
			}
			String[] parts = args.trim().split("\\s+");
			for (String part : parts) {
				if (part.startsWith("+") && part.length() > 1) {
					showAllTags = false;
					tagFilter.add(part.substring(1));
				} else if (part.startsWith("-") && part.length() > 1) {
					tagFilter.remove(part.substring(1));
					if (tagFilter.isEmpty()) showAllTags = true;
				}
			}
			updateTagToggleBtnText();
			filterDirty = true;
			return "当前 Tag 过滤: " + (showAllTags ? "All" : tagFilter.toString());
		});

		CommandRegistry.register("level", "设置显示等级 (level warn error / level all)", args -> {
			if (args.trim().equalsIgnoreCase("all")) {
				levelFilters.clear();
				levelFilters.addAll(EnumSet.allOf(DLog.Level.class));
				filterDirty = true;
				return "已显示全部等级";
			}
			levelFilters.clear();
			String[] parts = args.trim().split("\\s+");
			for (String part : parts) {
				try {
					levelFilters.add(DLog.Level.valueOf(part.toUpperCase()));
				} catch (IllegalArgumentException ignored) {
					// 忽略非法等级名
				}
			}
			if (levelFilters.isEmpty()) {
				levelFilters.addAll(EnumSet.allOf(DLog.Level.class));
				return "无有效等级名, 已重置为全部";
			}
			filterDirty = true;
			return "当前等级过滤: " + levelFilters.toString();
		});

		CommandRegistry.register("search", "设置搜索关键词 (search 关键词 / search)", args -> {
			setSearchTerm(args.trim());
			return args.trim().isEmpty()
				? "已清除搜索"
				: "搜索: " + args.trim();
		});
	}

	// ============================================================
	//  筛选逻辑
	// ============================================================

	/**
	 * 按当前筛选条件过滤日志条目。
	 *
	 * @return 通过筛选的条目列表
	 */
	public List<LogEntry> getFilteredEntries() {
		List<LogEntry> source = DLog.getLogEntries();
		List<LogEntry> result = new ArrayList<>();
		for (int i = 0; i < source.size(); i++) {
			LogEntry entry = source.get(i);
			// 1. 等级过滤
			if (!levelFilters.contains(entry.level)) continue;
			// 2. Tag 过滤
			if (!showAllTags && !tagFilter.contains(entry.tag)) continue;
			// 3. 搜索词过滤
			if (!entry.matchesSearch(searchTerm)) continue;
			result.add(entry);
		}
		return result;
	}

	/** 刷新日志显示区 */
	private void refreshLogDisplay() {
		List<LogEntry> filtered = getFilteredEntries();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < filtered.size(); i++) {
			sb.append(filtered.get(i).formatted);
			if (i < filtered.size() - 1) sb.append("\n");
		}
		logLabel.setText(sb.toString());

		if (autoScroll) {
			logScroll.layout();
			logScroll.setScrollY(logScroll.getMaxY());
			logScroll.setVelocityY(0);
		}
	}

	// ============================================================
	//  等级过滤
	// ============================================================

	private void toggleLevel(DLog.Level level, VisTextButton btn) {
		if (levelFilters.contains(level)) {
			levelFilters.remove(level);
			btn.setColor(Color.DARK_GRAY);
		} else {
			levelFilters.add(level);
			// 还原原色
			Color[] levelColors = { Color.WHITE, Color.CYAN, Color.YELLOW, Color.RED };
			btn.setColor(levelColors[level.ordinal()]);
		}
		filterDirty = true;
	}

	// ============================================================
	//  公开 API
	// ============================================================

	/**
	 * 设置默认 Tag 过滤 (只显示指定 tag 的日志)。
	 * 调用后自动切换为非 All 模式。
	 *
	 * @param tags 要显示的 tag 列表
	 */
	public void setDefaultTagFilter(String... tags) {
		showAllTags = false;
		tagFilter.clear();
		for (String t : tags) {
			tagFilter.add(t);
		}
		updateTagToggleBtnText();
		filterDirty = true;
	}

	/**
	 * 重置为显示所有 Tag。
	 */
	public void resetTagFilter() {
		showAllTags = true;
		tagFilter.clear();
		updateTagToggleBtnText();
		filterDirty = true;
	}

	/**
	 * 以编程方式设置搜索词。
	 */
	public void setSearchTerm(String term) {
		this.searchTerm = term != null ? term : "";
		if (searchField != null) {
			searchField.setText(this.searchTerm);
		}
		filterDirty = true;
	}

	/**
	 * 获取当前启用的等级过滤集合 (只读副本)。
	 */
	public EnumSet<DLog.Level> getLevelFilters() {
		return EnumSet.copyOf(levelFilters);
	}

	/**
	 * 获取是否为 "显示全部Tag" 模式。
	 */
	public boolean isShowAllTags() {
		return showAllTags;
	}

	/**
	 * 获取当前选中的 Tag 集合 (只读副本)。
	 */
	public Set<String> getTagFilter() {
		return new LinkedHashSet<>(tagFilter);
	}

	/**
	 * 获取/设置自动滚动。
	 */
	public boolean isAutoScroll() { return autoScroll; }
	public void setAutoScroll(boolean val) {
		autoScroll = val;
		if (autoScrollBtn != null) {
			autoScrollBtn.setText(autoScroll ? "Auto: ON" : "Auto: OFF");
			autoScrollBtn.setColor(autoScroll ? Color.GREEN : Color.GRAY);
		}
	}

	// ============================================================
	//  帧更新
	// ============================================================

	@Override
	public void act(float delta) {
		super.act(delta);

		refreshTimer += delta;
		if (refreshTimer < REFRESH_INTERVAL) return;
		refreshTimer = 0;

		// 检测是否有新日志或筛选条件变化
		int currentCount = DLog.getLogEntries().size();
		if (currentCount != lastEntryCount || filterDirty) {
			lastEntryCount = currentCount;
			filterDirty = false;
			refreshLogDisplay();
		}
	}
}

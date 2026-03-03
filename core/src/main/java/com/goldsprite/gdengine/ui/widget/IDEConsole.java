package com.goldsprite.gdengine.ui.widget;

import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.goldsprite.gdengine.log.DLog;
import com.goldsprite.gdengine.log.LogEntry;
import com.goldsprite.gdengine.log.LogPanel;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

/**
 * IDE 风格可折叠日志面板。
 * <p>
 * [Phase4] 内部使用 {@link LogPanel} 替代旧的手动渲染逻辑，
 * 保留折叠/展开外壳行为。
 */
public class IDEConsole extends VisTable {

	private boolean expanded = false;
	private final LogPanel logPanel;
	private final VisLabel lastLogLabel;
	private final VisTextButton toggleBtn;

	private final float COLLAPSED_HEIGHT = 35f;
	private final float CONTENT_HEIGHT = 200f; // 内容区展开高度

	public IDEConsole() {
		setBackground("window-bg");

		// 1. 内容区: LogPanel (放在第一行, 默认隐藏)
		logPanel = new LogPanel();
		add(logPanel).growX().height(0).row();

		// 2. 底部栏 (放在第二行, 常驻)
		VisTable header = new VisTable();

		lastLogLabel = new VisLabel("Ready");
		lastLogLabel.setColor(Color.GRAY);
		lastLogLabel.setEllipsis(true);

		toggleBtn = new VisTextButton("▲");
		toggleBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				toggle();
			}
		});

		header.add(lastLogLabel).padLeft(20).expandX().fillX().minWidth(0).padRight(5);
		float size = 40;
		header.add(toggleBtn).width(size).height(size).padRight(10);

		add(header).padBottom(10).growX().height(COLLAPSED_HEIGHT);

		setExpanded(false);
	}

	private void toggle() {
		setExpanded(!expanded);
	}

	public void setExpanded(boolean expand) {
		this.expanded = expand;
		toggleBtn.setText(expand ? "▼" : "▲");

		Cell<?> panelCell = getCell(logPanel);
		if (expand) {
			panelCell.height(CONTENT_HEIGHT);
			logPanel.setVisible(true);
		} else {
			panelCell.height(0);
			logPanel.setVisible(false);
		}

		invalidateHierarchy();
	}

	/**
	 * 获取内部 LogPanel 实例 (可用于配置 tag 过滤等)。
	 */
	public LogPanel getLogPanel() {
		return logPanel;
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		// 更新末行预览
		updateLastLogPreview();
	}

	/** 更新底部栏的最新日志预览 */
	private void updateLastLogPreview() {
		List<LogEntry> entries = DLog.getLogEntries();
		if (entries.isEmpty()) {
			lastLogLabel.setText("No logs.");
			return;
		}
		LogEntry last = entries.get(entries.size() - 1);
		String preview = last.message.split("\n")[0]; // 取第一行
		lastLogLabel.setText("[" + last.tag + "] " + preview);
	}
}

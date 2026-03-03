package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import java.util.List;
import com.badlogic.gdx.utils.Align;
import com.goldsprite.gdengine.log.DLog;

public class IDEConsole extends VisTable {

	private boolean expanded = false;
	private final VisLabel logContent;
	private final VisLabel lastLogLabel;
	private final HoverFocusScrollPane scrollPane;
	private final VisTextButton toggleBtn;

	private final float COLLAPSED_HEIGHT = 35f;
	private final float CONTENT_HEIGHT = 200f; // 内容区展开高度

	public IDEConsole() {
		setBackground("window-bg");

		// 1. 内容区 (放在第一行，默认隐藏)
		logContent = new VisLabel();
		logContent.setAlignment(Align.topLeft);
		logContent.setWrap(true);

		scrollPane = new HoverFocusScrollPane(logContent);
		scrollPane.setFadeScrollBars(false);
		scrollPane.setScrollingDisabled(true, false);

		// 先添加 ScrollPane，初始高度为 0
		add(scrollPane).growX().height(0).row();

		// 2. 顶部/底部栏 (放在第二行，常驻)
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

		VisTextButton clearBtn = new VisTextButton("Clear");
		clearBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				DLog.getLogs().clear();
				updateLogText();
			}
		});

		header.add(lastLogLabel).padLeft(20).expandX().fillX().minWidth(0).padRight(5);
		float size = 40;
		header.add(toggleBtn).width(size).height(size).padRight(10);
		header.add(clearBtn).height(size).padRight(20);

		add(header).padBottom(10).growX().height(COLLAPSED_HEIGHT);

		setExpanded(false);
	}

	private void toggle() {
		setExpanded(!expanded);
	}

	public void setExpanded(boolean expand) {
		this.expanded = expand;
		toggleBtn.setText(expand ? "▼" : "▲");

		// 控制内容区高度
		Cell<?> scrollCell = getCell(scrollPane);
		if (expand) {
			scrollCell.height(CONTENT_HEIGHT);
			scrollPane.setVisible(true);
		} else {
			scrollCell.height(0);
			scrollPane.setVisible(false);
		}

		invalidateHierarchy(); // 触发重新布局
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		updateLogText();
	}

	private void updateLogText() {
		List<String> logs = DLog.getLogs();
		if (logs.isEmpty()) {
			lastLogLabel.setText("No logs.");
			logContent.setText("");
			return;
		}

		String last = logs.get(logs.size() - 1);
		last = last.split("\n")[0]; // 换行截断
		lastLogLabel.setText(last);

		if (expanded) {
			StringBuilder sb = new StringBuilder();
			int start = Math.max(0, logs.size() - 50);
			for (int i = start; i < logs.size(); i++) {
				sb.append(logs.get(i)).append("\n");
			}
			String newText = sb.toString();
			// 简单防抖，避免每帧 setText 造成 layout 开销
			if (!newText.equals(logContent.getText().toString())) {
				logContent.setText(newText);
				scrollPane.layout();
				scrollPane.setScrollY(scrollPane.getMaxY());
			}
		}
	}
}

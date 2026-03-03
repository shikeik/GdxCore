package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.goldsprite.gdengine.assets.FontUtils;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;

public class BioCodeEditor extends VisTable {
	private CodeTextArea textArea;
	private VisLabel lineNumbers;
	private HoverFocusScrollPane scrollPane;
	private VisTable popupMenu;
	private VisTable contentTable;

	private boolean isSelectionMode = false;
	private boolean hasLongPressed = false;
	private float autoScrollSpeed = 0f;

	private Runnable onSaveCallback;

	// [新增] 内容变更回调
	private Runnable onTextChangedCallback;

	public BioCodeEditor() {
		this(1.3f);
	}
	public BioCodeEditor(float baseFntScale) {
		super();
		build(baseFntScale);
	}

	public void setOnSave(Runnable onSave) {
		this.onSaveCallback = onSave;
	}

	// [新增] 设置监听器
	public void setOnTextChanged(Runnable onChange) {
		this.onTextChangedCallback = onChange;
	}

	private void build(float baseFntScale) {
		setBackground("window-bg");

		VisTextField.VisTextFieldStyle baseStyle = VisUI.getSkin().get(VisTextField.VisTextFieldStyle.class);
		VisTextField.VisTextFieldStyle customStyle = new VisTextField.VisTextFieldStyle(baseStyle);

		// --- 字体配置 ---
		customStyle.font = FontUtils.generateAutoClarity(35);
		customStyle.font.getData().markupEnabled = true;
		customStyle.font.getData().setScale(customStyle.font.getData().scaleX * 0.5f * baseFntScale);

		// [修复2] 赋予换行符宽度，让空行选区可见
		BitmapFont.Glyph spaceGlyph = customStyle.font.getData().getGlyph('\n');
		if (spaceGlyph != null) {
			// 给 \n 设置和空格一样的宽度
			BitmapFont.Glyph newlineGlyph = customStyle.font.getData().getGlyph('\n');
			if (newlineGlyph == null) {
				// 如果没有，手动创建一个
				customStyle.font.getData().setGlyph('\n', spaceGlyph);
				newlineGlyph = customStyle.font.getData().getGlyph('\n');
			}
			newlineGlyph.xadvance = spaceGlyph.xadvance;
			newlineGlyph.width = spaceGlyph.width;
		}

		// --- 光标与选区 ---
		// 光标：亮黄，加粗 (3px)
		Pixmap pCursor = new Pixmap(3, (int)customStyle.font.getLineHeight(), Pixmap.Format.RGBA8888);
		pCursor.setColor(Color.YELLOW);
		pCursor.fill();
		customStyle.cursor = new TextureRegionDrawable(new Texture(pCursor));
		pCursor.dispose(); // [Fix] Dispose Pixmap after creating Texture

		// 选区：半透明蓝
		Pixmap pSelection = createColorPixmap(new Color(0, 0.5f, 1f, 0.4f));
		customStyle.selection = new TextureRegionDrawable(new Texture(pSelection));
		pSelection.dispose(); // [Fix] Dispose Pixmap

		// --- 组件初始化 ---
		textArea = new CodeTextArea("");
		textArea.setStyle(customStyle);
		textArea.setFocusTraversal(false); // 允许 Tab

		lineNumbers = new VisLabel("1");
		lineNumbers.setStyle(new VisLabel.LabelStyle(customStyle.font, Color.GRAY));
		lineNumbers.setAlignment(Align.topRight);

		// --- 布局 ---
		contentTable = new VisTable();
		// [Fix] 给行号左边加一点点 Padding，防止贴死在边缘
		// 之前的代码: contentTable.add(lineNumbers).top().right().padRight(5).padTop(3);
		// 修改后:
		contentTable.add(lineNumbers).top().right().padLeft(5).padRight(10).padTop(3);

		contentTable.add(textArea).grow().top();

		scrollPane = new HoverFocusScrollPane(contentTable);
		scrollPane.setScrollBarPositions(false, false);
		scrollPane.setFadeScrollBars(false);
		scrollPane.setFlickScroll(false);
		scrollPane.setOverscroll(false, false);
		scrollPane.setCancelTouchFocus(false);

		createPopupMenu();
		setupInteraction();

		// [Fix] 确保 ScrollPane 填满
		this.add(scrollPane).grow();
	}

	private Pixmap createColorPixmap(Color c) {
		Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		p.setColor(c); p.fill(); return p;
	}

	private void setupInteraction() {
		// [修复3] 监听变化，自动滚动
		textArea.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				updateLayoutAndLineNumbers();
				scrollToCursor(); // 只要内容变了（换行、删除），就尝试跟随光标

				// [新增] 通知外部内容变了
				if (onTextChangedCallback != null) {
					onTextChangedCallback.run();
				}
			}
		});

		// 监听光标移动（方向键）
		textArea.addListener(new InputListener() {
			@Override
			public boolean keyDown(InputEvent event, int keycode) {
				// Ctrl + S
				if (keycode == Input.Keys.S && isCtrlPressed()) {
					if (onSaveCallback != null) {
						onSaveCallback.run();
						textArea.addAction(Actions.sequence(
							Actions.color(Color.GREEN, 0.1f),
							Actions.color(Color.WHITE, 0.3f)
						));
					}
					return true;
				}
				// 方向键移动光标时自动滚动
				if (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN ||
					keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT ||
					keycode == Input.Keys.ENTER || keycode == Input.Keys.BACKSPACE) {

					Gdx.app.postRunnable(() -> scrollToCursor());
				}
				return false;
			}

			private boolean isCtrlPressed() {
				return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
			}
		});

		// 手势与菜单逻辑
		textArea.addListener(new ActorGestureListener(20, 0.4f, 0.5f, 0.15f) {
			@Override
			public boolean longPress(Actor actor, float x, float y) {
				hasLongPressed = true;
				isSelectionMode = true;
				scrollPane.setFlickScroll(false);
				Gdx.input.vibrate(50);
				return true;
			}
		});

		textArea.addListener(new InputListener() {
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				hasLongPressed = false;
				if (popupMenu.isVisible()) hidePopupMenu();
				return true;
			}

			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer) {
				if (isSelectionMode) checkAutoScroll(event.getStageY());
			}

			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				autoScrollSpeed = 0f;
				if (hasLongPressed) {
					showPopupMenu(x, y);
					hasLongPressed = false;
				}
				// 拖拽结束后，也对齐一次光标
				if (isSelectionMode) scrollToCursor();
			}
		});
	}

	private void updateLayoutAndLineNumbers() {
		int lines = textArea.getLines();
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= lines; i++) {
			sb.append(i).append('\n');
		}
		sb.append("~");

		String newNums = sb.toString();
		if (!newNums.equals(lineNumbers.getText().toString())) {
			lineNumbers.setText(newNums);
		}

		// 强制触发布局更新，解决截断问题
		Gdx.app.postRunnable(() -> {
			textArea.invalidateHierarchy();
			contentTable.invalidateHierarchy();
			scrollPane.layout();
		});
	}

	// [修复3] 光标自动跟随滚动
	private void scrollToCursor() {
		float lineHeight = textArea.getStyle().font.getLineHeight();
		float cursorY = (textArea.getLines() - 1 - textArea.getCursorLine()) * lineHeight;

		// 增加一点 Padding，不要贴边
		float pad = lineHeight * 2;

		// 让 ScrollPane 滚动到光标位置
		// scrollTo(x, y, width, height) 是让这块区域可见
		// 注意 Y 轴：Scene2D 内部是 Y-up，但 TextArea 内部布局是 Top-Down 排列文本
		// CodeTextArea 计算高度是 lines * lineHeight。
		// 第 0 行 (顶端) 的 Y 是 Height - lineHeight。
		// 第 N 行 的 Y 是 Height - (N+1) * lineHeight。

		// 计算光标在 TextArea 内部的 Local Y
		float widgetHeight = textArea.getHeight();
		float localCursorY = widgetHeight - (textArea.getCursorLine() + 1) * lineHeight;

		// 调用 ScrollPane 确保这块区域可见
		scrollPane.scrollTo(0, localCursorY, 0, lineHeight);
	}

	public void setText(String text) {
		textArea.setText(text);

		// [修复1] 切换文件时，立即刷新布局和行号
		updateLayoutAndLineNumbers();

		// 重置滚动条到顶部
		Gdx.app.postRunnable(() -> {
			scrollPane.setScrollY(0);
			scrollPane.updateVisualScroll();
			// 再次刷新确保万无一失
			updateLayoutAndLineNumbers();
		});
	}

	public String getText() {
		return textArea.getText();
	}

	// --- Menu 和 AutoScroll Helpers (Same as before) ---

	private void createPopupMenu() {
		popupMenu = new VisTable();
		try { popupMenu.setBackground(VisUI.getSkin().getDrawable("window-bg")); }
		catch (Exception e) { popupMenu.setBackground(VisUI.getSkin().getDrawable("button")); }

		String btnStyle = "default";

		VisTextButton btnCopy = new VisTextButton("复制", btnStyle);
		btnCopy.addListener(new ClickListener() { @Override public void clicked(InputEvent event, float x, float y) { textArea.copy(); hidePopupMenu(); }});

		VisTextButton btnPaste = new VisTextButton("粘贴", btnStyle);
		btnPaste.addListener(new ClickListener() {
			@Override public void clicked(InputEvent event, float x, float y) {
				String content = Gdx.app.getClipboard().getContents();
				if (content != null) {
					// 手动拼接
					String oldText = textArea.getText();
					int cursor = textArea.getCursorPosition();
					int selectionStart = textArea.getSelectionStart();
					int start = Math.min(cursor, selectionStart);
					int end = Math.max(cursor, selectionStart);

					StringBuilder sb = new StringBuilder(oldText);
					if (end > start) {
						sb.delete(start, end);
						cursor = start;
					}
					sb.insert(cursor, content);
					textArea.setText(sb.toString());
					textArea.setCursorPosition(cursor + content.length());
					textArea.clearSelection();
					updateLayoutAndLineNumbers(); // 粘贴后必须刷新布局
				}
				hidePopupMenu();
			}
		});

		VisTextButton btnCut = new VisTextButton("剪切", btnStyle);
		btnCut.addListener(new ClickListener() {
			@Override public void clicked(InputEvent event, float x, float y) {
				textArea.cut();
				updateLayoutAndLineNumbers(); // 粘贴后必须刷新布局
				hidePopupMenu();
			}
		});

		VisTextButton btnAll = new VisTextButton("全选", btnStyle);
		btnAll.addListener(new ClickListener() { @Override public void clicked(InputEvent event, float x, float y) { textArea.selectAll(); hidePopupMenu(); isSelectionMode = true; }});

		popupMenu.add(btnCopy).pad(5);
		popupMenu.add(btnCut).pad(5); // Add here
		popupMenu.add(btnPaste).pad(5);
		popupMenu.add(btnAll).pad(5);
		popupMenu.pack();
		popupMenu.setVisible(false);
	}

	private void showPopupMenu(float x, float y) {
		if (getStage() == null) return;
		if (popupMenu.getStage() == null) getStage().addActor(popupMenu);
		popupMenu.toFront(); popupMenu.setVisible(true);
		Vector2 stagePos = textArea.localToStageCoordinates(new Vector2(x, y));
		float menuX = stagePos.x - popupMenu.getWidth() / 2;
		float menuY = stagePos.y + 50;
		popupMenu.setPosition(menuX, menuY);
	}

	private void hidePopupMenu() {
		if (popupMenu != null) popupMenu.setVisible(false);
	}

	private void checkAutoScroll(float stageY) {
		float topEdge = scrollPane.localToStageCoordinates(new Vector2(0, scrollPane.getHeight())).y;
		float bottomEdge = scrollPane.localToStageCoordinates(new Vector2(0, 0)).y;
		float threshold = 60f; float maxSpeed = 15f;
		if (stageY > topEdge - threshold) {
			float ratio = (stageY - (topEdge - threshold)) / threshold;
			autoScrollSpeed = -maxSpeed * ratio;
		} else if (stageY < bottomEdge + threshold) {
			float ratio = ((bottomEdge + threshold) - stageY) / threshold;
			autoScrollSpeed = maxSpeed * ratio;
		} else {
			autoScrollSpeed = 0f;
		}
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		if (isSelectionMode && Math.abs(autoScrollSpeed) > 0.1f) {
			float currentY = scrollPane.getScrollY();
			float maxY = scrollPane.getMaxY();
			float nextY = currentY + autoScrollSpeed;
			if (nextY < 0) nextY = 0; if (nextY > maxY) nextY = maxY;
			scrollPane.setScrollY(nextY);
		}
	}

	private class CodeTextArea extends VisTextArea {
		public CodeTextArea(String text) { super(text); }
		@Override
		public float getPrefHeight() {
			float rows = Math.max(getLines(), 15);
			return rows * getStyle().font.getLineHeight() + 20;
		}
	}
}

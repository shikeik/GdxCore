package com.goldsprite.gdengine.ui.widget.single;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.goldsprite.gdengine.assets.FontUtils;
import com.goldsprite.gdengine.log.DLog;

public class ToastUI extends Label {

	private static ToastUI instance;
	private static NinePatchDrawable bgDrawable;

	private ToastUI() {
		super("", createStyle());
		instance = this;
	}

	private static Label.LabelStyle createStyle() {
		Label.LabelStyle style = new Label.LabelStyle(FontUtils.generate(24), Color.WHITE);

		if (bgDrawable == null) {
			// Generate a small texture for 9-patch
			// 32x32 size:
			// Corners: 8px radius
			// Center: 16x16 stretchable
			int size = 32;
			int r = 10; // corner radius

			Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
			pixmap.setColor(0, 0, 0, 0);
			pixmap.fill();
			pixmap.setColor(0, 0, 0, 0.7f); // Semi-transparent black

			// Draw filled circles at corners
			pixmap.fillCircle(r, r, r);
			pixmap.fillCircle(size-1-r, r, r);
			pixmap.fillCircle(r, size-1-r, r);
			pixmap.fillCircle(size-1-r, size-1-r, r);

			// Fill connecting rectangles
			pixmap.fillRectangle(r, 0, size - 2*r, size); // Vertical center strip? No, Horizontal
			pixmap.fillRectangle(0, r, size, size - 2*r); // Vertical

			Texture tex = new Texture(pixmap);
			pixmap.dispose();

			// Create NinePatch: left, right, top, bottom splits
			// size=32, r=10.
			// split at 10, 10 from sides.
			// middle = 32 - 20 = 12px stretchable
			NinePatch patch = new NinePatch(tex, r, r, r, r);
			bgDrawable = new NinePatchDrawable(patch);
		}

		style.background = bgDrawable;
		return style;
	}

	public static ToastUI inst() {
		if (instance == null) new ToastUI();
		return instance;
	}

	public void show(String msg) {
		Gdx.app.postRunnable(()->{
			DLog.logT("ToastUI", msg);
			setText(msg);
			pack(); // 重新计算尺寸

			if (getStage() != null) {
				// 居中显示在屏幕下方 20% 处
				float stageW = getStage().getWidth();
				float stageH = getStage().getHeight();
				setPosition((stageW - getWidth()) / 2, stageH * 0.025f);
			}

			clearActions();

			// Reset state
			getColor().a = 0;

			// H5: transition: opacity 0.5s.
			// Logic: Set text -> Opacity 1 (fast) -> Wait -> Opacity 0 (0.5s)
			addAction(Actions.sequence(
				Actions.fadeIn(0.05f),
				Actions.delay(0.5f),
				Actions.fadeOut(0.5f)
			));
		});
	}
}

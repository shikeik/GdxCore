package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.goldsprite.gdengine.screens.GScreen;
import com.goldsprite.gdengine.screens.ScreenManager;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.VisDialog;
/**
 * 基础对话框，继承自 VisDialog，自动设置为模态并添加关闭按钮。
 * 可通过 autoPack 控制是否在显示时自动调用 pack()。
 * 对话框的生命周期由 ScreenManager 统一管理，支持淡入淡出动画。
 */
public class BaseDialog extends VisDialog {
	public boolean autoPack = false;
	private boolean spacingInitialized = false;

	public BaseDialog(String title) {
		super(title);
		getTitleLabel().setFontScale(0.95f); // 字体太大修正
		setModal(true);
		addCloseButton();
	}

	// 方便子类显示
	@Override
	public VisDialog show(Stage stage) {
		if(!spacingInitialized) {
			TableUtils.setSpacingDefaults(this);
			spacingInitialized = true;
		}
		if(autoPack) pack();
		centerWindow();
		stage.addActor(this.fadeIn());
		
		// 注册到当前屏幕的 Dialog 栈
		GScreen curScreen = ScreenManager.getInstance().getCurScreen();
		if (curScreen != null) {
			curScreen.pushDialog(this);
		}
		
		return this;
	}
	
	@Override
	public void hide() {
		// 关闭时自动应用淡出动画，动画完成后自动 remove()
		this.fadeOut();
	}
	
	@Override
	public boolean remove() {
		// 从当前屏幕的 Dialog 栈移除
		GScreen curScreen = ScreenManager.getInstance().getCurScreen();
		if (curScreen != null) {
			curScreen.popDialog(this);
		}
		return super.remove();
	}
}

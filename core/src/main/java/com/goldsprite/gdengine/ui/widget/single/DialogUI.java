package com.goldsprite.gdengine.ui.widget.single;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.goldsprite.gdengine.screens.ScreenManager;
import com.goldsprite.gdengine.ui.widget.BaseDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;

/**
 * 全局通用弹窗工具类 (纯静态方法)
 * 提供简易的弹窗创建方法，无需管理实例。
 */
public class DialogUI {
	
	private DialogUI() {} // 禁止实例化

	// --- 便捷方法 (自动获取当前 Stage) ---

	public static void show(String title, String message) {
		show(getCurrentStage(), title, message, null);
	}

	public static void show(String title, String message, Runnable onConfirm) {
		show(getCurrentStage(), title, message, onConfirm);
	}
	
	/**
	 * 显示自定义内容弹窗 (自动获取 Stage)
	 */
	public static BaseDialog showCustom(String title, Table content, Runnable onConfirm) {
		return showCustom(getCurrentStage(), title, content, "确定", onConfirm, "取消", null);
	}

	// --- 核心方法 ---

	/**
	 * 显示简单的提示框 (只有确定按钮)
	 */
	public static BaseDialog show(Stage stage, String title, String message, Runnable onConfirm) {
		return show(stage, title, message, "确定", onConfirm, null, null);
	}

	/**
	 * 显示确认框 (确定/取消)
	 */
	public static BaseDialog confirm(Stage stage, String title, String message, Runnable onConfirm) {
		return show(stage, title, message, "确定", onConfirm, "取消", null);
	}
	
	/**
	 * 显示确认框 (确定/取消) - 自动获取 Stage
	 */
	public static BaseDialog confirm(String title, String message, Runnable onConfirm) {
		return confirm(getCurrentStage(), title, message, onConfirm);
	}

	/**
	 * 显示文本消息弹窗
	 */
	public static BaseDialog show(Stage stage, String title, String message, 
									String posText, Runnable onPos, 
									String negText, Runnable onNeg) {
		// 构造简单的文本内容 Table
		VisLabel msgLabel = new VisLabel(message);
		msgLabel.setAlignment(Align.center);
		msgLabel.setWrap(true);
		
		Table content = new Table();
		content.add(msgLabel).width(400).pad(20).row();
		
		return showCustom(stage, title, content, posText, onPos, negText, onNeg);
	}

	/**
	 * 完全自定义弹窗 (核心实现)
	 * @param stage 目标舞台
	 * @param title 标题
	 * @param content 自定义内容 Table
	 * @param posText 确定按钮文本 (传 null 则不显示)
	 * @param onPos 确定回调
	 * @param negText 取消按钮文本 (传 null 则不显示)
	 * @param onNeg 取消回调
	 */
	public static BaseDialog showCustom(Stage stage, String title, Table content, 
									String posText, Runnable onPos, 
									String negText, Runnable onNeg) {
		if (stage == null) return null;

		BaseDialog dialog = new BaseDialog(title);
		dialog.setModal(true);
		dialog.setMovable(true);
		
		// 内容
		if (content != null) {
			dialog.getContentTable().add(content).row();
		}
		
		// 按钮
		if (posText != null) {
			VisTextButton posBtn = new VisTextButton(posText);
			posBtn.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					dialog.fadeOut();
					if (onPos != null) onPos.run();
				}
			});
			dialog.getButtonsTable().add(posBtn).pad(10).width(80);
		}
		
		if (negText != null) {
			VisTextButton negBtn = new VisTextButton(negText);
			negBtn.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					dialog.fadeOut();
					if (onNeg != null) onNeg.run();
				}
			});
			dialog.getButtonsTable().add(negBtn).pad(10).width(80);
		}
		
		// 如果没有按钮，默认加一个关闭按钮？BaseDialog 已经有右上角关闭了。
		// 但通常内容弹窗至少需要一个确定。
		if (posText == null && negText == null) {
			 VisTextButton okBtn = new VisTextButton("确定");
			 okBtn.addListener(new ClickListener() {
				 @Override
				 public void clicked(InputEvent event, float x, float y) {
					 dialog.fadeOut();
				 }
			 });
			 dialog.getButtonsTable().add(okBtn).pad(10).width(80);
		}
		
		dialog.pack();
		dialog.centerWindow();
		dialog.show(stage);
		
		return dialog;
	}
	
	private static Stage getCurrentStage() {
		if (ScreenManager.getInstance().getCurScreen() != null) {
			return ScreenManager.getInstance().getCurScreen().getStage();
		}
		return null;
	}
}

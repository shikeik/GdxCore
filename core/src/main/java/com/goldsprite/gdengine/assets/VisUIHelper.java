package com.goldsprite.gdengine.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ObjectMap;
import com.goldsprite.gdengine.PlatformImpl;
import com.goldsprite.gdengine.log.DLog;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;

public class VisUIHelper {
	public static BitmapFont cnFont;
	public static BitmapFont cnFontSmall;
	private static int separatorThickness = 5;

	/**
	 * 初始化 UI (VisUI + Game Skin) 并注入中文字体
	 */
	public static void loadWithChineseFont() {
		init();
	}

	public static void init() {
		if (VisUI.isLoaded()) return;

		try {
			DLog.log("开始初始化 UI 系统...");

			int fntSize = 16, smFntSize = 12;
			float scl = 1, smScl = 1;
			// 1. 准备字体
			// 大小设为 24，清晰度较高
			cnFont = FontUtils.generateAutoClarity(fntSize);
			cnFont.getData().setScale(scl); // 重置缩放，根据需要调整
			// 小字体
			cnFontSmall = FontUtils.generate(smFntSize);
			cnFontSmall.getData().setScale(smScl);

			// 预注入字体 (Neutralizer 使用 font 和 title)
			// [Fix] 生成新的字体实例以避免 Skin.dispose() 时的资源释放冲突
			BitmapFont gameCnFont = FontUtils.generateAutoClarity(fntSize);
			gameCnFont.getData().setScale(scl);
			BitmapFont gameCnFontSmall = FontUtils.generate(smFntSize);
			gameCnFontSmall.getData().setScale(smScl);


			// 2. 加载主皮肤 (Shimmer UI)
			Skin mainSkin = new Skin();
			// 添加 TenPatch 支持 (如果项目中使用了 com.ray3k.tenpatch)
			try {
				// 尝试通过反射注册，避免编译错误如果依赖尚未刷新
				Class<?> tenPatchClass = Class.forName("TenPatchDrawable");
				// Skin 不需要显式注册类，只要 JSON 里写了全限定名且 Classpath 里有即可
				DLog.log("检测到 TenPatch 库，支持动态切片皮肤。");
			} catch (ClassNotFoundException e) {
				DLog.log("未检测到 TenPatch 库...");
			}

			// 预注入字体 (Shimmer 使用 font 和 small)
			mainSkin.add("font", cnFont);
			mainSkin.add("small", cnFontSmall);
			mainSkin.add("default-font", cnFont); // 备用

			TextureAtlas mainAtlas = new TextureAtlas(Gdx.files.internal("ui_skins/Neutralizer_UI_Skin/neutralizerui/neutralizer-ui.atlas"));
			mainSkin.addRegions(mainAtlas);
			mainSkin.load(Gdx.files.internal("ui_skins/Neutralizer_UI_Skin/neutralizerui/neutralizer-ui.json"));

//			 // 暂时加载默认, 因为shimmer还没做好
//			mainSkin.addRegions(new TextureAtlas("ui_skins/visui/x1/uiskin.atlas"));
//			mainSkin.load(Gdx.files.internal("ui_skins/visui/x1/uiskin.json"));

			// 映射 VisUI 样式 (因为 Shimmer 是标准皮肤)
//			mapStandardStylesToVisStyles(mainSkin);

			// 加载 VisUI
			VisUI.load(mainSkin);
			DLog.log("VisUI (Shimmer) 加载成功。");

			// 4. 修复一些细节
			fixHandleSize();
			addGlobalAssetsStyles(mainSkin, cnFont, cnFontSmall);

			// [新增] 修复 Window 样式
			if (!mainSkin.has("window-noborder", Drawable.class)) {
				mainSkin.add("window-noborder", new TextureRegionDrawable(ColorTextureUtils.createColorTexture(new Color(0,0,0,0.5f))));
			}
			if (!mainSkin.has("window-bg", Drawable.class)) {
				mainSkin.add("window-bg", "window");
			}

		} catch (Exception e) {
			e.printStackTrace();
			DLog.log("UI 初始化失败: " + e.getMessage());
			// 失败回退
			if (!VisUI.isLoaded()) VisUI.load();
		}
	}

	private static void mapStandardStylesToVisStyles(Skin skin) {
		// VisUI 组件通常需要特定的 Vis*Style。如果皮肤里没有，我们需要从标准样式复制。
		// 1. TextButton -> VisTextButton
		ObjectMap<String, TextButton.TextButtonStyle> textButtonStyles = skin.getAll(TextButton.TextButtonStyle.class);
		for (String name : textButtonStyles.keys()) {
			TextButton.TextButtonStyle original = textButtonStyles.get(name);
			VisTextButton.VisTextButtonStyle visStyle = new VisTextButton.VisTextButtonStyle();
			visStyle.up = original.up;
			visStyle.down = original.down;
			visStyle.over = original.over;
			visStyle.checked = original.checked;
			visStyle.checkedOver = original.checkedOver;
			visStyle.disabled = original.disabled;
			visStyle.font = original.font;
			visStyle.fontColor = original.fontColor;
			visStyle.downFontColor = original.downFontColor;
			visStyle.overFontColor = original.overFontColor;
			visStyle.checkedFontColor = original.checkedFontColor;
			visStyle.checkedOverFontColor = original.checkedOverFontColor;
			visStyle.disabledFontColor = original.disabledFontColor;
			// Vis 特有字段，默认给个 null 或者 reusing up
			// [Fix] 不要将背景赋值给 focusBorder，否则会导致遮挡文字
			visStyle.focusBorder = null;
			skin.add(name, visStyle);
		}

		// [新增] 确保 toggle 样式存在
		if (skin.has("default", VisTextButton.VisTextButtonStyle.class) && !skin.has("toggle", VisTextButton.VisTextButtonStyle.class)) {
			VisTextButton.VisTextButtonStyle defaultStyle = skin.get("default", VisTextButton.VisTextButtonStyle.class);
			VisTextButton.VisTextButtonStyle toggleStyle = new VisTextButton.VisTextButtonStyle(defaultStyle);
			// 简单的 toggle 模拟：checked 状态使用 down 状态的 drawable
			toggleStyle.checked = defaultStyle.down;
			skin.add("toggle", toggleStyle);
		}

		// [新增] 确保 TextButton.TextButtonStyle 也有 toggle 样式
		if (skin.has("default", TextButton.TextButtonStyle.class) && !skin.has("toggle", TextButton.TextButtonStyle.class)) {
			TextButton.TextButtonStyle defaultStyle = skin.get("default", TextButton.TextButtonStyle.class);
			TextButton.TextButtonStyle toggleStyle = new TextButton.TextButtonStyle(defaultStyle);
			toggleStyle.checked = defaultStyle.down;
			skin.add("toggle", toggleStyle);
		}

		// 2. CheckBox -> VisCheckBox
		ObjectMap<String, CheckBox.CheckBoxStyle> checkBoxStyles = skin.getAll(CheckBox.CheckBoxStyle.class);
		for (String name : checkBoxStyles.keys()) {
			CheckBox.CheckBoxStyle original = checkBoxStyles.get(name);
			VisCheckBox.VisCheckBoxStyle visStyle = new VisCheckBox.VisCheckBoxStyle();
			visStyle.font = original.font;
			visStyle.fontColor = original.fontColor;
			visStyle.disabledFontColor = original.disabledFontColor;
			// Vis 特有
			visStyle.focusBorder = null;
			visStyle.checkBackground = original.checkboxOff; // Mapping
			visStyle.checkBackgroundOver = original.checkboxOver; // Mapping
			visStyle.checkBackgroundDown = original.checkboxOff;
			visStyle.tick = original.checkboxOn; // Mapping
			skin.add(name, visStyle);
		}

		// [新增] 确保 radio 样式存在
		if (skin.has("default", VisCheckBox.VisCheckBoxStyle.class) && !skin.has("radio", VisCheckBox.VisCheckBoxStyle.class)) {
			// 如果没有 radio，直接复用 default (虽然图标可能不是圆的，但至少不崩)
			// TODO: 如果需要圆形单选框，需要专门的资源
			skin.add("radio", skin.get("default", VisCheckBox.VisCheckBoxStyle.class));
		}

		// [新增] 确保 CheckBox.CheckBoxStyle 也有 radio 样式 (VisUIDemoScreen 可能用到)
		if (skin.has("default", CheckBox.CheckBoxStyle.class) && !skin.has("radio", CheckBox.CheckBoxStyle.class)) {
			skin.add("radio", skin.get("default", CheckBox.CheckBoxStyle.class));
		}

		// 3. ImageButton -> VisImageButton
		ObjectMap<String, ImageButton.ImageButtonStyle> imageButtonStyles = skin.getAll(ImageButton.ImageButtonStyle.class);
		for (String name : imageButtonStyles.keys()) {
			ImageButton.ImageButtonStyle original = imageButtonStyles.get(name);
			VisImageButton.VisImageButtonStyle visStyle = new VisImageButton.VisImageButtonStyle();
			visStyle.up = original.up;
			visStyle.down = original.down;
			visStyle.over = original.over;
			visStyle.checked = original.checked;
			visStyle.checkedOver = original.checkedOver;
			visStyle.disabled = original.disabled;
			visStyle.imageUp = original.imageUp;
			visStyle.imageDown = original.imageDown;
			visStyle.imageOver = original.imageOver;
			visStyle.imageChecked = original.imageChecked;
			visStyle.imageCheckedOver = original.imageCheckedOver;
			visStyle.imageDisabled = original.imageDisabled;
			// [Fix] 不要将背景赋值给 focusBorder
			visStyle.focusBorder = null;
			skin.add(name, visStyle);
		}

		// 4. TextField -> VisTextField
		ObjectMap<String, TextField.TextFieldStyle> textFieldStyles = skin.getAll(TextField.TextFieldStyle.class);
		for (String name : textFieldStyles.keys()) {
			TextField.TextFieldStyle original = textFieldStyles.get(name);
			VisTextField.VisTextFieldStyle visStyle = new VisTextField.VisTextFieldStyle();
			visStyle.font = original.font;
			visStyle.fontColor = original.fontColor;
			visStyle.background = original.background;
			visStyle.cursor = original.cursor;
			visStyle.selection = original.selection;
			visStyle.messageFont = original.messageFont;
			visStyle.messageFontColor = original.messageFontColor;
			// Vis 特有
			visStyle.errorBorder = original.background; // Fallback
			// [Fix] 不要将背景赋值给 focusBorder
			visStyle.focusBorder = null;
			skin.add(name, visStyle);
		}

		// 5. Window -> VisWindow (其实 VisWindow 用的是 WindowStyle，但需要检查)
		// VisWindow uses WindowStyle, so no mapping needed if standard WindowStyle exists.

		// 6. SplitPane -> VisSplitPane
		ObjectMap<String, SplitPane.SplitPaneStyle> splitStyles = skin.getAll(SplitPane.SplitPaneStyle.class);
		for (String name : splitStyles.keys()) {
			SplitPane.SplitPaneStyle original = splitStyles.get(name);
			VisSplitPane.VisSplitPaneStyle visStyle = new VisSplitPane.VisSplitPaneStyle();
			visStyle.handle = original.handle;
			// VisSplitPaneStyle defines handleOver but standard doesn't
			visStyle.handleOver = original.handle;

			// VisUI expects specific names "default-vertical" and "default-horizontal"
			// Shimmer might only have "default-horizontal" and "default-vertical" or just "default"
			// We'll map whatever we find
			skin.add(name, visStyle);
		}

		// Ensure default-vertical/horizontal exist for VisSplitPane
		if (!skin.has("default-vertical", VisSplitPane.VisSplitPaneStyle.class) && skin.has("default-vertical", SplitPane.SplitPaneStyle.class)) {
			// Already handled by loop
		}

		// 7. ScrollPane -> VisScrollPane (Important: VisScrollPane uses "list" style by default)
		if (skin.has("default", ScrollPane.ScrollPaneStyle.class) && !skin.has("list", ScrollPane.ScrollPaneStyle.class)) {
			skin.add("list", skin.get("default", ScrollPane.ScrollPaneStyle.class));
		}
	}

	private static void addGlobalAssetsStyles(Skin skin, BitmapFont font, BitmapFont smallFont) {
		// [新增] 确保 window-bg 存在 (用于 DialogUI 和 VisUIDemoScreen)
		// 优先复用现有的窗口背景
		if (!skin.has("window-bg", Drawable.class)) {
			DLog.log("Injecting window-bg fallback for skin.");
			try {
				if (skin.has("window", Drawable.class)) {
					skin.add("window-bg", skin.getDrawable("window"));
				} else if (skin.has("dialog", Drawable.class)) {
					skin.add("window-bg", skin.getDrawable("dialog"));
				} else if (skin.has("default-window", Drawable.class)) {
					skin.add("window-bg", skin.getDrawable("default-window"));
				} else {
					// 最终兜底：生成纯色背景
					skin.add("window-bg", new TextureRegionDrawable(ColorTextureUtils.createColorTexture(new Color(0.1f, 0.1f, 0.1f, 0.9f))));
				}
			} catch (Exception e) {
				DLog.log("Failed to inject window-bg: " + e.getMessage());
			}
		}

		// [新增] 确保所有样式使用中文字体
		overwriteFonts(skin, font);

		// Logger Style
		Label.LabelStyle loggerLabelStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
		loggerLabelStyle.font = font;
		skin.add("loggerLabelStyle", loggerLabelStyle);

		// Title
		if (!skin.has("title", Label.LabelStyle.class)) {
			Label.LabelStyle titleLabelStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
			titleLabelStyle.font = font;
			skin.add("title", titleLabelStyle);
		} else {
			// No need to replace font, as it's already injected by name reference
		}

		// 确保 CheckBoxStyle 也有 radio 样式 (GameSkin 可能没有)
		if (skin.has("default", CheckBox.CheckBoxStyle.class) && !skin.has("radio", CheckBox.CheckBoxStyle.class)) {
			skin.add("radio", skin.get("default", CheckBox.CheckBoxStyle.class));
		}

		// 确保 TextButtonStyle 也有 toggle 样式 (GameSkin 可能没有)
		if (skin.has("default", TextButton.TextButtonStyle.class) && !skin.has("toggle", TextButton.TextButtonStyle.class)) {
			TextButton.TextButtonStyle defaultStyle = skin.get("default", TextButton.TextButtonStyle.class);
			TextButton.TextButtonStyle toggleStyle = new TextButton.TextButtonStyle(defaultStyle);
			toggleStyle.checked = defaultStyle.down;
			skin.add("toggle", toggleStyle);
		}

		// Subtitle
		if (!skin.has("subtitle", Label.LabelStyle.class)) {
			Label.LabelStyle subtitleLabelStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
			subtitleLabelStyle.font = smallFont;
			skin.add("subtitle", subtitleLabelStyle);
		}

		// No Background TextField
		if (skin.has("default", TextField.TextFieldStyle.class)) {
			TextField.TextFieldStyle textFieldStyle = skin.get(TextField.TextFieldStyle.class);
			TextField.TextFieldStyle noBackgroundTextFieldStyle = new TextField.TextFieldStyle(textFieldStyle);
			noBackgroundTextFieldStyle.background = null;
			skin.add("nobackground", noBackgroundTextFieldStyle);
		}

		// No Background ScrollPane
		if (skin.has("default", ScrollPane.ScrollPaneStyle.class)) {
			ScrollPane.ScrollPaneStyle defaultScrollStyle = skin.get(ScrollPane.ScrollPaneStyle.class);
			ScrollPane.ScrollPaneStyle noBackgroundScrollStyle = new ScrollPane.ScrollPaneStyle(defaultScrollStyle);
			noBackgroundScrollStyle.background = null;
			skin.add("nobackground", noBackgroundScrollStyle);
		}

		// [新增] 确保 VisImageButton 有 close-window 样式 (VisDialog 需要)
		if (!skin.has("close-window", VisImageButton.VisImageButtonStyle.class)) {
			VisImageButton.VisImageButtonStyle closeStyle = new VisImageButton.VisImageButtonStyle();

			// 不要从 default 复制背景，否则会导致按钮过宽 (9-patch background)
			// 只设置图标即可

			// 尝试寻找关闭图标
			Drawable icon = null;
			String[] iconNames = {"icon-close", "close", "title-close", "icon-cancel", "cancel", "icon_close", "window-close"};
			for (String name : iconNames) {
				if (skin.has(name, Drawable.class)) {
					icon = skin.getDrawable(name);
					break;
				}
			}

			// 如果没找到图标，生成一个更美观的 X 图标
			if (icon == null) {
				try {
					// Up state: Grey X, transparent bg
					icon = createCloseIcon(Color.LIGHT_GRAY, null);
					// Over state: White X, Red bg
					Drawable overIcon = createCloseIcon(Color.WHITE, new Color(0.8f, 0.2f, 0.2f, 0.5f));
					// Down state: White X, Darker Red bg
					Drawable downIcon = createCloseIcon(Color.WHITE, new Color(0.6f, 0.1f, 0.1f, 0.5f));

					closeStyle.imageUp = icon;
					closeStyle.imageOver = overIcon;
					closeStyle.imageDown = downIcon;
				} catch (Exception e) {
					// 极端情况忽略
				}
			} else {
				closeStyle.imageUp = icon;
			}

			skin.add("close-window", closeStyle);
		}

		// [新增] 确保 Separator$SeparatorStyle 存在 (SettingsDialog 可能用到)
		if (!skin.has("default", Separator.SeparatorStyle.class)) {
			// 如果有 Vis UI 的 separator 样式，尝试复用
			if (skin.has("default", Separator.SeparatorStyle.class)) {
				// 已经有了，不需要做任何事
			} else {
				// 创建一个简单的分隔线样式
				Separator.SeparatorStyle sepStyle = new Separator.SeparatorStyle();
				sepStyle.background = new TextureRegionDrawable(ColorTextureUtils.createColorTexture(Color.GRAY));
				sepStyle.thickness = separatorThickness;
				skin.add("default", sepStyle);
			}
		}
		if (!skin.has("vertical", Separator.SeparatorStyle.class)) {
			// 如果有 Vis UI 的 separator 样式，尝试复用
			if (skin.has("vertical", Separator.SeparatorStyle.class)) {
				// 已经有了，不需要做任何事
			} else {
				// 创建一个简单的分隔线样式
				Separator.SeparatorStyle sepStyle = new Separator.SeparatorStyle();
				sepStyle.background = new TextureRegionDrawable(ColorTextureUtils.createColorTexture(Color.GRAY));
				sepStyle.thickness = separatorThickness;
				skin.add("vertical", sepStyle);
			}
		}

		// 再次检查，防止 VisUI 内部组件引用失败
		// 注意：VisUI 的 Separator 可能使用 "menu" 或 "default"
		if (!skin.has("menu", Separator.SeparatorStyle.class) && skin.has("default", Separator.SeparatorStyle.class)) {
			skin.add("menu", skin.get("default", Separator.SeparatorStyle.class));
		}
	}

	private static void overwriteFonts(Skin skin, BitmapFont font) {
		// 遍历所有样式，强制替换字体
		// Label
		ObjectMap<String, Label.LabelStyle> labelStyles = skin.getAll(Label.LabelStyle.class);
		if (labelStyles != null) {
			for (Label.LabelStyle style : labelStyles.values()) {
				style.font = font;
			}
		}

		// TextButton
		ObjectMap<String, TextButton.TextButtonStyle> textButtonStyles = skin.getAll(TextButton.TextButtonStyle.class);
		if (textButtonStyles != null) {
			for (TextButton.TextButtonStyle style : textButtonStyles.values()) {
				style.font = font;
			}
		}

		// VisTextButton
		ObjectMap<String, VisTextButton.VisTextButtonStyle> visTextButtonStyles = skin.getAll(VisTextButton.VisTextButtonStyle.class);
		if (visTextButtonStyles != null) {
			for (VisTextButton.VisTextButtonStyle style : visTextButtonStyles.values()) {
				style.font = font;
			}
		}

		// CheckBox
		ObjectMap<String, CheckBox.CheckBoxStyle> checkBoxStyles = skin.getAll(CheckBox.CheckBoxStyle.class);
		if (checkBoxStyles != null) {
			for (CheckBox.CheckBoxStyle style : checkBoxStyles.values()) {
				style.font = font;
			}
		}

		// VisCheckBox
		ObjectMap<String, VisCheckBox.VisCheckBoxStyle> visCheckBoxStyles = skin.getAll(VisCheckBox.VisCheckBoxStyle.class);
		if (visCheckBoxStyles != null) {
			for (VisCheckBox.VisCheckBoxStyle style : visCheckBoxStyles.values()) {
				style.font = font;
			}
		}

		// TextField
		ObjectMap<String, TextField.TextFieldStyle> textFieldStyles = skin.getAll(TextField.TextFieldStyle.class);
		if (textFieldStyles != null) {
			for (TextField.TextFieldStyle style : textFieldStyles.values()) {
				style.font = font;
				style.messageFont = font;
			}
		}

		// VisTextField
		ObjectMap<String, VisTextField.VisTextFieldStyle> visTextFieldStyles = skin.getAll(VisTextField.VisTextFieldStyle.class);
		if (visTextFieldStyles != null) {
			for (VisTextField.VisTextFieldStyle style : visTextFieldStyles.values()) {
				style.font = font;
				style.messageFont = font;
			}
		}

		// List
		ObjectMap<String, List.ListStyle> listStyles = skin.getAll(List.ListStyle.class);
		if (listStyles != null) {
			for (List.ListStyle style : listStyles.values()) {
				style.font = font;
			}
		}

		// SelectBox
		ObjectMap<String, SelectBox.SelectBoxStyle> selectBoxStyles = skin.getAll(SelectBox.SelectBoxStyle.class);
		if (selectBoxStyles != null) {
			for (SelectBox.SelectBoxStyle style : selectBoxStyles.values()) {
				style.font = font;
				if (style.listStyle != null) {
					style.listStyle.font = font;
				}
			}
		}

		// Window
		ObjectMap<String, Window.WindowStyle> windowStyles = skin.getAll(Window.WindowStyle.class);
		if (windowStyles != null) {
			for (Window.WindowStyle style : windowStyles.values()) {
				style.titleFont = font;
			}
		}

		// VisImageTextButton
		ObjectMap<String, VisImageTextButton.VisImageTextButtonStyle> visImageTextButtonStyles = skin.getAll(VisImageTextButton.VisImageTextButtonStyle.class);
		if (visImageTextButtonStyles != null) {
			for (VisImageTextButton.VisImageTextButtonStyle style : visImageTextButtonStyles.values()) {
				style.font = font;
			}
		}

		// [新增] ImageTextButton (防止 VisTextButton 在某些情况下回退到这个样式，或者其他组件使用)
		ObjectMap<String, ImageTextButton.ImageTextButtonStyle> imageTextButtonStyles = skin.getAll(ImageTextButton.ImageTextButtonStyle.class);
		if (imageTextButtonStyles != null) {
			for (ImageTextButton.ImageTextButtonStyle style : imageTextButtonStyles.values()) {
				style.font = font;
			}
		}

		// [新增] TextTooltip
		ObjectMap<String, TextTooltip.TextTooltipStyle> textTooltipStyles = skin.getAll(TextTooltip.TextTooltipStyle.class);
		if (textTooltipStyles != null) {
			for (TextTooltip.TextTooltipStyle style : textTooltipStyles.values()) {
				if (style.label != null) {
					style.label.font = font;
				}
			}
		}

		// [新增] 注入全局焦点光标 (Focus Border)
		injectFocusBorders(skin);
	}

	private static void injectFocusBorders(Skin skin) {
		Drawable focusBorder = createFocusBorder();
		skin.add("focus-border", focusBorder);

		// VisTextButton
		ObjectMap<String, VisTextButton.VisTextButtonStyle> visTextButtonStyles = skin.getAll(VisTextButton.VisTextButtonStyle.class);
		if (visTextButtonStyles != null) {
			for (VisTextButton.VisTextButtonStyle style : visTextButtonStyles.values()) {
				style.focusBorder = focusBorder;
			}
		}

		// VisImageButton
		ObjectMap<String, VisImageButton.VisImageButtonStyle> visImageButtonStyles = skin.getAll(VisImageButton.VisImageButtonStyle.class);
		if (visImageButtonStyles != null) {
			for (VisImageButton.VisImageButtonStyle style : visImageButtonStyles.values()) {
				style.focusBorder = focusBorder;
			}
		}

		// VisImageTextButton
		ObjectMap<String, VisImageTextButton.VisImageTextButtonStyle> visImageTextButtonStyles = skin.getAll(VisImageTextButton.VisImageTextButtonStyle.class);
		if (visImageTextButtonStyles != null) {
			for (VisImageTextButton.VisImageTextButtonStyle style : visImageTextButtonStyles.values()) {
				style.focusBorder = focusBorder;
			}
		}

		// VisCheckBox
		ObjectMap<String, VisCheckBox.VisCheckBoxStyle> visCheckBoxStyles = skin.getAll(VisCheckBox.VisCheckBoxStyle.class);
		if (visCheckBoxStyles != null) {
			for (VisCheckBox.VisCheckBoxStyle style : visCheckBoxStyles.values()) {
				style.focusBorder = focusBorder;
			}
		}

		// VisTextField
		ObjectMap<String, VisTextField.VisTextFieldStyle> visTextFieldStyles = skin.getAll(VisTextField.VisTextFieldStyle.class);
		if (visTextFieldStyles != null) {
			for (VisTextField.VisTextFieldStyle style : visTextFieldStyles.values()) {
				style.focusBorder = focusBorder;
			}
		}
	}

	private static Drawable createFocusBorder() {
		int size = 9;
		int border = 2;
		Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		p.setColor(Color.CLEAR);
		p.fill();
		p.setColor(new Color(1f, 0.84f, 0f, 0.5f)); // Gold, semi-transparent

		// Draw border
		p.fillRectangle(0, 0, size, border); // Top
		p.fillRectangle(0, size - border, size, border); // Bottom
		p.fillRectangle(0, 0, border, size); // Left
		p.fillRectangle(size - border, 0, border, size); // Right

		Texture t = new Texture(p);
		p.dispose();

		return new NinePatchDrawable(new NinePatch(t, 3, 3, 3, 3));
	}

	private static Drawable createCloseIcon(Color xColor, Color bgColor) {
		int size = 20;
		Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);

		// Background
		if (bgColor != null) {
			p.setColor(bgColor);
			p.fillRectangle(2, 2, size-4, size-4); // slight padding
		}

		// X
		p.setColor(xColor);
		// Top-left to bottom-right
		p.drawLine(5, 5, 14, 14);
		p.drawLine(6, 5, 15, 14); // thicker
		// Bottom-left to top-right
		p.drawLine(5, 14, 14, 5);
		p.drawLine(6, 14, 15, 5); // thicker

		Texture t = new Texture(p);
		p.dispose();
		return new TextureRegionDrawable(new TextureRegion(t));
	}

	private static void fixHandleSize() {
		Skin skin = VisUI.getSkin();
		float size = 20f;
		float splitBarThickness = PlatformImpl.isAndroidUser() ? 15f : 8f;
		float g = 0.4f;
		TextureRegionDrawable cDrawable = ColorTextureUtils.createColorDrawable(new Color(g, g, g, 0.4f));

		// Fix SplitPane handles if they exist
		if (skin.has("default-vertical", SplitPane.SplitPaneStyle.class)) {
			SplitPane.SplitPaneStyle splitPaneStyle = skin.get("default-vertical", SplitPane.SplitPaneStyle.class);
			splitPaneStyle.handle = cDrawable;
			splitPaneStyle.handle.setMinHeight(splitBarThickness);
		}

		if (skin.has("default-horizontal", SplitPane.SplitPaneStyle.class)) {
			SplitPane.SplitPaneStyle splitPaneStyleHori = skin.get("default-horizontal", SplitPane.SplitPaneStyle.class);
			splitPaneStyleHori.handle = cDrawable;
			splitPaneStyleHori.handle.setMinWidth(splitBarThickness);
		}

		if (skin.has("default-vertical", VisSplitPane.VisSplitPaneStyle.class)) {
			VisSplitPane.VisSplitPaneStyle visSplitPaneStyle = skin.get("default-vertical", VisSplitPane.VisSplitPaneStyle.class);
			visSplitPaneStyle.handle = cDrawable;
			visSplitPaneStyle.handle.setMinWidth(splitBarThickness);
		}

		if (skin.has("default-horizontal", VisSplitPane.VisSplitPaneStyle.class)) {
			VisSplitPane.VisSplitPaneStyle visSplitPaneStyleHori = skin.get("default-horizontal", VisSplitPane.VisSplitPaneStyle.class);
			visSplitPaneStyleHori.handle = cDrawable;
			visSplitPaneStyleHori.handle.setMinHeight(splitBarThickness);
		}

		// VisTree fixes
		if (skin.has("default", VisTree.TreeStyle.class)) {
			VisTree.TreeStyle treeStyle = skin.get("default", VisTree.TreeStyle.class);
			if (treeStyle.plus != null) {
				treeStyle.plus.setMinWidth(size);
				treeStyle.plus.setMinHeight(size);
			}
			if (treeStyle.minus != null) {
				treeStyle.minus.setMinWidth(size);
				treeStyle.minus.setMinHeight(size);
			}
		}

		// VisCheckBox fixes
		size = 30;
		if (skin.has("default", VisCheckBox.VisCheckBoxStyle.class)) {
			VisCheckBox.VisCheckBoxStyle checkBoxStyle = skin.get(VisCheckBox.VisCheckBoxStyle.class);
			if (checkBoxStyle.focusBorder != null) {
				checkBoxStyle.focusBorder.setMinWidth(size);
				checkBoxStyle.focusBorder.setMinHeight(size);
			}
			if (checkBoxStyle.checkBackground != null) {
				checkBoxStyle.checkBackground.setMinWidth(size);
				checkBoxStyle.checkBackground.setMinHeight(size);
			}
			if (checkBoxStyle.checkBackgroundOver != null) {
				checkBoxStyle.checkBackgroundOver.setMinWidth(size);
				checkBoxStyle.checkBackgroundOver.setMinHeight(size);
			}
			if (checkBoxStyle.checkBackgroundDown != null) {
				checkBoxStyle.checkBackgroundDown.setMinWidth(size);
				checkBoxStyle.checkBackgroundDown.setMinHeight(size);
			}
			if (checkBoxStyle.checkedFocused != null) {
				checkBoxStyle.checkedFocused.setMinWidth(size);
				checkBoxStyle.checkedFocused.setMinHeight(size);
			}
			if (checkBoxStyle.tick != null) {
				checkBoxStyle.tick.setMinWidth(size);
				checkBoxStyle.tick.setMinHeight(size);
			}
			if (checkBoxStyle.tickDisabled != null) {
				checkBoxStyle.tickDisabled.setMinWidth(size);
				checkBoxStyle.tickDisabled.setMinHeight(size);
			}
		}
	}
}

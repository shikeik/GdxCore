package com.goldsprite.gdengine.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

public class FontUtils {
	public static String fnt1Path = "fonts/ZhengQingKeZhiYaTiRegular-2.ttf";
	public static String fnt2Path = "fonts/NotoSansMonoCJKsc-Regular.otf";
	public static String fnt2_1Path = "fonts/NotoSansMonoCJKsc-Regular_500.otf";
	public static String fnt3Path = "fonts/pixelFont-7-8x14-sproutLands.ttf";
	public static String fnt4Path = "fonts/ipix_12px_6000.ttf";
	public static String fnt5Path = "fonts/QingNiaoHuaGuangJianMeiHei-2.ttf";
	public static String fnt6Path = "fonts/HYSongYunLangHeiW-1.ttf";
	public static String fnt7Path = "fonts/SanJiHuaChaoTi-Cu-2.ttf";
	public static String fnt8Path = "fonts/ark-pixel-10px-monospaced-zh_cn.ttf";
	//public static String fntPath = fnt6Path;
	public static String fntPath = fnt2Path;

	public static int defaultFntSize = 30;

	public static float clarityFntSizeRatio = 30;

	private static FontUtils instance;

	public static BitmapFont generate() {
		return generate(defaultFntSize);
	}

	public static BitmapFont generate(int fntSize) {
		return generate(fntSize, 1);
	}

	public static BitmapFont generateAutoClarity(int fntSize) {
		return generateAutoClarity(fntSize, fntPath);
	}

	public static BitmapFont generateAutoClarity(int fntSize, String fntPath) {
		return generate(fntSize, clarityFntSizeRatio / fntSize, fntPath);
	}

	public static BitmapFont generate(int fntSize, float clarity) {
		return generate(fntSize, clarity, fntPath);
	}

	public static BitmapFont generate(int fntSize, float clarity, String fntPath) {
		// 加载字体
		FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(fntPath));
		FreeTypeFontParameter parameter = new FreeTypeFontParameter();

		//clarity = 3.0f;
		// [New Strategy] 2.0x Scale for High DPI support
		clarity = 2.0f;
		parameter.size = Math.round(fntSize * clarity);
		//parameter.size = (int) Math.ceil(fntSize * clarity);
		parameter.mono = false; // [关键] 强制等宽，保证选区对齐
		parameter.incremental = true;
		parameter.color = Color.WHITE;
		// 稍微增加间距，防止缩小后字符粘连
		parameter.spaceX = 1;

		// [核心 1] 关闭 MipMap：incremental 模式下开启 MipMap 开销巨大且不稳定
		parameter.genMipMaps = false;

		// [核心 2] 过滤模式：
		parameter.minFilter = Texture.TextureFilter.Linear;
		parameter.magFilter = Texture.TextureFilter.Linear;

		// 显式设置基础字符集 (ASCII)，防止某些情况下初始为空
		parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS;

		BitmapFont fnt = generator.generateFont(parameter);
		fnt.getData().setScale(1 / clarity);
		fnt.getData().markupEnabled = true;

		// 开启线性过滤（保证由 SDF Shader 接管时边缘平滑）
		fnt.setUseIntegerPositions(false);

		// [关键] 修复 Tab 显示
		fixTabSupport(fnt);

		return fnt;
	}

	// [新增] 核心修复方法：注入 Tab 字形
	// 以后任何字体只要调一下这个方法就能支持 \t
	public static void fixTabSupport(BitmapFont font) {
		BitmapFont.BitmapFontData data = font.getData();
		BitmapFont.Glyph space = data.getGlyph(' ');
		if (space == null) return;

		BitmapFont.Glyph tab = data.getGlyph('\t');
		if (tab == null) {
			tab = new BitmapFont.Glyph();
			tab.id = '\t';
			tab.srcX = space.srcX; tab.srcY = space.srcY;
			tab.page = space.page; // 关键：纹理页引用
			tab.width = 0; // 不可见
			// 宽度 = 4个空格 (你可以改成 2，看个人喜好)
			tab.xadvance = (int) (space.xadvance * 2);
			tab.fixedWidth = true;

			data.setGlyph('\t', tab);
		}
	}
}

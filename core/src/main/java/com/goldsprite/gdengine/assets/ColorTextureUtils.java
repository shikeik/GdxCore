package com.goldsprite.gdengine.assets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class ColorTextureUtils {

	public static TextureRegionDrawable createColorDrawable(Color color) {
		return new TextureRegionDrawable(createColorTextureRegion(color));
	}

	public static TextureRegion createColorTextureRegion(Color color) {
		return createColorTextureRegion(color, false);
	}

	public static TextureRegion createColorTextureRegion(Color color, boolean keepPm) {
		return new TextureRegion(createColorTexture(color, keepPm));
	}

	public static Texture createColorTexture(Color color) {
		return createColorTexture(color, false);
	}

	public static Texture createColorTexture(Color color, boolean keepPm) {
		Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pm.setColor(color);
		pm.fill();
		Texture tex = new Texture(pm);
		if (!keepPm) pm.dispose();
		return tex;
	}

}

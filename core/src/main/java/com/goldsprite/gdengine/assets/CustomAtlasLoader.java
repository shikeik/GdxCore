package com.goldsprite.gdengine.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;

/**
 * 通用自定义 Atlas 加载器
 * <p>
 * 支持加载同级目录下 .json 描述文件，实现 TextureRegion 切片。
 * </p>
 */
public class CustomAtlasLoader {

	private static CustomAtlasLoader instance;

	// 缓存：Image Path -> Texture
	private final Map<String, Texture> textureCache = new HashMap<>();
	// 缓存：Image Path -> Atlas Metadata
	private final Map<String, AtlasData> atlasDataCache = new HashMap<>();

	private CustomAtlasLoader() {}

	public static CustomAtlasLoader inst() {
		if (instance == null) instance = new CustomAtlasLoader();
		return instance;
	}

	// 缓存：Image Path -> Split Regions (TextureRegion[][])
	private final Map<String, TextureRegion[][]> splitCache = new HashMap<>();

	/**
	 * 获取指定图片或其中的 Region
	 * @param imagePath 图片路径 (相对于 assets)
	 * @param regionName Region 名称 (可选，null 则返回整图)
	 * @return TextureRegion，如果加载失败或找不到 region 则返回 null (或整图)
	 */
	public TextureRegion getRegion(String imagePath, String regionName) {
		// 1. 获取 Texture
		if (!textureCache.containsKey(imagePath)) {
			try {
				if (!Gdx.files.internal(imagePath).exists()) {
					Gdx.app.error("CustomAtlasLoader", "File not found: " + imagePath);
					return null;
				}
				Texture tex = new Texture(Gdx.files.internal(imagePath));
				textureCache.put(imagePath, tex);

				// 顺便尝试加载同级 JSON
				loadAtlasData(imagePath);

				// 如果加载了 Data 且有 grid，预切分
				AtlasData data = atlasDataCache.get(imagePath);
				if (data != null && data.gridWidth > 0 && data.gridHeight > 0) {
					 TextureRegion[][] splits = TextureRegion.split(tex, data.gridWidth, data.gridHeight);
					 splitCache.put(imagePath, splits);
				}

			} catch (Exception e) {
				Gdx.app.error("CustomAtlasLoader", "Failed to load texture: " + imagePath, e);
				return null;
			}
		}

		Texture texture = textureCache.get(imagePath);

		// 2. 如果没有指定 regionName，直接返回整图
		if (regionName == null || regionName.isEmpty()) {
			return new TextureRegion(texture);
		}

		// 3. 查找 Region
		AtlasData data = atlasDataCache.get(imagePath);
		if (data != null && data.regions.containsKey(regionName)) {
			String[] info = data.regions.get(regionName);
			int index = Integer.parseInt(info[0]);

			// 使用 Split Cache
			TextureRegion[][] splits = splitCache.get(imagePath);
			if (splits != null) {
				int cols = splits[0].length;
				int rows = splits.length;

				int r = index / cols;
				int c = index % cols;

				if (r < rows && c < cols) {
					return splits[r][c];
				} else {
					 Gdx.app.error("CustomAtlasLoader", "Index out of bounds: " + regionName + " index=" + index);
				}
			} else {
				// Fallback to manual calculation if split failed (unlikely) or not grid
				// ... (Previous logic or simplified)
			}

			// Fallback: manual calc (previous logic was likely flawed on Y, let's trust split)
			return new TextureRegion(texture);
		}

		// 找不到 Region，降级为整图
		Gdx.app.log("CustomAtlasLoader", "Region not found: " + regionName + " in " + imagePath);
		return new TextureRegion(texture);
	}

	/**
	 * 获取指定 Atlas 中所有定义的 Region 名称
	 * @param imagePath 图片路径
	 * @return Region 名称集合
	 */
	public Set<String> getRegionNames(String imagePath) {
		if (!atlasDataCache.containsKey(imagePath)) {
			loadAtlasData(imagePath);
		}
		AtlasData data = atlasDataCache.get(imagePath);
		return data != null ? data.regions.keySet() : Collections.emptySet();
	}

	/**
	 * 获取 Region 显示名称 (如果配置了)
	 * @param imagePath 图片路径
	 * @param regionName Region 名称
	 * @return 显示名称，如果没有配置则返回 regionName
	 */
	public String getDisplayName(String imagePath, String regionName) {
		if (!atlasDataCache.containsKey(imagePath)) return regionName;
		AtlasData data = atlasDataCache.get(imagePath);
		if (data != null && data.regions.containsKey(regionName)) {
			String[] info = data.regions.get(regionName);
			if (info.length > 1) {
				return info[1];
			}
		}
		return regionName;
	}

	private void loadAtlasData(String imagePath) {
		String jsonPath = imagePath.substring(0, imagePath.lastIndexOf('.')) + ".json";
		if (!Gdx.files.internal(jsonPath).exists()) return;

		try {
			JsonValue root = new JsonReader().parse(Gdx.files.internal(jsonPath));
			AtlasData data = new AtlasData();
			data.gridWidth = root.getInt("gridWidth", 0);
			data.gridHeight = root.getInt("gridHeight", 0);

			JsonValue regions = root.get("regions");
			if (regions != null) {
				for (JsonValue entry : regions) {
					data.regions.put(entry.name, entry.asStringArray());
				}
			}
			atlasDataCache.put(imagePath, data);
		} catch (Exception e) {
			Gdx.app.error("CustomAtlasLoader", "Failed to parse json: " + jsonPath, e);
		}
	}

	public void dispose() {
		for (Texture t : textureCache.values()) {
			t.dispose();
		}
		textureCache.clear();
		atlasDataCache.clear();
		splitCache.clear();
	}

	// --- Data Structures ---

	private static class AtlasData {
		int gridWidth;
		int gridHeight;
		Map<String, String[]> regions = new HashMap<>();
	}

	private static class RegionInfo {
		int index;
		// int x, y, w, h; // Future extension
	}
}

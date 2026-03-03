package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.goldsprite.gdengine.neonbatch.NeonBatch;
import com.goldsprite.gdengine.neonbatch.NeonActor;

/**
 * 通用赛博朋克风格血条组件 (H5复刻版 v2.0)
 * 特性：
 * - 支持数值范围 (min/max)
 * - 支持伤害缓冲动画 (Damage Flash)
 * - 支持任意角度倾斜 (Skew Degrees)
 * - 支持双向填充 (Left-to-Right / Right-to-Left)
 */
public class SkewBar extends NeonActor {

	// --- 样式定义 ---
	public static class BarStyle {
		/** 满血时的起始颜色 (左/右取决于填充方向) */
		public Color gradientStart = Color.valueOf("00eaff");
		/** 满血时的结束颜色 */
		public Color gradientEnd = Color.valueOf("0088aa");
		/** 空血槽背景色 */
		public Color backgroundColor = new Color(0.12f, 0.12f, 0.12f, 0.8f);
		/** 边框颜色 */
		public Color borderColor = Color.valueOf("555555");
		/** 伤害缓冲层颜色 */
		public Color damageColor = Color.WHITE;

		/** 边框粗细 (像素) */
		public float borderThickness = 2f;

		/** 倾斜角度 (度)。负数向左歪(\)，正数向右歪(/) */
		public float skewDeg = -20f;

		/** 缓冲条回落速度 (每秒百分比，0.5 = 50%/秒) */
		public float damageSpeed = 0.3f;

		public BarStyle() {}

		// 复制构造
		public BarStyle(BarStyle other) {
			this.gradientStart = new Color(other.gradientStart);
			this.gradientEnd = new Color(other.gradientEnd);
			this.backgroundColor = new Color(other.backgroundColor);
			this.borderColor = new Color(other.borderColor);
			this.damageColor = new Color(other.damageColor);
			this.borderThickness = other.borderThickness;
			this.skewDeg = other.skewDeg;
			this.damageSpeed = other.damageSpeed;
		}
	}

	// --- 成员变量 ---
	private BarStyle style;

	private float min, max;
	private float value;       // 当前逻辑数值
	private float visualValue; // 当前视觉数值 (用于伤害缓冲)

	private boolean fillFromRight = false; // 是否从右向左填充 (P2模式)

	/**
	 * @param min 最小值
	 * @param max 最大值
	 */
	public SkewBar(float min, float max, BarStyle style) {
		this.min = min;
		this.max = max;
		this.value = max;
		this.visualValue = max;
		setStyle(style);
		setSize(200, 20); // 默认尺寸
	}

	public void setStyle(BarStyle style) {
		if (style == null) throw new IllegalArgumentException("Style cannot be null");
		this.style = style;
	}
	public BarStyle getStyle() {
		return style;
	}

	/** 设置填充方向: true 为从右向左 (适合 P2) */
	public void setFillFromRight(boolean fromRight) {
		this.fillFromRight = fromRight;
	}

	/** 设置数值范围 */
	public void setRange(float min, float max) {
		this.min = min;
		this.max = max;
		// Clamp current value
		setValue(this.value);
	}

	/** 设置当前数值 (自动处理范围限制) - 带动画 */
	public void setValue(float newValue) {
		this.value = MathUtils.clamp(newValue, min, max);
	}

	/** 瞬间设置数值 (无动画) */
	public void setInstantValue(float newValue) {
		this.value = MathUtils.clamp(newValue, min, max);
		this.visualValue = this.value;
	}

	/** 设置百分比 - 带动画 */
	public void setPercent(float percent) {
		setValue(min + (max - min) * percent);
	}

	public float getValue() { return value; }
	public float getPercent() { return (max == min) ? 0 : (value - min) / (max - min); }
	public float getVisualPercent() { return (max == min) ? 0 : (visualValue - min) / (max - min); }

	@Override
	public void act(float delta) {
		super.act(delta);

		float range = max - min;
		if (range <= 0) return;

		// 动画逻辑
		if (visualValue != value) {
			float speed = range * style.damageSpeed * delta;

			if (visualValue > value) {
				// 扣血 (Drop)
				visualValue -= speed;
				if (visualValue < value) visualValue = value;
			} else {
				// 回血 (Rise)
				visualValue += speed;
				if (visualValue > value) visualValue = value;
			}
		}
	}

	@Override
	public void draw(NeonBatch neonBatch, float parentAlpha) {
		if (style == null) return;

		float x = getX();
		float y = getY();
		float w = getWidth();
		float h = getHeight();

		// 1. 计算倾斜几何
		// tan(deg) = offset / height
		float skewOffset = h * MathUtils.tanDeg(style.skewDeg);

		// 2. 绘制边框 (Border)
		// 顶点: BL, BR, TR, TL
		float[] borderVerts = new float[] {
			x, y,
			x + w, y,
			x + w + skewOffset, y + h,
			x + skewOffset, y + h
		};
		// 使用 NeonBatch 画闭合多边形描边
		neonBatch.drawPolygon(borderVerts, 4, style.borderThickness, style.borderColor, false);

		// 3. 计算内容区域 (Padding)
		float pad = style.borderThickness;
		// 简单内缩，为了严谨视觉，垂直方向内缩 pad，水平方向需要根据斜率修正，这里简化处理
		float cX = x + pad;
		float cY = y + pad;
		float cW = w - pad * 2;
		float cH = h - pad * 2;
		float cSkew = cH * MathUtils.tanDeg(style.skewDeg);

		// 4. 绘制槽底 (Background)
		neonBatch.drawSkewGradientRect(cX, cY, cW, cH, cSkew, style.backgroundColor, style.backgroundColor);

		// 5. 准备绘制条形 (通用逻辑)
		float visualP = getVisualPercent();
		float actualP = getPercent();

		// 辅助方法：根据 fillFromRight 计算绘制区的 X 坐标
		// width: 要绘制的条的宽度
		// return: 该条的左下角起始 X
		float startX = fillFromRight ? (cX + cW) : cX; // 锚点

		// 6. 绘制白色缓冲层 (Damage Layer)
		if (visualP > 0) {
			float dmgWidth = cW * visualP;
			float drawX = fillFromRight ? (startX - dmgWidth) : startX;
			neonBatch.drawSkewGradientRect(drawX, cY, dmgWidth, cH, cSkew, style.damageColor, style.damageColor);
		}

		// 7. 绘制实际血量 (Health Layer)
		if (actualP > 0) {
			float barWidth = cW * actualP;
			float drawX = fillFromRight ? (startX - barWidth) : startX;

			// 处理渐变方向：如果从右填充，通常希望右边是 StartColor(亮)，左边是 EndColor(暗)
			// 或者保持左暗右亮？ H5中 P2 是左红右暗红。
			// 这里我们设定：StartColor 永远是"满血端"的颜色，EndColor 是"空血端"的颜色
			Color cLeft, cRight;
			if (fillFromRight) {
				cLeft = style.gradientEnd;   // 左侧(条尾)是暗色
				cRight = style.gradientStart;// 右侧(条头)是亮色
			} else {
				cLeft = style.gradientStart; // 左侧(条头)是亮色
				cRight = style.gradientEnd;  // 右侧(条尾)是暗色
			}

			// 应用透明度
			Color c1 = tmpColor(cLeft, parentAlpha);
			Color c2 = tmpColor(cRight, parentAlpha);

			neonBatch.drawSkewGradientRect(drawX, cY, barWidth, cH, cSkew, c1, c2);
		}
	}

	private Color tmpColor(Color c, float alpha) {
		Color out = new Color(c);
		out.a *= alpha;
		return out;
	}
}

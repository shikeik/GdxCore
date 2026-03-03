package com.goldsprite.gdengine.neonbatch;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Neon 图形工具箱
 * 提供具体形状的生成逻辑：线、矩形、多边形、星形、圆、圆弧、贝塞尔
 * 统一支持：描边/填充、闭合选项、细分度控制
 */
public class NeonBatch extends BaseShapeBatch {

	// 顶点生成缓存 (避免每次 drawCircle 都 new 数组)
	private float[] pathBuffer = new float[2048];

	// ==========================================================
	// 1. 基础形状 (Line, Rect, Triangle)
	// ==========================================================

	public void drawLine(float x1, float y1, float x2, float y2, float width, Color color) {
		// 线段视为不闭合的路径
		float[] verts = getBuffer(2);
		verts[0] = x1; verts[1] = y1;
		verts[2] = x2; verts[3] = y2;
		pathStroke(verts, 2, width, false, color);
	}

	/**
	 * 绘制矩形
	 * @param rotationDeg 旋转角度 (角度制)
	 */
	public void drawRect(float ldx, float ldy, float width, float height, float rotationDeg, float lineWidth, Color color, boolean filled) {
		//处理空心厚度修正
		ldx = ldx + (filled ? 0 : lineWidth/2f);
		ldy = ldy + (filled ? 0 : lineWidth/2f);
		width = filled ? width : width - lineWidth;
		height = filled ? height : height - lineWidth;

		float halfW = width / 2;
		float halfH = height / 2;

		//转为中心起始坐标
		float cx = ldx + halfW;
		float cy = ldy + halfH;

		// 计算4个角 (从中心旋转)
		float rad = rotationDeg * MathUtils.degreesToRadians;
		float cos = MathUtils.cos(rad);
		float sin = MathUtils.sin(rad);

		// Local corners: (-w, -h), (w, -h), (w, h), (-w, h)
		float[] lx = {-halfW, halfW, halfW, -halfW};
		float[] ly = {-halfH, -halfH, halfH, halfH};

		float[] verts = getBuffer(4);
		for (int i = 0; i < 4; i++) {
			// Rotate 和 Translate
			verts[i * 2] = cx + (lx[i] * cos - ly[i] * sin);
			verts[i * 2 + 1] = cy + (lx[i] * sin + ly[i] * cos);
		}

		if (filled) pathFill(verts, 4, color);
		else pathStroke(verts, 4, lineWidth, true, color);
	}

	/**
	 * [新增] 绘制三角形
	 */
	public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, float lineWidth, Color color, boolean filled) {
		float[] verts = getBuffer(3);
		verts[0] = x1; verts[1] = y1;
		verts[2] = x2; verts[3] = y2;
		verts[4] = x3; verts[5] = y3;

		if (filled) pathFill(verts, 3, color);
		else pathStroke(verts, 3, lineWidth, true, color);
	}

	// ==========================================================
	// 2. 多边形 和 星形 (Polygons 和 Stars)
	// ==========================================================

	/**
	 * 绘制任意多边形 (自动计算顶点数，基于数组长度)
	 */
	public void drawPolygon(float[] vertices, float lineWidth, Color color, boolean filled) {
		drawPolygon(vertices, vertices.length / 2, lineWidth, color, filled);
	}

	/**
	 * [新增] 绘制任意多边形 (指定顶点数量)
	 * @param vertices 顶点数组
	 * @param count 实际使用的顶点对数量 (例如三角形=3)
	 */
	public void drawPolygon(float[] vertices, int count, float lineWidth, Color color, boolean filled) {
		if (filled) {
			pathFill(vertices, count, color);
		} else {
			pathStroke(vertices, count, lineWidth, true, color);
		}
	}

	/**
	 * 正多边形 (Regular Polygon)
	 * @param rotationDeg 初始旋转角度
	 */
	public void drawRegularPolygon(float x, float y, float radius, int sides, float rotationDeg, float lineWidth, Color color, boolean filled) {
		if (sides < 3) return;
		float[] verts = getBuffer(sides);
		float angleStep = 360f / sides;
		float startRad = rotationDeg * MathUtils.degreesToRadians;

		for (int i = 0; i < sides; i++) {
			float rad = startRad + i * angleStep * MathUtils.degreesToRadians;
			verts[i * 2] = x + MathUtils.cos(rad) * radius;
			verts[i * 2 + 1] = y + MathUtils.sin(rad) * radius;
		}
		// 内部调用带 count 的方法
		drawPolygon(verts, sides, lineWidth, color, filled);
	}

	/**
	 * 星形 (Star) - 凹多边形
	 * @param points 角的数量 (如五角星 points=5)
	 */
	public void drawStar(float x, float y, float rOuter, float rInner, int points, float rotationDeg, float lineWidth, Color color, boolean filled) {
		if (points < 3) return;
		int count = points * 2;

		// 修复: 对于填充模式，我们需要多一个顶点来闭合回路 (用于 sectorFill)
		// pathFill 对于凹多边形(如星形)会出错，必须使用基于中心的 sectorFill
		int vertexCount = filled ? count + 1 : count;
		float[] verts = getBuffer(vertexCount);

		// 修复: 使用弧度制直接计算，提高精度，避免首尾闭合时的角度微小误差
		float startRad = rotationDeg * MathUtils.degreesToRadians;
		float stepRad = MathUtils.PI2 / count;

		for (int i = 0; i < count; i++) {
			float rad = startRad + i * stepRad;
			float r = (i % 2 == 0) ? rOuter : rInner; // 偶数点外径，奇数点内径
			verts[i * 2] = x + MathUtils.cos(rad) * r;
			verts[i * 2 + 1] = y + MathUtils.sin(rad) * r;
		}

		if (filled) {
			// 闭合回路：复制第一个点到末尾
			verts[count * 2] = verts[0];
			verts[count * 2 + 1] = verts[1];
			// 使用 sectorFill (以中心点为扇心)，解决凹角填充错误
			sectorFill(x, y, verts, vertexCount, color);
		} else {
			// 描边逻辑不变 (pathStroke 自带闭合处理)
			pathStroke(verts, count, lineWidth, true, color);
		}
	}

	// ==========================================================
	// 3. 圆形 和 圆弧 (Circle, Oval, Arcs)
	// ==========================================================

	/**
	 * 圆形 (Circle)
	 * @param segments 细分度 (建议 24-64)
	 */
	public void drawCircle(float x, float y, float radius, float lineWidth, Color color, int segments, boolean filled) {
		//处理空心厚度修正
		radius = filled ? radius : radius - lineWidth/2f;
		// 圆形就是边数很多的正多边形
		drawRegularPolygon(x, y, radius, segments, 0, lineWidth, color, filled);
	}

	/**
	 * [新增] 椭圆 (Oval)
	 */
	public void drawOval(float x, float y, float width, float height, float rotationDeg, float lineWidth, Color color, int segments, boolean filled) {
		if (segments < 3) return;

		//处理空心厚度修正
		float w = filled ? width : width - lineWidth;
		float h = filled ? height : height - lineWidth;
		float halfW = w / 2;
		float halfH = h / 2;

		float[] verts = getBuffer(segments);
		float angleStep = 360f / segments;
		float rotRad = rotationDeg * MathUtils.degreesToRadians;
		float cosRot = MathUtils.cos(rotRad);
		float sinRot = MathUtils.sin(rotRad);

		for (int i = 0; i < segments; i++) {
			float rad = i * angleStep * MathUtils.degreesToRadians;
			float lx = MathUtils.cos(rad) * halfW;
			float ly = MathUtils.sin(rad) * halfH;

			// Rotate and translate
			verts[i * 2] = x + (lx * cosRot - ly * sinRot);
			verts[i * 2 + 1] = y + (lx * sinRot + ly * cosRot);
		}

		drawPolygon(verts, segments, lineWidth, color, filled);
	}

	/**
	 * 圆弧线 (Stroke Arc)
	 * @param startAngleDeg 起始角度
	 * @param degrees 跨度角度 (可以为负)
	 * @param segments 细分度
	 */
	public void drawArc(float x, float y, float radius, float startAngleDeg, float degrees, float lineWidth, Color color, int segments) {
		if (segments < 2) segments = 2;
		float[] verts = getBuffer(segments + 1); // N段需要 N+1 个点

		float startRad = startAngleDeg * MathUtils.degreesToRadians;
		float stepRad = (degrees * MathUtils.degreesToRadians) / segments;

		for (int i = 0; i <= segments; i++) {
			float rad = startRad + i * stepRad;
			verts[i * 2] = x + MathUtils.cos(rad) * radius;
			verts[i * 2 + 1] = y + MathUtils.sin(rad) * radius;
		}

		// 圆弧通常不闭合
		pathStroke(verts, segments + 1, lineWidth, false, color);
	}

	/**
	 * 扇面 (Sector) - 填充
	 * 形状：圆心 -> 弧线 -> 回到圆心
	 */
	public void drawSector(float x, float y, float radius, float startAngleDeg, float degrees, Color color, int segments) {
		if (segments < 2) segments = 2;
		// 生成弧线上的点
		float[] verts = getBuffer(segments + 1);
		float startRad = startAngleDeg * MathUtils.degreesToRadians;
		float stepRad = (degrees * MathUtils.degreesToRadians) / segments;

		for (int i = 0; i <= segments; i++) {
			float rad = startRad + i * stepRad;
			verts[i * 2] = x + MathUtils.cos(rad) * radius;
			verts[i * 2 + 1] = y + MathUtils.sin(rad) * radius;
		}

		// 使用基类的 sectorFill (专门处理中心点)
		sectorFill(x, y, verts, segments + 1, color);
	}

	// ==========================================================
	// 4. 贝塞尔曲线 (Bezier)
	// ==========================================================

	/**
	 * 二阶贝塞尔 (Quadratic)
	 */
	public void drawQuadraticBezier(float x0, float y0, float x1, float y1, float x2, float y2, float lineWidth, Color color, int segments) {
		float[] verts = getBuffer(segments + 1);
		for (int i = 0; i <= segments; i++) {
			float t = i / (float) segments;
			float invT = 1 - t;
			// B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
			float f0 = invT * invT;
			float f1 = 2 * invT * t;
			float f2 = t * t;
			verts[i * 2] = f0 * x0 + f1 * x1 + f2 * x2;
			verts[i * 2 + 1] = f0 * y0 + f1 * y1 + f2 * y2;
		}
		pathStroke(verts, segments + 1, lineWidth, false, color);
	}

	/**
	 * 三阶贝塞尔 (Cubic)
	 * @param p [x0, y0, x1, y1, x2, y2, x3, y3]
	 */
	public void drawCubicBezier(float[] p, float lineWidth, Color color, int segments) {
		if (p.length < 8) return;
		float[] verts = getBuffer(segments + 1);
		float x0 = p[0], y0 = p[1], x1 = p[2], y1 = p[3], x2 = p[4], y2 = p[5], x3 = p[6], y3 = p[7];

		for (int i = 0; i <= segments; i++) {
			float t = i / (float) segments;
			float u = 1 - t;
			float tt = t * t, uu = u * u;
			float uuu = uu * u, ttt = tt * t;

			// B(t)
			float px = uuu * x0 + 3 * uu * t * x1 + 3 * u * tt * x2 + ttt * x3;
			float py = uuu * y0 + 3 * uu * t * y1 + 3 * u * tt * y2 + ttt * y3;
			verts[i * 2] = px; verts[i * 2 + 1] = py;
		}
		pathStroke(verts, segments + 1, lineWidth, false, color);
	}

	/**
	 * [新增] 绘制带倾斜和水平渐变的矩形 (专为 HP Bar 优化)
	 * 原理：手动构建 SpriteBatch 兼容的 20 个顶点数据 (4个点 x 5属性: x, y, color, u, v)
	 *
	 * @param x 左下角X
	 * @param y 左下角Y
	 * @param width 宽度
	 * @param height 高度
	 * @param skewX 倾斜的水平偏移量 (对应 CSS skewX，正数头向右歪，负数头向左歪)
	 * @param cLeft 左侧颜色
	 * @param cRight 右侧颜色
	 */
	public void drawSkewGradientRect(float x, float y, float width, float height, float skewX, Color cLeft, Color cRight) {
		float leftBits = cLeft.toFloatBits();
		float rightBits = cRight.toFloatBits();

		// 顶点顺序: SpriteBatch 默认顺序是 BL, TL, TR, BR (0, 1, 2, 3)
		// 实际上 SpriteBatch.draw(texture, vertices...) 需要按照这个顺序填充

		// P1: Bottom-Left (BL)
		float x1 = x;
		float y1 = y;

		// P2: Top-Left (TL)
		float x2 = x + skewX;
		float y2 = y + height;

		// P3: Top-Right (TR)
		float x3 = x + width + skewX;
		float y3 = y + height;

		// P4: Bottom-Right (BR)
		float x4 = x + width;
		float y4 = y;

		// 构造顶点数据 (复用 pathBuffer)
		float[] verts = getBuffer(4);

		int idx = 0;
		// Vertex 0 (BL) - Color Left
		verts[idx++] = x1; verts[idx++] = y1; verts[idx++] = leftBits; verts[idx++] = whiteU; verts[idx++] = whiteV;

		// Vertex 1 (TL) - Color Left
		verts[idx++] = x2; verts[idx++] = y2; verts[idx++] = leftBits; verts[idx++] = whiteU; verts[idx++] = whiteV;

		// Vertex 2 (TR) - Color Right
		verts[idx++] = x3; verts[idx++] = y3; verts[idx++] = rightBits; verts[idx++] = whiteU; verts[idx++] = whiteV;

		// Vertex 3 (BR) - Color Right
		verts[idx++] = x4; verts[idx++] = y4; verts[idx++] = rightBits; verts[idx++] = whiteU; verts[idx++] = whiteV;

		// 提交绘制
		// SpriteBatch 会自动把这4个点处理成两个三角形
		draw(blankRegion.getTexture(), verts, 0, 20);
	}

	/**
	 * [重载] 绘制渐变三角形带 (Triangle Strip)
	 * 顶点数组: [x0, y0, x1, y1, ...]
	 * 颜色数组: [floatBits0, floatBits1, ...] 长度必须与顶点数一致
	 */
	public void drawTriangleStrip(float[] vertices, float[] colors, int count) {
		super.drawTriangleStrip(vertices, colors, count);
	}

	// ==========================================================
	// 5. 新增的高级渐变 API (Gradient & Glow)
	// ==========================================================

	/**
	 * [新增] 绘制任意三点渐变三角形
	 */
	public void drawGradientTriangle(float x1, float y1, Color c1, float x2, float y2, Color c2, float x3, float y3, Color c3) {
		drawSolidTriangle(x1, y1, c1.toFloatBits(), x2, y2, c2.toFloatBits(), x3, y3, c3.toFloatBits());
	}

	/**
	 * [新增] 垂直线性渐变矩形 (Top -> Bottom)
	 */
	public void drawVerticalGradientRect(float x, float y, float width, float height, Color bottomColor, Color topColor) {
		float cBot = bottomColor.toFloatBits();
		float cTop = topColor.toFloatBits();
		// Tri 1: BL, TL, TR
		drawSolidTriangle(x, y, cBot, x, y + height, cTop, x + width, y + height, cTop);
		// Tri 2: BL, TR, BR
		drawSolidTriangle(x, y, cBot, x + width, y + height, cTop, x + width, y, cBot);
	}

	/**
	 * [新增] 水平三色渐变 (Left -> Center -> Right)
	 * 适合做柱状体体积感或反光高光
	 */
	public void drawHorizontalGradientRect(float x, float y, float width, float height, Color left, Color center, Color right) {
		float cL = left.toFloatBits();
		float cC = center.toFloatBits();
		float cR = right.toFloatBits();
		float midX = x + width / 2;

		// Left half
		drawSolidTriangle(x, y, cL, x, y + height, cL, midX, y + height, cC);
		drawSolidTriangle(x, y, cL, midX, y + height, cC, midX, y, cC);

		// Right half
		drawSolidTriangle(midX, y, cC, midX, y + height, cC, x + width, y + height, cR);
		drawSolidTriangle(midX, y, cC, x + width, y + height, cR, x + width, y, cR);
	}

	/**
	 * [新增] 径向渐变圆形 (Center -> Edge)
	 * 制造球体效果的核心
	 * @param centerColor 中心点颜色 (高光)
	 * @param edgeColor 边缘颜色 (阴影)
	 */
	public void drawRadialGradientCircle(float x, float y, float radius, Color centerColor, Color edgeColor, int segments) {
		float[] verts = getBuffer(segments);
		float angleStep = 360f / segments;
		for (int i = 0; i < segments; i++) {
			float rad = i * angleStep * MathUtils.degreesToRadians;
			verts[i * 2] = x + MathUtils.cos(rad) * radius;
			verts[i * 2 + 1] = y + MathUtils.sin(rad) * radius;
		}
		pathFillRadialGradient(x, y, verts, segments, centerColor.toFloatBits(), edgeColor.toFloatBits());
	}

	/**
	 * [新增] 渐变描边圆形 (支持羽化/发光)
	 * @param width 线宽
	 * @param innerColor 线条内侧颜色
	 * @param outerColor 线条外侧颜色 (设为 alpha=0 可做发光/抗锯齿)
	 */
	public void drawCircleGradientStroke(float x, float y, float radius, float width, Color innerColor, Color outerColor, int segments) {
		float[] verts = getBuffer(segments);
		float angleStep = 360f / segments;
		for (int i = 0; i < segments; i++) {
			float rad = i * angleStep * MathUtils.degreesToRadians;
			verts[i * 2] = x + MathUtils.cos(rad) * radius;
			verts[i * 2 + 1] = y + MathUtils.sin(rad) * radius;
		}
		// 使用闭合的渐变描边
		pathStrokeGradient(verts, segments, width, true, innerColor.toFloatBits(), outerColor.toFloatBits());
	}

	/**
	 * [新增] 绘制羽化抗锯齿的多边形 (Smooth Polygon)
	 * 自动叠加一层填充和一层透明渐变描边
	 * @param featherWidth 羽化边缘的宽度 (通常 1.0~2.0)
	 */
	public void drawSmoothPolygon(float[] vertices, int count, Color color, float featherWidth) {
		// 1. 实心填充
		pathFill(vertices, count, color);

		// 2. 透明边缘描边 (Inner=Color, Outer=Transparent)
		// 注意：这需要创建一个临时的透明色，稍微有点 GC，高性能场景建议缓存颜色对象
		float cBits = color.toFloatBits();
		Color cTransparent = color.cpy(); // 建议外部缓存传入以优化
		cTransparent.a = 0f;
		float cTransBits = cTransparent.toFloatBits();

		pathStrokeGradient(vertices, count, featherWidth, true, cBits, cTransBits);
	}

	// --- 内部辅助 ---

	// 自动扩容缓存
	private float[] getBuffer(int vertexCount) {
		int len = vertexCount * 2;
		if (pathBuffer.length < len) {
			// 翻倍扩容
			pathBuffer = new float[len * 2];
		}
		return pathBuffer;
	}
}

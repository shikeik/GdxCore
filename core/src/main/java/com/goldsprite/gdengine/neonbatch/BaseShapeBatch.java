package com.goldsprite.gdengine.neonbatch;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

/**
 * 通用矢量图形绘制基类
 * 职责：
 * 1. 管理 1x1 白点纹理
 * 2. 提供通用的描边 (Stroke) 和 填充 (Fill) 算法
 * 3. 所有的几何计算（Miter Join）都在这里完成
 */
public class BaseShapeBatch extends SpriteBatch{
	protected final TextureRegion blankRegion;
	// 缓存 UV，避免每次绘制调用方法
	protected final float whiteU, whiteV;

	// 顶点缓存 (避免 new)，最大支持 1024 个顶点的路径
	// [x, y, x, y, ...]
	private final float[] tempVerts = new float[2048];
	// Miter 计算用的临时变量
	private final Vector2 tmpV1 = new Vector2();
	private final Vector2 tmpV2 = new Vector2();
	private final Vector2 miterTmp = new Vector2();

	public BaseShapeBatch() {
		// 生成 1x1 纯白纹理
		Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixmap.setColor(Color.WHITE);
		pixmap.fill();
		Texture texture = new Texture(pixmap);
		// [修改建议] Linear 滤波能让羽化效果更平滑，如果追求极致像素风可改回 Nearest
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		blankRegion = new TextureRegion(texture);
		whiteU = blankRegion.getU();
		whiteV = blankRegion.getV();
		pixmap.dispose();
	}

	// [新增] 暴露白纹理供高级自定义绘制使用
	public TextureRegion getBlankRegion() {
		return blankRegion;
	}

	// --- 核心算法 1: 填充 (Fill) ---

	/**
	 * 填充凸多边形 (Convex Polygon) 或星形
	 * 使用 Triangle Fan (三角扇) 原理：以第一个点为中心，连接其余点
	 * @param vertices 顶点数组 [x0, y0, x1, y1, ...]
	 * @param count 顶点数量 (不是数组长度)
	 */
	protected void pathFill(float[] vertices, int count, Color color) {
		if (count < 3) return;

		// 三角扇中心点 (默认取第一个点)
		float cX = vertices[0];
		float cY = vertices[1];

		// 预先打包颜色 float bits
		float colorBits = color.toFloatBits();

		for (int i = 1; i < count - 1; i++) {
			float p1x = vertices[i * 2];
			float p1y = vertices[i * 2 + 1];
			float p2x = vertices[(i + 1) * 2];
			float p2y = vertices[(i + 1) * 2 + 1];

			// 绘制三角形: Center -> P1 -> P2
			drawSolidTriangle(cX, cY, p1x, p1y, p2x, p2y, colorBits);
		}
	}

	/**
	 * 填充扇形/圆弧 (Sector)
	 * 特殊逻辑：需要显式指定中心点
	 */
	protected void sectorFill(float centerX, float centerY, float[] vertices, int count, Color color) {
		if (count < 2) return;
		float colorBits = color.toFloatBits();
		for (int i = 0; i < count - 1; i++) {
			drawSolidTriangle(centerX, centerY,
				vertices[i * 2], vertices[i * 2 + 1],
				vertices[(i + 1) * 2], vertices[(i + 1) * 2 + 1],
				colorBits);
		}
	}

	// [新增 API] 径向渐变填充 (Radial Gradient Fill)
	// 适用于球体、发光中心等效果
	// centerColor: 中心点颜色, edgeColor: 边缘点颜色
	protected void pathFillRadialGradient(float centerX, float centerY, float[] vertices, int count, float centerColorBits, float edgeColorBits) {
		if (count < 2) return;
		for (int i = 0; i < count - 1; i++) {
			drawSolidTriangle(
				centerX, centerY, centerColorBits,      // 中心点
				vertices[i * 2], vertices[i * 2 + 1], edgeColorBits, // P1
				vertices[(i + 1) * 2], vertices[(i + 1) * 2 + 1], edgeColorBits // P2
			);
		}
		// 处理首尾闭合 (如果是圆)
		drawSolidTriangle(
			centerX, centerY, centerColorBits,
			vertices[(count - 1) * 2], vertices[(count - 1) * 2 + 1], edgeColorBits,
			vertices[0], vertices[1], edgeColorBits
		);
	}

	// --- 核心算法 2: 描边 (Stroke) ---

	/**
	 * 通用路径描边 (Miter Join)
	 * @param vertices 顶点数组 [x0, y0, x1, y1, ...]
	 * @param count 顶点数量
	 * @param width 线宽
	 * @param isClosed 是否闭合 (首尾相连)
	 */
	protected void pathStroke(float[] vertices, int count, float width, boolean isClosed, Color color) {
		// 复用原来的逻辑，只需将颜色转为 floatBits 并传入单色版本
		pathStrokeGradient(vertices, count, width, isClosed, color.toFloatBits(), color.toFloatBits());
	}

	// [新增/重构 API] 支持双色渐变的描边 (用于羽化、发光、立体边框)
	// innerColor: 线条内侧颜色, outerColor: 线条外侧颜色
	protected void pathStrokeGradient(float[] vertices, int count, float width, boolean isClosed, float innerColorBits, float outerColorBits) {
		if (count < 2) return;

		float halfWidth = width * 0.5f;

		// 缓存上一个断面的内外点
		float prevOuterX = 0, prevOuterY = 0, prevInnerX = 0, prevInnerY = 0;
		// 记录第一个断面的点，用于闭合
		float firstOuterX = 0, firstOuterY = 0, firstInnerX = 0, firstInnerY = 0;

		// 遍历所有顶点
		for (int i = 0; i < count; i++) {
			float curX = vertices[i * 2];
			float curY = vertices[i * 2 + 1];

			// 1. 确定 Prev 和 Next 坐标
			float prevX, prevY, nextX, nextY;

			if (i == 0) {
				if (isClosed) {
					// 闭合：Prev 是最后一个点
					prevX = vertices[(count - 1) * 2];
					prevY = vertices[(count - 1) * 2 + 1];
				} else {
					// 不闭合：Prev 是根据 Curr->Next 的反向延长
					// P_prev = P_curr - (P_next - P_curr)
					nextX = vertices[2]; nextY = vertices[3];
					prevX = curX - (nextX - curX);
					prevY = curY - (nextY - curY);
				}
			} else {
				prevX = vertices[(i - 1) * 2];
				prevY = vertices[(i - 1) * 2 + 1];
			}

			if (i == count - 1) {
				if (isClosed) {
					// 闭合：Next 是第一个点
					nextX = vertices[0];
					nextY = vertices[1];
				} else {
					// 不闭合：Next 是 Prev->Curr 的延长
					nextX = curX + (curX - prevX);
					nextY = curY + (curY - prevY);
				}
			} else {
				// 中间点
				int nextIdx = (i + 1) % count; // 防越界
				nextX = vertices[nextIdx * 2];
				nextY = vertices[nextIdx * 2 + 1];
			}

			// 2. 计算 Miter Offset
			computeMiterOffset(prevX, prevY, curX, curY, nextX, nextY, halfWidth);

			// 当前断面的内外点
			float currOuterX = curX + miterTmp.x;
			float currOuterY = curY + miterTmp.y;
			float currInnerX = curX - miterTmp.x;
			float currInnerY = curY - miterTmp.y;

			// 保存第一个断面以便闭合
			if (i == 0) {
				firstOuterX = currOuterX; firstOuterY = currOuterY;
				firstInnerX = currInnerX; firstInnerY = currInnerY;
			}

			// 3. 缝合 (Strip Generation)
			if (i > 0) {
				// [关键修改] 传入外侧和内侧颜色
				drawQuadStrip(prevOuterX, prevOuterY, currOuterX, currOuterY, currInnerX, currInnerY, prevInnerX, prevInnerY, outerColorBits, innerColorBits);
			}

			// 更新缓存
			prevOuterX = currOuterX; prevOuterY = currOuterY;
			prevInnerX = currInnerX; prevInnerY = currInnerY;
		}

		// 4. 处理最后的闭合缝隙 (Last -> First)
		if (isClosed) {
			drawQuadStrip(prevOuterX, prevOuterY, firstOuterX, firstOuterY, firstInnerX, firstInnerY, prevInnerX, prevInnerY, outerColorBits, innerColorBits);
		}
	}

	// --- 辅助计算 ---

	private void computeMiterOffset(float xPrev, float yPrev, float xCurr, float yCurr, float xNext, float yNext, float halfWidth) {
		// V1: Prev -> Curr
		tmpV1.set(xCurr - xPrev, yCurr - yPrev).nor();
		// V2: Curr -> Next
		tmpV2.set(xNext - xCurr, yNext - yCurr).nor();

		// Tangent (切线) = V1 + V2
		float tx = tmpV1.x + tmpV2.x;
		float ty = tmpV1.y + tmpV2.y;
		float tLen = (float) Math.sqrt(tx * tx + ty * ty);

		// 法线 (Normal) = (-ty, tx)
		float nx, ny;

		if (tLen < 0.0001f) {
			// 共线或尖角折返，退化处理：取 V1 的法线
			nx = -tmpV1.y;
			ny = tmpV1.x;
			miterTmp.set(nx, ny).scl(halfWidth);
			return;
		}

		tx /= tLen; ty /= tLen;
		nx = -ty; ny = tx;

		// Miter Length = halfWidth / dot(normal, v1_normal)
		float dot = nx * (-tmpV1.y) + ny * tmpV1.x;

		// 防止除零和过度尖角
		float miterLen = halfWidth / Math.max(0.1f, dot);
		float limit = halfWidth * 3f; // 限制尖角长度
		if (miterLen > limit) miterLen = limit;

		miterTmp.set(nx, ny).scl(miterLen);
	}

	// --- 底层 Vertex 提交 (画四边形和三角形) ---

	// [旧 API] 单色四边形带 (保留兼容)
	protected void drawQuadStrip(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, float colorBits) {
		drawQuadStrip(x1, y1, x2, y2, x3, y3, x4, y4, colorBits, colorBits);
	}

	// [新增 API] 双色四边形带 (用于渐变描边)
	// 顺序: 1(Outer), 2(Outer), 3(Inner), 4(Inner)
	protected void drawQuadStrip(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, float colorOuter, float colorInner) {
		// Tri 1: 1-2-3 (Outer-Outer-Inner)
		drawSolidTriangle(x1, y1, colorOuter, x2, y2, colorOuter, x3, y3, colorInner);
		// Tri 2: 1-3-4 (Outer-Inner-Inner)
		drawSolidTriangle(x1, y1, colorOuter, x3, y3, colorInner, x4, y4, colorInner);
	}

	// [旧 API] 单色三角形 (保留兼容)
	protected void drawSolidTriangle(float x1, float y1, float x2, float y2, float x3, float y3, float colorBits) {
		drawSolidTriangle(x1, y1, colorBits, x2, y2, colorBits, x3, y3, colorBits);
	}

	// [新增 API] 三色三角形 (用于自由渐变)
	protected void drawSolidTriangle(float x1, float y1, float c1, float x2, float y2, float c2, float x3, float y3, float c3) {
		// 构造4个顶点 (SpriteBatch 需要 4 个顶点画两个三角，这里我们通过退化第4个点画一个三角)
		float[] vArr = tempVerts; // 复用数组
		int idx = 0;

		// V1
		vArr[idx++] = x1; vArr[idx++] = y1; vArr[idx++] = c1; vArr[idx++] = whiteU; vArr[idx++] = whiteV;
		// V2
		vArr[idx++] = x2; vArr[idx++] = y2; vArr[idx++] = c2; vArr[idx++] = whiteU; vArr[idx++] = whiteV;
		// V3
		vArr[idx++] = x3; vArr[idx++] = y3; vArr[idx++] = c3; vArr[idx++] = whiteU; vArr[idx++] = whiteV;
		// V4 (Repeat V3) -> Degenerate
		vArr[idx++] = x3; vArr[idx++] = y3; vArr[idx++] = c3; vArr[idx++] = whiteU; vArr[idx++] = whiteV;

		draw(blankRegion.getTexture(), vArr, 0, 20);
	}

	// [新增] 支持独立颜色的三角形带绘制
	protected void drawTriangleStrip(float[] vertices, float[] colors, int count) {
		if (count < 3) return;
		for (int i = 0; i < count - 2; i++) {
			// 构成三角形: (i, i+1, i+2)
			int idx1 = i, idx2 = i + 1, idx3 = i + 2;
			// 简单的交替绕序处理
			if (i % 2 != 0) { int tmp = idx2; idx2 = idx3; idx3 = tmp; } // Swap to maintain winding

			drawSolidTriangle(
				vertices[idx1*2], vertices[idx1*2+1], colors[idx1],
				vertices[idx2*2], vertices[idx2*2+1], colors[idx2],
				vertices[idx3*2], vertices[idx3*2+1], colors[idx3]
			);
		}
	}
}

package com.goldsprite.gdengine.neonbatch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

public class BloomRenderer {
	//TODO： 这些参数有些遗漏了实现，后面要补回来
	public float blurScale = 1f;
	public int iterations = 6;
	public int overlay = 1;
	public float intensity = 1f;
	public float baseRadius = 1.2f;
	public float bloomSpreadMul = 1f;
	public float saturation = 1.2f;

	// --- Shaders ---
	private static final String VERT = "attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0; uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_texCoords; void main() { v_color = a_color; v_texCoords = a_texCoord0; gl_Position = u_projTrans * a_position; }";
	private static final String BLUR_FRAG = "#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color; varying vec2 v_texCoords; uniform sampler2D u_texture; uniform vec2 u_dir; void main() { float weight[3]; weight[0] = 0.227027; weight[1] = 0.316216; weight[2] = 0.070270; vec2 offset1 = vec2(1.3846153846) * u_dir; vec2 offset2 = vec2(3.2307692308) * u_dir; vec4 sum = texture2D(u_texture, v_texCoords) * weight[0]; sum += texture2D(u_texture, v_texCoords + offset1) * weight[1]; sum += texture2D(u_texture, v_texCoords - offset1) * weight[1]; sum += texture2D(u_texture, v_texCoords + offset2) * weight[2]; sum += texture2D(u_texture, v_texCoords - offset2) * weight[2]; gl_FragColor = sum * v_color; }";
	private static final String COMBINE_FRAG = "#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec2 v_texCoords; uniform sampler2D u_texture; uniform sampler2D u_texture1; uniform float u_intensity; uniform float u_saturation; vec3 adjustSaturation(vec3 color, float saturation) { float grey = dot(color, vec3(0.299, 0.587, 0.114)); return mix(vec3(grey), color, saturation); } void main() { vec4 base = texture2D(u_texture, v_texCoords); vec4 bloom = texture2D(u_texture1, v_texCoords); vec3 bloomColor = bloom.rgb * u_intensity; bloomColor = adjustSaturation(bloomColor, u_saturation); vec3 screenResult = 1.0 - (1.0 - base.rgb) * (1.0 - min(vec3(1.0), bloomColor)); gl_FragColor = vec4(screenResult, base.a); }";

	private FrameBuffer mainFBO, pingFBO, pongFBO;
	private ShaderProgram blurShader;
	private ShaderProgram combineShader;
	private SpriteBatch batch;
	private Matrix4 fboMatrix = new Matrix4();
	private Matrix4 screenMatrix = new Matrix4();
	private int screenWidth, screenHeight;

	public BloomRenderer() {
		batch = new SpriteBatch();
		ShaderProgram.pedantic = false;
		blurShader = new ShaderProgram(VERT, BLUR_FRAG);
		combineShader = new ShaderProgram(VERT, COMBINE_FRAG);
	}

	public void resize(int width, int height) {
		try{
			this.screenWidth = width;
			this.screenHeight = height;
			if (mainFBO != null) { mainFBO.dispose(); pingFBO.dispose(); pongFBO.dispose(); }

			mainFBO = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
			int smallW = (int)(width / blurScale);
			int smallH = (int)(height / blurScale);

			pingFBO = new FrameBuffer(Pixmap.Format.RGBA8888, smallW, smallH, false);
			pongFBO = new FrameBuffer(Pixmap.Format.RGBA8888, smallW, smallH, false);

			Texture.TextureFilter filter = Texture.TextureFilter.Linear;
			Texture.TextureWrap wrap = Texture.TextureWrap.ClampToEdge;
			for (FrameBuffer fbo : new FrameBuffer[]{mainFBO, pingFBO, pongFBO}) {
				fbo.getColorBufferTexture().setFilter(filter, filter);
				fbo.getColorBufferTexture().setWrap(wrap, wrap);
			}
			fboMatrix.setToOrtho2D(0, 0, smallW, smallH);
			screenMatrix.setToOrtho2D(0, 0, width, height);
		}catch(Exception ignored){
			//这里为了防止一个奇怪的全屏截屏时闪退bug
		}
	}

	public void captureStart(SpriteBatch gameBatch) {
		if(mainFBO == null) return;
		mainFBO.begin();
		Gdx.gl.glClearColor(0, 0, 0, 0); // 核心：背景透明
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		gameBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
	}

	public void captureEnd() {
		if(mainFBO == null) return;
		mainFBO.end();
	}

	/**
	 * 新增方法：只计算模糊，不输出到屏幕
	 * 在 captureEnd() 之后调用
	 */
	public void process() {
		if (mainFBO == null) return;

		// 1. 提取 (Downsample) -> Ping
		pingFBO.begin();
		Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		batch.setProjectionMatrix(fboMatrix);
		batch.setShader(null);
		batch.disableBlending();
		batch.begin();
		// 注意：FBO 纹理通常是倒置的，但在 FBO 间传递时不需要翻转，因为坐标系一致
		batch.draw(mainFBO.getColorBufferTexture(), 0, 0, pingFBO.getWidth(), pingFBO.getHeight(), 0, 0, 1, 1);
		batch.end();
		pingFBO.end();

		// 2. 模糊 (Blur) -> Ping/Pong Loop
		batch.setShader(blurShader);
		for (int i = 0; i < iterations; i++) {
			float radius = baseRadius + i * bloomSpreadMul;
			// Ping -> Pong (Horizontal)
			pongFBO.begin();
			batch.begin();
			blurShader.setUniformf("u_dir", radius / pongFBO.getWidth(), 0f);
			batch.draw(pingFBO.getColorBufferTexture(), 0, 0, pongFBO.getWidth(), pongFBO.getHeight(), 0, 0, 1, 1);
			batch.end();
			pongFBO.end();

			// Pong -> Ping (Vertical)
			pingFBO.begin();
			batch.begin();
			blurShader.setUniformf("u_dir", 0f, radius / pingFBO.getHeight());
			batch.draw(pongFBO.getColorBufferTexture(), 0, 0, pingFBO.getWidth(), pingFBO.getHeight(), 0, 0, 1, 1);
			batch.end();
			pingFBO.end();
		}
		batch.setShader(null);
	}

	/**
	 * 旧的渲染方法（保留兼容性），会自动上屏覆盖
	 */
	Color tmpColor = new Color();
	public void render(SpriteBatch batch) {
		// 手动合成绘制 (核心步骤)
		// [修复] 使用屏幕空间矩阵 (Screen Space)，而不是世界空间矩阵
		// FBO 里的纹理已经是经过相机变换后的“照片”，不能再被相机变换一次
		batch.setProjectionMatrix(screenMatrix);
		batch.begin();

		TextureRegion raw = getOriginalRegion();
		TextureRegion glow = getBloomRegion();

		if (raw != null && glow != null) {

			batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

			batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
			batch.setColor(tmpColor.set(Color.WHITE));

			// [修复] 绘制位置固定为 (0,0)，宽高使用屏幕物理分辨率
			// 因为 screenMatrix 是 setToOrtho2D(0, 0, width, height)
			for(int i=0; i<1; i++)
				batch.draw(glow, 0, 0, screenWidth, screenHeight);
			batch.draw(raw, 0, 0, screenWidth, screenHeight);

			batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		}

		batch.end();
	}

	/**
	 * 获取原始场景纹理 (Region已处理Y轴翻转，可直接 draw)
	 */
	public TextureRegion getOriginalRegion() {
		if (mainFBO == null) return null;
		TextureRegion tr = new TextureRegion(mainFBO.getColorBufferTexture());
		tr.flip(false, true); // FBO 坐标系修正
		return tr;
	}

	/**
	 * 获取 Bloom 辉光纹理 (Region已处理Y轴翻转，可直接 draw)
	 * 这是一个只有辉光（黑色背景）的纹理，建议使用 GL_ONE, GL_ONE 混合模式绘制
	 */
	public TextureRegion getBloomRegion() {
		if (pingFBO == null) return null;
		TextureRegion tr = new TextureRegion(pingFBO.getColorBufferTexture());
		tr.flip(false, true); // FBO 坐标系修正
		return tr;
	}

	public void setBlurScale(float scl) { blurScale = scl; resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); }

	public void dispose() {
		if(mainFBO!=null)mainFBO.dispose(); if(pingFBO!=null)pingFBO.dispose(); if(pongFBO!=null)pongFBO.dispose();
		if(blurShader!=null)blurShader.dispose(); if(combineShader!=null)combineShader.dispose();
		batch.dispose();
	}
}

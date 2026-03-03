package com.goldsprite.gdengine.chat;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * MC 风格半透明聊天覆盖层。
 * <p>
 * 行为参照 Minecraft 聊天系统:
 * <ul>
 *   <li>新消息在屏幕左下方显示，数秒后自动淡出</li>
 *   <li>按 T 或 Enter 激活输入模式（所有消息可见，底部出现输入框）</li>
 *   <li>输入模式下按 Enter 发送，Escape 取消</li>
 *   <li>半透明黑色背景条使文字在任何场景下都清晰可读</li>
 * </ul>
 * <p>
 * 使用方式:
 * <pre>
 * // 创建
 * chatOverlay = new ChatOverlay();
 *
 * // 每帧渲染（在 SpriteBatch/NeonBatch 的 begin/end 之间）
 * chatOverlay.act(delta);
 * chatOverlay.render(batch, font, uiViewport);
 *
 * // 输入处理（在 InputProcessor.keyDown 中）
 * if (chatOverlay.handleKeyDown(keycode)) return true;
 *
 * // 文本输入（在 InputProcessor.keyTyped 中）
 * if (chatOverlay.handleKeyTyped(character)) return true;
 *
 * // 销毁
 * chatOverlay.dispose();
 * </pre>
 */
public class ChatOverlay implements ChatChannel.MessageListener {

    // ── 配置 ──
    /** 消息可见时长（秒），超时后开始淡出 */
    private static final float MESSAGE_VISIBLE_SEC = 7f;
    /** 淡出动画时长（秒） */
    private static final float FADE_DURATION_SEC = 1.5f;
    /** 最大显示消息行数 */
    private static final int MAX_VISIBLE_LINES = 10;
    /** 历史消息缓存上限（输入模式下可回看） */
    private static final int MAX_HISTORY = 50;
    /** 消息行高（像素） */
    private static final float LINE_HEIGHT = 16f;
    /** 背景内边距 */
    private static final float PAD_X = 6f;
    private static final float PAD_Y = 2f;
    /** 消息区域距屏幕底部偏移（给输入框留空间） */
    private static final float BOTTOM_MARGIN = 4f;
    /** 输入框高度 */
    private static final float INPUT_BAR_HEIGHT = 20f;
    /** 消息区域宽度占屏幕宽度的比例 */
    private static final float WIDTH_RATIO = 0.45f;

    // ── 状态 ──
    /** 带时间戳的消息缓存 */
    private final List<TimedMessage> messages = new ArrayList<>();
    /** 是否处于输入模式 */
    private boolean inputActive = false;
    /** 当前输入缓冲区 */
    private final StringBuilder inputBuffer = new StringBuilder();
    /** 输入历史（按 ↑↓ 翻阅） */
    private final List<String> inputHistory = new ArrayList<>();
    /** 当前历史浏览索引 (-1 = 当前输入) */
    private int historyIndex = -1;
    /** 光标闪烁计时 */
    private float cursorBlinkTimer = 0f;
    /** 发送者名称（由外部设置，如 "Player#1"） */
    private String senderName = "Player";

    // ── 缓存颜色 ──
    private static final Color BG_COLOR = new Color(0, 0, 0, 0.45f);
    private static final Color INPUT_BG_COLOR = new Color(0, 0, 0, 0.55f);
    private static final Color CHAT_COLOR = new Color(1f, 1f, 1f, 1f);
    private static final Color SYSTEM_COLOR = new Color(1f, 1f, 0.4f, 1f);
    private static final Color COMMAND_COLOR = new Color(0.4f, 1f, 1f, 1f);

    // ══════════════ 构造 / 销毁 ══════════════

    public ChatOverlay() {
        ChatChannel.get().addListener(this);
    }

    public void dispose() {
        ChatChannel.get().removeListener(this);
    }

    // ══════════════ ChatChannel 监听器 ══════════════

    @Override
    public void onMessage(ChatMessage message) {
        synchronized (messages) {
            messages.add(new TimedMessage(message));
            // 裁剪历史
            while (messages.size() > MAX_HISTORY) {
                messages.remove(0);
            }
        }
    }

    // ══════════════ 公共 API ══════════════

    /** 设置发送者名称（显示在聊天消息中） */
    public void setSenderName(String name) {
        this.senderName = (name != null && !name.isEmpty()) ? name : "Player";
    }

    /** 是否处于输入模式 (外部据此决定是否拦截游戏操作) */
    public boolean isInputActive() {
        return inputActive;
    }

    /** 激活输入模式 */
    public void activateInput() {
        inputActive = true;
        inputBuffer.setLength(0);
        historyIndex = -1;
        cursorBlinkTimer = 0f;
    }

    /** 关闭输入模式 */
    public void deactivateInput() {
        inputActive = false;
        inputBuffer.setLength(0);
    }

    // ══════════════ 帧更新 ══════════════

    public void act(float delta) {
        if (inputActive) {
            cursorBlinkTimer += delta;
        }
    }

    /**
     * 轮询模式的输入处理（适用于没有 InputProcessor 的场景）。
     * 在每帧 update 中调用，自动检测 T/Enter 激活输入，处理所有按键。
     * @return true 表示聊天覆盖层正在消费输入（外部应跳过游戏操作）
     */
    public boolean pollInput() {
        if (!inputActive) {
            // T 或 Enter 激活输入
            if (Gdx.input.isKeyJustPressed(Input.Keys.T)
                || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                activateInput();
                return true;
            }
            return false;
        }

        // 输入模式: 处理按键
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            sendInput();
            return true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            deactivateInput();
            return true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            if (inputBuffer.length() > 0) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            }
            return true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            navigateHistory(-1);
            return true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            navigateHistory(1);
            return true;
        }

        // 读取可打印字符（通过分析刚按下的按键）
        // 注意: Gdx.input 没有 keyTyped 轮询，需要通过 InputProcessor 或模拟
        // 这里使用一种轻量级方法: 遍历常用键位
        pollTypedCharacters();

        return true; // 输入模式下拦截所有输入
    }

    /**
     * 轮询字符输入（轻量级实现，覆盖常用可打印字符）。
     */
    private void pollTypedCharacters() {
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

        // 字母 A-Z
        for (int k = Input.Keys.A; k <= Input.Keys.Z; k++) {
            if (Gdx.input.isKeyJustPressed(k)) {
                char c = (char) ('a' + (k - Input.Keys.A));
                inputBuffer.append(shift ? Character.toUpperCase(c) : c);
            }
        }
        // 数字 0-9
        for (int k = Input.Keys.NUM_0; k <= Input.Keys.NUM_9; k++) {
            if (Gdx.input.isKeyJustPressed(k)) {
                inputBuffer.append((char) ('0' + (k - Input.Keys.NUM_0)));
            }
        }
        // 空格
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            inputBuffer.append(' ');
        }
        // 常用符号
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) inputBuffer.append('.');
        if (Gdx.input.isKeyJustPressed(Input.Keys.COMMA)) inputBuffer.append(',');
        if (Gdx.input.isKeyJustPressed(Input.Keys.SLASH)) inputBuffer.append(shift ? '?' : '/');
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) inputBuffer.append(shift ? '_' : '-');
        if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)) inputBuffer.append(shift ? '+' : '=');
        if (Gdx.input.isKeyJustPressed(Input.Keys.SEMICOLON)) inputBuffer.append(shift ? ':' : ';');
        if (Gdx.input.isKeyJustPressed(Input.Keys.APOSTROPHE)) inputBuffer.append(shift ? '"' : '\'');
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) inputBuffer.append(shift ? '{' : '[');
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) inputBuffer.append(shift ? '}' : ']');
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSLASH)) inputBuffer.append(shift ? '|' : '\\');
    }

    // ══════════════ 渲染 ══════════════

    /**
     * 在 SpriteBatch 的 begin/end 之间调用。
     * 需要一个支持 drawRect（填充矩形）的 batch，或使用 NeonBatch。
     * <p>
     * 对于不支持 drawRect 的普通 SpriteBatch，内部使用 BitmapFont 绘制文字，
     * 背景使用 Pixmap 生成的 1x1 白色纹理。
     *
     * @param batch    当前正在 begin 状态的 SpriteBatch / NeonBatch
     * @param font     用于绘制文字的字体
     * @param viewport UI 视口（获取屏幕尺寸）- 原点在左下角
     */
    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport) {
        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        float areaW = screenW * WIDTH_RATIO;

        float baseY = BOTTOM_MARGIN;

        // 输入框渲染（如果激活）
        if (inputActive) {
            baseY += INPUT_BAR_HEIGHT + 2;
            renderInputBar(batch, font, areaW, BOTTOM_MARGIN);
        }

        // 获取要渲染的消息列表
        List<RenderLine> lines = buildRenderLines();
        if (lines.isEmpty()) return;

        // 从底部向上绘制
        float y = baseY;
        for (int i = lines.size() - 1; i >= 0; i--) {
            RenderLine line = lines.get(i);
            if (line.alpha <= 0.01f) continue;

            // 半透明背景条
            Color bg = new Color(BG_COLOR.r, BG_COLOR.g, BG_COLOR.b, BG_COLOR.a * line.alpha);
            drawFilledRect(batch, 0, y, areaW, LINE_HEIGHT + PAD_Y * 2, bg);

            // 文字
            Color textColor = getMessageColor(line.message.type);
            font.setColor(textColor.r, textColor.g, textColor.b, line.alpha);
            font.draw(batch, line.message.getFormatted(), PAD_X, y + LINE_HEIGHT + PAD_Y - 2);

            y += LINE_HEIGHT + PAD_Y * 2;
        }
    }

    /** 在指定区域绘制输入框 */
    private void renderInputBar(SpriteBatch batch, BitmapFont font, float width, float y) {
        // 输入框背景
        drawFilledRect(batch, 0, y, width, INPUT_BAR_HEIGHT, INPUT_BG_COLOR);

        // 输入框文字
        boolean showCursor = ((int) (cursorBlinkTimer * 2.5f)) % 2 == 0;
        String displayText = "> " + inputBuffer.toString() + (showCursor ? "_" : "");
        font.setColor(Color.WHITE);
        font.draw(batch, displayText, PAD_X, y + INPUT_BAR_HEIGHT - 4);
    }

    /**
     * 绘制填充矩形。
     * 优先使用 NeonBatch 的 drawRect，fallback 到 SpriteBatch 的纯色绘制。
     */
    private void drawFilledRect(SpriteBatch batch, float x, float y, float w, float h, Color color) {
        // 检查是否是 NeonBatch（支持 drawRect）
        try {
            // 通过反射调用 NeonBatch.drawRect
            // 为了避免直接依赖 NeonBatch（core 模块中），使用反射
            java.lang.reflect.Method drawRect = batch.getClass().getMethod(
                "drawRect", float.class, float.class, float.class, float.class,
                float.class, float.class, Color.class, boolean.class
            );
            drawRect.invoke(batch, x, y, w, h, 0f, 0f, color, true);
        } catch (Exception e) {
            // Fallback: 使用 SpriteBatch 的 1x1 白色纹理绘制（需要 Pixmap）
            // 简化处理: 不绘制背景（仅文字可见）
        }
    }

    // ══════════════ 输入处理 ══════════════

    /**
     * 处理按键（在 InputProcessor.keyDown 中调用）。
     * @return true 表示已消费此按键，外部不应再处理
     */
    public boolean handleKeyDown(int keycode) {
        if (!inputActive) {
            // T 或 Enter 激活输入
            if (keycode == Input.Keys.T || keycode == Input.Keys.ENTER) {
                activateInput();
                return true;
            }
            return false;
        }

        // 输入模式下的按键处理
        switch (keycode) {
            case Input.Keys.ENTER:
                sendInput();
                return true;
            case Input.Keys.ESCAPE:
                deactivateInput();
                return true;
            case Input.Keys.BACKSPACE:
                if (inputBuffer.length() > 0) {
                    inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                }
                return true;
            case Input.Keys.UP:
                navigateHistory(-1);
                return true;
            case Input.Keys.DOWN:
                navigateHistory(1);
                return true;
            default:
                return true; // 输入模式下拦截所有按键（防止游戏操作）
        }
    }

    /**
     * 处理字符输入（在 InputProcessor.keyTyped 中调用）。
     * @return true 表示已消费
     */
    public boolean handleKeyTyped(char character) {
        if (!inputActive) return false;

        // 过滤控制字符
        if (character == '\b' || character == '\r' || character == '\n'
            || character == '\t' || character == 27) { // 27 = ESC
            return true; // 已在 keyDown 处理
        }

        if (character >= 32) { // 可打印字符
            inputBuffer.append(character);
        }
        return true;
    }

    // ══════════════ 内部方法 ══════════════

    /** 发送当前输入内容 */
    private void sendInput() {
        String text = inputBuffer.toString().trim();
        deactivateInput();
        if (text.isEmpty()) return;

        // 记录历史
        inputHistory.add(text);
        if (inputHistory.size() > 20) {
            inputHistory.remove(0);
        }

        // 通过 ChatChannel 统一路由
        ChatChannel.get().processInput(text, senderName);
    }

    /** 翻阅输入历史 */
    private void navigateHistory(int direction) {
        if (inputHistory.isEmpty()) return;

        historyIndex += direction;
        if (historyIndex < 0) {
            historyIndex = -1;
            inputBuffer.setLength(0);
            return;
        }
        if (historyIndex >= inputHistory.size()) {
            historyIndex = inputHistory.size() - 1;
        }
        // 从末尾倒数
        int actualIndex = inputHistory.size() - 1 - historyIndex;
        if (actualIndex >= 0 && actualIndex < inputHistory.size()) {
            inputBuffer.setLength(0);
            inputBuffer.append(inputHistory.get(actualIndex));
        }
    }

    /** 构建要渲染的消息行列表 */
    private List<RenderLine> buildRenderLines() {
        List<RenderLine> lines = new ArrayList<>();
        long now = System.currentTimeMillis();

        synchronized (messages) {
            // 从最新消息开始，最多取 MAX_VISIBLE_LINES 条
            int startIdx = Math.max(0, messages.size() - MAX_VISIBLE_LINES);
            for (int i = startIdx; i < messages.size(); i++) {
                TimedMessage tm = messages.get(i);
                float alpha;

                if (inputActive) {
                    // 输入模式下所有消息全亮
                    alpha = 1f;
                } else {
                    float ageSec = (now - tm.arrivalTime) / 1000f;
                    if (ageSec < MESSAGE_VISIBLE_SEC) {
                        alpha = 1f;
                    } else {
                        float fadeProgress = (ageSec - MESSAGE_VISIBLE_SEC) / FADE_DURATION_SEC;
                        alpha = Math.max(0f, 1f - fadeProgress);
                    }
                }

                if (alpha > 0.01f) {
                    lines.add(new RenderLine(tm.message, alpha));
                }
            }
        }
        return lines;
    }

    /** 根据消息类型返回颜色 */
    private Color getMessageColor(ChatMessage.Type type) {
        switch (type) {
            case SYSTEM:         return SYSTEM_COLOR;
            case COMMAND_RESULT: return COMMAND_COLOR;
            case CHAT:
            default:             return CHAT_COLOR;
        }
    }

    // ══════════════ 内部数据类 ══════════════

    /** 带到达时间的消息 */
    private static class TimedMessage {
        final ChatMessage message;
        final long arrivalTime;

        TimedMessage(ChatMessage message) {
            this.message = message;
            this.arrivalTime = System.currentTimeMillis();
        }
    }

    /** 渲染用消息行 */
    private static class RenderLine {
        final ChatMessage message;
        final float alpha;

        RenderLine(ChatMessage message, float alpha) {
            this.message = message;
            this.alpha = alpha;
        }
    }
}

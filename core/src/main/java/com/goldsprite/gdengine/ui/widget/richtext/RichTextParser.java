package com.goldsprite.gdengine.ui.widget.richtext;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Colors;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RichTextParser {

	private static final Pattern TAG_PATTERN = Pattern.compile("(\\[.*?\\])");

	public static List<RichElement> parse(String text, RichStyle defaultStyle) {
		List<RichElement> elements = new ArrayList<>();
		Stack<RichStyle> styleStack = new Stack<>();

		// Push default style
		styleStack.push(defaultStyle != null ? defaultStyle.copy() : new RichStyle());

		if (text == null || text.isEmpty()) return elements;

		// Split by tags
		Matcher matcher = TAG_PATTERN.matcher(text);
		int lastEnd = 0;

		while (matcher.find()) {
			// Text before tag
			if (matcher.start() > lastEnd) {
				String sub = text.substring(lastEnd, matcher.start());
				if (!sub.isEmpty()) {
					elements.add(new RichElement(sub, styleStack.peek()));
				}
			}

			// The tag
			String tag = matcher.group(1);
			processTag(tag, styleStack, elements);

			lastEnd = matcher.end();
		}

		// Remaining text
		if (lastEnd < text.length()) {
			elements.add(new RichElement(text.substring(lastEnd), styleStack.peek()));
		}

		return elements;
	}

	private static void processTag(String tag, Stack<RichStyle> stack, List<RichElement> elements) {
		// Strip [ and ]
		String content = tag.substring(1, tag.length() - 1);

		if (content.startsWith("/")) {
			// End tag, pop style
			// Only pop if it matches the current stack expectation?
			// For simplicity, just pop if stack > 1
			if (stack.size() > 1) {
				stack.pop();
			}
			return;
		}

		// Start tag or self-closing tag
		RichStyle current = stack.peek().copy();
		boolean pushed = false;

		if (content.startsWith("color=")) {
			String val = content.substring(6);
			Color c = parseColor(val);
			if (c != null) current.color = c;
			stack.push(current);
			pushed = true;
		}
		else if (content.startsWith("#")) { // Short color [#RRGGBB]
			 Color c = parseColor(content); // parseColor handles #
			 if (c != null) current.color = c;
			 stack.push(current);
			 pushed = true;
		}
		else if (content.startsWith("size=")) {
			try {
				float size = Float.parseFloat(content.substring(5));
				current.fontSize = size;
				stack.push(current);
				pushed = true;
			} catch(Exception e) {}
		}
		else if (content.startsWith("event=")) {
			current.event = content.substring(6);
			stack.push(current);
			pushed = true;
		}
		else if (content.startsWith("img=")) {
			// [img=path] or [img=path|32x32] or [img=path#region|32x32]
			String val = content.substring(4);
			String[] parts = val.split("\\|");

			// Parse path and optional region (#)
			String fullPath = parts[0];
			String path = fullPath;
			String regionName = null;

			int hashIndex = fullPath.lastIndexOf('#');
			if (hashIndex != -1) {
				path = fullPath.substring(0, hashIndex);
				regionName = fullPath.substring(hashIndex + 1);
			}

			float w = 0, h = 0;
			if (parts.length > 1) {
				String[] size = parts[1].split("x");
				if (size.length == 2) {
					 try {
						w = Float.parseFloat(size[0]);
						h = Float.parseFloat(size[1]);
					 } catch(Exception e){}
				}
			}

			elements.add(new RichElement(path, regionName, w, h, current));
		}
		else if (content.equals("br") || content.equals("n")) {
			 elements.add(new RichElement("\n", current));
		}

		// If it was a start tag, we pushed. If it was img/br, we didn't push.
	}

	private static Color parseColor(String str) {
		try {
			return Color.valueOf(str);
		} catch (Exception e) {
			Color c = Colors.get(str);
			if (c == null) {
				c = Colors.get(str.toUpperCase());
			}
			return c;
		}
	}
}

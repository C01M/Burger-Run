package com.c01m.burgerrun.engine;

import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immediate-mode 2D helpers over the SpriteBatch, in logical 1280x720 y-down coordinates. */
public final class G {
    private G() {}

    public static final int WIDTH = 1280, HEIGHT = 720;
    public static final int CREAM = 0xFFFFF4DC, KETCHUP = 0xFFD8392B, MUSTARD = 0xFFF5B82E, TEAL = 0xFF1F8A8A, DARK = 0xFF3A2416,
            BROWN = 0xFF6B4226, WHITE = 0xFFFFFFFF, GREEN = 0xFF46A33E, GREY = 0xFF8E8678, INK = 0xFF2A1A10;

    public static SpriteBatch batch;
    public static Sprites sprites;
    /** Visible world edges: wider than 0..WIDTH on screens longer than 16:9. */
    public static float left = 0, right = WIDTH;

    private static TextureRegion white, disc;
    private static final TextureRegion[] round = new TextureRegion[9], ring = new TextureRegion[9];
    private static final int ROUND = 32;

    // ---------------------------------------------------------------- setup

    public static void init(SpriteBatch b, Sprites s) {
        batch = b;
        sprites = s;
        // Android can rebuild the app in the same process; glyphs cached for the old atlas would
        // otherwise point at disposed textures whose ids the new atlas reuses
        for (Font f : new Font[]{BOLD_S, BOLD_L, REG}) f.glyphs.clear();
        // sample only the centre texels: stretching a region whose edge touches the transparent atlas
        // padding blurs the edges of large rectangles
        TextureRegion w6 = s.get("white", 6, 6, 1, c -> c.drawColor(0xFFFFFFFF));
        white = new TextureRegion(w6.getTexture(), w6.getRegionX() + 2, Math.round(w6.getV2() * w6.getTexture().getHeight()) + 2, 2, 2);
        white.flip(false, true);
        disc = s.get("disc", 128, 128, 1, c -> {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(64, 64, 63, p);
        });
        TextureRegion rr = s.get("round", ROUND * 2 + 4, ROUND * 2 + 4, 1, c -> {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            c.drawRoundRect(new RectF(0, 0, ROUND * 2 + 4, ROUND * 2 + 4), ROUND, ROUND, p);
        });
        TextureRegion rl = s.get("ringrect", ROUND * 2 + 4, ROUND * 2 + 4, 1, c -> {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            c.drawRoundRect(new RectF(4, 4, ROUND * 2, ROUND * 2), ROUND - 4, ROUND - 4, p);
        });
        slice(rr, round);
        slice(rl, ring);
    }

    private static void slice(TextureRegion base, TextureRegion[] out) {
        int bx = base.getRegionX();
        // the region is flipped, so v2 holds the page row of its top edge
        int by = Math.round(base.getV2() * base.getTexture().getHeight());
        int[] xs = {0, ROUND, ROUND + 4}, ws = {ROUND, 4, ROUND};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                TextureRegion t = new TextureRegion(base.getTexture(), bx + xs[col], by + xs[row], ws[col], ws[row]);
                t.flip(false, true);
                out[row * 3 + col] = t;
            }
        }
    }

    public static void color(int argb) {
        float a = (argb >>> 24) / 255f;
        batch.setColor(((argb >> 16) & 255) / 255f * a, ((argb >> 8) & 255) / 255f * a, (argb & 255) / 255f * a, a);
    }

    public static void tint(float alpha) { batch.setColor(alpha, alpha, alpha, alpha); }

    // ---------------------------------------------------------------- shapes

    public static void rect(float x, float y, float w, float h, int argb) {
        color(argb);
        batch.draw(white, x, y, w, h);
    }

    private static void nine(TextureRegion[] t, float x, float y, float w, float h, float rad) {
        rad = Math.min(rad, Math.min(w, h) / 2);
        float mw = w - rad * 2, mh = h - rad * 2;
        float[] xs = {x, x + rad, x + w - rad}, ws = {rad, mw, rad}, ys = {y, y + rad, y + h - rad}, hs = {rad, mh, rad};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (ws[c] > 0 && hs[r] > 0) batch.draw(t[r * 3 + c], xs[c], ys[r], ws[c], hs[r]);
            }
        }
    }

    public static void rrect(float x, float y, float w, float h, float rad, int argb) {
        color(argb);
        nine(round, x, y, w, h, rad);
    }

    public static void rrectLine(float x, float y, float w, float h, float rad, int argb) {
        color(argb);
        nine(ring, x, y, w, h, Math.max(rad, 6));
    }

    public static void panel(float x, float y, float w, float h, float rad, int fill, int border) {
        rrect(x, y + 4, w, h, rad, 0x44000000);
        rrect(x, y, w, h, rad, border);
        rrect(x + 3, y + 3, w - 6, h - 6, Math.max(1, rad - 3), fill);
    }

    public static void circle(float cx, float cy, float rad, int argb) { oval(cx, cy, rad, rad, argb); }

    public static void oval(float cx, float cy, float rx, float ry, int argb) {
        color(argb);
        batch.draw(disc, cx - rx, cy - ry, rx * 2, ry * 2);
    }

    public static void line(float x0, float y0, float x1, float y1, float width, int argb) {
        color(argb);
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float deg = (float) Math.toDegrees(Math.atan2(dy, dx));
        batch.draw(white, x0, y0 - width / 2, 0, width / 2, len, width, 1, 1, deg);
    }

    public static void bar(float x, float y, float w, float h, double frac, int fg, int bg) {
        rrect(x, y, w, h, h / 2, bg);
        float fw = (float) (w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) rrect(x, y, Math.max(h, fw), h, h / 2, fg);
    }

    /** A full-screen background stretched across the whole visible width. */
    public static void backdrop(TextureRegion t) {
        tint(1);
        batch.draw(t, left, 0, right - left, HEIGHT);
    }

    public static void sprite(TextureRegion t, float x, float y, float w, float h) {
        tint(1);
        batch.draw(t, x, y, w, h);
    }

    public static void sprite(TextureRegion t, float x, float y, float w, float h, float alpha) {
        tint(alpha);
        batch.draw(t, x, y, w, h);
    }

    public static void spriteTinted(TextureRegion t, float x, float y, float w, float h, int argb) {
        color(argb);
        batch.draw(t, x, y, w, h);
    }

    public static void spriteRot(TextureRegion t, float cx, float cy, float w, float h, float deg) {
        tint(1);
        batch.draw(t, cx - w / 2, cy - h / 2, w / 2, h / 2, w, h, 1, 1, deg);
    }

    // ---------------------------------------------------------------- text

    private static final class Font {
        final float size;
        final Typeface face;
        final Map<Character, TextureRegion> glyphs = new HashMap<>();
        final Map<Character, Float> advances = new HashMap<>();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String key;
        Font(String key, Typeface face, float size) {
            this.key = key; this.face = face; this.size = size;
            paint.setTypeface(face);
            paint.setTextSize(size);
            paint.setColor(0xFFFFFFFF);
        }
        float advance(char ch) {
            Float a = advances.get(ch);
            if (a == null) {
                a = paint.measureText(String.valueOf(ch));
                advances.put(ch, a);
            }
            return a;
        }
        TextureRegion glyph(char ch) {
            TextureRegion g = glyphs.get(ch);
            if (g == null) {
                float w = advance(ch) + 4, h = size * 1.4f;
                String s = String.valueOf(ch);
                g = sprites.get(key + ch, w, h, 1, c -> c.drawText(s, 2, size * 1.05f, paint));
                glyphs.put(ch, g);
            }
            return g;
        }
    }

    private static final Font BOLD_S = new Font("bs", Typeface.create("sans-serif-black", Typeface.NORMAL), 30);
    private static final Font BOLD_L = new Font("bl", Typeface.create("sans-serif-black", Typeface.NORMAL), 72);
    private static final Font REG = new Font("rg", Typeface.create("sans-serif-medium", Typeface.NORMAL), 32);

    private static Font font(float size, boolean bold) {
        if (!bold) return REG;
        return size > 26 ? BOLD_L : BOLD_S;
    }

    public static float width(String s, float size, boolean bold) {
        Font f = font(size, bold);
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += f.advance(s.charAt(i));
        return w * size / f.size;
    }

    /** Text vertically centred on y. align: -1 left, 0 centre, 1 right. */
    public static void text(String s, float x, float y, float size, int argb, int align, boolean bold) {
        Font f = font(size, bold);
        float k = size / f.size;
        float cx = x;
        if (align == 0) cx -= width(s, size, bold) / 2;
        else if (align > 0) cx -= width(s, size, bold);
        float top = y - f.size * .70f * k;
        color(argb);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != ' ') batch.draw(f.glyph(ch), cx - 2 * k, top, (f.advance(ch) + 4) * k, f.size * 1.4f * k);
            cx += f.advance(ch) * k;
        }
    }

    /** Chunky outlined game text for titles and big numbers. */
    public static void title(String s, float x, float y, float size, int argb, int outline, int align) {
        float o = Math.max(1.5f, size * .06f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            text(s, x + (float) Math.cos(a) * o, y + (float) Math.sin(a) * o, size, outline, align, true);
        }
        text(s, x, y, size, argb, align, true);
    }

    public static float wrap(String s, float x, float y, float maxW, float size, int argb, float lineH) {
        for (String line : lines(s, maxW, size)) {
            text(line, x, y, size, argb, -1, false);
            y += lineH;
        }
        return y;
    }

    public static List<String> lines(String s, float maxW, float size) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            String attempt = cur.length() == 0 ? w : cur + " " + w;
            if (width(attempt, size, false) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(attempt);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ---------------------------------------------------------------- small utilities

    public static String money(double v) {
        long cents = Math.round(v * 100);
        if (cents % 100 == 0) return "$" + cents / 100;
        return String.format(java.util.Locale.US, "$%d.%02d", cents / 100, Math.abs(cents % 100));
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}

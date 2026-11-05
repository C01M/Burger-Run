package com.c01m.burgerrun.engine;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.c01m.burgerrun.sim.Content;

import java.util.Random;

/** Original cartoon art, drawn as vectors and baked into atlases by Sprites. */
public final class Art {
    private Art() {}

    public static final float LAYER_W = 130;
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint L = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int OUTLINE = 0xFF3A2416;

    static { L.setStyle(Paint.Style.STROKE); L.setStrokeCap(Paint.Cap.ROUND); L.setStrokeJoin(Paint.Join.ROUND); }

    private static void f(int color) { P.setColor(color); P.setShader(null); }
    private static void s(int color, float w) { L.setColor(color); L.setStrokeWidth(w); }
    private static void oval(Canvas c, float l, float t, float r, float b, int color) { f(color); c.drawOval(new RectF(l, t, r, b), P); }
    private static void ovalOut(Canvas c, float l, float t, float r, float b, int color, float sw) {
        oval(c, l, t, r, b, color);
        s(OUTLINE, sw);
        c.drawOval(new RectF(l, t, r, b), L);
    }
    private static void rr(Canvas c, float l, float t, float r, float b, float rad, int color) { f(color); c.drawRoundRect(new RectF(l, t, r, b), rad, rad, P); }
    private static void rrOut(Canvas c, float l, float t, float r, float b, float rad, int color, float sw) {
        rr(c, l, t, r, b, rad, color);
        s(OUTLINE, sw);
        c.drawRoundRect(new RectF(l, t, r, b), rad, rad, L);
    }

    // ---------------------------------------------------------------- burger layers (side view)

    public static float layerHeight(String id) {
        switch (id) {
            case "bunT": return 50;
            case "bunB": return 26;
            case "patty": return 22;
            case "lettuce": return 16;
            case "tomato": return 14;
            case "avocado": case "mushroom": case "olive": return 14;
            case "ketchup": case "mustard": case "mayo": case "bbq": case "hot": return SAUCE_H;
            default: return 12;
        }
    }

    private static final float SAUCE_H = 22, SAUCE_TOP = 2.5f, SAUCE_BASE = 6.5f;

    private static boolean isSauce(String id) {
        switch (id) {
            case "ketchup": case "mustard": case "mayo": case "bbq": case "hot": return true;
            default: return false;
        }
    }

    /** How far the next layer overlaps this one when stacked. Sauce is thin, so it barely adds height. */
    public static float layerStep(String id) {
        if (isSauce(id)) return 4;
        return layerHeight(id) * (id.equals("bunB") || id.equals("patty") ? .8f : .68f);
    }

    /** How far a layer's sprite hangs below the stack line: sauce drips run down over the layer beneath. */
    public static float layerSink(String id) {
        // the puddle rests on top of the layer below; only the drips hang down over it
        return isSauce(id) ? SAUCE_H - SAUCE_BASE - 1.5f : 0;
    }

    public static TextureRegion layer(String id) {
        float h = layerHeight(id);
        return G.sprites.get("layer_" + id, LAYER_W, h, c -> paintLayer(c, id, h));
    }

    private static void paintLayer(Canvas c, String id, float h) {
        float w = LAYER_W;
        switch (id) {
            case "bunB":
                rrOut(c, 8, 2, w - 8, h - 2, 11, 0xFFE9B566, 2);
                rr(c, 11, 3, w - 11, 10, 4, 0xFFF7DBA0);
                break;
            case "bunT": {
                Path p = new Path();
                p.moveTo(6, h - 4);
                p.cubicTo(8, 2, w - 8, 2, w - 6, h - 4);
                p.quadTo(w / 2, h + 1, 6, h - 4);
                f(0xFFE5A650);
                c.drawPath(p, P);
                s(OUTLINE, 2);
                c.drawPath(p, L);
                f(0xFFF3C77F);
                c.drawOval(new RectF(26, 9, 72, 22), P);
                Random r = new Random(4);
                for (int i = 0; i < 16; i++) {
                    float x = 24 + r.nextFloat() * (w - 48), y = 12 + r.nextFloat() * 24;
                    f(0xFFFFF6DC);
                    c.drawOval(new RectF(x, y, x + 6, y + 3.5f), P);
                }
                break;
            }
            case "patty":
                // greyscale so doneness can be applied as a tint
                rrOut(c, 8, 2, w - 8, h - 2, 9, 0xFFFFFFFF, 2);
                f(0x30000000);
                Random r = new Random(8);
                for (int i = 0; i < 22; i++) c.drawCircle(13 + r.nextFloat() * (w - 26), 5 + r.nextFloat() * (h - 10), 1.6f, P);
                break;
            case "lettuce": {
                Path p = new Path();
                p.moveTo(2, 5);
                for (int x = 2; x < w - 2; x += 14) {
                    p.quadTo(x + 7, h + 3, x + 14, 6);
                }
                p.quadTo(w / 2, -3, 2, 5);
                f(0xFF7FCC45);
                c.drawPath(p, P);
                s(0xFF3F8A22, 2);
                c.drawPath(p, L);
                break;
            }
            case "tomato":
                rrOut(c, 10, 2, w / 2 + 2, h - 2, 5, 0xFFE0442E, 1.5f);
                rrOut(c, w / 2 - 2, 2, w - 10, h - 2, 5, 0xFFEA5A40, 1.5f);
                rr(c, 16, 4, 40, 7, 2, 0xFFFF9A84);
                rr(c, w / 2 + 6, 4, w / 2 + 30, 7, 2, 0xFFFF9A84);
                break;
            case "onion":
                for (int i = 0; i < 4; i++) {
                    float x = 8 + i * 29;
                    s(0xFF9A5FC0, 3.4f);
                    c.drawOval(new RectF(x, 2, x + 28, h - 2), L);
                    s(0xFFEEDDF6, 1.4f);
                    c.drawOval(new RectF(x, 2, x + 28, h - 2), L);
                }
                break;
            case "pickle":
                for (int i = 0; i < 4; i++) {
                    float x = 10 + i * 28;
                    ovalOut(c, x, 1, x + 27, h - 1, 0xFF8AA832, 1.4f);
                    oval(c, x + 7, 4, x + 20, h - 4, 0xFFB7CC5C);
                }
                break;
            case "olive":
                for (int i = 0; i < 5; i++) {
                    float x = 9 + i * 23;
                    ovalOut(c, x, 2, x + 20, h - 2, 0xFF34313C, 1.2f);
                    oval(c, x + 6, 5, x + 14, h - 5, 0xFFC9A64A);
                }
                break;
            case "jalapeno":
                for (int i = 0; i < 5; i++) {
                    float x = 9 + i * 23;
                    ovalOut(c, x, 1, x + 21, h - 1, 0xFF2FA84F, 1.3f);
                    oval(c, x + 6, 4, x + 15, h - 4, 0xFFD8EFA0);
                    f(0xFFFFF4C2);
                    c.drawCircle(x + 9, h / 2, 1.3f, P);
                    c.drawCircle(x + 13, h / 2, 1.3f, P);
                }
                break;
            case "avocado":
                for (int i = 0; i < 3; i++) {
                    float x = 8 + i * 38;
                    rrOut(c, x, 2, x + 38, h - 2, 6, 0xFFC3DA62, 1.4f);
                    rr(c, x + 3, h - 6, x + 35, h - 3, 2, 0xFF5D8A2D);
                }
                break;
            case "mushroom":
                for (int i = 0; i < 4; i++) {
                    float x = 9 + i * 28;
                    ovalOut(c, x, 1, x + 27, h - 3, 0xFFC7AA8A, 1.3f);
                    rr(c, x + 10, h - 7, x + 17, h - 1, 2, 0xFFEFE2CE);
                }
                break;
            default:
                paintSauce(c, id, w, h);
        }
    }

    /** A glossy sheet of sauce with a wavy top and rounded drips hanging off its front edge. */
    private static void paintSauce(Canvas c, String id, float w, float h) {
        Content.Ingredient ing = Content.ingredient(id);
        int col = ing == null ? 0xFFCC0000 : ing.color;
        int lum = ((col >> 16) & 255) + ((col >> 8) & 255) + (col & 255);
        int dark = lum > 600 ? 0xFFB3A57A : G.mix(col, 0xFF000000, .38f);
        int light = G.mix(col, 0xFFFFFFFF, lum > 600 ? .6f : .32f);
        float top = SAUCE_TOP, base = SAUCE_BASE;
        Random r = new Random(id.hashCode());

        // a thin puddle with a blobby outline...
        Path sheet = new Path();
        sheet.moveTo(14, base);
        sheet.cubicTo(8, base, 8, top + .5f, 16, top + .8f);
        for (float x = 16; x < w - 16; ) {
            float nx = Math.min(w - 16, x + 11 + r.nextFloat() * 6);
            sheet.quadTo((x + nx) / 2, top - .8f + r.nextFloat() * 1.4f, nx, top + .6f + r.nextFloat() * .8f);
            x = nx;
        }
        sheet.cubicTo(w - 8, top + .5f, w - 8, base, w - 14, base);
        for (float x = w - 14; x > 14; ) {
            float nx = Math.max(14, x - 9 - r.nextFloat() * 6);
            sheet.quadTo((x + nx) / 2, base + .8f + r.nextFloat() * 1.6f, nx, base + r.nextFloat() * .6f);
            x = nx;
        }
        sheet.close();
        // ...merged with teardrop drips: a tapering neck and a round bead at the end
        int n = 4 + r.nextInt(2);
        float[] dx = new float[n], bead = new float[n], cy = new float[n];
        Path shape = new Path(sheet);
        for (int i = 0; i < n; i++) {
            dx[i] = 22 + (w - 44) * (i + .2f + r.nextFloat() * .6f) / n;
            bead[i] = 2.1f + r.nextFloat() * .9f;
            cy[i] = Math.min(h - bead[i] - .6f, base + 2 + r.nextFloat() * (h - base - 5));
            float neck = bead[i] * .86f;
            Path drip = new Path();
            drip.moveTo(dx[i] - neck - 2, base - 1);
            drip.quadTo(dx[i] - neck * .8f, base + 1.2f, dx[i] - neck * .85f, cy[i]);
            drip.lineTo(dx[i] + neck * .85f, cy[i]);
            drip.quadTo(dx[i] + neck * .8f, base + 1.2f, dx[i] + neck + 2, base - 1);
            drip.close();
            drip.addCircle(dx[i], cy[i], bead[i], Path.Direction.CW);
            shape.op(drip, Path.Op.UNION);
        }

        P.setShader(new LinearGradient(0, top - 1, 0, h, new int[]{light, col, col, dark}, new float[]{0, .25f, .6f, 1}, Shader.TileMode.CLAMP));
        c.drawPath(shape, P);
        P.setShader(null);
        s(dark, 1.2f);
        c.drawPath(shape, L);
        // wet shine: a broken streak along the puddle and a glint on each bead
        f(0xC8FFFFFF);
        float sx = 20;
        while (sx < w - 30) {
            float len = 8 + r.nextFloat() * 14;
            c.drawRoundRect(new RectF(sx, top + 1.3f, sx + len, top + 2.6f), 1, 1, P);
            sx += len + 5 + r.nextFloat() * 8;
        }
        for (int i = 0; i < n; i++) c.drawCircle(dx[i] - bead[i] * .35f, cy[i] - bead[i] * .2f, bead[i] * .28f, P);
    }

    public static int pattyTint(double p) {
        int[] stops = {0xFFEF9A9A, 0xFFD98C78, 0xFFB57748, 0xFF8A5530, 0xFF5D3820, 0xFF2A1A12};
        double t = Math.max(0, Math.min(1.0, p)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) t);
        return G.mix(stops[i], stops[i + 1], (float) (t - i));
    }

    public static TextureRegion pattyTop() {
        return G.sprites.get("pattyTop", 104, 72, c -> {
            ovalOut(c, 3, 3, 101, 69, 0xFFFFFFFF, 3);
            f(0x26000000);
            Random r = new Random(3);
            for (int i = 0; i < 40; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble());
                c.drawCircle((float) (52 + Math.cos(a) * d * 42), (float) (36 + Math.sin(a) * d * 26), 2.2f, P);
            }
        });
    }

    public static TextureRegion grillMarks() {
        return G.sprites.get("grillMarks", 104, 72, c -> {
            s(0xFF000000, 5);
            for (int i = 0; i < 4; i++) c.drawLine(20 + i * 20, 54, 38 + i * 20, 18, L);
        });
    }

    // ---------------------------------------------------------------- supplies and food

    public static TextureRegion bottle(String id) {
        return G.sprites.get("bottle_" + id, 40, 74, c -> {
            int col = Content.ingredient(id).color;
            rrOut(c, 6, 22, 34, 72, 8, col, 2);
            Path nozzle = new Path();
            nozzle.moveTo(14, 22); nozzle.lineTo(18, 3); nozzle.lineTo(22, 3); nozzle.lineTo(26, 22); nozzle.close();
            f(G.mix(col, 0xFFFFFFFF, .25f));
            c.drawPath(nozzle, P);
            s(OUTLINE, 2);
            c.drawPath(nozzle, L);
            rr(c, 10, 34, 30, 58, 3, 0xCCFFFFFF);
            f(0x44FFFFFF);
            c.drawRect(9, 26, 13, 68, P);
        });
    }

    public static TextureRegion drink(String id) {
        return G.sprites.get("drink_" + id, 64, 110, c -> {
            Content.Drink d = Content.drink(id);
            Path glass = new Path();
            glass.moveTo(8, 24); glass.lineTo(14, 106); glass.lineTo(50, 106); glass.lineTo(56, 24); glass.close();
            f(d.color);
            c.drawPath(glass, P);
            f(d.label);
            c.drawRect(12, 52, 53, 80, P);
            f(0xFFFFFFFF);
            c.drawCircle(32, 66, 9, P);
            f(d.label);
            c.drawCircle(32, 66, 5, P);
            f(0x38FFFFFF);
            c.drawRect(15, 28, 21, 102, P);
            s(OUTLINE, 2.5f);
            c.drawPath(glass, L);
            rrOut(c, 4, 16, 60, 26, 3, 0xFFF4F4F4, 2);
            s(0xFFD8392B, 4.5f);
            c.drawLine(38, 16, 46, 0, L);
        });
    }

    public static TextureRegion friesBox(int stage) {
        return G.sprites.get("fries_" + stage, 76, 88, c -> {
            int[] cols = {0xFFF4E9B8, 0xFFF2DA8C, 0xFFF3B93B, 0xFFB57C2B, 0xFF4A3018};
            Random r = new Random(6);
            for (int i = 0; i < 18; i++) {
                float x = 14 + r.nextFloat() * 44, top = 2 + r.nextFloat() * 22;
                rrOut(c, x, top, x + 8, 56, 2, G.mix(cols[stage], 0xFFFFFFFF, r.nextFloat() * .15f), 1);
            }
            Path box = new Path();
            box.moveTo(8, 38); box.quadTo(38, 52, 68, 38); box.lineTo(60, 86); box.lineTo(16, 86); box.close();
            f(0xFFD8392B);
            c.drawPath(box, P);
            s(OUTLINE, 2.5f);
            c.drawPath(box, L);
            f(0xFFF5B82E);
            c.drawCircle(38, 66, 11, P);
        });
    }

    public static TextureRegion friesPile(int stage) {
        return G.sprites.get("pile_" + stage, 110, 60, c -> {
            int[] cols = {0xFFF4E9B8, 0xFFF2DA8C, 0xFFF3B93B, 0xFFB57C2B, 0xFF4A3018};
            Random r = new Random(9);
            for (int i = 0; i < 34; i++) {
                float x = 8 + r.nextFloat() * 90, y = 8 + r.nextFloat() * 42;
                c.save();
                c.rotate(r.nextFloat() * 120 - 60, x, y);
                rrOut(c, x - 3, y - 13, x + 3, y + 13, 2, G.mix(cols[stage], 0xFFFFFFFF, r.nextFloat() * .15f), 1);
                c.restore();
            }
        });
    }

    public static TextureRegion rawBag() {
        return G.sprites.get("rawBag", 96, 110, c -> {
            Random r = new Random(11);
            for (int i = 0; i < 12; i++) {
                float x = 20 + r.nextFloat() * 50;
                rrOut(c, x, 2, x + 8, 44, 2, 0xFFF4E9B8, 1);
            }
            rrOut(c, 8, 28, 88, 108, 8, 0xFF4F87C7, 2.5f);
            rr(c, 18, 54, 78, 90, 4, 0xFFFFF4DC);
        });
    }

    public static TextureRegion cartons() {
        return G.sprites.get("cartons", 96, 110, c -> {
            for (int i = 0; i < 3; i++) {
                float o = i * 12;
                Path box = new Path();
                box.moveTo(16, 18 + o); box.quadTo(48, 28 + o, 80, 18 + o); box.lineTo(70, 92 + o); box.lineTo(26, 92 + o); box.close();
                f(i == 2 ? 0xFFD8392B : 0xFFB72C20);
                c.drawPath(box, P);
                s(OUTLINE, 2.5f);
                c.drawPath(box, L);
            }
        });
    }

    public static TextureRegion rawPatties() {
        return G.sprites.get("rawPatties", 118, 118, c -> {
            rrOut(c, 4, 34, 114, 114, 10, 0xFF9DC3D8, 3);
            for (int i = 0; i < 3; i++) {
                float y = 10 + i * 18;
                ovalOut(c, 16, y, 102, y + 42, 0xFFEF9A9A, 2.5f);
            }
            rr(c, 8, 82, 110, 112, 8, 0xFFB7D6E6);
        });
    }

    public static TextureRegion veggieBin(String id) {
        return G.sprites.get("bin_" + id, 84, 72, c -> {
            rrOut(c, 2, 2, 82, 70, 10, 0xFFEFEFEA, 2.5f);
            rr(c, 7, 8, 77, 64, 7, 0xFFCFCAB8);
            for (int i = 0; i < 3; i++) {
                c.save();
                c.translate(9, 14 + i * 13);
                c.scale(.51f, 1);
                paintLayer(c, id, layerHeight(id));
                c.restore();
            }
        });
    }

    public static TextureRegion wrench() { return emoji("🔧", 40); }

    public static TextureRegion emoji(String e, float size) {
        return G.sprites.get("emoji_" + e, size, size * 1.2f, 2, c -> {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextSize(size * .86f);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(e, size / 2, size * .92f, p);
        });
    }

    // ---------------------------------------------------------------- customers

    /** mood: 0 delighted, 1 content, 2 worried, 3 angry */
    public static TextureRegion customer(Content.CustomerDef d, int mood) {
        return G.sprites.get("cust_" + d.id + "_" + mood, 130, 170, c -> paintCustomer(c, d, mood));
    }

    private static void paintCustomer(Canvas c, Content.CustomerDef d, int mood) {
        // shoulders
        Path body = new Path();
        body.moveTo(14, 170);
        body.cubicTo(14, 112, 40, 104, 65, 104);
        body.cubicTo(90, 104, 116, 112, 116, 170);
        body.close();
        f(d.shirt);
        c.drawPath(body, P);
        s(OUTLINE, 3);
        c.drawPath(body, L);
        rr(c, 54, 92, 76, 114, 8, d.skin);

        hairBack(c, d);
        ovalOut(c, 28, 26, 102, 106, d.skin, 3);
        ovalOut(c, 22, 60, 34, 78, d.skin, 2.5f);
        ovalOut(c, 96, 60, 108, 78, d.skin, 2.5f);
        hairFront(c, d);
        face(c, d, mood);
        accessory(c, d);
    }

    private static void hairBack(Canvas c, Content.CustomerDef d) {
        int h = d.hairColor;
        switch (d.hair) {
            case AFRO: ovalOut(c, 8, 2, 122, 96, h, 3); break;
            case LONG: rrOut(c, 20, 22, 110, 130, 34, h, 3); break;
            case CURLY:
                for (int i = 0; i < 9; i++) {
                    double a = Math.PI * (.95 + i * .14);
                    float x = (float) (65 + Math.cos(a) * 46), y = (float) (58 + Math.sin(a) * 42);
                    ovalOut(c, x - 17, y - 17, x + 17, y + 17, h, 2.5f);
                }
                rrOut(c, 16, 54, 34, 104, 9, h, 2.5f);
                rrOut(c, 96, 54, 114, 104, 9, h, 2.5f);
                break;
            case PUFFS:
                ovalOut(c, 2, 8, 44, 50, h, 3);
                ovalOut(c, 86, 8, 128, 50, h, 3);
                break;
            case BOB: rrOut(c, 20, 18, 110, 96, 30, h, 3); break;
            case BUN: ovalOut(c, 46, 0, 84, 34, h, 3); break;
            default:
        }
    }

    private static void hairFront(Canvas c, Content.CustomerDef d) {
        int h = d.hairColor;
        Path p = new Path();
        switch (d.hair) {
            case AFRO: case CURLY: case PUFFS: case BUN: case BOB: case LONG:
                p.moveTo(28, 62);
                p.cubicTo(30, 16, 100, 16, 102, 62);
                p.cubicTo(84, 44, 46, 44, 28, 62);
                break;
            case SPIKE:
                p.moveTo(28, 58);
                p.lineTo(30, 26); p.lineTo(42, 34); p.lineTo(48, 12); p.lineTo(60, 28); p.lineTo(70, 8);
                p.lineTo(78, 28); p.lineTo(92, 16); p.lineTo(94, 36); p.lineTo(102, 34); p.lineTo(102, 58);
                p.cubicTo(84, 44, 46, 44, 28, 58);
                break;
            case SIDE:
                p.moveTo(28, 62);
                p.cubicTo(24, 14, 104, 12, 102, 60);
                p.cubicTo(90, 40, 64, 36, 46, 44);
                p.cubicTo(38, 46, 32, 52, 28, 62);
                break;
            case MOHAWK:
                p.moveTo(54, 40);
                p.lineTo(50, 12); p.lineTo(58, 18); p.lineTo(62, 2); p.lineTo(68, 18); p.lineTo(76, 8); p.lineTo(78, 40);
                p.close();
                break;
            case BALD:
            default:
                return;
        }
        f(h);
        c.drawPath(p, P);
        s(OUTLINE, 2.5f);
        c.drawPath(p, L);
    }

    private static void face(Canvas c, Content.CustomerDef d, int mood) {
        float ey = 64;
        oval(c, 46, ey - 8, 60, ey + 8, 0xFFFFFFFF);
        oval(c, 70, ey - 8, 84, ey + 8, 0xFFFFFFFF);
        float look = mood == 3 ? 0 : 1;
        oval(c, 50 + look, ey - 4, 58 + look, ey + 5, OUTLINE);
        oval(c, 74 + look, ey - 4, 82 + look, ey + 5, OUTLINE);
        s(OUTLINE, 3.2f);
        if (mood == 3) {
            c.drawLine(43, ey - 16, 60, ey - 9, L);
            c.drawLine(87, ey - 16, 70, ey - 9, L);
        } else if (mood == 2) {
            c.drawLine(44, ey - 11, 59, ey - 15, L);
            c.drawLine(86, ey - 11, 71, ey - 15, L);
        } else {
            c.drawLine(44, ey - 13, 60, ey - 15, L);
            c.drawLine(70, ey - 15, 86, ey - 13, L);
        }
        s(G.mix(d.skin, OUTLINE, .5f), 3);
        c.drawLine(65, 70, 63, 79, L);
        Path m = new Path();
        s(OUTLINE, 3.4f);
        switch (mood) {
            case 0:
                m.moveTo(48, 84); m.quadTo(65, 102, 82, 84); m.close();
                f(0xFF8A2A22);
                c.drawPath(m, P);
                c.drawPath(m, L);
                break;
            case 1:
                m.moveTo(52, 88); m.quadTo(65, 96, 78, 88);
                c.drawPath(m, L);
                break;
            case 2:
                m.moveTo(53, 91); m.lineTo(77, 89);
                c.drawPath(m, L);
                break;
            default:
                m.moveTo(50, 96); m.quadTo(65, 80, 80, 96);
                c.drawPath(m, L);
        }
    }

    private static void accessory(Canvas c, Content.CustomerDef d) {
        Path p = new Path();
        switch (d.acc) {
            case GLASSES:
                s(OUTLINE, 3);
                c.drawCircle(53, 64, 12, L);
                c.drawCircle(77, 64, 12, L);
                c.drawLine(65, 62, 65, 62, L);
                break;
            case EARRINGS:
                oval(c, 24, 80, 32, 90, 0xFFF5B82E);
                oval(c, 98, 80, 106, 90, 0xFFF5B82E);
                break;
            case CAP:
                p.moveTo(26, 46); p.cubicTo(28, 8, 102, 8, 104, 46); p.close();
                f(d.shirt);
                c.drawPath(p, P);
                s(OUTLINE, 2.5f);
                c.drawPath(p, L);
                rrOut(c, 22, 40, 118, 50, 5, G.mix(d.shirt, 0xFF000000, .25f), 2.5f);
                break;
            case MOUSTACHE:
                p.moveTo(46, 84); p.quadTo(56, 72, 65, 80); p.quadTo(74, 72, 84, 84); p.quadTo(65, 88, 46, 84);
                f(d.hairColor);
                c.drawPath(p, P);
                break;
            case TOPHAT:
                rrOut(c, 18, 32, 112, 42, 4, 0xFF20202A, 2.5f);
                rrOut(c, 38, 0, 92, 36, 3, 0xFF20202A, 2.5f);
                rr(c, 39, 25, 91, 33, 1, 0xFFB02A3A);
                break;
            case BOWTIE:
                p.moveTo(65, 112); p.lineTo(46, 102); p.lineTo(46, 122); p.close();
                p.moveTo(65, 112); p.lineTo(84, 102); p.lineTo(84, 122); p.close();
                f(0xFFD8392B);
                c.drawPath(p, P);
                s(OUTLINE, 2);
                c.drawPath(p, L);
                break;
            case BEARD:
                p.moveTo(29, 74); p.cubicTo(32, 118, 98, 118, 101, 74); p.cubicTo(92, 84, 80, 80, 65, 82);
                p.cubicTo(50, 80, 38, 84, 29, 74);
                f(d.hairColor);
                c.drawPath(p, P);
                face2Mouth(c);
                break;
            case CROWN:
                p.moveTo(40, 34); p.lineTo(36, 8); p.lineTo(50, 20); p.lineTo(65, 2); p.lineTo(80, 20); p.lineTo(94, 8); p.lineTo(90, 34);
                p.close();
                f(0xFFF5B82E);
                c.drawPath(p, P);
                s(OUTLINE, 2.5f);
                c.drawPath(p, L);
                break;
            default:
        }
    }

    private static void face2Mouth(Canvas c) {
        s(0xFFF4E4D0, 3);
        c.drawLine(56, 92, 74, 92, L);
    }

    // ---------------------------------------------------------------- scenery

    public static TextureRegion dining(int makeover, boolean plants, boolean neon) {
        return G.sprites.get("dining_" + makeover + plants + neon, 800, 200, 1, c -> {
            int top = makeover >= 2 ? 0xFF2E7C7C : makeover == 1 ? 0xFFF1A84A : 0xFFE8C78F;
            int bottom = makeover >= 2 ? 0xFF1E5E62 : makeover == 1 ? 0xFFDB8A3A : 0xFFD8AE72;
            P.setShader(new LinearGradient(0, 0, 0, 200, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 800, 200, P);
            P.setShader(null);
            for (int i = 0; i < 4; i++) {
                float x = 30 + i * 195;
                rrOut(c, x, 14, x + 150, 106, 8, 0xFFBDE7F5, 3);
                f(0x66FFFFFF);
                Path g = new Path();
                g.moveTo(x + 20, 104); g.lineTo(x + 70, 16); g.lineTo(x + 100, 16); g.lineTo(x + 50, 104); g.close();
                c.drawPath(g, P);
                s(0xFF7A5230, 3);
                c.drawLine(x + 75, 14, x + 75, 106, L);
            }
            if (plants) {
                for (float x : new float[]{10, 188, 383, 578, 770}) {
                    f(0xFF3D9A45);
                    for (int i = 0; i < 5; i++) c.drawOval(new RectF(x - 12 + i * 4, 70 - i % 2 * 8, x + i * 4, 110), P);
                    rrOut(c, x - 8, 108, x + 20, 138, 3, 0xFFB5623A, 2);
                }
            }
            if (neon) {
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                t.setTextSize(24);
                t.setFakeBoldText(true);
                t.setColor(0xFFFF5EA8);
                t.setShadowLayer(8, 0, 0, 0xFFFF2E88);
                c.drawText("OPEN LATE", 335, 44, t);
            }
        });
    }

    public static TextureRegion counter() {
        return G.sprites.get("counter", 800, 54, 1, c -> {
            rr(c, 0, 0, 800, 16, 4, 0xFFE9E3D2);
            f(0xFFC9402E);
            c.drawRect(0, 16, 800, 54, P);
            for (int x = 0; x < 800; x += 32) {
                f(x % 64 == 0 ? 0xFFFFF4DC : 0xFFC9402E);
                c.drawRect(x, 22, x + 32, 34, P);
                f(x % 64 == 0 ? 0xFFC9402E : 0xFFFFF4DC);
                c.drawRect(x, 34, x + 32, 46, P);
            }
        });
    }

    public static TextureRegion prepWall() {
        return G.sprites.get("prepWall", 800, 400, 1, c -> {
            c.drawColor(0xFFEDE0C8);
            f(0xFFE2D2B4);
            for (int y = 0; y < 400; y += 40) {
                for (int x = (y / 40) % 2 * 40; x < 800; x += 80) c.drawRect(x, y, x + 40, y + 40, P);
            }
        });
    }

    public static TextureRegion kitchenWall() {
        return G.sprites.get("kitchenWall", 1170, 592, 1, c -> {
            c.drawColor(0xFFC9DDE0);
            s(0xFFB2CACD, 2);
            for (int x = 0; x <= 1170; x += 46) c.drawLine(x, 0, x, 592, L);
            for (int y = 0; y <= 592; y += 46) c.drawLine(0, y, 1170, y, L);
            f(0xFF8A7A66);
            c.drawRect(0, 570, 1170, 592, P);
            Path hood = new Path();
            hood.moveTo(150, 34); hood.lineTo(620, 34); hood.lineTo(590, 0); hood.lineTo(180, 0); hood.close();
            f(0xFF9AA5AC);
            c.drawPath(hood, P);
            s(OUTLINE, 2);
            c.drawPath(hood, L);
        });
    }

    public static TextureRegion menuBackground() {
        return G.sprites.get("menuBg", 1280, 720, 1, c -> {
            P.setShader(new LinearGradient(0, 0, 0, 720, 0xFFFF8A3C, 0xFFFFD36B, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 1280, 720, P);
            P.setShader(null);
            f(0x30FFFFFF);
            for (int i = 0; i < 12; i++) {
                Path ray = new Path();
                double a = i * Math.PI / 6;
                ray.moveTo(640, 720);
                ray.lineTo((float) (640 + Math.cos(a) * 1400), (float) (720 + Math.sin(a) * 1400));
                ray.lineTo((float) (640 + Math.cos(a + .25) * 1400), (float) (720 + Math.sin(a + .25) * 1400));
                ray.close();
                c.drawPath(ray, P);
            }
            f(0xFF3A2416);
            for (int x = 0; x < 1280; x += 150) c.drawRect(x, 610 - (x % 300 == 0 ? 90 : 40), x + 130, 720, P);
            f(0xFFC9402E);
            c.drawRect(0, 650, 1280, 720, P);
            for (int x = 0; x < 1280; x += 40) {
                f(x % 80 == 0 ? 0xFFFFF4DC : 0xFFC9402E);
                c.drawRect(x, 660, x + 40, 680, P);
                f(x % 80 == 0 ? 0xFFC9402E : 0xFFFFF4DC);
                c.drawRect(x, 680, x + 40, 700, P);
            }
        });
    }
}

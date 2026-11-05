package com.c01m.burgerrun.ui;

import com.c01m.burgerrun.engine.G;

import java.util.ArrayList;
import java.util.List;

/** Base screen with a simple button list. Coordinates are logical 1280x720, y-down. */
public abstract class Screen {
    public static class Btn {
        public float x, y, w, h;
        public String label;
        public int color;
        public boolean enabled = true, visible = true;
        public float textSize = 26;
        public Runnable action;

        public Btn(float x, float y, float w, float h, String label, int color, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.label = label; this.color = color; this.action = action;
        }
        public boolean hit(float px, float py) { return visible && px >= x && px <= x + w && py >= y && py <= y + h; }
        public void draw(boolean pressed) {
            if (!visible) return;
            int base = enabled ? color : 0xFF9A938A;
            float off = pressed ? 3 : 0;
            G.rrect(x, y + 5, w, h, 16, 0x66000000);
            G.rrect(x, y + off, w, h, 16, G.DARK);
            G.rrect(x + 3, y + 3 + off, w - 6, h - 6, 13, base);
            G.rrect(x + 7, y + 6 + off, w - 14, (h - 12) * .38f, 9, 0x2EFFFFFF);
            G.title(label, x + w / 2, y + h / 2 + off, textSize, enabled ? G.WHITE : 0xFFE4DED4, G.DARK, 0);
        }
    }

    public final BurgerRunGame game;
    protected final List<Btn> buttons = new ArrayList<>();
    private Btn pressed;

    protected Screen(BurgerRunGame game) { this.game = game; }

    protected Btn add(Btn b) { buttons.add(b); return b; }

    public void update(float dt) {}
    public abstract void draw();
    public void back() {}
    public void key(int key) {}
    public void onAppPause() {}

    public void down(float x, float y) {
        pressed = null;
        for (Btn b : buttons) if (b.hit(x, y)) { pressed = b; return; }
    }
    public void move(float x, float y) {}
    public void up(float x, float y) {
        Btn b = pressed;
        pressed = null;
        if (b != null && b.hit(x, y)) {
            if (b.enabled) { game.audio.play("tap"); b.action.run(); }
            else game.audio.play("wrong");
        }
    }
    protected boolean anyButtonPressed() { return pressed != null; }

    protected void drawButtons() {
        for (Btn b : buttons) b.draw(b == pressed);
    }

    /** A blocking dialog drawn over the current screen. */
    public static final class Modal {
        private final BurgerRunGame game;
        private final List<String> lines;
        private final Btn yes, no;
        private Btn pressed;

        Modal(BurgerRunGame game, String text, String yesLabel, String noLabel, Runnable onYes) {
            this.game = game;
            lines = G.lines(text, 600, 26);
            float top = 360 - height() / 2;
            float by = top + height() - 90;
            if (noLabel == null) {
                yes = new Btn(530, by, 220, 70, yesLabel, G.GREEN, () -> { game.closeModal(); if (onYes != null) onYes.run(); });
                no = null;
            } else {
                yes = new Btn(660, by, 240, 70, yesLabel, G.KETCHUP, () -> { game.closeModal(); onYes.run(); });
                no = new Btn(380, by, 240, 70, noLabel, G.TEAL, game::closeModal);
            }
        }
        private float height() { return 160 + lines.size() * 36; }
        void down(float x, float y) {
            pressed = yes.hit(x, y) ? yes : no != null && no.hit(x, y) ? no : null;
        }
        void up(float x, float y) {
            Btn b = pressed;
            pressed = null;
            if (b != null && b.hit(x, y)) { game.audio.play("tap"); b.action.run(); }
        }
        void cancel() { if (no != null) no.action.run(); else yes.action.run(); }
        void draw() {
            G.rect(G.left, 0, G.right - G.left, G.HEIGHT, 0x99000000);
            float h = height(), top = 360 - h / 2;
            G.panel(320, top, 640, h, 26, G.CREAM, G.DARK);
            float y = top + 42;
            for (String l : lines) { G.text(l, 640, y, 26, G.INK, 0, false); y += 36; }
            yes.draw(pressed == yes);
            if (no != null) no.draw(pressed == no);
        }
    }
}

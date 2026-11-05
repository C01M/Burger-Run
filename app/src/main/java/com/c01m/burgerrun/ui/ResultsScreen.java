package com.c01m.burgerrun.ui;

import com.c01m.burgerrun.engine.Art;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.sim.Progression;

import java.util.List;
import java.util.Locale;

/** Animated end-of-shift report with star rating, earnings and the coach's three tips. */
public final class ResultsScreen extends Screen {
    private final Progression.Result r;
    private float t;
    private int starsPlayed;
    private boolean levelPlayed;

    public ResultsScreen(BurgerRunGame g, Progression.Result r) {
        super(g);
        this.r = r;
        add(new Btn(30, 642, 400, 68, "RETURN TO MAIN MENU", 0xFF8A6A50, this::back)).textSize = 22;
        add(new Btn(440, 642, 350, 68, "UPGRADE SHOP", G.MUSTARD, () -> g.show(new ShopScreen(g)))).textSize = 24;
        add(new Btn(800, 642, 450, 68, "CONTINUE TO NEXT SHIFT", G.GREEN, () -> g.show(new BriefingScreen(g)))).textSize = 24;
    }

    @Override public void back() { game.show(new MenuScreen(game)); }

    @Override public void update(float dt) {
        t += dt;
        int due = (int) Math.min(r.stars, Math.max(0, (t - .3f) / .35f));
        while (starsPlayed < due) { starsPlayed++; game.audio.play("star"); }
        if (!levelPlayed && t > 2.2f && r.levelAfter > r.levelBefore) { levelPlayed = true; game.audio.play("level"); }
    }

    private float ease(float start, float dur) {
        float x = Math.max(0, Math.min(1, (t - start) / dur));
        return 1 - (1 - x) * (1 - x);
    }

    private void row(String name, String value, float x, float y, float start) {
        float a = ease(start, .3f);
        if (a <= 0) return;
        G.text(name, x, y, 20, G.BROWN, -1, false);
        G.text(value, x + 300, y, 22, G.INK, 1, true);
    }

    private void meter(String name, double v, float x, float y, float start) {
        if (v < 0) return;
        double a = v * ease(start, .6f);
        G.text(name, x, y, 18, G.BROWN, -1, false);
        G.bar(x + 110, y - 8, 150, 16, a / 100, a >= 80 ? G.GREEN : a >= 50 ? G.MUSTARD : G.KETCHUP, 0xFFD4C6A8);
        G.text(String.format(Locale.US, "%.0f", a), x + 298, y, 18, G.INK, 1, true);
    }

    @Override public void draw() {
        G.backdrop(Art.menuBackground());
        G.title("SHIFT " + r.shift + " COMPLETE", 640, 34, 44, G.MUSTARD, G.DARK, 0);
        for (int i = 0; i < 5; i++) {
            float sx = 640 + (i - 2) * 76;
            boolean on = i < r.stars;
            float pop = on ? ease(.3f + i * .35f, .3f) : 1;
            float size = on ? 40 + 34 * pop : 58;
            G.title("★", sx, 104, size, on ? G.MUSTARD : 0x88FFFFFF, on ? G.DARK : 0x663A2416, 0);
        }

        G.panel(20, 150, 340, 482, 20, G.CREAM, G.DARK);
        float y = 180;
        double earned = (r.money + r.tips + r.bonus) * ease(.4f, 1.2f);
        G.title(G.money(earned), 190, y, 36, G.GREEN, G.WHITE, 0);
        y += 44;
        row("Base pay", G.money(r.money), 40, y, .5f); y += 31;
        row("Tips", G.money(r.tips), 40, y, .6f); y += 31;
        row("Star bonus", G.money(r.bonus), 40, y, .7f); y += 31;
        row("Customers served", "" + r.customers, 40, y, .8f); y += 31;
        row("Avg satisfaction", String.format(Locale.US, "%.0f%%", r.satisfaction), 40, y, .9f); y += 31;
        row("Orders correct", r.correct + " / " + r.customers, 40, y, 1f); y += 31;
        row("Mistakes", "" + r.mistakes, 40, y, 1.1f); y += 31;
        row("Best streak", "" + r.bestStreak, 40, y, 1.2f); y += 31;
        row("XP earned", "+" + r.xp, 40, y, 1.3f); y += 36;
        int need = Progression.xpToNext(game.save.level);
        String lvl = r.levelAfter > r.levelBefore ? "LEVEL UP! Lv " + r.levelAfter : "Lv " + r.levelAfter + "  " + Progression.rank(r.levelAfter);
        G.text(lvl, 190, y, 22, r.levelAfter > r.levelBefore ? G.KETCHUP : G.TEAL, 0, true);
        y += 26;
        G.bar(40, y, 300, 16, (double) game.save.xp / need * ease(1.4f, .7f), G.MUSTARD, 0xFFD4C6A8);
        y += 34;
        if (!r.unlocks.isEmpty()) G.text("New unlocks: " + r.unlocks.size() + " (see next shift)", 190, y, 17, G.KETCHUP, 0, true);

        G.panel(370, 150, 340, 482, 20, G.CREAM, G.DARK);
        G.text("Performance", 540, 180, 26, G.KETCHUP, 0, true);
        float my = 226;
        meter("Accuracy", r.accuracy, 388, my, .8f); my += 46;
        meter("Grill", r.cook, 388, my, .9f); my += r.cook >= 0 ? 46 : 0;
        meter("Fryer", r.fry, 388, my, 1f); my += r.fry >= 0 ? 46 : 0;
        meter("Placement", r.place, 388, my, 1.1f); my += r.place >= 0 ? 46 : 0;
        meter("Drinks", r.drink, 388, my, 1.2f); my += r.drink >= 0 ? 46 : 0;
        meter("Wait time", r.wait, 388, my, 1.3f); my += 46;
        meter("Efficiency", r.efficiency, 388, my, 1.4f); my += 46;
        meter("Happiness", r.satisfaction, 388, my, 1.5f); my += 50;
        G.text(String.format(Locale.US, "Shift score: %.0f / 100", r.score * ease(1.6f, .5f)), 540, my, 24, G.INK, 0, true);

        G.panel(720, 150, 540, 482, 20, 0xFF23505A, G.DARK);
        G.text("COACH'S NOTES", 990, 182, 28, G.MUSTARD, 0, true);
        List<String> tips = r.tips3;
        float ty = 226;
        for (int i = 0; i < tips.size(); i++) {
            if (ease(1.8f + i * .3f, .3f) <= 0) continue;
            G.circle(756, ty + 8, 18, G.MUSTARD);
            G.text("" + (i + 1), 756, ty + 8, 22, G.DARK, 0, true);
            ty = G.wrap(tips.get(i), 786, ty, 460, 20, G.WHITE, 27) + 16;
        }
        drawButtons();
    }
}

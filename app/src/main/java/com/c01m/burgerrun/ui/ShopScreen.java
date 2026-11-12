package com.c01m.burgerrun.ui;

import com.c01m.burgerrun.engine.Art;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.sim.Content;
import com.c01m.burgerrun.sim.Progression;
import com.c01m.burgerrun.sim.SaveData;

import java.util.ArrayList;
import java.util.List;

/** The upgrade shop: tabs by category, cards with price and level pips. */
public final class ShopScreen extends Screen {
    private static final float CW = 396, CH = 228;
    private int tab;
    private final List<Content.UpgradeDef> shown = new ArrayList<>();
    private float flash;
    private Content.UpgradeDef flashed;
    private float downX, downY;

    public ShopScreen(BurgerRunGame g) {
        super(g);
        for (int i = 0; i < Content.UPGRADE_CATS.length; i++) {
            int idx = i;
            Btn b = add(new Btn(30 + i * 192, 76, 184, 56, Content.UPGRADE_CATS[i][1], G.TEAL, () -> setTab(idx)));
            b.textSize = 21;
        }
        add(new Btn(1000, 8, 250, 60, "DONE", G.GREEN, this::back));
        setTab(0);
    }

    private void setTab(int t) {
        tab = t;
        shown.clear();
        for (Content.UpgradeDef u : Content.UPGRADES) if (u.cat.equals(Content.UPGRADE_CATS[t][0])) shown.add(u);
    }

    @Override public void back() { game.persist(); game.show(new BriefingScreen(game)); }

    @Override public void update(float dt) { if (flash > 0) flash -= dt; }

    private float cardX(int i) { return 30 + (i % 3) * (CW + 12); }
    private float cardY(int i) { return 146 + (i / 3) * (CH + 12); }

    @Override public void down(float x, float y) { super.down(x, y); downX = x; downY = y; }

    @Override public void up(float x, float y) {
        super.up(x, y);
        if (Math.abs(x - downX) > 20 || Math.abs(y - downY) > 20) return;
        for (int i = 0; i < shown.size(); i++) {
            float cx = cardX(i), cy = cardY(i);
            if (x >= cx && x <= cx + CW && y >= cy && y <= cy + CH) { buy(shown.get(i)); return; }
        }
    }

    private void buy(Content.UpgradeDef u) {
        SaveData d = game.save;
        game.topUpCheat();
        if (Progression.buy(d, u)) {
            game.topUpCheat();
            game.audio.play("buy");
            flashed = u;
            flash = .6f;
            game.persist();
        } else {
            game.audio.play("wrong");
        }
    }

    @Override public void draw() {
        SaveData d = game.save;
        G.backdrop(Art.menuBackground());
        G.title("UPGRADE SHOP", 30, 38, 46, G.MUSTARD, G.DARK, -1);
        G.title(game.infiniteMoney() ? "∞ DEBUG" : G.money(d.money), 980, 38, 42, G.WHITE, G.GREEN, 1);
        for (int i = 0; i < Content.UPGRADE_CATS.length; i++) buttons.get(i).color = i == tab ? G.KETCHUP : G.TEAL;
        for (int i = 0; i < shown.size(); i++) card(shown.get(i), cardX(i), cardY(i), d);
        drawButtons();
    }

    private void card(Content.UpgradeDef u, float x, float y, SaveData d) {
        int lv = d.up(u.id);
        int price = Progression.priceOf(d, u);
        boolean locked = d.level < u.level, maxed = price < 0, afford = Progression.canBuy(d, u);
        int bg = maxed ? 0xFFE3F1D0 : locked ? 0xFFCFC8BA : G.CREAM;
        if (u == flashed && flash > 0) bg = G.mix(bg, G.MUSTARD, flash / .6f);
        G.panel(x, y, CW, CH, 18, bg, afford ? G.GREEN : G.DARK);
        G.circle(x + 44, y + 48, 34, 0xFFFFD98A);
        G.sprite(Art.emoji(u.icon, 44), x + 22, y + 21, 44, 52.8f, locked ? .4f : 1);
        G.text(u.name, x + 88, y + 28, 22, G.INK, -1, true);
        for (int i = 0; i < u.prices.length; i++) G.circle(x + 98 + i * 22, y + 60, 8, i < lv ? G.KETCHUP : 0xFFBDB098);
        G.wrap(u.desc, x + 18, y + 104, CW - 36, 18, G.BROWN, 24);
        if (!maxed) G.text("Next: " + u.levels[lv], x + 18, y + 158, 18, G.TEAL, -1, true);
        else G.text(u.levels[u.levels.length - 1], x + 18, y + 158, 18, G.TEAL, -1, true);
        if (maxed) G.text("MAXED", x + CW / 2, y + 196, 26, G.GREEN, 0, true);
        else if (locked) G.text("Reach level " + u.level, x + CW / 2, y + 196, 24, 0xFF6E655A, 0, true);
        else G.text(G.money(price), x + CW / 2, y + 196, 30, afford ? G.GREEN : G.KETCHUP, 0, true);
    }
}

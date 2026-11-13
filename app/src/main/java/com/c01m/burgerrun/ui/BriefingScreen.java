package com.c01m.burgerrun.ui;

import com.c01m.burgerrun.engine.Art;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.sim.Content;
import com.c01m.burgerrun.sim.Progression;
import com.c01m.burgerrun.sim.SaveData;
import com.c01m.burgerrun.sim.ShiftManager;

import java.util.List;

/** Shown before each shift: number, expected customers, special events and new unlocks. */
public final class BriefingScreen extends Screen {
    private final Content.Special special;
    private final int expected;
    private final List<String> unlocks;
    private final Content.Special nextSpecial;
    private final int nextSpecialIn;

    private static final String[] TIPS = {
            "Put patties on the grill first: they take the longest of anything.",
            "Drop fries in while the patty cooks, then grab drinks while both finish.",
            "The warmer keeps food hot, so cook ahead when a rush is coming.",
            "Flip when the first gauge reaches the target so both sides match.",
            "Check the ticket's cooking level before you pull a patty off.",
            "Sweep the sauce bottle side to side for even coverage.",
            "Impatient customers leave sooner: serve them first.",
            "Lift fries while they're golden. They keep cooking in the oil.",
    };

    public BriefingScreen(BurgerRunGame g) {
        super(g);
        SaveData d = g.save;
        special = ShiftManager.specialFor(d.shift, d.level);
        expected = ShiftManager.customerCount(d.shift, special);
        unlocks = Progression.unlocksAt(d.level);
        Content.Special next = null;
        int in = 0;
        for (int i = 1; i <= 20 && next == null; i++) {
            next = ShiftManager.specialFor(d.shift + i, d.level);
            in = i;
        }
        nextSpecial = next;
        nextSpecialIn = next == null ? 0 : in;
        add(new Btn(860, 610, 370, 86, "START SHIFT", G.GREEN, () -> g.show(new GameScreen(g))));
        add(new Btn(460, 610, 370, 86, "UPGRADE SHOP", G.MUSTARD, () -> g.show(new ShopScreen(g))));
        add(new Btn(60, 610, 370, 86, "MAIN MENU", 0xFF8A6A50, this::back));
    }

    @Override public void back() { game.persist(); game.show(new MenuScreen(game)); }

    @Override public void draw() {
        SaveData d = game.save;
        G.backdrop(Art.menuBackground());
        G.title("SHIFT " + d.shift, 640, 62, 72, G.MUSTARD, G.DARK, 0);
        G.panel(60, 120, 560, 470, 24, G.CREAM, G.DARK);
        G.text("Tonight's board", 340, 155, 30, G.KETCHUP, 0, true);
        G.text("Expected customers", 90, 210, 25, G.BROWN, -1, false);
        G.text("" + expected, 590, 210, 34, G.INK, 1, true);
        G.text("Rank", 90, 262, 25, G.BROWN, -1, false);
        G.text("Lv " + d.level + " " + Progression.rank(d.level), 590, 262, 26, G.INK, 1, true);
        G.text("Cash on hand", 90, 314, 25, G.BROWN, -1, false);
        G.text(G.money(d.money), 590, 314, 30, G.GREEN, 1, true);
        G.bar(90, 356, 500, 20, (double) d.xp / Progression.xpToNext(d.level), G.MUSTARD, 0xFFD4C6A8);
        G.text(d.xp + " / " + Progression.xpToNext(d.level) + " XP", 340, 396, 20, G.BROWN, 0, false);
        double rate = Progression.offlineRatePerHour(d);
        if (rate > 0) G.text("Night Crew: " + G.money(rate) + " per hour while closed", 340, 440, 22, G.TEAL, 0, true);
        else G.text("Hire staff in the shop to lighten the load!", 340, 440, 22, G.TEAL, 0, true);

        G.panel(650, 120, 570, 470, 24, G.CREAM, G.DARK);
        float y = 155;
        if (special != null) {
            G.sprite(Art.emoji(special.icon, 48), 670, y - 26, 48, 57.6f);
            G.text(special.title, 726, y, 32, G.KETCHUP, -1, true);
            y = G.wrap(special.desc, 680, y + 46, 510, 24, G.INK, 32) + 10;
        } else {
            G.text("A regular day on the line", 935, y, 30, G.TEAL, 0, true);
            y += 50;
        }
        if (!unlocks.isEmpty() && d.level > 1) {
            G.text("New at level " + d.level + ":", 680, y, 26, G.KETCHUP, -1, true);
            y += 36;
            for (int i = 0; i < unlocks.size() && i < 7; i++) {
                G.text("★ " + unlocks.get(i), 690, y, 22, G.INK, -1, false);
                y += 32;
            }
        } else if (d.shift == 1) {
            y = G.wrap("Take orders, grill patties, fry fries, grab drinks from the fridge, then build and serve every tray.", 680, y, 510, 24, G.INK, 32);
            y = G.wrap("Hints will guide you through your first shift.", 680, y + 12, 510, 24, G.BROWN, 32);
        }
        y += 14;
        // fill the rest of the board: regulars, the next special shift, and a kitchen tip
        if (y < 470) {
            int regulars = d.regulars.size();
            G.text(regulars == 0 ? "No regulars yet: happy customers come back"
                    : regulars + " regular" + (regulars == 1 ? "" : "s") + " may drop by", 680, y, 22, G.BROWN, -1, false);
            y += 34;
        }
        if (y < 470 && special == null && nextSpecialIn > 0) {
            G.text("Next special shift: " + nextSpecial.title + " in " + nextSpecialIn + " shift" + (nextSpecialIn == 1 ? "" : "s"),
                    680, y, 22, G.BROWN, -1, false);
            y += 34;
        }
        if (y < 480) {
            G.rrect(675, 486, 520, 86, 14, 0xFFF3E2BE);
            G.text("Kitchen tip", 695, 506, 18, G.KETCHUP, -1, true);
            G.wrap(TIPS[d.shift % TIPS.length], 695, 528, 485, 19, G.INK, 24);
        }
        drawButtons();
    }
}

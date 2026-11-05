package com.c01m.burgerrun.ui;

import com.c01m.burgerrun.engine.Art;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.sim.Progression;
import com.c01m.burgerrun.sim.SaveData;

import java.util.Locale;

/** Lifetime statistics read directly from the save. */
public final class StatsScreen extends Screen {
    private final String[][] rows;

    public StatsScreen(BurgerRunGame g) {
        super(g);
        SaveData d = g.saves.load();
        if (d == null) d = new SaveData();
        SaveData.Stats s = d.stats;
        int n = Math.max(1, s.orders);
        rows = new String[][]{
                {"Shifts completed", "" + s.shifts}, {"Customers served", "" + s.customers},
                {"Money earned", G.money(s.money)}, {"Tips earned", G.money(s.tips)},
                {"Burgers prepared", "" + s.burgers}, {"Fries prepared", "" + s.fries},
                {"Drinks served", "" + s.drinks}, {"Avg satisfaction", String.format(Locale.US, "%.0f%%", s.orders == 0 ? 0 : s.satisfactionSum / n)},
                {"Avg order accuracy", String.format(Locale.US, "%.0f%%", s.orders == 0 ? 0 : s.accuracySum / n)},
                {"Best shift", s.bestStars + "★  " + G.money(s.bestMoney)},
                {"Most customers in a shift", "" + s.bestCustomers}, {"Current rank", "Lv " + d.level + " " + Progression.rank(d.level)},
                {"Upgrades purchased", "" + s.upgrades}, {"Perfect orders", "" + s.perfectOrders},
                {"Patties burned", "" + s.burnedPatties}, {"Night Crew earnings", G.money(s.offlineMoney)},
        };
        add(new Btn(490, 628, 300, 76, "BACK", 0xFF8A6A50, this::back));
    }

    @Override public void back() { game.show(new MenuScreen(game)); }

    @Override public void draw() {
        G.backdrop(Art.menuBackground());
        G.title("YOUR STATS", 640, 56, 60, G.MUSTARD, G.DARK, 0);
        for (int i = 0; i < rows.length; i++) {
            float x = 50 + (i % 2) * 600, y = 110 + (i / 2) * 63;
            G.panel(x, y, 580, 54, 14, G.CREAM, G.DARK);
            G.text(rows[i][0], x + 18, y + 27, 22, G.BROWN, -1, false);
            G.text(rows[i][1], x + 562, y + 27, 24, G.KETCHUP, 1, true);
        }
        drawButtons();
    }
}

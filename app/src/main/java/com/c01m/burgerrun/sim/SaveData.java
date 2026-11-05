package com.c01m.burgerrun.sim;

import java.util.HashMap;
import java.util.Map;

/** Everything that persists between sessions. Encoded to JSON by engine.SaveSystem. */
public final class SaveData {
    public static final class Regular {
        public int visits;
        public int loyalty;      // 0..5 hearts, each adds a little tip
        public String favorite;  // Order.encode()
    }

    public static final class Stats {
        public int shifts, customers, burgers, fries, drinks, orders, upgrades, bestStars, bestCustomers, burnedPatties, perfectOrders;
        public double money, tips, satisfactionSum, accuracySum, bestScore, bestMoney, offlineMoney;
    }

    public int shift = 1;
    public int level = 1;
    public int xp = 0;
    public double money = 0;
    public long lastSeen = 0;
    public boolean tutorialDone;
    public final Map<String, Integer> upgrades = new HashMap<>();
    public final Map<String, Regular> regulars = new HashMap<>();
    public final Stats stats = new Stats();

    public int up(String id) {
        Integer v = upgrades.get(id);
        return v == null ? 0 : v;
    }
}

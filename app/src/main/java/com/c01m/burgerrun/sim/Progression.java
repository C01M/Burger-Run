package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.List;

/** XP, ranks, unlocks, the upgrade shop's rules, end-of-shift results and offline earnings. */
public final class Progression {
    private Progression() {}

    public static int xpToNext(int level) {
        return (int) Math.round(90 + 55 * Math.pow(level - 1, 1.35));
    }

    public static String rank(int level) {
        String name = (String) Content.RANKS[0][1];
        for (Object[] r : Content.RANKS) if ((Integer) r[0] <= level) name = (String) r[1];
        return name;
    }

    public static List<String> unlocksAt(int level) {
        List<String> out = new ArrayList<>();
        for (Content.Ingredient i : Content.VEG) if (i.level == level) out.add("Topping: " + i.name);
        for (Content.Ingredient i : Content.SAUCE) if (i.level == level) out.add("Sauce: " + i.name);
        for (Content.Drink d : Content.DRINKS) if (d.level == level && d.fridge == 0) out.add("Drink: " + d.name);
        for (Content.CustomerDef c : Content.CUSTOMERS) if (c.level == level) out.add("Customer: " + c.name + " (" + c.arch.label + ")");
        for (Content.UpgradeDef u : Content.UPGRADES) if (u.level == level && level > 1) out.add("Upgrade: " + u.name);
        return out;
    }

    // ---------------------------------------------------------------- shop

    public static int priceOf(SaveData s, Content.UpgradeDef u) {
        int lv = s.up(u.id);
        return lv >= u.prices.length ? -1 : u.prices[lv];
    }

    public static boolean canBuy(SaveData s, Content.UpgradeDef u) {
        int price = priceOf(s, u);
        return price >= 0 && s.level >= u.level && s.money >= price;
    }

    public static boolean buy(SaveData s, Content.UpgradeDef u) {
        if (!canBuy(s, u)) return false;
        s.money -= priceOf(s, u);
        s.upgrades.put(u.id, s.up(u.id) + 1);
        s.stats.upgrades++;
        return true;
    }

    // ---------------------------------------------------------------- offline earnings (idle layer)

    public static double offlineRatePerHour(SaveData s) {
        int night = s.up("night");
        if (night == 0) return 0;
        double base = 14 + 4 * s.level;
        double staff = 1 + .1 * (s.up("greeter") + s.up("runner") + s.up("griller") + s.up("fryer"));
        return base * staff * (1 + .5 * (night - 1));
    }

    public static double offlineCapHours(SaveData s) {
        return new int[]{0, 4, 8, 12}[s.up("night")];
    }

    /** Money earned for an absence; 0 for absences under a minute or a clock moved backwards. */
    public static double offlineEarnings(SaveData s, long now) {
        if (s.lastSeen <= 0) return 0;
        long away = now - s.lastSeen;
        if (away < 60_000) return 0;
        double hours = Math.min(away / 3_600_000.0, offlineCapHours(s));
        return Math.round(hours * offlineRatePerHour(s) * 100) / 100.0;
    }

    // ---------------------------------------------------------------- results

    public static final class Result {
        public int shift, stars, customers, correct, mistakes, xp, levelBefore, levelAfter, bestStreak;
        public double money, tips, bonus, satisfaction, accuracy, cook, fry, place, drink, wait, efficiency, score;
        public final List<String> unlocks = new ArrayList<>();
        public List<String> tips3;
        public Content.Special special;
    }

    private static double mean(double sum, int n, double dflt) { return n == 0 ? dflt : sum / n; }

    public static int starsFor(double score) {
        return score >= 90 ? 5 : score >= 78 ? 4 : score >= 64 ? 3 : score >= 48 ? 2 : score >= 30 ? 1 : 0;
    }

    /** Commits a finished shift to the save and returns what the results screen shows. */
    public static Result finish(ShiftManager s) {
        SaveData d = s.save;
        Metrics m = s.metrics;
        Result r = new Result();
        r.shift = s.number;
        r.special = s.special;
        r.customers = s.served;
        r.money = s.moneyEarned;
        r.tips = s.tipsEarned;
        r.bestStreak = s.bestStreak;

        double sat = 0, acc = 0, cook = 0, fry = 0, place = 0, drink = 0, wait = 0, done = 0;
        int nCook = 0, nFry = 0, nPlace = 0, nDrink = 0, perfect = 0;
        for (Scoring.Review v : s.reviews) {
            sat += v.satisfaction; acc += v.accuracy; wait += v.wait; done += v.completion;
            if (v.cook >= 0) { cook += v.cook; nCook++; }
            if (v.fry >= 0) { fry += v.fry; nFry++; }
            if (v.place >= 0) { place += v.place; nPlace++; }
            if (v.drink >= 0) { drink += v.drink; nDrink++; }
            if (v.accuracy >= 99 && !v.incomplete) r.correct++;
            if (v.perfect) perfect++;
        }
        int n = Math.max(1, s.reviews.size());
        r.satisfaction = sat / n;
        r.accuracy = acc / n;
        r.wait = wait / n;
        r.cook = mean(cook, nCook, -1);
        r.fry = mean(fry, nFry, -1);
        r.place = mean(place, nPlace, -1);
        r.drink = mean(drink, nDrink, -1);
        r.mistakes = m.wrongIngredients + m.missingIngredients + m.wrongDrinks + m.wrongSizes + m.burnedPatties
                + m.burnedFries + m.incompleteServes + m.sequenceErrors;
        double waste = m.pattiesTrashed + m.friesTrashed + m.drinksTrashed;
        double ratio = 0;
        for (double x : m.waitRatios) ratio += x;
        ratio /= n;
        r.efficiency = clamp(115 - ratio * 70 - waste / n * 25 - Math.max(0, (double) m.switches / n - 4) * 5);

        double[][] parts = {{r.accuracy, 2}, {r.cook, 1.5}, {r.fry, 1}, {r.place, 1}, {r.drink, 1}, {r.wait, 1.2}, {r.efficiency, 1}, {r.satisfaction, 2}};
        double sum = 0, wsum = 0;
        for (double[] p : parts) if (p[0] >= 0) { sum += p[0] * p[1]; wsum += p[1]; }
        // the quality parts only judge what was served, so rushing out partial orders must cost here
        double completion = s.reviews.isEmpty() ? 1 : done / s.reviews.size();
        r.score = sum / wsum * (.2 + .8 * completion);
        r.stars = starsFor(r.score);

        r.bonus = Math.round((r.stars * (6 + s.number * 1.5)) * 100) / 100.0;
        r.xp = (int) Math.round(r.satisfaction / 100 * 9 * n + r.stars * 12);

        d.money += r.money + r.tips + r.bonus;
        d.xp += r.xp;
        r.levelBefore = d.level;
        while (d.level < Content.MAX_LEVEL && d.xp >= xpToNext(d.level)) {
            d.xp -= xpToNext(d.level);
            d.level++;
            r.unlocks.addAll(unlocksAt(d.level));
        }
        r.levelAfter = d.level;

        SaveData.Stats st = d.stats;
        st.shifts++;
        st.customers += s.served;
        st.money += r.money + r.tips + r.bonus;
        st.tips += r.tips;
        st.burgers += s.burgersMade;
        st.fries += s.friesMade;
        st.drinks += s.drinksMade;
        st.orders += s.reviews.size();
        st.satisfactionSum += sat;
        st.accuracySum += acc;
        st.burnedPatties += m.burnedPatties;
        st.perfectOrders += perfect;
        if (r.score > st.bestScore) { st.bestScore = r.score; st.bestStars = r.stars; }
        st.bestMoney = Math.max(st.bestMoney, r.money + r.tips + r.bonus);
        st.bestCustomers = Math.max(st.bestCustomers, s.served);

        d.shift++;
        r.tips3 = PerformanceAnalyzer.tips(s);
        return r;
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}

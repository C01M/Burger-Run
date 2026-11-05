package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.List;

/** Turns a served tray into category scores, satisfaction and pay. */
public final class Scoring {
    private Scoring() {}

    public static final class Review {
        public double accuracy = 100, cook = -1, fry = -1, place = -1, drink = -1, wait = 100;
        public double satisfaction, pay, tip;
        public boolean burned, perfect, incomplete;
        public double completion = 1; // share of the ordered items that were actually on the tray
        public String comment = "";
    }

    public static double cookScore(Food.Patty p, int target, Fx fx) {
        int st = p.stage();
        if (st == Food.BURNED) return 5;
        double t = Food.targetOf(target);
        double win = .05 + fx.cookTol;
        double score = 100 - Math.max(0, Math.abs(p.avg() - t) - win) * 280;
        score -= Math.max(0, Math.abs(p.a - p.b) - .12) * 150;
        if (st == Food.OVER) score = Math.min(score, 45);
        return Math.max(0, score);
    }

    public static double fryScore(double p, Fx fx) {
        int st = Food.fryStage(p);
        if (st == 4) return 5;
        if (st == 3) return Math.max(15, 55 - (p - .7) * 130);
        double win = .08 + fx.goldenWiden;
        double score = 100 - Math.max(0, Math.abs(p - .575) - win) * 330;
        return Math.max(0, score);
    }

    public static double placementScore(double dx) {
        return Math.max(0, 100 - Math.max(0, Math.abs(dx) - 4) * 2.4);
    }

    public static double waitScore(double waited, double patience, Fx fx) {
        double grace = .4 + fx.graceBonus;
        double r = waited / patience;
        if (r <= grace) return 100;
        return Math.max(0, 100 * (1 - (r - grace) / (1 - grace)));
    }

    public static Review score(Customer c, Fx fx, Metrics m, Content.Special sp, int streak) {
        Review rv = new Review();
        Food.Order o = c.order;
        Food.Tray t = c.tray;
        List<Double> acc = new ArrayList<>();
        double pay = 0;
        int missingItems = 0, extraItems = 0;

        if (o.burger) {
            if (t.burger == null) { missingItems++; acc.add(0.0); }
            else {
                acc.add(scoreBurger(t.burger, o, rv, fx, m));
                pay += 5.5 + .3 * (o.veg.size() + o.sauce.size());
            }
        } else if (t.burger != null) extraItems++;

        if (o.fries) {
            if (t.fries == null) { missingItems++; acc.add(0.0); }
            else {
                rv.fry = fryScore(t.fries.p, fx);
                int st = t.fries.stage();
                if (st == 4) { rv.burned = true; m.burnedFries++; }
                else if (st == 3) m.overFries++;
                else if (st < 2) m.underFries++;
                acc.add(100.0);
                pay += 3;
            }
        } else if (t.fries != null) extraItems++;

        if (o.drink != null) {
            if (t.drink == null) { missingItems++; acc.add(0.0); }
            else {
                boolean right = t.drink.id.equals(o.drink);
                boolean size = t.drink.size == o.size;
                if (!right) m.wrongDrinks++; else if (!size) m.wrongSizes++;
                rv.drink = !right ? 0 : size ? 100 : 55;
                acc.add(rv.drink);
                if (right) pay += Content.drink(o.drink).price * new double[]{.8, 1, 1.3}[o.size];
            }
        } else if (t.drink != null) extraItems++;

        double accuracy = acc.isEmpty() ? 100 : acc.stream().mapToDouble(d -> d).average().orElse(100);
        accuracy = Math.max(0, accuracy - extraItems * 15);
        rv.accuracy = accuracy;
        rv.incomplete = missingItems > 0;
        rv.completion = o.itemCount() == 0 ? 1 : (double) (o.itemCount() - missingItems) / o.itemCount();
        rv.wait = waitScore(c.waited, c.patience, fx);

        double[] w = c.def.arch.weights;
        double sum = 0, wsum = 0;
        double[][] parts = {{rv.accuracy, w[0]}, {rv.cook, w[1]}, {rv.fry, w[2]}, {rv.place, w[3]}, {rv.drink, w[4]}, {rv.wait, w[5]}};
        for (double[] part : parts) {
            if (part[0] >= 0) { sum += part[0] * part[1]; wsum += part[1]; }
        }
        double sat = sum / wsum + fx.satBonus;
        if (rv.burned) sat -= 25;
        sat -= missingItems * 25;
        rv.satisfaction = Math.max(0, Math.min(100, sat));
        rv.perfect = rv.satisfaction >= 92 && missingItems == 0;

        double mult = fx.payMult * (sp != null ? sp.payMult : 1);
        if (rv.accuracy >= 99) pay *= 1.1;
        rv.pay = pay * mult * (rv.burned ? .7 : 1);
        double tipRate = rv.satisfaction < 40 ? 0 : (rv.satisfaction - 40) / 60 * .55;
        double streakMult = 1 + Math.min(streak, 10) * .05;
        rv.tip = pay * tipRate * c.def.arch.tip * fx.tipMult * streakMult * (1 + c.loyalty * .03) * (sp != null ? sp.tipMult : 1);
        rv.pay = round(rv.pay);
        rv.tip = round(rv.tip);
        rv.comment = comment(rv);
        return rv;
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }

    private static double scoreBurger(Food.Burger b, Food.Order o, Review rv, Fx fx, Metrics m) {
        List<String> expected = o.sequence();
        List<String> actual = new ArrayList<>();
        double placeSum = 0;
        int placeN = 0;
        for (Food.Layer l : b.layers) {
            actual.add(l.id);
            if (l.pour != null) {
                double q = 100 * Food.pourQuality(l.pour);
                m.sauces.add(q);
                placeSum += q;
            } else {
                placeSum += placementScore(l.dx);
            }
            placeN++;
        }
        List<String> left = new ArrayList<>(expected);
        int unwanted = 0;
        for (String id : actual) if (!left.remove(id)) unwanted++;
        int missing = left.size();
        m.wrongIngredients += unwanted;
        m.missingIngredients += missing;
        double accuracy = Math.max(0, 100 - 22 * (missing + unwanted));

        int lcs = lcs(expected, actual);
        if (lcs < expected.size() - missing) m.sequenceErrors += expected.size() - missing - lcs;
        double seq = 100.0 * lcs / expected.size();
        double center = placeN > 0 ? placeSum / placeN : 0;
        rv.place = .5 * seq + .5 * center;
        m.placements.add(center);

        Food.Patty p = b.patty();
        if (p == null) {
            rv.cook = 0;
        } else {
            rv.cook = cookScore(p, o.doneness, fx);
            int st = p.stage();
            if (st == Food.BURNED) { rv.burned = true; m.burnedPatties++; }
            else if (st == Food.OVER) m.overcookedPatties++;
            else if (st < o.doneness) m.undercookedPatties++;
            else if (st > o.doneness) m.overcookedPatties++;
            if (Math.abs(p.a - p.b) > .2) m.unevenPatties++;
        }
        return accuracy;
    }

    static int lcs(List<String> a, List<String> b) {
        int[] dp = new int[b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            int prev = 0;
            for (int j = 1; j <= b.size(); j++) {
                int tmp = dp[j];
                dp[j] = a.get(i - 1).equals(b.get(j - 1)) ? prev + 1 : Math.max(dp[j], dp[j - 1]);
                prev = tmp;
            }
        }
        return dp[b.size()];
    }

    private static String comment(Review r) {
        if (r.incomplete) return "Where's the rest?!";
        if (r.burned) return "This is charcoal!";
        if (r.accuracy < 60) return "That's not what I ordered.";
        if (r.cook >= 0 && r.cook < 50) return "Wrong doneness...";
        if (r.fry >= 0 && r.fry < 50) return "These fries are off.";
        if (r.wait < 35) return "Finally!";
        if (r.perfect) return "Perfect!";
        if (r.satisfaction >= 75) return "Delicious!";
        return "It's okay.";
    }
}

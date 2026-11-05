package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Generates varied orders: any non-empty mix of burger, fries and drink. */
public final class OrderManager {
    private OrderManager() {}

    // bit 1 burger, bit 2 fries, bit 4 drink. Array index = combo mask.
    static int pickCombo(Random r, int shift, Content.Arch arch, boolean multiItem) {
        double multi = Math.min(3.5, .8 + shift * .12) + (multiItem ? 3 : 0) + (arch == Content.Arch.BIG_EATER ? 3 : 0);
        double[] weights = {
                0,
                multiItem ? .2 : 1.6,  // burger
                multiItem ? .1 : .7,   // fries
                multi * .8,            // burger + fries
                multiItem ? .1 : .7,   // drink
                multi * .9,            // burger + drink
                multi * .5,            // fries + drink
                multi};                // everything
        double total = 0;
        for (double w : weights) total += w;
        double x = r.nextDouble() * total;
        for (int i = 1; i < weights.length; i++) {
            x -= weights[i];
            if (x <= 0) return i;
        }
        return 7;
    }

    public static Food.Order generate(Random r, Content.CustomerDef def, int shift, int level, Fx fx, Content.Special special) {
        Food.Order o = new Food.Order();
        int combo = pickCombo(r, shift, def.arch, special != null && special.multiItem);
        o.burger = (combo & 1) != 0;
        o.fries = (combo & 2) != 0;
        if ((combo & 4) != 0) {
            o.drink = pickDrink(r, def, level, fx);
            o.size = fx.cups ? r.nextInt(3) : 1;
        }
        if (o.burger) fillBurger(o, r, def, shift, level, special);
        return o;
    }

    private static void fillBurger(Food.Order o, Random r, Content.CustomerDef def, int shift, int level, Content.Special special) {
        int extra = special != null ? special.complexity : 0;
        int maxVeg = Math.min(6, 1 + shift / 3 + def.arch.complexity + extra);
        int vegCount = 1 + r.nextInt(maxVeg);
        if (r.nextDouble() < .08) vegCount = 0;
        List<String> pool = new ArrayList<>();
        for (Content.Ingredient i : Content.VEG) if (i.level <= level) pool.add(i.id);
        Collections.shuffle(pool, r);
        // customers lean toward what they like, so regulars feel consistent
        for (String like : def.likes) {
            if (pool.remove(like)) {
                if (r.nextDouble() < .75) pool.add(0, like); else pool.add(like);
            }
        }
        for (int i = 0; i < vegCount && i < pool.size(); i++) o.veg.add(pool.get(i));
        Collections.shuffle(o.veg, r);

        List<String> sauces = new ArrayList<>();
        for (Content.Ingredient i : Content.SAUCE) if (i.level <= level) sauces.add(i.id);
        Collections.shuffle(sauces, r);
        int sauceCount = Math.min(sauces.size(), r.nextInt(2 + (shift >= 4 ? 1 : 0) + (def.arch.complexity > 0 ? 1 : 0)));
        o.sauce.addAll(sauces.subList(0, sauceCount));

        if (def.arch == Content.Arch.GRILLER || def.arch == Content.Arch.CRITIC || shift >= 3) {
            o.doneness = Food.LIGHT + r.nextInt(3);
        } else {
            o.doneness = Food.MEDIUM;
        }
    }

    private static String pickDrink(Random r, Content.CustomerDef def, int level, Fx fx) {
        List<String> avail = new ArrayList<>();
        for (Content.Drink d : Content.DRINKS) {
            if (d.level <= level && d.fridge <= fx.premium) avail.add(d.id);
        }
        if (avail.contains(def.drink) && r.nextDouble() < .55) return def.drink;
        return avail.get(r.nextInt(avail.size()));
    }

    /** Estimated seconds a competent player needs, used to scale customer patience. */
    public static double workSeconds(Food.Order o) {
        double t = 30;
        if (o.burger) t += 62 + 5 * (o.veg.size() + o.sauce.size());
        if (o.fries) t += 34;
        if (o.drink != null) t += 8;
        return t;
    }
}

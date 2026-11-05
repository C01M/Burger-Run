package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Plain food objects shared by the grill, fryer, boards and trays. */
public final class Food {
    private Food() {}

    public static final String[] PATTY_STAGE = {"Raw", "Lightly Cooked", "Medium", "Well Done", "Overcooked", "Burned"};
    public static final String[] FRY_STAGE = {"Raw", "Lightly Fried", "Golden", "Overcooked", "Burned"};
    public static final int LIGHT = 1, MEDIUM = 2, WELL = 3, OVER = 4, BURNED = 5;
    public static final double SECONDS_PER_SIDE = 24;
    public static final double SECONDS_FRY = 28;

    public static double targetOf(int stage) {
        return stage == LIGHT ? .3 : stage == WELL ? .7 : .5;
    }

    public static final class Patty {
        public double a, b;          // per-side cook amount: 0 raw, 1 burned
        public boolean downIsB;
        public int flips;
        public int target = MEDIUM;  // what the grill hand aims for
        public boolean warned, burnedEvent, readyEvent;
        public double flipAnim;

        public double down() { return downIsB ? b : a; }
        public double up() { return downIsB ? a : b; }
        public double avg() { return (a + b) / 2; }
        public int stage() {
            double max = Math.max(a, b), v = avg();
            if (max >= 1) return BURNED;
            if (max >= .8) return OVER;
            return v < .2 ? 0 : v < .4 ? LIGHT : v < .6 ? MEDIUM : WELL;
        }
        public void cook(double dt, Fx fx) {
            double rate = fx.grillRate / SECONDS_PER_SIDE;
            if (down() >= .8) rate *= fx.heatSlow;
            if (downIsB) b += rate * dt; else a += rate * dt;
            if (flipAnim > 0) flipAnim = Math.max(0, flipAnim - dt * 3);
        }
        public void flip() { downIsB = !downIsB; flips++; flipAnim = 1; }
        /** 0..1 progress of the flip animation, for drawing only. */
        public float flipAnimAmount() { return (float) flipAnim; }
        public float flipAnimHop() { return (float) Math.sin(flipAnim * Math.PI); }
    }

    public static final class Fries {
        public double p;
        public Fries(double p) { this.p = p; }
        public int stage() { return fryStage(p); }
    }

    public static int fryStage(double p) {
        return p < .2 ? 0 : p < .45 ? 1 : p < .7 ? 2 : p < 1 ? 3 : 4;
    }

    public static final class Basket {
        public static final int EMPTY = 0, UP = 1, DOWN = 2;
        public int state = EMPTY;
        public double p;
        public boolean warned, burnedEvent, readyEvent;
    }

    /** One item in the warming cabinet: a patty or a box of fries. */
    public static final class Held {
        public Patty patty;
        public Fries fries;
        public Held(Patty p) { patty = p; }
        public Held(Fries f) { fries = f; }
    }

    public static final class Layer {
        public final String id; // bunB, patty, bunT, or an ingredient id
        public final double dx;
        public final Patty patty;
        /** For poured sauce: how much landed across the burger, left to right (null for solid layers). */
        public final double[] pour;
        public float dropAnim = 1;
        public Layer(String id, double dx, Patty patty) { this.id = id; this.dx = dx; this.patty = patty; this.pour = null; }
        public Layer(String id, double[] pour) { this.id = id; this.dx = 0; this.patty = null; this.pour = pour; this.dropAnim = 0; }
    }

    /** Sauce coverage is tracked in this many strips across the burger; the outer two are the bun's rim. */
    public static final int SAUCE_BINS = 13;
    /** Amount per strip that counts as covered, and the comfortable range for the average amount. */
    public static final double SAUCE_COVERED = .35, SAUCE_MIN = .6, SAUCE_MAX = 1.8;

    /** 0..1: how fully, evenly and generously (but not too generously) a poured sauce covers the burger. */
    public static double pourQuality(double[] a) {
        int n = 0, covered = 0;
        double sum = 0, sq = 0;
        for (int i = 1; i < a.length - 1; i++) {
            n++;
            sum += a[i];
            sq += a[i] * a[i];
            if (a[i] >= SAUCE_COVERED) covered++;
        }
        double mean = sum / n, sd = Math.sqrt(Math.max(0, sq / n - mean * mean));
        double cover = (double) covered / n;
        double even = mean <= 0 ? 0 : Math.max(0, 1 - sd / mean);
        double amount = mean < SAUCE_MIN ? mean / SAUCE_MIN : mean > SAUCE_MAX ? Math.max(0, 1 - (mean - SAUCE_MAX) / SAUCE_MAX) : 1;
        return .5 * cover + .3 * even + .2 * amount;
    }

    public static final class Burger {
        public final List<Layer> layers = new ArrayList<>();
        public boolean finished() {
            return !layers.isEmpty() && layers.get(layers.size() - 1).id.equals("bunT");
        }
        public Patty patty() {
            for (Layer l : layers) if (l.patty != null) return l.patty;
            return null;
        }
    }

    public static final class DrinkItem {
        public final String id;
        public final int size; // 0 S, 1 M, 2 L
        public DrinkItem(String id, int size) { this.id = id; this.size = size; }
    }

    public static final class Order {
        public boolean burger, fries;
        public int doneness = MEDIUM;
        public final List<String> veg = new ArrayList<>();
        public final List<String> sauce = new ArrayList<>();
        public String drink; // null when not ordered
        public int size = 1;

        public int itemCount() { return (burger ? 1 : 0) + (fries ? 1 : 0) + (drink != null ? 1 : 0); }
        public List<String> sequence() {
            List<String> s = new ArrayList<>();
            s.add("bunB"); s.add("patty"); s.addAll(veg); s.addAll(sauce); s.add("bunT");
            return s;
        }
        public String encode() {
            return (burger ? 1 : 0) + "|" + (fries ? 1 : 0) + "|" + doneness + "|" + String.join(",", veg) + "|"
                    + String.join(",", sauce) + "|" + (drink == null ? "" : drink) + "|" + size;
        }
        public static Order decode(String s) {
            String[] p = s.split("\\|", -1);
            Order o = new Order();
            o.burger = p[0].equals("1");
            o.fries = p[1].equals("1");
            o.doneness = Integer.parseInt(p[2]);
            if (!p[3].isEmpty()) o.veg.addAll(Arrays.asList(p[3].split(",")));
            if (!p[4].isEmpty()) o.sauce.addAll(Arrays.asList(p[4].split(",")));
            o.drink = p[5].isEmpty() ? null : p[5];
            o.size = Integer.parseInt(p[6]);
            return o;
        }
    }

    public static final class Tray {
        public Burger burger;
        public Fries fries;
        public DrinkItem drink;
        public boolean empty() { return burger == null && fries == null && drink == null; }
    }
}

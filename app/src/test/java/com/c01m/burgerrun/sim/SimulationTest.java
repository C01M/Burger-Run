package com.c01m.burgerrun.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

public class SimulationTest {
    private static final ShiftManager.Listener QUIET = new ShiftManager.Listener() {
        @Override public void sound(String id) {}
        @Override public void alert(String text) {}
        @Override public void served(Customer c, Scoring.Review r) {}
    };

    /** A bot that plays through the same public actions as the touch UI. */
    private static final class Bot {
        final ShiftManager s;
        final boolean careless;
        final double reaction;
        double nextAct, lastServeAt;
        int lastServed;
        Bot(ShiftManager s, boolean careless, double reaction) { this.s = s; this.careless = careless; this.reaction = reaction; }

        void act() {
            if (s.time < nextAct) return;
            nextAct = s.time + reaction;
            if (s.served != lastServed) { lastServed = s.served; lastServeAt = s.time; }
            if (s.time - lastServeAt > 90) { // unstick, as a player would, by binning clutter
                lastServeAt = s.time;
                for (int i = 0; i < s.cabinet.length; i++) s.trashHeld(i);
            }
            for (Customer c : s.active) if (c.status == Customer.WAITING) { s.takeOrder(c); return; }
            if (careless) return;
            cook();
            for (Customer c : new ArrayList<>(s.active)) {
                if (c.status != Customer.ORDERED) continue;
                if (c.complete()) { s.serve(c); return; }
                if (!s.hasTrayFor(c)) continue;
                if (c.order.drink != null && c.tray.drink == null) { s.drinkToTray(c.order.drink, c.order.size, c); return; }
                if (c.order.fries && c.tray.fries == null) {
                    for (int i = 0; i < s.cabinet.length; i++) {
                        if (s.cabinet[i] != null && s.cabinet[i].fries != null) { s.friesToTray(i, c); return; }
                    }
                }
                if (c.order.burger && c.tray.burger == null && build(c)) return;
            }
        }

        Customer owner; // who the burger on the board is for

        boolean build(Customer c) {
            if (owner != null && !s.active.contains(owner)) { s.trashBoard(0); owner = null; }
            if (owner != null && owner != c && !s.hasTrayFor(owner)) { s.trashBoard(0); owner = null; }
            if (owner != null && owner != c) return false;
            owner = c;
            Food.Burger b = s.boards[0];
            List<String> seq = c.order.sequence();
            if (b.layers.size() == seq.size()) { owner = null; return s.burgerToTray(0, c); }
            String next = seq.get(b.layers.size());
            if (next.equals("bunB")) return s.addBun(0, false, 2);
            if (next.equals("bunT")) return s.addBun(0, true, 2);
            if (next.equals("patty")) {
                for (int i = 0; i < s.cabinet.length; i++) {
                    Food.Held h = s.cabinet[i];
                    if (h != null && h.patty != null && h.patty.target == c.order.doneness) return s.addPatty(0, i, 2);
                }
                return false;
            }
            if (Content.ingredient(next).sauce) {
                // a steady edge-to-edge sweep, like a careful player
                for (int k = 0; k <= 30; k++) s.pour(0, next, -1 + 2 * k / 30.0, 2.0 / 30);
                return true;
            }
            return s.addIngredient(0, next, 2);
        }

        void cook() {
            for (int stage = Food.LIGHT; stage <= Food.WELL; stage++) {
                int pending = 0, have = 0;
                for (Customer c : s.active) if (c.status == Customer.ORDERED && c.order.burger && c.tray.burger == null && c.order.doneness == stage) pending++;
                for (Food.Patty p : s.grill) if (p != null && p.target == stage) have++;
                for (Food.Held h : s.cabinet) if (h != null && h.patty != null && h.patty.target == stage) have++;
                Food.Patty onBoard = s.boards[0].patty();
                if (onBoard != null && onBoard.target == stage) have++;
                if (have < pending && s.freeGrill() >= 0) {
                    int slot = s.freeGrill();
                    s.placePatty(slot);
                    s.grill[slot].target = stage;
                }
                // bin surplus patties so the cabinet can't clog
                if (have > pending && s.freeCabinet() < 0) {
                    for (int i = 0; i < s.cabinet.length; i++) {
                        Food.Held h = s.cabinet[i];
                        if (h != null && h.patty != null && h.patty.target == stage) { s.trashHeld(i); break; }
                    }
                }
            }
            for (int i = 0; i < s.grill.length; i++) {
                Food.Patty p = s.grill[i];
                if (p == null) continue;
                double t = Food.targetOf(p.target);
                if (p.down() >= t && p.up() < t) s.flip(i);
                else if (p.a >= t && p.b >= t) s.pattyToCabinet(i, -1);
            }
            int wantFries = 0;
            for (Customer c : s.active) if (c.status == Customer.ORDERED && c.order.fries && c.tray.fries == null) wantFries++;
            int haveFries = 0;
            for (Food.Held h : s.cabinet) if (h != null && h.fries != null) haveFries++;
            for (Food.Basket b : s.baskets) {
                if (b.state != Food.Basket.EMPTY) haveFries++;
            }
            if (s.packed != null) haveFries++;
            if (haveFries < wantFries && s.freeBasket() >= 0) s.fillBasket(s.freeBasket());
            for (int i = 0; i < s.baskets.length; i++) {
                Food.Basket b = s.baskets[i];
                if (b.state == Food.Basket.UP && b.p == 0) s.toggleBasket(i);
                else if (b.state == Food.Basket.DOWN && b.p >= .55) s.toggleBasket(i);
                else if (b.state == Food.Basket.UP && s.packed == null) s.boxFries(i);
            }
            if (s.packed != null) {
                // like a player would: bin a patty when a full warmer blocks the fries
                if (s.freeCabinet() < 0) s.trashHeld(0);
                s.packedToCabinet(-1);
            }
        }
    }

    private static ShiftManager run(SaveData save, long seed, boolean careless, double reaction, double maxSeconds) {
        ShiftManager s = new ShiftManager(save, new Random(seed), 1, QUIET);
        Bot bot = new Bot(s, careless, reaction);
        double dt = 1 / 30.0;
        while (!s.finished && s.time < maxSeconds) {
            s.tick(dt);
            bot.act();
        }
        return s;
    }

    private static String dump(ShiftManager s) {
        StringBuilder b = new StringBuilder("served " + s.served + "/" + s.scheduled + " t=" + (int) s.time + "\n");
        for (Customer c : s.active) b.append("  ").append(c.def.id).append(" st=").append(c.status).append(" order=").append(c.order.encode())
                .append(" tray[b=").append(c.tray.burger != null).append(",f=").append(c.tray.fries != null).append(",d=").append(c.tray.drink != null).append("]\n");
        b.append("  cabinet:");
        for (Food.Held h : s.cabinet) b.append(h == null ? " -" : h.patty != null ? " P" + h.patty.target : " F");
        b.append("\n  grill:");
        for (Food.Patty p : s.grill) b.append(p == null ? " -" : " p" + p.target);
        b.append("\n  baskets:");
        for (Food.Basket k : s.baskets) b.append(" ").append(k.state);
        b.append("\n  board layers=").append(s.boards[0].layers.size());
        return b.toString();
    }

    @Test public void tutorialHoldsOneCalmCustomerUntilServed() {
        ShiftManager s = new ShiftManager(new SaveData(), new Random(5), 1, QUIET);
        s.tutorial = true;
        for (int i = 0; i < 600; i++) s.tick(1);
        assertEquals("nobody else arrives during the tutorial", 1, s.active.size());
        Customer c = s.active.get(0);
        assertEquals(Content.Arch.EASY, c.def.arch);
        assertEquals(0, c.waited, 0);
        assertEquals(ShiftManager.tutorialOrder().encode(), c.order.encode());
        s.takeOrder(c);
        assertTrue(s.drinkToTray("cola", 1, c));
        s.serve(c);
        assertFalse(s.tutorialHold());
        while (s.spawned < 2) s.tick(1);
        s.tick(1);
        assertTrue("patience runs again after the tutorial", s.active.get(0).waited > 0);
    }

    @Test public void rushingOutPartialOrdersEarnsFewStars() {
        SaveData d = new SaveData();
        ShiftManager s = new ShiftManager(d, new Random(2), 1, QUIET);
        while (!s.finished) {
            s.tick(1);
            for (Customer c : new ArrayList<>(s.active)) {
                s.takeOrder(c);
                if (c.tray.empty() && s.drinkToTray("water", 1, c)) s.serve(c);
            }
        }
        Progression.Result r = Progression.finish(s);
        assertTrue("drink-only trays should not pass: " + r.score, r.stars <= 1);
    }

    @Test public void listenerSeesTheShiftFinishWhenTheLastCustomerIsServed() {
        boolean[] finishedAtServe = new boolean[1];
        ShiftManager[] ref = new ShiftManager[1];
        ShiftManager s = new ShiftManager(new SaveData(), new Random(2), 1, new ShiftManager.Listener() {
            @Override public void sound(String id) {}
            @Override public void alert(String text) {}
            @Override public void served(Customer c, Scoring.Review r) { finishedAtServe[0] = ref[0].finished; }
        });
        ref[0] = s;
        while (s.served < s.scheduled) {
            s.tick(1);
            for (Customer c : new ArrayList<>(s.active)) {
                s.takeOrder(c);
                if (c.tray.empty() && s.drinkToTray("water", 1, c)) s.serve(c);
            }
        }
        assertTrue(s.finished);
        assertTrue("the last serve must report a finished shift", finishedAtServe[0]);
    }

    @Test public void botFinishesATutorialShift() {
        ShiftManager s = new ShiftManager(new SaveData(), new Random(9), 1, QUIET);
        s.tutorial = true;
        Bot bot = new Bot(s, false, 1.5);
        while (!s.finished && s.time < 3600) { s.tick(1 / 30.0); bot.act(); }
        assertTrue(dump(s), s.finished);
        assertEquals(s.scheduled, s.served);
    }

    @Test public void pattyPassesThroughEveryStageAndBurns() {
        Fx fx = Fx.of(new SaveData());
        Food.Patty p = new Food.Patty();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 800; i++) {
            p.cook(.1, fx);
            if (i % 40 == 39) p.flip();
            seen.add(p.stage());
        }
        assertEquals(6, seen.size());
        assertEquals(Food.BURNED, p.stage());
    }

    @Test public void unflippedPattyBurnsBeforeReachingWellDone() {
        Fx fx = Fx.of(new SaveData());
        Food.Patty p = new Food.Patty();
        p.cook(25, fx);
        assertEquals(Food.BURNED, p.stage());
    }

    @Test public void heatControlSlowsBurning() {
        SaveData d = new SaveData();
        d.upgrades.put("heat", 2);
        Food.Patty p = new Food.Patty();
        for (int i = 0; i < 250; i++) p.cook(.1, Fx.of(d));
        assertTrue(p.a < 1);
    }

    @Test public void cookScoreRewardsTheRequestedDoneness() {
        Fx fx = Fx.of(new SaveData());
        Food.Patty p = new Food.Patty();
        p.a = .5; p.b = .5;
        assertEquals(100, Scoring.cookScore(p, Food.MEDIUM, fx), .01);
        assertTrue(Scoring.cookScore(p, Food.WELL, fx) < 70);
        p.a = 1.1;
        assertEquals(5, Scoring.cookScore(p, Food.MEDIUM, fx), .01);
    }

    @Test public void friesScorePeaksWhenGolden() {
        Fx fx = Fx.of(new SaveData());
        assertEquals(100, Scoring.fryScore(.575, fx), .01);
        assertTrue(Scoring.fryScore(.3, fx) < 70);
        assertTrue(Scoring.fryScore(.85, fx) < 50);
        assertEquals(5, Scoring.fryScore(1.2, fx), .01);
    }

    @Test public void everyOrderComboIsGeneratedAndNoOrderIsEmpty() {
        Random r = new Random(7);
        Fx fx = Fx.of(new SaveData());
        Set<String> combos = new HashSet<>();
        Set<String> distinct = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            Content.CustomerDef def = Content.CUSTOMERS.get(i % 5);
            Food.Order o = OrderManager.generate(r, def, 6, 6, fx, null);
            assertTrue(o.itemCount() > 0);
            combos.add(o.burger + "/" + o.fries + "/" + (o.drink != null));
            distinct.add(o.encode());
        }
        assertEquals(7, combos.size());
        assertTrue(distinct.size() > 150);
    }

    @Test public void lockedContentIsNeverOrderedEarly() {
        Random r = new Random(9);
        Fx fx = Fx.of(new SaveData());
        for (int i = 0; i < 500; i++) {
            Food.Order o = OrderManager.generate(r, Content.CUSTOMERS.get(i % 5), 2, 1, fx, null);
            for (String v : o.veg) assertEquals(1, Content.ingredient(v).level);
            for (String v : o.sauce) assertEquals(1, Content.ingredient(v).level);
            if (o.drink != null) assertEquals(1, Content.drink(o.drink).level);
        }
    }

    @Test public void orderEncodingRoundTrips() {
        Food.Order o = new Food.Order();
        o.burger = true; o.doneness = Food.WELL; o.veg.add("olive"); o.sauce.add("mayo"); o.drink = "tea"; o.size = 2;
        assertEquals(o.encode(), Food.Order.decode(o.encode()).encode());
        Food.Order f = new Food.Order();
        f.fries = true;
        Food.Order back = Food.Order.decode(f.encode());
        assertNull(back.drink);
        assertTrue(back.fries);
        assertFalse(back.burger);
    }

    @Test public void itemsStayOnTheirOwnTicketAndTraysAreLimited() {
        SaveData d = new SaveData();
        ShiftManager s = new ShiftManager(d, new Random(3), 1, QUIET);
        while (s.active.size() < 3) s.tick(1);
        for (Customer c : s.active) s.takeOrder(c);
        Customer a = s.active.get(0), b = s.active.get(1), c = s.active.get(2);
        assertTrue(s.drinkToTray("cola", 1, a));
        assertNull(b.tray.drink);
        assertTrue(s.drinkToTray("water", 1, b));
        assertEquals("cola", a.tray.drink.id);
        assertFalse("only two trays at start", s.drinkToTray("cola", 1, c));
        assertEquals(1, s.metrics.traysFull);
    }

    @Test public void wrongDrinkAndMissingItemsHurt() {
        SaveData d = new SaveData();
        Fx fx = Fx.of(d);
        Food.Order o = new Food.Order();
        o.drink = "cola"; o.fries = true;
        Customer c = new Customer(1, Content.CUSTOMERS.get(0), o, "", 100);
        c.tray.drink = new Food.DrinkItem("water", 1);
        c.tray.fries = new Food.Fries(.57);
        Metrics m = new Metrics();
        Scoring.Review wrong = Scoring.score(c, fx, m, null, 0);
        assertEquals(1, m.wrongDrinks);
        c.tray.drink = new Food.DrinkItem("cola", 1);
        Scoring.Review right = Scoring.score(c, fx, new Metrics(), null, 0);
        assertTrue(right.satisfaction > wrong.satisfaction);
        assertTrue(right.pay > wrong.pay);
        c.tray.fries = null;
        Scoring.Review partial = Scoring.score(c, fx, new Metrics(), null, 0);
        assertTrue(partial.incomplete);
        assertTrue(partial.satisfaction < right.satisfaction - 25);
    }

    @Test public void customerCountsGrowAndReachLateGameSize() {
        assertEquals(5, ShiftManager.customerCount(1, null));
        assertTrue(ShiftManager.customerCount(30, null) >= 25);
        assertTrue(ShiftManager.customerCount(8, Content.Special.LUNCH) > ShiftManager.customerCount(8, null));
        assertNull(ShiftManager.specialFor(3, 1));
        assertNotNull(ShiftManager.specialFor(4, 1));
    }

    @Test public void skilledBotCompletesEarlyShiftsWithHighStars() {
        SaveData d = new SaveData();
        for (int i = 0; i < 6; i++) {
            ShiftManager s = run(d, 100 + i, false, .35, 3000);
            assertTrue("shift " + (i + 1) + " should finish", s.finished);
            Progression.Result r = Progression.finish(s);
            System.out.printf("shift %d: %d customers, %.0fs, score %.0f, %d stars, sat %.0f, wait %.0f, eff %.0f, $%.0f, level %d%n",
                    r.shift, r.customers, s.time, r.score, r.stars, r.satisfaction, r.wait, r.efficiency, r.money + r.tips + r.bonus, r.levelAfter);
            assertTrue("competent play earns at least 3 stars, got " + r.stars + " on shift " + (i + 1), r.stars >= 3);
            assertEquals(3, r.tips3.size());
        }
        assertEquals(7, d.shift);
        assertTrue(d.level > 1);
        assertTrue(d.stats.customers >= 30);
        assertFalse(d.regulars.isEmpty());
    }

    @Test public void lateGameIsHarderForTheSameBot() {
        SaveData early = new SaveData();
        ShiftManager a = run(early, 1, false, .9, 6000);
        double earlyScore = Progression.finish(a).score;
        SaveData late = new SaveData();
        late.shift = 30;
        late.level = 20;
        ShiftManager b = run(late, 1, false, .9, 6000);
        assertTrue("late shift stuck at " + b.served + "/" + b.scheduled, b.finished);
        Progression.Result r = Progression.finish(b);
        System.out.printf("late-game score %.0f vs early %.0f%n", r.score, earlyScore);
        assertTrue(r.score < earlyScore);
    }

    @Test public void humanPacedDifficultyCurve() {
        double[] reactions = {1.5, 2.5, 3.5};
        int[] shifts = {1, 5, 10, 15, 20, 30};
        for (double reaction : reactions) {
            StringBuilder line = new StringBuilder("reaction " + reaction + "s:");
            for (int shift : shifts) {
                double score = 0, wait = 0, money = 0, stars = 0;
                int runs = 8;
                for (int seed = 0; seed < runs; seed++) {
                    SaveData d = new SaveData();
                    d.shift = shift;
                    d.level = Math.min(30, 1 + shift);
                    ShiftManager s = run(d, seed, false, reaction, 8000);
                    assertTrue("stuck on shift " + shift + " reaction " + reaction + " " + dump(s), s.finished);
                    Progression.Result r = Progression.finish(s);
                    score += r.score; wait += r.wait; stars += r.stars; money += r.money + r.tips + r.bonus;
                }
                line.append(String.format(java.util.Locale.US, "  S%d=%.0f/%.1f*/w%.0f/$%.0f", shift, score / runs, stars / runs, wait / runs, money / runs));
            }
            System.out.println("CURVE " + line);
        }
    }

    @Test public void coachQuotesRealDataAndAlwaysGivesThreeDistinctTips() {
        SaveData d = new SaveData();
        ShiftManager s = new ShiftManager(d, new Random(1), 1, QUIET);
        s.metrics.burnedPatties = 3;
        s.metrics.wrongDrinks = 2;
        s.metrics.missedDrinkTime = 30;
        List<String> tips = PerformanceAnalyzer.tips(s);
        assertEquals(3, tips.size());
        assertEquals(3, new HashSet<>(tips).size());
        assertTrue(tips.get(0).contains("3 burned"));
        assertEquals(3, PerformanceAnalyzer.tips(new ShiftManager(new SaveData(), new Random(1), 1, QUIET)).size());
    }

    @Test public void upgradesCostMoneyAndRespectLevelGates() {
        SaveData d = new SaveData();
        Content.UpgradeDef prep = Content.upgrade("prep");
        d.money = 1000;
        assertFalse(Progression.buy(d, prep));
        d.level = 3;
        assertTrue(Progression.buy(d, prep));
        assertEquals(880, d.money, .01);
        assertEquals(2, Fx.of(d).boards);
        Progression.buy(d, prep);
        assertFalse("max level", Progression.buy(d, prep));
    }

    @Test public void everyUpgradeLevelProducesValidFx() {
        SaveData d = new SaveData();
        for (Content.UpgradeDef u : Content.UPGRADES) {
            assertEquals(u.id, u.prices.length, u.levels.length);
            d.upgrades.put(u.id, u.prices.length);
        }
        Fx fx = Fx.of(d);
        assertEquals(9, fx.grillSlots);
        assertEquals(6, fx.trays);
        assertEquals(12, Progression.offlineCapHours(d), .01);
    }

    @Test public void offlineEarningsRequireNightCrewAndAreCapped() {
        SaveData d = new SaveData();
        long now = 1_000_000_000L;
        d.lastSeen = now - 3_600_000L * 100;
        assertEquals(0, Progression.offlineEarnings(d, now), .01);
        d.upgrades.put("night", 1);
        double capped = Progression.offlineEarnings(d, now);
        assertEquals(Progression.offlineRatePerHour(d) * 4, capped, .01);
        d.lastSeen = now + 5000;
        assertEquals("clock moved backwards", 0, Progression.offlineEarnings(d, now), .01);
    }

    @Test public void staffDoTheirJobs() {
        SaveData d = new SaveData();
        d.upgrades.put("greeter", 1);
        d.upgrades.put("runner", 2);
        d.upgrades.put("griller", 2);
        d.upgrades.put("fryer", 2);
        ShiftManager s = new ShiftManager(d, new Random(4), 1, QUIET);
        for (int i = 0; i < 60; i++) s.tick(.2);
        Customer c = s.active.get(0);
        assertEquals("Gia took the order", Customer.ORDERED, c.status);
        s.placePatty(0);
        s.fillBasket(0);
        s.toggleBasket(0);
        for (int i = 0; i < 600 && s.grillCount() > 0; i++) s.tick(.1);
        assertEquals("Hal moved it to the cabinet", 0, s.grillCount());
        Food.Held h = s.cabinet[0];
        assertNotNull(h);
        assertTrue("Hal flipped it", h.patty == null || h.patty.flips >= 1);
        for (int i = 0; i < 300; i++) s.tick(.1);
        assertEquals("Faye boxed the fries", Food.Basket.EMPTY, s.baskets[0].state);
        assertNull("and moved the box to the warmer", s.packed);
    }

    @Test public void sauceIsScoredOnEvenCoverage() {
        ShiftManager s = new ShiftManager(new SaveData(), new Random(7), 1, QUIET);
        assertFalse("no bun yet", s.pour(0, "ketchup", 0, .1));
        s.addBun(0, false, 0);
        for (int k = 0; k <= 30; k++) s.pour(0, "ketchup", -1 + 2 * k / 30.0, 2.0 / 30);
        Food.Layer even = s.boards[0].layers.get(1);
        assertEquals("one layer however long you pour", 2, s.boards[0].layers.size());
        double sweep = Food.pourQuality(even.pour);

        double[] blob = new double[Food.SAUCE_BINS];
        ShiftManager t = new ShiftManager(new SaveData(), new Random(7), 1, QUIET);
        t.addBun(0, false, 0);
        for (int k = 0; k < 30; k++) t.pour(0, "ketchup", 0, 2.0 / 30);
        double spot = Food.pourQuality(t.boards[0].layers.get(1).pour);

        ShiftManager u = new ShiftManager(new SaveData(), new Random(7), 1, QUIET);
        u.addBun(0, false, 0);
        for (int r = 0; r < 4; r++) for (int k = 0; k <= 30; k++) u.pour(0, "ketchup", -1 + 2 * k / 30.0, 2.0 / 30);
        double drowned = Food.pourQuality(u.boards[0].layers.get(1).pour);

        assertTrue("sweep " + sweep, sweep > .9);
        assertTrue("one spot " + spot, spot < .5);
        assertTrue("too much " + drowned, drowned < sweep - .1);
        assertEquals(0, Food.pourQuality(blob), 1e-9);
        assertFalse("pouring off the side lands nothing", s.pour(0, "ketchup", 1.5, .1));
    }

    @Test public void friesMustBeBoxedBeforeTheWarmer() {
        ShiftManager s = new ShiftManager(new SaveData(), new Random(6), 1, QUIET);
        s.fillBasket(0);
        s.toggleBasket(0);
        assertFalse("still in the oil", s.boxFries(0));
        for (int i = 0; i < 160; i++) s.tick(.1);
        s.toggleBasket(0);
        assertFalse("nothing packed yet", s.packedToCabinet(-1));
        assertTrue(s.boxFries(0));
        assertEquals(Food.Basket.EMPTY, s.baskets[0].state);
        assertNotNull(s.packed);
        s.fillBasket(0);
        s.toggleBasket(0);
        s.toggleBasket(0);
        assertFalse("one box at a time", s.boxFries(0));
        assertTrue(s.packedToCabinet(-1));
        assertNull(s.packed);
        assertNotNull(s.cabinet[0].fries);
    }
}

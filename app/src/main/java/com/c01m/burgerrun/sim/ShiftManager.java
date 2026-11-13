package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** One working day: the customers, the grill, the fryer, boards, trays and every player action. */
public final class ShiftManager {
    public interface Listener {
        void sound(String id);
        void alert(String text);
        void served(Customer c, Scoring.Review r);
    }

    public static final int MAX_ACTIVE = 6;
    public static final int STATION_ASSEMBLY = 0, STATION_COOKING = 1;

    public final SaveData save;
    public final Fx fx;
    public final int number;
    public final Content.Special special;
    public final Metrics metrics = new Metrics();
    public final List<Customer> active = new ArrayList<>();
    public final List<Scoring.Review> reviews = new ArrayList<>();
    public final Food.Patty[] grill;
    public final Food.Basket[] baskets;
    public final Food.Held[] cabinet;
    public final Food.Burger[] boards;
    public final int scheduled;
    public int spawned, served, streak, bestStreak, burgersMade, friesMade, drinksMade;
    public double time, moneyEarned, tipsEarned;
    public Customer selected;
    public boolean finished;
    public int station = STATION_ASSEMBLY;
    public boolean fridgeOpen;
    /** Fries poured into a box at the packing station, waiting to go into the warmer. */
    public Food.Fries packed;
    /** Guided first order: one calm customer whose patience is frozen until they are served. */
    public boolean tutorial;

    private final Random rng;
    private final Listener listener;
    private final double difficulty;
    private double nextSpawn = 1.5;
    private int uidCounter;
    private double leftCookingAt = -1;

    public ShiftManager(SaveData save, Random rng, double difficulty, Listener listener) {
        this.save = save;
        this.rng = rng;
        this.difficulty = difficulty;
        this.listener = listener;
        fx = Fx.of(save);
        number = save.shift;
        special = specialFor(number, save.level);
        scheduled = customerCount(number, special);
        grill = new Food.Patty[fx.grillSlots];
        baskets = new Food.Basket[fx.baskets];
        for (int i = 0; i < baskets.length; i++) baskets[i] = new Food.Basket();
        cabinet = new Food.Held[fx.cabinet];
        boards = new Food.Burger[fx.boards];
        for (int i = 0; i < boards.length; i++) boards[i] = new Food.Burger();
    }

    public static Content.Special specialFor(int shift, int level) {
        if (shift < 4 || shift % 4 != 0) return null;
        Content.Special[] order = Content.Special.values();
        Content.Special s = order[(shift / 4 - 1) % order.length];
        return s.minLevel > level ? Content.Special.LUNCH : s;
    }

    public static int customerCount(int shift, Content.Special sp) {
        int base = Math.min(25, 5 + (int) ((shift - 1) * .7));
        return (int) Math.min(32, Math.round(base * (sp != null ? sp.countMult : 1)));
    }

    double spawnInterval() {
        double base = Math.max(11, 40 - number * 1.3);
        return base * (special != null ? special.intervalMult : 1) * (1.1 - .1 * difficulty);
    }

    public int remaining() { return scheduled - served; }
    public int linedUp() { return scheduled - spawned; }
    private final java.util.Set<String> seenThisShift = new java.util.HashSet<>();

    public boolean tutorialHold() { return tutorial && served == 0; }

    static Food.Order tutorialOrder() {
        Food.Order o = new Food.Order();
        o.burger = true;
        o.doneness = Food.MEDIUM;
        o.veg.add("lettuce");
        o.veg.add("tomato");
        o.sauce.add("ketchup");
        o.fries = true;
        o.drink = "cola";
        return o;
    }

    // ---------------------------------------------------------------- tick

    public void tick(double dt) {
        if (finished) return;
        time += dt;
        if (station == STATION_COOKING) metrics.timeCooking += dt;
        else if (fridgeOpen) metrics.timeFridge += dt;
        else metrics.timeAssembly += dt;

        boolean hold = tutorialHold();
        nextSpawn -= dt;
        if (spawned < scheduled && active.size() < MAX_ACTIVE && (nextSpawn <= 0 || active.isEmpty()) && !(hold && spawned > 0)) {
            spawn();
            nextSpawn = spawnInterval() * (.8 + rng.nextDouble() * .4);
        }
        for (Customer c : active) {
            if (!hold) c.waited += dt;
            if (c.enterAnim > 0) c.enterAnim = Math.max(0, c.enterAnim - dt * 1.4);
        }
        tickGrill(dt);
        tickFryer(dt);
        tickStaff();
        recordIdle(dt);
    }

    private void spawn() {
        Content.CustomerDef def = chooseCustomer();
        Food.Order order = tutorial && spawned == 0 ? tutorialOrder() : makeOrder(def);
        SaveData.Regular reg = save.regulars.get(def.id);
        double patience = (OrderManager.workSeconds(order) * 1.9 + 20) * def.arch.patience * fx.patience * difficultyPatience();
        String hello = def.arch.hello[rng.nextInt(def.arch.hello.length)];
        Customer c = new Customer(++uidCounter, def, order, hello, patience);
        c.arrivedAt = time;
        c.loyalty = reg != null ? reg.loyalty : 0;
        boolean[] used = new boolean[MAX_ACTIVE];
        for (Customer o : active) used[o.spot] = true;
        for (int i = 0; i < MAX_ACTIVE; i++) if (!used[i]) { c.spot = i; break; }
        active.add(c);
        spawned++;
        if (selected == null) selected = c;
        listener.sound("bell");
        if (station != STATION_ASSEMBLY && fx.greeter == 0) listener.alert("New customer: " + def.name);
    }

    private double difficultyPatience() {
        return Math.max(.62, 1 - .011 * (number - 1)) * difficulty;
    }

    private Content.CustomerDef chooseCustomer() {
        Content.CustomerDef d = chooseCustomer(true);
        if (d == null) d = chooseCustomer(false); // everyone has been in already: repeats are fine
        if (d == null) d = Content.CUSTOMERS.get(rng.nextInt(5));
        seenThisShift.add(d.id);
        return d;
    }

    /** @param fresh skip customers who already came in this shift, so the same face is not back straight away */
    private Content.CustomerDef chooseCustomer(boolean fresh) {
        List<Content.CustomerDef> pool = new ArrayList<>();
        for (Content.CustomerDef d : Content.CUSTOMERS) {
            if (d.level > save.level || isPresent(d)) continue;
            if (fresh && seenThisShift.contains(d.id)) continue;
            if ((number == 1 || tutorial) && d.arch != Content.Arch.EASY && spawned < 2) continue;
            if (d.arch == Content.Arch.CRITIC && special != Content.Special.CRITICS && rng.nextDouble() > .35) continue;
            pool.add(d);
        }
        if (special != null && special.archChance > 0 && rng.nextDouble() < special.archChance) {
            List<Content.CustomerDef> themed = new ArrayList<>();
            for (Content.CustomerDef d : pool) {
                boolean match = special == Content.Special.WEALTHY ? d.arch == Content.Arch.WEALTHY
                        : d.arch == Content.Arch.CRITIC || d.arch == Content.Arch.GRILLER || d.arch == Content.Arch.STICKLER;
                if (match) themed.add(d);
            }
            if (!themed.isEmpty()) pool = themed;
        }
        if (pool.isEmpty()) return null;
        return pool.get(rng.nextInt(pool.size()));
    }

    private boolean isPresent(Content.CustomerDef d) {
        for (Customer c : active) if (c.def == d) return true;
        return false;
    }

    private Food.Order makeOrder(Content.CustomerDef def) {
        SaveData.Regular reg = save.regulars.get(def.id);
        if (reg != null && reg.favorite != null && rng.nextDouble() < .6) {
            Food.Order fav = Food.Order.decode(reg.favorite);
            if (!fx.cups) fav.size = 1;
            return fav;
        }
        return OrderManager.generate(rng, def, number, save.level, fx, special);
    }

    private void tickGrill(double dt) {
        boolean anyone = false;
        for (Food.Patty p : grill) {
            if (p == null) continue;
            anyone = true;
            int before = p.stage();
            p.cook(dt, fx);
            int after = p.stage();
            if (after >= Food.OVER) metrics.overshootTime += dt;
            if (after != before) {
                if (after == Food.WELL || after == Food.MEDIUM || after == Food.LIGHT) listener.sound("tick");
                if (after == Food.OVER && !p.warned) {
                    p.warned = true;
                    listener.sound("warn");
                    if (fx.alarms > 0 || station == STATION_COOKING) listener.alert("Patty overcooking!");
                }
                if (after == Food.BURNED && !p.burnedEvent) {
                    p.burnedEvent = true;
                    listener.sound("burn");
                    listener.alert("A patty burned!");
                }
            }
            if (!p.readyEvent && p.down() >= .45 && p.flips == 0) {
                p.readyEvent = true;
                listener.sound("ding");
                if (fx.alarms > 0 && station != STATION_COOKING) listener.alert("Patty needs flipping");
            }
        }
        int cooking = cookingCount();
        if (cooking > metrics.peakCooking) metrics.peakCooking = cooking;
        if (anyone) checkGapTracking();
    }

    private void checkGapTracking() {
        if (station != STATION_COOKING && leftCookingAt < 0) leftCookingAt = time;
    }

    public int cookingCount() {
        int n = 0;
        for (Food.Patty p : grill) if (p != null) n++;
        for (Food.Basket b : baskets) if (b.state == Food.Basket.DOWN) n++;
        return n;
    }

    public int fryingCount() {
        int n = 0;
        for (Food.Basket b : baskets) if (b.state == Food.Basket.DOWN) n++;
        return n;
    }

    public int grillCount() {
        int n = 0;
        for (Food.Patty p : grill) if (p != null) n++;
        return n;
    }

    private void tickFryer(double dt) {
        for (Food.Basket b : baskets) {
            if (b.state != Food.Basket.DOWN) continue;
            int before = Food.fryStage(b.p);
            b.p += fx.fryRate / Food.SECONDS_FRY * dt;
            int after = Food.fryStage(b.p);
            if (after >= 3) metrics.overshootTime += dt;
            if (after != before) {
                if (after == 2 && !b.readyEvent) {
                    b.readyEvent = true;
                    listener.sound("ding");
                    if (fx.alarms > 0 && station != STATION_COOKING) listener.alert("Fries are golden");
                }
                if (after == 3 && !b.warned) {
                    b.warned = true;
                    listener.sound("warn");
                    if (fx.alarms > 0 || station == STATION_COOKING) listener.alert("Fries overcooking!");
                }
                if (after == 4 && !b.burnedEvent) {
                    b.burnedEvent = true;
                    listener.sound("burn");
                    listener.alert("Fries burned!");
                }
            }
        }
    }

    /** The idle layer: hired staff quietly do the jobs they were hired for. */
    private void tickStaff() {
        if (fx.greeter > 0) {
            for (Customer c : active) if (c.status == Customer.WAITING && c.waited > 3) takeOrder(c);
        }
        if (fx.runner > 0) {
            double delay = fx.runner >= 2 ? 3 : 8;
            for (Customer c : active) {
                if (c.status == Customer.ORDERED && c.order.drink != null && c.tray.drink == null
                        && time - c.takenAt > delay && hasTrayFor(c)) {
                    c.tray.drink = new Food.DrinkItem(c.order.drink, c.order.size);
                    drinksMade++;
                    listener.alert("Sam: " + Content.drink(c.order.drink).name + " for " + c.def.name);
                }
            }
        }
        if (fx.griller > 0) {
            for (int i = 0; i < grill.length; i++) {
                Food.Patty p = grill[i];
                if (p == null) continue;
                double t = Food.targetOf(p.target);
                if (p.down() >= t && p.up() < t - .01) { p.flip(); listener.sound("flip"); }
                else if (fx.griller >= 2 && p.a >= t && p.b >= t) {
                    int slot = freeCabinet();
                    if (slot >= 0) { cabinet[slot] = new Food.Held(p); grill[i] = null; listener.sound("drop"); }
                }
            }
        }
        if (fx.fryer > 0) {
            for (Food.Basket b : baskets) {
                if (b.state == Food.Basket.DOWN && b.p >= .575) { b.state = Food.Basket.UP; listener.sound("splash"); }
                if (fx.fryer >= 2 && b.state == Food.Basket.UP && b.p >= .5 && packed == null && freeCabinet() >= 0) {
                    packed = new Food.Fries(b.p);
                    resetBasket(b);
                    packedToCabinet(-1);
                }
            }
        }
    }

    private void recordIdle(double dt) {
        int needBurgers = 0, needFries = 0;
        boolean drinkOwed = false;
        for (Customer c : active) {
            if (c.status != Customer.ORDERED) continue;
            if (c.order.burger && c.tray.burger == null) needBurgers++;
            if (c.order.fries && c.tray.fries == null) needFries++;
            if (c.order.drink != null && c.tray.drink == null) drinkOwed = true;
        }
        int heldPatties = grillCount(), heldFries = fryingCount();
        for (Food.Held h : cabinet) {
            if (h == null) continue;
            if (h.patty != null) heldPatties++; else heldFries++;
        }
        for (Food.Burger b : boards) if (b.patty() != null) heldPatties++;
        for (Food.Basket b : baskets) if (b.state == Food.Basket.UP && b.p > 0) heldFries++;
        if (needBurgers > heldPatties) metrics.grillIdle += dt;
        if (needFries > heldFries) metrics.fryerIdle += dt;
        if (station == STATION_COOKING && drinkOwed && cookingCount() > 0 && !anythingUrgent()) metrics.missedDrinkTime += dt;
    }

    /** True if some food on the heat needs the player within a few seconds. */
    private boolean anythingUrgent() {
        for (Food.Patty p : grill) {
            if (p != null && p.down() >= Food.targetOf(p.target) - .15) return true;
        }
        for (Food.Basket b : baskets) {
            if (b.state == Food.Basket.DOWN && b.p >= .4) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- station tracking

    public void setStation(int s) {
        if (s == station) return;
        station = s;
        metrics.switches++;
        if (s != STATION_ASSEMBLY) fridgeOpen = false;
        if (s == STATION_COOKING && leftCookingAt >= 0) {
            metrics.checkGaps.add(time - leftCookingAt);
            leftCookingAt = -1;
        }
        if (s != STATION_COOKING && grillCount() + fryingCount() > 0) leftCookingAt = time;
    }

    // ---------------------------------------------------------------- orders

    public void takeOrder(Customer c) {
        if (c.status != Customer.WAITING) return;
        c.status = Customer.ORDERED;
        c.takenAt = time;
        metrics.takeOrderDelaySum += c.waited;
        metrics.ordersTaken++;
        listener.sound("ticket");
        if (selected == null || selected.status == Customer.WAITING) selected = c;
    }

    public void select(Customer c) { selected = c; }

    // ---------------------------------------------------------------- grill

    public boolean placePatty(int slot) {
        if (slot < 0 || slot >= grill.length || grill[slot] != null) return false;
        Food.Patty p = new Food.Patty();
        p.target = nextDonenessNeeded();
        grill[slot] = p;
        listener.sound("sizzle");
        return true;
    }

    public int freeGrill() {
        for (int i = 0; i < grill.length; i++) if (grill[i] == null) return i;
        return -1;
    }

    /** Picks the doneness a new patty should aim for: the selected ticket first, then the oldest open one. */
    private int nextDonenessNeeded() {
        if (selected != null && selected.status == Customer.ORDERED && selected.order.burger && selected.tray.burger == null) {
            return selected.order.doneness;
        }
        for (Customer c : active) {
            if (c.status == Customer.ORDERED && c.order.burger && c.tray.burger == null) return c.order.doneness;
        }
        return Food.MEDIUM;
    }

    public void flip(int slot) {
        if (grill[slot] == null) return;
        grill[slot].flip();
        listener.sound("flip");
    }

    public boolean pattyToCabinet(int slot, int cabSlot) {
        if (grill[slot] == null) return false;
        if (cabSlot < 0) cabSlot = freeCabinet();
        if (cabSlot < 0 || cabinet[cabSlot] != null) {
            metrics.cabinetFull++;
            listener.alert("Warming cabinet is full");
            return false;
        }
        cabinet[cabSlot] = new Food.Held(grill[slot]);
        grill[slot] = null;
        listener.sound("drop");
        return true;
    }

    public void trashGrill(int slot) {
        if (grill[slot] == null) return;
        grill[slot] = null;
        metrics.pattiesTrashed++;
        listener.sound("trash");
    }

    public boolean cabinetToGrill(int cabSlot, int slot) {
        Food.Held h = cabinet[cabSlot];
        if (h == null || h.patty == null || slot < 0 || grill[slot] != null) return false;
        grill[slot] = h.patty;
        cabinet[cabSlot] = null;
        listener.sound("sizzle");
        return true;
    }

    public void moveGrill(int from, int to) {
        if (grill[from] == null || grill[to] != null) return;
        grill[to] = grill[from];
        grill[from] = null;
        listener.sound("drop");
    }

    public int freeCabinet() {
        for (int i = 0; i < cabinet.length; i++) if (cabinet[i] == null) return i;
        return -1;
    }

    // ---------------------------------------------------------------- fryer

    public boolean fillBasket(int i) {
        if (i < 0 || i >= baskets.length || baskets[i].state != Food.Basket.EMPTY) return false;
        resetBasket(baskets[i]);
        baskets[i].state = Food.Basket.UP;
        listener.sound("drop");
        return true;
    }

    public int freeBasket() {
        for (int i = 0; i < baskets.length; i++) if (baskets[i].state == Food.Basket.EMPTY) return i;
        return -1;
    }

    public void toggleBasket(int i) {
        Food.Basket b = baskets[i];
        if (b.state == Food.Basket.UP) { b.state = Food.Basket.DOWN; listener.sound("splash"); }
        else if (b.state == Food.Basket.DOWN) { b.state = Food.Basket.UP; listener.sound("splash"); }
    }

    /** Pours a lifted basket into a box at the packing station. */
    public boolean boxFries(int i) {
        Food.Basket b = baskets[i];
        if (b.state == Food.Basket.EMPTY) return false;
        if (b.state == Food.Basket.DOWN) { listener.alert("Lift the basket first"); return false; }
        if (packed != null) { listener.alert("Move the boxed fries to the warmer first"); return false; }
        packed = new Food.Fries(b.p);
        resetBasket(b);
        listener.sound("drop");
        return true;
    }

    /** Moves the boxed fries from the packing station into the warmer (cabSlot -1 picks a free slot). */
    public boolean packedToCabinet(int cabSlot) {
        if (packed == null) return false;
        if (cabSlot < 0) cabSlot = freeCabinet();
        if (cabSlot < 0 || cabinet[cabSlot] != null) {
            metrics.cabinetFull++;
            listener.alert("The warmer is full");
            return false;
        }
        cabinet[cabSlot] = new Food.Held(packed);
        packed = null;
        listener.sound("drop");
        return true;
    }

    public void trashPacked() {
        if (packed == null) return;
        packed = null;
        metrics.friesTrashed++;
        listener.sound("trash");
    }

    public void trashBasket(int i) {
        if (baskets[i].state == Food.Basket.EMPTY) return;
        resetBasket(baskets[i]);
        metrics.friesTrashed++;
        listener.sound("trash");
    }

    private void resetBasket(Food.Basket b) {
        b.state = Food.Basket.EMPTY;
        b.p = 0;
        b.warned = b.burnedEvent = b.readyEvent = false;
    }

    // ---------------------------------------------------------------- assembly

    public double applySnap(double dx) {
        double clamped = Math.max(-40, Math.min(40, dx));
        return Math.abs(clamped) <= fx.snap ? 0 : clamped - Math.signum(clamped) * fx.snap;
    }

    /** Taps are less precise than drags; gloves steady them. */
    public double tapJitter() {
        double range = Math.max(2, 13 - fx.snap * .4);
        return (rng.nextDouble() * 2 - 1) * range;
    }

    public boolean addBun(int board, boolean top, double dx) {
        Food.Burger b = boards[board];
        if (b.finished()) { listener.alert("That burger is finished"); return false; }
        if (!top && !b.layers.isEmpty()) { listener.alert("Bottom bun goes first"); return false; }
        if (top && b.layers.isEmpty()) { listener.alert("Start with a bottom bun"); return false; }
        b.layers.add(new Food.Layer(top ? "bunT" : "bunB", applySnap(dx), null));
        listener.sound("drop");
        return true;
    }

    /** Sauce units leaving a tipped bottle per second. A steady two-second sweep covers a burger well. */
    public static final double POUR_RATE = 5.5;

    /**
     * Pours from a held bottle onto a board for dt seconds. rel is the nozzle's position across the
     * burger, -1 at the left edge to 1 at the right. Pouring onto a top layer of the same sauce tops it
     * up; anything else starts a new sauce layer. Returns false when nothing lands on the burger.
     */
    public boolean pour(int board, String id, double rel, double dt) {
        Food.Burger b = boards[board];
        if (b.finished()) { listener.alert("That burger is finished"); return false; }
        if (b.layers.isEmpty()) { listener.alert("Start with a bottom bun"); return false; }
        if (Math.abs(rel) > 1.12) return false; // missed the burger
        Food.Layer top = b.layers.get(b.layers.size() - 1);
        if (top.pour == null || !top.id.equals(id)) {
            top = new Food.Layer(id, new double[Food.SAUCE_BINS]);
            b.layers.add(top);
            listener.sound("squirt");
        }
        double pos = (Math.max(-1, Math.min(1, rel)) + 1) / 2 * (Food.SAUCE_BINS - 1);
        double[] w = new double[Food.SAUCE_BINS];
        double norm = 0;
        for (int i = 0; i < w.length; i++) { w[i] = Math.exp(-(i - pos) * (i - pos) / 1.28); norm += w[i]; }
        for (int i = 0; i < w.length; i++) top.pour[i] += POUR_RATE * dt * w[i] / norm;
        return true;
    }

    public boolean addIngredient(int board, String id, double dx) {
        Food.Burger b = boards[board];
        if (b.finished()) { listener.alert("That burger is finished"); return false; }
        if (b.layers.isEmpty()) { listener.alert("Start with a bottom bun"); return false; }
        b.layers.add(new Food.Layer(id, applySnap(dx), null));
        Content.Ingredient ing = Content.ingredient(id);
        listener.sound(ing != null && ing.sauce ? "squirt" : "drop");
        return true;
    }

    public boolean addPatty(int board, int cabSlot, double dx) {
        Food.Burger b = boards[board];
        Food.Held h = cabinet[cabSlot];
        if (h == null || h.patty == null) return false;
        if (b.finished()) { listener.alert("That burger is finished"); return false; }
        if (b.layers.isEmpty()) { listener.alert("Start with a bottom bun"); return false; }
        b.layers.add(new Food.Layer("patty", applySnap(dx), h.patty));
        cabinet[cabSlot] = null;
        listener.sound("drop");
        return true;
    }

    public void undoLayer(int board) {
        Food.Burger b = boards[board];
        if (b.layers.isEmpty()) return;
        Food.Layer l = b.layers.remove(b.layers.size() - 1);
        metrics.undos++;
        if (l.patty != null) {
            int slot = freeCabinet();
            if (slot >= 0) cabinet[slot] = new Food.Held(l.patty);
            else metrics.pattiesTrashed++;
        }
        listener.sound("trash");
    }

    public int focusBoard() {
        for (int i = 0; i < boards.length; i++) if (!boards[i].finished()) return i;
        return 0;
    }

    public void trashBoard(int board) {
        if (boards[board].patty() != null) metrics.pattiesTrashed++;
        boards[board] = new Food.Burger();
        listener.sound("trash");
    }

    public void trashHeld(int slot) {
        Food.Held h = cabinet[slot];
        if (h == null) return;
        if (h.patty != null) metrics.pattiesTrashed++; else metrics.friesTrashed++;
        cabinet[slot] = null;
        listener.sound("trash");
    }

    // ---------------------------------------------------------------- trays

    public boolean hasTrayFor(Customer c) {
        if (!c.tray.empty()) return true;
        int used = 0;
        for (Customer o : active) if (!o.tray.empty()) used++;
        return used < fx.trays;
    }

    private boolean checkTray(Customer c) {
        if (c == null) { listener.alert("Select a ticket first"); return false; }
        if (c.status != Customer.ORDERED) { listener.alert("Take this order first"); return false; }
        if (!hasTrayFor(c)) {
            metrics.traysFull++;
            listener.alert("All trays in use: serve an order first");
            return false;
        }
        return true;
    }

    public boolean burgerToTray(int board, Customer c) {
        Food.Burger b = boards[board];
        if (!b.finished()) { listener.alert("Finish it with a top bun"); return false; }
        if (!checkTray(c)) return false;
        if (c.tray.burger != null) { listener.alert("This tray already has a burger"); return false; }
        c.tray.burger = b;
        boards[board] = new Food.Burger();
        burgersMade++;
        listener.sound("tray");
        return true;
    }

    public boolean friesToTray(int cabSlot, Customer c) {
        Food.Held h = cabinet[cabSlot];
        if (h == null || h.fries == null) return false;
        if (!checkTray(c)) return false;
        if (c.tray.fries != null) { listener.alert("This tray already has fries"); return false; }
        c.tray.fries = h.fries;
        cabinet[cabSlot] = null;
        friesMade++;
        listener.sound("tray");
        return true;
    }

    public boolean drinkToTray(String id, int size, Customer c) {
        if (!checkTray(c)) return false;
        if (c.tray.drink != null) { listener.alert("This tray already has a drink"); return false; }
        c.tray.drink = new Food.DrinkItem(id, size);
        drinksMade++;
        listener.sound("can");
        return true;
    }

    /** slot: 0 burger, 1 fries, 2 drink */
    public void trashTrayItem(Customer c, int slot) {
        if (slot == 0 && c.tray.burger != null) {
            if (c.tray.burger.patty() != null) metrics.pattiesTrashed++;
            c.tray.burger = null;
            burgersMade--;
        } else if (slot == 1 && c.tray.fries != null) {
            c.tray.fries = null;
            metrics.friesTrashed++;
            friesMade--;
        } else if (slot == 2 && c.tray.drink != null) {
            c.tray.drink = null;
            metrics.drinksTrashed++;
            drinksMade--;
        }
        listener.sound("trash");
    }

    public boolean moveTrayItem(Customer from, int slot, Customer to) {
        if (from == to) return false;
        if (!checkTray(to)) return false;
        if (slot == 0 && from.tray.burger != null && to.tray.burger == null) {
            to.tray.burger = from.tray.burger; from.tray.burger = null;
        } else if (slot == 1 && from.tray.fries != null && to.tray.fries == null) {
            to.tray.fries = from.tray.fries; from.tray.fries = null;
        } else if (slot == 2 && from.tray.drink != null && to.tray.drink == null) {
            to.tray.drink = from.tray.drink; from.tray.drink = null;
        } else {
            return false;
        }
        listener.sound("tray");
        return true;
    }

    // ---------------------------------------------------------------- serving

    public Scoring.Review serve(Customer c) {
        if (c == null || c.status != Customer.ORDERED || c.tray.empty()) return null;
        Scoring.Review r = Scoring.score(c, fx, metrics, special, streak);
        if (!c.complete()) metrics.incompleteServes++;
        metrics.waits.add(c.waited);
        metrics.waitRatios.add(c.waited / c.patience);
        metrics.prepTimes.add(time - c.takenAt);
        reviews.add(r);
        moneyEarned += r.pay;
        tipsEarned += r.tip;
        served++;
        if (r.satisfaction >= 80) { streak++; bestStreak = Math.max(bestStreak, streak); }
        else streak = 0;
        rememberRegular(c, r);
        active.remove(c);
        if (selected == c) {
            selected = null;
            for (Customer o : active) {
                if (selected == null || (o.status == Customer.ORDERED && selected.status != Customer.ORDERED)) selected = o;
            }
        }
        // finish before notifying, so the listener sees the shift is over when the last customer leaves
        if (served >= scheduled) finished = true;
        listener.sound("serve");
        listener.served(c, r);
        return r;
    }

    private void rememberRegular(Customer c, Scoring.Review r) {
        SaveData.Regular reg = save.regulars.get(c.def.id);
        if (reg == null) { reg = new SaveData.Regular(); save.regulars.put(c.def.id, reg); }
        reg.visits++;
        if (reg.favorite == null) reg.favorite = c.order.encode();
        if (r.satisfaction >= 70 && reg.visits % 2 == 0) reg.loyalty = Math.min(5, reg.loyalty + 1);
    }
}

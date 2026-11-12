package com.c01m.burgerrun.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.c01m.burgerrun.engine.Art;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.sim.Content;
import com.c01m.burgerrun.sim.Customer;
import com.c01m.burgerrun.sim.Food;
import com.c01m.burgerrun.sim.Progression;
import com.c01m.burgerrun.sim.Scoring;
import com.c01m.burgerrun.sim.ShiftManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The restaurant. A fixed order rail and warming cabinet frame a panning view of the assembly and
 * cooking stations. Dragging the background right reveals the kitchen; dragging left returns.
 */
public final class GameScreen extends Screen implements ShiftManager.Listener {
    private static final float PX = 110, PY = 128, PW = 1170, PH = 592;
    private static final float RAIL_X = 115, TICKET_W = 188, TICKET_H = 122, TICKET_STEP = 194;
    private static final float CAB_Y = 158, CAB_STEP = 78, CAB_H = 72, TRASH_Y = 628;
    private static final float BOARD_BASE = 540, BOARD_W = 135, FRIDGE_OPEN_TOP = 112;
    private static final float GHOST_LIFT = 26;

    private enum Kind { NONE, PAN, FRIDGE, BUN_B, BUN_T, ING, BOARD, CAB, GRILL, RAW_PATTY, RAW_FRIES, BASKET, PACKED, SAUCE, DRINK, TRAY }

    private static final class Alert { String text; float t = 2.6f; }
    private static final class Pop { Customer c; Scoring.Review r; float t = 2.8f; }
    private static final class Floater { String text; float x, y, t = 1.3f; int color; }

    private final ShiftManager s;
    private final List<Alert> alerts = new ArrayList<>();
    private final List<Pop> pops = new ArrayList<>();
    private final List<Floater> floaters = new ArrayList<>();
    private final List<Customer> rail = new ArrayList<>();

    private float panX, panTarget, panStart;
    private float fridgeT, fridgeTarget, fridgeStart;
    private Kind drag = Kind.NONE;
    private boolean dragMoved;
    private float dragX, dragY, startX, startY;
    private int dragIdx;
    private String dragId;
    private Customer dragFrom;
    private int cupSize = 1;
    private boolean paused;
    private float endTimer = -1, time;
    private final Btn resumeBtn, restartBtn, optionsBtn, quitBtn, pauseBtn;

    public GameScreen(BurgerRunGame g) {
        super(g);
        s = new ShiftManager(g.save, new Random(), g.settings.difficultyMult(), this);
        s.tutorial = g.settings.hints && !g.save.tutorialDone;
        pauseBtn = add(new Btn(8, 92, 94, 32, "PAUSE", 0xFF8A6A50, () -> setPaused(true)));
        pauseBtn.textSize = 16;
        resumeBtn = add(new Btn(440, 190, 400, 80, "RESUME", G.GREEN, () -> setPaused(false)));
        optionsBtn = add(new Btn(440, 284, 400, 80, "OPTIONS", G.TEAL, () -> g.show(new OptionsScreen(g, this))));
        restartBtn = add(new Btn(440, 378, 400, 80, "RESTART SHIFT", G.MUSTARD, () -> g.confirm(
                "Restart this shift? Nothing earned so far this shift will count.", "RESTART", "CANCEL", () -> g.show(new GameScreen(g)))));
        quitBtn = add(new Btn(440, 472, 400, 80, "QUIT TO MENU", G.KETCHUP, () -> g.confirm(
                "Quit to the menu? Your save is safe, but this shift's progress will be lost.", "QUIT", "CANCEL", () -> g.show(new MenuScreen(g)))));
        setPaused(false);
    }

    private void setPaused(boolean on) {
        paused = on;
        resumeBtn.visible = optionsBtn.visible = restartBtn.visible = quitBtn.visible = on;
        pauseBtn.visible = !on;
        if (on) { cancelDrag(); game.audio.setAmbience(0, 0); }
    }

    // ---------------------------------------------------------------- simulation hooks

    @Override public void sound(String id) { game.audio.play(id); }

    @Override public void alert(String text) {
        for (Alert a : alerts) if (a.text.equals(text)) { a.t = 2.6f; return; }
        Alert a = new Alert();
        a.text = text;
        alerts.add(a);
        if (alerts.size() > 3) alerts.remove(0);
    }

    @Override public void served(Customer c, Scoring.Review r) {
        Pop p = new Pop();
        p.c = c;
        p.r = r;
        pops.add(p);
        game.audio.play("coin");
        if (s.finished) endTimer = 2.4f;
        if (s.tutorial && !game.save.tutorialDone) {
            game.save.tutorialDone = true;
            game.persist();
            if (!s.finished) game.notice("Tutorial complete! From now on customers arrive on their own and their patience runs. "
                    + "Tip: put patties on the grill first, since they take the longest.");
        }
    }

    private void floater(String text, float x, float y, int color) {
        Floater f = new Floater();
        f.text = text; f.x = x; f.y = y; f.color = color;
        floaters.add(f);
    }

    // ---------------------------------------------------------------- update

    @Override public void update(float dt) {
        if (paused) return;
        time += dt;
        if (s.finished && endTimer < 0) endTimer = 2.4f;
        if (endTimer >= 0) {
            endTimer -= dt;
            if (endTimer < 0) {
                game.audio.setAmbience(0, 0);
                Progression.Result r = Progression.finish(s);
                game.persist();
                game.show(new ResultsScreen(game, r));
                return;
            }
        }
        s.tick(dt);
        game.audio.setAmbience(Math.min(1, s.grillCount() / 3f), Math.min(1, s.fryingCount() / 2f));

        if (drag != Kind.PAN) {
            float speed = PW / (s.fx.swingMs / 1000f);
            if (panX < panTarget) panX = Math.min(panTarget, panX + speed * dt);
            else if (panX > panTarget) panX = Math.max(panTarget, panX - speed * dt);
        }
        if (drag != Kind.FRIDGE) {
            float step = dt * 3.4f;
            if (fridgeT < fridgeTarget) fridgeT = Math.min(fridgeTarget, fridgeT + step);
            else if (fridgeT > fridgeTarget) fridgeT = Math.max(fridgeTarget, fridgeT - step);
        }
        for (Food.Burger b : s.boards) {
            for (Food.Layer l : b.layers) if (l.dropAnim > 0) l.dropAnim = Math.max(0, l.dropAnim - dt * 6);
        }
        updatePour(dt);
        for (Iterator<Alert> it = alerts.iterator(); it.hasNext(); ) { Alert a = it.next(); a.t -= dt; if (a.t <= 0) it.remove(); }
        for (Iterator<Pop> it = pops.iterator(); it.hasNext(); ) { Pop p = it.next(); p.t -= dt; if (p.t <= 0) it.remove(); }
        for (Iterator<Floater> it = floaters.iterator(); it.hasNext(); ) { Floater f = it.next(); f.t -= dt; f.y -= dt * 40; if (f.t <= 0) it.remove(); }

        rail.clear();
        rail.addAll(s.active);
        if (s.fx.ticketLv >= 1) rail.sort((a, b) -> Double.compare(a.patienceLeft(), b.patienceLeft()));
    }

    // ---------------------------------------------------------------- pouring sauce

    private static final float NOZZLE = 8; // the upturned bottle's nozzle sits this far below the ghost point
    private int pourBoard = -1;
    private float pourX, pourY, pourTop;

    /** Where the next layer would sit on a board, in screen coordinates. */
    private float stackTop(int i) {
        float y = PY + BOARD_BASE + 2;
        for (Food.Layer l : s.boards[i].layers) y -= Art.layerStep(l.id);
        return y;
    }

    /** While a bottle is held nozzle-down over a burger, sauce lands wherever the nozzle is. */
    private void updatePour(float dt) {
        pourBoard = -1;
        if (drag != Kind.SAUCE || !dragMoved || !atAssembly() || s.fridgeOpen) return;
        float nx = dragX, ny = dragY - GHOST_LIFT + NOZZLE;
        for (int i = 0; i < s.boards.length; i++) {
            float cx = ax() + boardCx(i), top = stackTop(i);
            if (Math.abs(nx - cx) > BOARD_W / 2 + 6 || ny > top + 4 || ny < PY + 150) continue;
            if (s.pour(i, dragId, (nx - cx) / (Art.LAYER_W / 2), dt)) {
                pourBoard = i;
                pourX = nx;
                pourY = ny;
                pourTop = stackTop(i) + 1; // the surface of the puddle being poured
            }
            return;
        }
    }

    private boolean settled() { return panX == panTarget; }
    private boolean atAssembly() { return settled() && panTarget == 0; }
    private boolean atCooking() { return settled() && panTarget == PW; }

    private void goTo(int station) {
        if (station == s.station) return;
        s.setStation(station);
        panTarget = station == ShiftManager.STATION_COOKING ? PW : 0;
        if (station != ShiftManager.STATION_ASSEMBLY) setFridge(false);
        game.audio.play("whoosh");
    }

    private void setFridge(boolean open) {
        if (open != s.fridgeOpen) game.audio.play("fridge");
        s.fridgeOpen = open;
        fridgeTarget = open ? 1 : 0;
    }

    @Override public void key(int key) {
        if (key == Input.Keys.RIGHT) goTo(ShiftManager.STATION_COOKING);
        if (key == Input.Keys.LEFT) goTo(ShiftManager.STATION_ASSEMBLY);
    }

    @Override public void back() { setPaused(!paused); }
    @Override public void onAppPause() { setPaused(true); }

    // ---------------------------------------------------------------- geometry

    private static boolean in(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
    private float ax() { return PX + panX; }          // assembly station origin on screen
    private float kx() { return PX + panX - PW; }     // cooking station origin on screen

    private float boardCx(int i) {
        float total = s.boards.length * BOARD_W;
        return 385 + (405 - total) / 2 + BOARD_W * (i + .5f);
    }
    private float vegX(int i) { return 10 + (i % 4) * 90; }
    private float vegY(int i) { return 270 + (i / 4) * 80; }
    private float sauceX(int i) { return 10 + i * 72; }
    private float slotX(int i) { return 175 + (i % 3) * 142; }
    private float slotY(int i) { return 52 + (i / 3) * 170; }
    private float basketX(int i) { return 640 + i * 168; }
    private float fridgeTop() { return PH + (FRIDGE_OPEN_TOP - PH) * fridgeT; }

    private Customer ticketAt(float x, float y) {
        if (y > TICKET_H + 4) return null;
        int i = (int) ((x - RAIL_X) / TICKET_STEP);
        return x >= RAIL_X && i < rail.size() ? rail.get(i) : null;
    }
    private int cabinetAt(float x, float y) {
        if (x > 106 || y < CAB_Y) return -1;
        int i = (int) ((y - CAB_Y) / CAB_STEP);
        return i < s.cabinet.length ? i : -1;
    }
    private boolean inTrash(float x, float y) { return in(x, y, 0, TRASH_Y, 108, 92); }

    // ---------------------------------------------------------------- input

    private void begin(Kind k, int idx, String id, float x, float y) {
        drag = k; dragIdx = idx; dragId = id; startX = dragX = x; startY = dragY = y; dragMoved = false;
    }

    private void cancelDrag() {
        if (drag == Kind.PAN) panX = panTarget;
        drag = Kind.NONE;
        dragFrom = null;
    }

    @Override public void down(float x, float y) {
        super.down(x, y);
        if (anyButtonPressed() || paused) return;
        Customer t = ticketAt(x, y);
        if (t != null) { tapTicket(t); return; }
        int cab = cabinetAt(x, y);
        if (cab >= 0) { if (s.cabinet[cab] != null) begin(Kind.CAB, cab, null, x, y); return; }
        if (x < PX || y < PY) return;
        if (!settled()) return;
        float lx = x - ax(), ly = y - PY;
        if (atAssembly()) {
            float top = fridgeTop();
            if (in(lx, ly, 330, top - 40, 120, 42)) { begin(Kind.FRIDGE, 0, null, x, y); fridgeStart = fridgeT; return; }
            if (s.fridgeOpen && in(lx, ly, 10, top, 780, PH - top)) { downFridge(lx, ly, x, y); return; }
            if (downAssembly(lx, ly, x, y)) return;
        } else if (atCooking()) {
            if (downCooking(x - kx(), ly, x, y)) return;
        }
        begin(Kind.PAN, 0, null, x, y);
        panStart = panX;
    }

    private void tapTicket(Customer t) {
        if (t.status == Customer.WAITING) s.takeOrder(t);
        s.select(t);
        game.audio.play("tap");
    }

    private void downFridge(float lx, float ly, float x, float y) {
        float top = fridgeTop();
        List<Content.Drink> list = visibleDrinks();
        for (int i = 0; i < list.size(); i++) {
            float dx = 30 + (i % 4) * 190, dy = top + 30 + (i / 4) * 170;
            if (in(lx, ly, dx, dy, 170, 150)) {
                Content.Drink d = list.get(i);
                if (d.level > game.save.level) game.audio.play("wrong");
                else begin(Kind.DRINK, 0, d.id, x, y);
                return;
            }
        }
        if (s.fx.cups) {
            for (int i = 0; i < 3; i++) {
                if (in(lx, ly, 250 + i * 100, top + 400, 90, 56)) { cupSize = i; game.audio.play("tap"); return; }
            }
        }
    }

    private List<Content.Drink> visibleDrinks() {
        List<Content.Drink> out = new ArrayList<>();
        for (Content.Drink d : Content.DRINKS) if (d.fridge <= s.fx.premium) out.add(d);
        return out;
    }

    private boolean downAssembly(float lx, float ly, float x, float y) {
        if (in(lx, ly, 10, 205, 176, 60)) { begin(Kind.BUN_B, 0, "bunB", x, y); return true; }
        if (in(lx, ly, 190, 205, 176, 60)) { begin(Kind.BUN_T, 0, "bunT", x, y); return true; }
        for (int i = 0; i < Content.VEG.size(); i++) {
            if (in(lx, ly, vegX(i), vegY(i), 84, 76)) {
                Content.Ingredient ing = Content.VEG.get(i);
                if (ing.level > game.save.level) { game.audio.play("wrong"); return true; }
                begin(Kind.ING, 0, ing.id, x, y);
                return true;
            }
        }
        for (int i = 0; i < Content.SAUCE.size(); i++) {
            if (in(lx, ly, sauceX(i), 436, 68, 96)) {
                Content.Ingredient ing = Content.SAUCE.get(i);
                if (ing.level > game.save.level) { game.audio.play("wrong"); return true; }
                begin(Kind.SAUCE, 0, ing.id, x, y);
                return true;
            }
        }
        for (int i = 0; i < s.boards.length; i++) {
            float cx = boardCx(i);
            if (!s.boards[i].layers.isEmpty() && in(lx, ly, cx - 44, 208, 88, 34)) { s.undoLayer(i); return true; }
            if (in(lx, ly, cx - BOARD_W / 2, 244, BOARD_W, 310) && !s.boards[i].layers.isEmpty()) {
                begin(Kind.BOARD, i, null, x, y);
                return true;
            }
        }
        // tray panel
        Customer c = s.selected;
        if (in(lx, ly, 820, 500, 230, 80)) { serve(); return true; }
        if (c != null && in(lx, ly, 1060, 500, 100, 80)) {
            for (int i = 0; i < 3; i++) s.trashTrayItem(c, i);
            return true;
        }
        if (c != null) {
            for (int i = 0; i < 3; i++) {
                if (traySlotFilled(c, i) && in(lx, ly, traySlotX(i), 268, 112, 190)) {
                    dragFrom = c;
                    begin(Kind.TRAY, i, null, x, y);
                    return true;
                }
            }
        }
        if (in(lx, ly, 0, 0, 790, 190)) {
            for (Customer o : s.active) {
                if (in(lx, ly, 8 + o.spot * 130, 0, 125, 190)) { tapTicket(o); return true; }
            }
        }
        if (in(lx, ly, 196, 536, 128, 50)) { goTo(ShiftManager.STATION_COOKING); return true; }
        return lx >= 800; // the tray panel never starts a pan
    }

    private boolean downCooking(float lx, float ly, float x, float y) {
        if (in(lx, ly, 20, 190, 130, 150)) { begin(Kind.RAW_PATTY, 0, null, x, y); return true; }
        for (int i = 0; i < s.grill.length; i++) {
            if (s.grill[i] != null && in(lx, ly, slotX(i), slotY(i), 136, 164)) { begin(Kind.GRILL, i, null, x, y); return true; }
        }
        if (in(lx, ly, 640, 445, 130, 120)) { begin(Kind.RAW_FRIES, 0, null, x, y); return true; }
        if (s.packed != null && in(lx, ly, 790, 430, 130, 135)) { begin(Kind.PACKED, 0, null, x, y); return true; }
        for (int i = 0; i < s.baskets.length; i++) {
            if (s.baskets[i].state != Food.Basket.EMPTY && in(lx, ly, basketX(i), 150, 155, 275)) { begin(Kind.BASKET, i, null, x, y); return true; }
        }
        if (in(lx, ly, 950, 470, 200, 80)) { goTo(ShiftManager.STATION_ASSEMBLY); return true; }
        return false;
    }

    @Override public void move(float x, float y) {
        if (drag == Kind.NONE) return;
        dragX = x; dragY = y;
        float dx = x - startX, dy = y - startY;
        if (!dragMoved && dx * dx + dy * dy > 12 * 12) dragMoved = true;
        if (drag == Kind.PAN && dragMoved) {
            // only horizontal movement pans; vertical swipes do nothing
            panX = Math.max(-40, Math.min(PW + 40, panStart + dx * 1.15f));
        } else if (drag == Kind.FRIDGE) {
            float travel = PH - FRIDGE_OPEN_TOP;
            fridgeT = Math.max(0, Math.min(1, fridgeStart - dy / travel));
        }
    }

    @Override public void up(float x, float y) {
        super.up(x, y);
        Kind k = drag;
        boolean moved = dragMoved;
        drag = Kind.NONE;
        if (k == Kind.NONE) return;
        if (k == Kind.PAN) { endPan(x); return; }
        if (k == Kind.FRIDGE) {
            if (!moved) setFridge(!s.fridgeOpen);
            else { float dy = y - startY; setFridge(Math.abs(dy) > 60 ? dy < 0 : s.fridgeOpen); }
            return;
        }
        if (moved) drop(k, x, y - GHOST_LIFT);
        else tap(k);
        dragFrom = null;
    }

    private void endPan(float x) {
        float moved = x - startX;
        if (panTarget == 0 && moved > 110) goTo(ShiftManager.STATION_COOKING);
        else if (panTarget == PW && moved < -110) goTo(ShiftManager.STATION_ASSEMBLY);
    }

    private void tap(Kind k) {
        int board = s.focusBoard();
        switch (k) {
            case BUN_B: s.addBun(board, false, s.tapJitter()); break;
            case BUN_T: s.addBun(board, true, s.tapJitter()); break;
            case ING: s.addIngredient(board, dragId, s.tapJitter()); break;
            case SAUCE: alert("Hold the bottle over the burger and sweep to pour"); break;
            case BOARD: s.burgerToTray(dragIdx, s.selected); break;
            case CAB: {
                Food.Held h = s.cabinet[dragIdx];
                if (h == null) break;
                if (h.fries != null) s.friesToTray(dragIdx, s.selected);
                else if (atAssembly()) s.addPatty(board, dragIdx, s.tapJitter());
                else if (atCooking() && s.freeGrill() >= 0) s.cabinetToGrill(dragIdx, s.freeGrill());
                break;
            }
            case GRILL: s.flip(dragIdx); break;
            case RAW_PATTY:
                if (s.freeGrill() >= 0) s.placePatty(s.freeGrill());
                else alert("The grill is full");
                break;
            case RAW_FRIES:
                if (s.freeBasket() >= 0) s.fillBasket(s.freeBasket());
                else alert("All baskets are in use");
                break;
            case BASKET: s.toggleBasket(dragIdx); break;
            case PACKED: s.packedToCabinet(-1); break;
            case DRINK: s.drinkToTray(dragId, s.fx.cups ? cupSize : 1, s.selected); break;
            default:
        }
    }

    private void drop(Kind k, float x, float y) {
        if (inTrash(x, y)) {
            switch (k) {
                case CAB: s.trashHeld(dragIdx); return;
                case GRILL: s.trashGrill(dragIdx); return;
                case BASKET: s.trashBasket(dragIdx); return;
                case PACKED: s.trashPacked(); return;
                case BOARD: s.trashBoard(dragIdx); return;
                case TRAY: if (dragFrom != null) s.trashTrayItem(dragFrom, dragIdx); return;
                default: return;
            }
        }
        int cab = cabinetAt(x, y);
        if (cab >= 0) {
            int slot = s.cabinet[cab] == null ? cab : -1;
            if (k == Kind.GRILL) s.pattyToCabinet(dragIdx, slot);
            else if (k == Kind.PACKED) s.packedToCabinet(slot);
            else if (k == Kind.BASKET) alert("Box the fries first: drag the basket onto Boxes");
            return;
        }
        Customer ticket = ticketAt(x, y);
        boolean onTray = atAssembly() && in(x - ax(), y - PY, 800, 0, 370, PH);
        if (ticket != null || onTray) {
            Customer c = ticket != null ? ticket : s.selected;
            switch (k) {
                case BOARD: s.burgerToTray(dragIdx, c); break;
                case DRINK: s.drinkToTray(dragId, s.fx.cups ? cupSize : 1, c); break;
                case CAB:
                    if (s.cabinet[dragIdx] != null && s.cabinet[dragIdx].fries != null) s.friesToTray(dragIdx, c);
                    else alert("Patties go on a burger first");
                    break;
                case TRAY:
                    if (dragFrom != null && c != null) s.moveTrayItem(dragFrom, dragIdx, c);
                    break;
                default:
            }
            return;
        }
        if (atAssembly()) {
            float lx = x - ax(), ly = y - PY;
            for (int i = 0; i < s.boards.length; i++) {
                float cx = boardCx(i);
                if (in(lx, ly, cx - BOARD_W / 2, 200, BOARD_W, 360)) {
                    double dx = lx - cx;
                    if (k == Kind.BUN_B) s.addBun(i, false, dx);
                    else if (k == Kind.BUN_T) s.addBun(i, true, dx);
                    else if (k == Kind.ING) s.addIngredient(i, dragId, dx);
                    else if (k == Kind.CAB && s.cabinet[dragIdx] != null) {
                        if (s.cabinet[dragIdx].patty != null) s.addPatty(i, dragIdx, dx);
                        else alert("Fries go on a tray");
                    }
                    return;
                }
            }
        }
        if (atCooking()) {
            float lx = x - kx(), ly = y - PY;
            for (int i = 0; i < s.grill.length; i++) {
                if (in(lx, ly, slotX(i), slotY(i), 136, 164)) {
                    if (k == Kind.RAW_PATTY) s.placePatty(i);
                    else if (k == Kind.GRILL && i != dragIdx) s.moveGrill(dragIdx, i);
                    else if (k == Kind.CAB) s.cabinetToGrill(dragIdx, i);
                    return;
                }
            }
            for (int i = 0; i < s.baskets.length; i++) {
                if (k == Kind.RAW_FRIES && in(lx, ly, basketX(i), 150, 155, 275)) { s.fillBasket(i); return; }
            }
            if (k == Kind.BASKET && in(lx, ly, 790, 430, 130, 135)) { s.boxFries(dragIdx); return; }
        }
    }

    private void serve() {
        Customer c = s.selected;
        if (c == null) { alert("Select a ticket first"); return; }
        if (c.status != Customer.ORDERED) { alert("Take this order first"); return; }
        if (c.tray.empty()) { alert("The tray is empty"); return; }
        if (c.complete()) { s.serve(c); return; }
        game.confirm("This order is incomplete! Serve it anyway? Missing items earn nothing, and " + c.def.name + " will be unhappy.",
                "SERVE ANYWAY", "KEEP WORKING", () -> s.serve(c));
    }

    private boolean traySlotFilled(Customer c, int i) {
        return i == 0 ? c.tray.burger != null : i == 1 ? c.tray.fries != null : c.tray.drink != null;
    }
    private float traySlotX(int i) { return 816 + i * 116; }

    // ---------------------------------------------------------------- drawing

    @Override public void draw() {
        G.rect(G.left, 0, G.right - G.left, G.HEIGHT, 0xFF2B1B10);
        if (ax() < G.right) drawAssembly(ax());
        if (kx() + PW > G.left) drawCooking(kx());
        drawRail();
        drawCabinet();
        Guide guide = s.tutorialHold() && !paused ? guide() : null;
        float feedbackY = PY + 8;
        if (guide != null) feedbackY = drawGuide(guide);
        else if (drawHint()) feedbackY = PY + 46;
        drawFeedback(feedbackY);
        drawGhost();
        if (paused) {
            G.rect(G.left, 0, G.right - G.left, G.HEIGHT, 0xAA000000);
            G.title("PAUSED", 640, 130, 64, G.MUSTARD, G.DARK, 0);
        }
        drawButtons();
    }

    private void drawAssembly(float ox) {
        G.sprite(Art.prepWall(), ox, PY + 190, 800, 402);
        G.sprite(Art.dining(s.fx.makeover, s.fx.plants, s.fx.neon), ox, PY, 800, 200);
        for (Customer c : s.active) drawCustomer(c, ox);
        G.sprite(Art.counter(), ox, PY + 150, 800, 54);
        for (Pop p : pops) drawPop(p, ox);

        // buns
        drawBunBin(ox + 10, "bunB", "Bottom Bun", drag == Kind.BUN_B);
        drawBunBin(ox + 190, "bunT", "Top Bun", drag == Kind.BUN_T);
        String next = nextIngredient();
        for (int i = 0; i < Content.VEG.size(); i++) {
            Content.Ingredient ing = Content.VEG.get(i);
            float x = ox + vegX(i), y = PY + vegY(i);
            boolean locked = ing.level > game.save.level;
            G.sprite(Art.veggieBin(ing.id), x, y, 84, 62, locked ? .3f : 1);
            G.text(locked ? "Lv " + ing.level : ing.name, x + 42, y + 69, 13, locked ? 0xFF6E655A : G.INK, 0, true);
            if (ing.id.equals(next)) G.rrectLine(x - 2, y - 2, 88, 80, 12, G.GREEN);
        }
        for (int i = 0; i < Content.SAUCE.size(); i++) {
            Content.Ingredient ing = Content.SAUCE.get(i);
            float x = ox + sauceX(i), y = PY + 436;
            boolean locked = ing.level > game.save.level;
            G.sprite(Art.bottle(ing.id), x + 14, y, 40, 74, locked ? .3f : 1);
            G.text(locked ? "Lv " + ing.level : ing.name, x + 34, y + 84, 12, locked ? 0xFF6E655A : G.INK, 0, true);
            if (ing.id.equals(next)) G.rrectLine(x, y - 2, 68, 96, 12, G.GREEN);
        }
        G.panel(ox + 10, PY + 536, 180, 50, 12, 0xFFFFE2A8, G.DARK);
        G.text("Staff: " + staffLine(), ox + 100, PY + 561, 15, G.BROWN, 0, true);
        // narrow enough to leave room for the fridge's DRINKS tab beside it
        G.panel(ox + 196, PY + 536, 128, 50, 12, G.TEAL, G.DARK);
        G.text("◄ KITCHEN", ox + 260, PY + 561, 17, G.WHITE, 0, true);

        for (int i = 0; i < s.boards.length; i++) drawBoard(i, ox + boardCx(i));
        drawFridge(ox);
        drawTrayPanel(ox);
    }

    private String staffLine() {
        int n = (s.fx.greeter > 0 ? 1 : 0) + (s.fx.runner > 0 ? 1 : 0) + (s.fx.griller > 0 ? 1 : 0) + (s.fx.fryer > 0 ? 1 : 0);
        return n == 0 ? "just you" : n + " helping";
    }

    /** The next ingredient for the selected ticket, shown only with the top Ticket Organiser. */
    private String nextIngredient() {
        if (s.fx.ticketLv < 2 || s.selected == null || !s.selected.order.burger || s.selected.status != Customer.ORDERED) return null;
        List<String> seq = s.selected.order.sequence();
        int n = s.boards[s.focusBoard()].layers.size();
        return n < seq.size() ? seq.get(n) : null;
    }

    private void drawBunBin(float x, String id, String label, boolean active) {
        G.panel(x, PY + 205, 176, 60, 12, 0xFFEFEFEA, G.DARK);
        G.sprite(Art.layer(id), x + 8, PY + 207 + (id.equals("bunT") ? 4 : 18), 80, id.equals("bunT") ? 34 : 20);
        G.text(label, x + 132, PY + 235, 14, G.INK, 0, true);
    }

    private void drawCustomer(Customer c, float ox) {
        float slide = (float) (c.enterAnim * c.enterAnim * 180);
        float x = ox + 8 + c.spot * 130 + slide;
        float bob = (float) Math.sin(time * 2 + c.uid) * 1.5f;
        float a = (float) (1 - c.enterAnim * .6);
        G.sprite(Art.customer(c.def, mood(c)), x, PY + 18 + bob, 125, 164, a);
        if (c.status == Customer.WAITING) {
            float pulse = 1 + (float) Math.sin(time * 6) * .08f;
            G.panel(x + 8, PY + 2, 110, 34, 12, G.WHITE, G.DARK);
            G.title("ORDER!", x + 63, PY + 19, 18 * pulse, G.KETCHUP, G.WHITE, 0);
        }
    }

    private int mood(Customer c) {
        double p = c.patienceLeft();
        return p > .75 ? 0 : p > .45 ? 1 : p > .2 ? 2 : 3;
    }

    private void drawPop(Pop p, float ox) {
        float x = Math.min(ox + 2 + p.c.spot * 130, ox + 640);
        float alpha = Math.min(1, p.t * 3);
        Art.customer(p.c.def, 0);
        int stars = (int) Math.round(p.r.satisfaction / 20);
        G.sprite(Art.customer(p.c.def, p.r.satisfaction >= 70 ? 0 : p.r.satisfaction >= 40 ? 2 : 3), x + 6, PY + 18, 125, 164, alpha * Math.min(1, p.t));
        G.panel(x - 6, PY + 2, 156, 92, 12, G.WHITE, G.DARK);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < stars ? '★' : '☆');
        G.text(sb.toString(), x + 72, PY + 20, 20, G.MUSTARD, 0, true);
        G.text(p.r.comment, x + 72, PY + 45, 13, G.INK, 0, true);
        G.text(G.money(p.r.pay) + " + " + G.money(p.r.tip) + " tip", x + 72, PY + 71, 14, G.GREEN, 0, true);
    }

    private void drawBoard(int i, float cx) {
        float left = cx - BOARD_W / 2 + 3;
        G.rrect(left, PY + 244, BOARD_W - 6, 306, 12, 0x22000000);
        G.line(cx, PY + 250, cx, PY + BOARD_BASE, 2, 0x44000000);
        G.rrect(left - 2, PY + BOARD_BASE, BOARD_W - 2, 14, 5, 0xFFB5834E);
        G.rrect(left - 2, PY + BOARD_BASE + 10, BOARD_W - 2, 6, 3, 0xFF7E5A33);
        Food.Burger b = s.boards[i];
        if (!(drag == Kind.BOARD && dragIdx == i && dragMoved)) drawBurger(b, cx, PY + BOARD_BASE + 2, 1, true);
        if (!b.layers.isEmpty()) {
            G.panel(cx - 44, PY + 208, 88, 32, 10, 0xFFEFE6D0, G.DARK);
            G.text("↩ UNDO", cx, PY + 224, 14, G.INK, 0, true);
        } else {
            G.text("Build here", cx, PY + BOARD_BASE - 16, 14, 0x883A2416, 0, true);
        }
    }

    private void drawBurger(Food.Burger b, float cx, float base, float scale, boolean animate) {
        float y = base;
        for (Food.Layer l : b.layers) {
            if (l.pour != null) {
                drawPour(l, cx, y, scale);
                y -= Art.layerStep(l.id) * scale;
                continue;
            }
            float h = Art.layerHeight(l.id) * scale, w = Art.LAYER_W * scale;
            float x = cx - w / 2 + (float) l.dx * scale;
            float top = y - h + Art.layerSink(l.id) * scale - (animate ? l.dropAnim * 26 : 0);
            if (l.patty != null) G.spriteTinted(Art.layer("patty"), x, top, w, h, Art.pattyTint(l.patty.avg()));
            else G.sprite(Art.layer(l.id), x, top, w, h);
            y -= Art.layerStep(l.id) * scale;
        }
    }

    /**
     * Poured sauce, drawn from where it actually landed: a ribbon whose thickness follows the amount in
     * each strip, so bare patches show, and heavy spots run down the side in drips.
     */
    private void drawPour(Food.Layer l, float cx, float y, float k) {
        Content.Ingredient ing = Content.ingredient(l.id);
        int col = ing == null ? 0xFFCC0000 : ing.color;
        int lum = ((col >> 16) & 255) + ((col >> 8) & 255) + (col & 255);
        int dark = lum > 600 ? 0xFFB3A57A : G.mix(col, 0xFF000000, .38f);
        double[] a = l.pour;
        int n = a.length;
        float bw = Art.LAYER_W * k / n, left = cx - Art.LAYER_W * k / 2, py = y - 4 * k, o = 1.2f * k;
        float[] xs = new float[n], th = new float[n], drip = new float[n];
        for (int i = 0; i < n; i++) {
            float amt = (float) Math.min(Food.SAUCE_MAX, a[i]);
            xs[i] = left + bw * (i + .5f);
            th[i] = amt < .05f ? 0 : (2.2f + amt * 3f) * k;
            drip[i] = a[i] > 1.15 ? (float) Math.min(11, (a[i] - 1.15) * 9 + 3) * k : 0;
        }
        // outline, then fill: a band joins neighbouring strips so the sauce reads as one flowing ribbon,
        // with a rounded bulge wherever more of it landed
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? dark : col;
            float e = pass == 0 ? o : 0;
            for (int i = 0; i < n; i++) {
                if (th[i] == 0) continue;
                if (i + 1 < n && th[i + 1] > 0) {
                    float h = Math.min(th[i], th[i + 1]) * .8f;
                    G.rect(xs[i], py - h / 2 - e, bw, h + e * 2, c);
                }
                G.oval(xs[i], py, bw * .62f + e, th[i] / 2 + e, c);
                if (drip[i] > 0) {
                    G.rect(xs[i] - 1.2f * k - e, py, 2.4f * k + e * 2, drip[i], c);
                    G.circle(xs[i], py + drip[i], 2.6f * k + e, c);
                }
            }
        }
        // wet shine along the top of the ribbon and on each drip
        for (int i = 0; i < n; i++) {
            if (th[i] < 3.5f * k) continue;
            boolean next = i + 1 < n && th[i + 1] >= 3.5f * k;
            G.rrect(xs[i] - bw * .3f, py - th[i] * .3f, next ? bw * 1.05f : bw * .55f, Math.max(.8f * k, th[i] * .14f), .5f * k, 0x90FFFFFF);
            if (drip[i] > 0) G.circle(xs[i] - .8f * k, py + drip[i] - .8f * k, .8f * k, 0xC0FFFFFF);
        }
    }

    private void drawFridge(float ox) {
        float top = PY + fridgeTop();
        float x = ox + 10, w = 780;
        if (fridgeT > 0) {
            G.panel(x, top, w, PH - fridgeTop() + 10, 18, 0xFFDDEFF5, G.DARK);
            G.rect(x + 10, top + 10, w - 20, PH - fridgeTop() - 10, 0xFF9CC7D8);
            List<Content.Drink> list = visibleDrinks();
            for (int i = 0; i < list.size(); i++) {
                Content.Drink d = list.get(i);
                float dx = ox + 30 + (i % 4) * 190, dy = top + 30 + (i / 4) * 170;
                boolean locked = d.level > game.save.level;
                G.rect(dx - 12, dy + 136, 186, 8, 0xFFEFF7FA);
                G.sprite(Art.drink(d.id), dx + 52, dy + 2, 66, 114, locked ? .3f : 1);
                G.text(locked ? "Lv " + d.level : d.name, dx + 85, dy + 126, 15, locked ? 0xFF4C6A78 : G.INK, 0, true);
            }
            if (s.fx.cups) {
                String[] names = {"S", "M", "L"};
                G.text("Cup size", ox + 180, top + 428, 18, G.INK, 0, true);
                for (int i = 0; i < 3; i++) {
                    float bx = ox + 250 + i * 100;
                    G.panel(bx, top + 400, 90, 56, 12, i == cupSize ? G.MUSTARD : G.CREAM, G.DARK);
                    G.text(names[i], bx + 45, top + 428, 24, G.INK, 0, true);
                }
            } else {
                G.text("Drag a drink to the tray or a ticket, or tap it.", ox + 400, top + 432, 18, G.DARK, 0, true);
            }
        }
        // the handle stays attached to the fridge top so it can be dragged either way
        float hy = top - 40;
        G.panel(ox + 330, hy, 120, 42, 12, G.TEAL, G.DARK);
        G.text(fridgeT > .5f ? "▼ DRINKS" : "▲ DRINKS", ox + 390, hy + 21, 17, G.WHITE, 0, true);
    }

    private void drawTrayPanel(float ox) {
        float x = ox + 800;
        G.rect(x, PY, 370 + Math.max(0, G.right - G.WIDTH), PH, 0xFFF2E3C2); // runs on into any extra screen width
        G.rect(x, PY, 4, PH, G.DARK);
        Customer c = s.selected;
        if (c == null) {
            G.text("No ticket selected", x + 185, PY + 100, 22, G.BROWN, 0, true);
        } else if (c.status == Customer.WAITING) {
            G.sprite(Art.customer(c.def, mood(c)), x + 10, PY + 10, 60, 78);
            G.text(c.def.name, x + 80, PY + 30, 24, G.INK, -1, true);
            G.text("Tap the ticket to take the order", x + 185, PY + 120, 18, G.KETCHUP, 0, true);
            G.text("\"" + c.greeting + "\"", x + 185, PY + 150, 16, G.BROWN, 0, false);
        } else {
            drawOrderDetail(c, x);
        }
        // tray
        G.rrect(x + 10, PY + 268, 350, 195, 16, 0xFF5D7F86);
        G.rrect(x + 16, PY + 274, 338, 183, 12, 0xFF7FA2A9);
        int used = 0;
        for (Customer o : s.active) if (!o.tray.empty()) used++;
        G.text("Trays in use: " + used + " of " + s.fx.trays, x + 185, PY + 250, 15, G.BROWN, 0, true);
        if (c != null) drawTrayItems(c, ox);

        boolean ready = c != null && c.status == Customer.ORDERED && c.complete();
        boolean any = c != null && c.status == Customer.ORDERED && !c.tray.empty();
        G.panel(ox + 820, PY + 500, 230, 80, 16, ready ? G.GREEN : any ? 0xFFC9A04A : 0xFF9A938A, G.DARK);
        G.title(ready ? "SERVE!" : "SERVE", ox + 935, PY + 540, 34, G.WHITE, G.DARK, 0);
        G.panel(ox + 1060, PY + 500, 100, 80, 16, any ? G.KETCHUP : 0xFF9A938A, G.DARK);
        G.text("CLEAR", ox + 1110, PY + 540, 18, G.WHITE, 0, true);
    }

    private void drawOrderDetail(Customer c, float x) {
        G.sprite(Art.customer(c.def, mood(c)), x + 8, PY + 6, 52, 68);
        G.text(c.def.name, x + 66, PY + 18, 21, G.INK, -1, true);
        StringBuilder hearts = new StringBuilder(c.def.arch.label);
        if (c.loyalty > 0) { hearts.append("  "); for (int i = 0; i < c.loyalty; i++) hearts.append('♥'); }
        G.text(hearts.toString(), x + 66, PY + 42, 14, G.KETCHUP, -1, true);
        G.bar(x + 66, PY + 56, 290, 12, c.patienceLeft(), patienceColor(c), 0xFFCDBB98);
        float y = PY + 92;
        if (c.order.burger) {
            G.text(c.tray.burger != null ? "✔" : "", x + 346, y, 20, G.GREEN, 0, true);
            G.text("BURGER: " + Food.PATTY_STAGE[c.order.doneness], x + 12, y, 18, G.INK, -1, true);
            y += 22;
            float cx = x + 12;
            int n = 1;
            List<String> toppings = new ArrayList<>(c.order.veg);
            toppings.addAll(c.order.sauce);
            if (toppings.isEmpty()) G.text("Plain: no toppings", cx, y + 6, 14, G.BROWN, -1, true);
            for (String id : toppings) {
                Content.Ingredient ing = Content.ingredient(id);
                String label = n++ + " " + ing.name;
                float w = G.width(label, 14, true) + 14;
                if (cx + w > x + 362) { cx = x + 12; y += 26; }
                G.rrect(cx, y - 7, w, 24, 8, ing.sauce ? 0xFFFFE9B8 : 0xFFD6EDB8);
                G.text(label, cx + 7, y + 5, 14, G.INK, -1, true);
                cx += w + 4;
            }
            y += 30;
        }
        if (c.order.fries) {
            G.text("FRIES: Golden", x + 12, y, 18, G.INK, -1, true);
            if (c.tray.fries != null) G.text("✔", x + 346, y, 20, G.GREEN, 0, true);
            y += 25;
        }
        if (c.order.drink != null) {
            String size = s.fx.cups ? new String[]{"Small ", "Medium ", "Large "}[c.order.size] : "";
            G.text("DRINK: " + size + Content.drink(c.order.drink).name, x + 12, y, 18, G.INK, -1, true);
            if (c.tray.drink != null) G.text("✔", x + 346, y, 20, G.GREEN, 0, true);
        }
    }

    private void drawTrayItems(Customer c, float ox) {
        boolean movingTray = drag == Kind.TRAY && dragMoved;
        if (c.tray.burger != null && !(movingTray && dragIdx == 0)) {
            drawBurger(c.tray.burger, ox + traySlotX(0) + 56, PY + 440, .8f, false);
        }
        if (c.tray.fries != null && !(movingTray && dragIdx == 1)) {
            G.sprite(Art.friesBox(c.tray.fries.stage()), ox + traySlotX(1) + 12, PY + 340, 88, 102);
        }
        if (c.tray.drink != null && !(movingTray && dragIdx == 2)) {
            float k = new float[]{.82f, 1, 1.15f}[c.tray.drink.size];
            G.sprite(Art.drink(c.tray.drink.id), ox + traySlotX(2) + 56 - 33 * k, PY + 445 - 114 * k, 66 * k, 114 * k);
        }
    }

    private int patienceColor(Customer c) {
        double p = c.patienceLeft();
        return p > .5 ? G.GREEN : p > .25 ? G.MUSTARD : G.KETCHUP;
    }

    private void drawCooking(float ox) {
        G.sprite(Art.kitchenWall(), ox, PY, PW, PH);
        // patty supply
        G.sprite(Art.rawPatties(), ox + 26, PY + 200, 118, 118);
        G.text("Raw Patties", ox + 85, PY + 330, 16, G.INK, 0, true);
        // grill
        G.panel(ox + 165, PY + 40, 440, 530, 18, 0xFF4A4A50, G.DARK);
        for (int i = 0; i < 9; i++) drawGrillSlot(i, ox);
        // fryer
        G.panel(ox + 625, PY + 130, 530, 305, 18, 0xFFAEB7BD, G.DARK);
        G.text("FRYER", ox + 890, PY + 112, 22, G.DARK, 0, true);
        for (int i = 0; i < 3; i++) drawBasket(i, ox);
        G.sprite(Art.rawBag(), ox + 655, PY + 446, 96, 110);
        G.text("Raw Fries", ox + 703, PY + 562, 15, G.INK, 0, true);
        G.sprite(Art.cartons(), ox + 805, PY + 446, 96, 110);
        if (s.packed != null && !(drag == Kind.PACKED && dragMoved)) {
            // a filled box sits in front of the stack until it goes into the warmer
            G.oval(ox + 853, PY + 548, 46, 8, 0x44000000);
            G.sprite(Art.friesBox(s.packed.stage()), ox + 815, PY + 452, 76, 96);
            G.text("Boxed!", ox + 853, PY + 562, 15, G.GREEN, 0, true);
        } else {
            G.text("Boxes", ox + 853, PY + 562, 15, G.INK, 0, true);
        }
        G.panel(ox + 950, PY + 470, 200, 80, 14, G.TEAL, G.DARK);
        G.text("COUNTER ►", ox + 1050, PY + 498, 21, G.WHITE, 0, true);
        G.text("drag ← to return", ox + 1050, PY + 526, 14, G.WHITE, 0, false);
    }

    private void drawGrillSlot(int i, float ox) {
        float x = ox + slotX(i), y = PY + slotY(i);
        if (i >= s.grill.length) {
            G.rrect(x, y, 136, 164, 10, 0x55000000);
            G.text("Upgrade", x + 68, y + 82, 15, 0x88FFFFFF, 0, true);
            return;
        }
        G.rrect(x, y, 136, 164, 10, 0xFF2A2A2E);
        for (int b = 0; b < 6; b++) G.rect(x + 8, y + 14 + b * 16, 120, 3, 0xFF55555C);
        Food.Patty p = s.grill[i];
        if (p == null) return;
        boolean moving = drag == Kind.GRILL && dragIdx == i && dragMoved;
        if (!moving) {
            float squash = 1 - p.flipAnimAmount();
            float h = 72 * Math.max(.15f, Math.abs(squash * 2 - 1));
            float py = y + 50 - h / 2 - p.flipAnimHop() * 26;
            G.spriteTinted(Art.pattyTop(), x + 16, py, 104, h, Art.pattyTint(p.up()));
            if (p.up() > .25) G.sprite(Art.grillMarks(), x + 16, py, 104, h, Math.min(.5f, (float) p.up()));
            if (p.stage() >= Food.OVER) {
                float puff = (time * 1.2f + i * .3f) % 1;
                G.circle(x + 68, y + 30 - puff * 30, 10 + puff * 12, p.stage() == Food.BURNED ? 0x88222222 : 0x55888888);
            }
        }
        float gy = y + 100;
        drawGauge(x + 10, gy, p.a, !p.downIsB);
        drawGauge(x + 10, gy + 18, p.b, p.downIsB);
        if (s.fx.alarms >= 2 && s.selected != null && s.selected.order.burger && s.selected.status == Customer.ORDERED) {
            float tx = x + 10 + 116 * (float) Food.targetOf(s.selected.order.doneness);
            G.rect(tx - 1.5f, gy - 4, 3, 30, G.WHITE);
        }
        int st = p.stage();
        boolean needsFlip = p.down() >= .45 && p.up() < .2 && p.stage() < Food.OVER;
        G.text(needsFlip ? "FLIP ME!" : Food.PATTY_STAGE[st], x + 68, y + 148, 14,
                needsFlip ? G.MUSTARD : st >= Food.OVER ? 0xFFFF6B5A : G.WHITE, 0, true);
    }

    private void drawGauge(float x, float y, double v, boolean active) {
        int[] zones = {0xFF777777, 0xFFD9A58A, 0xFFB57748, 0xFF7E4E2C, 0xFFB5352A};
        float w = 116 / 5f;
        for (int i = 0; i < 5; i++) G.rect(x + i * w, y, w - 1, 10, zones[i]);
        float k = (float) Math.min(1, v);
        G.rect(x + 116 * k - 2, y - 3, 4, 16, active ? G.WHITE : 0xFFBBBBBB);
        if (active) G.circle(x - 5, y + 5, 3, 0xFFFF9A3C);
    }

    private void drawBasket(int i, float ox) {
        float x = ox + basketX(i), y = PY + 150;
        boolean locked = i >= s.baskets.length;
        G.rrect(x, y, 155, 265, 10, 0xFF6E777D);
        G.rrect(x + 6, y + 110, 143, 150, 8, locked ? 0xFF5A5A52 : 0xFFD8A23A);
        if (locked) {
            G.text("Upgrade", x + 77, y + 60, 15, G.WHITE, 0, true);
            return;
        }
        Food.Basket b = s.baskets[i];
        boolean frying = b.state == Food.Basket.DOWN;
        if (frying) {
            for (int k = 0; k < 8; k++) {
                float phase = (time * (0.8f + k * .13f) + k * .37f) % 1;
                G.circle(x + 20 + k * 16, y + 250 - phase * 130, 2 + phase * 4, 0x88FFF6C8);
            }
        }
        boolean moving = drag == Kind.BASKET && dragIdx == i && dragMoved;
        if (b.state != Food.Basket.EMPTY && !moving) {
            float by = frying ? y + 150 : y + 44;
            drawBasketArt(x + 22, by, b);
        } else if (b.state == Food.Basket.EMPTY) {
            G.rrectLine(x + 22, y + 44, 110, 60, 8, 0xFF3A3A3A);
            G.text("Empty", x + 77, y + 74, 14, G.WHITE, 0, true);
        }
        if (b.state != Food.Basket.EMPTY) {
            float gx = x + 10;
            int[] z = {0xFF777777, 0xFFE9D48A, 0xFFF3B93B, 0xFFB5752A, 0xFF5A2A18};
            float[] stops = {0, .2f, .45f, .7f, 1};
            for (int k = 0; k < 5; k++) {
                float a = stops[k], e = k == 4 ? 1.0f : stops[k + 1];
                if (k == 4) break;
                G.rect(gx + a * 135, y + 10, (e - a) * 135 - 1, 12, z[k]);
            }
            G.rect(gx + 135 * (float) Math.min(1, b.p) - 2, y + 6, 4, 20, G.WHITE);
            int st = Food.fryStage(b.p);
            String label = b.state == Food.Basket.UP && b.p == 0 ? "Tap to lower" : Food.FRY_STAGE[st];
            G.text(label, x + 77, y + 36, 14, st >= 3 ? 0xFFFFC2B5 : G.WHITE, 0, true);
        }
    }

    private void drawBasketArt(float x, float y, Food.Basket b) {
        G.sprite(Art.friesPile(Food.fryStage(b.p)), x, y, 110, 60);
        G.rrectLine(x, y + 2, 110, 58, 8, 0xFF2A2A2A);
        G.rect(x + 104, y - 18, 34, 6, 0xFF2A2A2A);
    }

    private void drawRail() {
        G.rect(G.left, 0, G.right - G.left, PY, 0xFF3A2416);
        G.rect(RAIL_X - 5, 124, G.right - RAIL_X + 5, 4, 0xFF8A6A50);
        G.rect(G.left, 0, 110 - G.left, PY, 0xFF2A1A10);
        String cash = G.money(game.save.money + s.moneyEarned + s.tipsEarned);
        float cashSize = Math.min(22, 22 * 100 / Math.max(1, G.width(cash, 22, true))); // shrink to fit the 110-wide box
        G.text(cash, 55, 18, cashSize, G.MUSTARD, 0, true);
        G.text("Shift " + s.number, 55, 42, 16, G.WHITE, 0, true);
        G.text(s.remaining() + " to serve", 55, 62, 14, G.CREAM, 0, false);
        if (s.streak >= 2) {
            // emoji are sprites: text is drawn one UTF-16 unit at a time and would split them
            G.sprite(Art.emoji("🔥", 18), 30, 69, 18, 21.6f);
            G.text("×" + s.streak + " streak", 50, 80, 13, G.MUSTARD, -1, true);
        }
        for (int i = 0; i < rail.size(); i++) drawTicket(rail.get(i), RAIL_X + i * TICKET_STEP);
        if (s.linedUp() > 0 && rail.size() < 6) {
            G.text(s.linedUp() + " more on the way", RAIL_X + rail.size() * TICKET_STEP + 90, 60, 15, 0x88FFFFFF, 0, true);
        }
    }

    private void drawTicket(Customer c, float x) {
        boolean sel = c == s.selected;
        float y = 3 + (sel ? 2 : 0);
        G.rrect(x, y + 3, TICKET_W, TICKET_H - 3, 8, 0x55000000);
        G.rrect(x, y, TICKET_W, TICKET_H - 3, 8, sel ? G.MUSTARD : 0xFF8A6A50);
        G.rrect(x + 3, y + 3, TICKET_W - 6, TICKET_H - 9, 6, 0xFFFFFBEF);
        G.sprite(Art.customer(c.def, mood(c)), x + 5, y + 4, 28, 37);
        G.text(c.def.name, x + 35, y + 14, 14, G.INK, -1, true);
        G.text(com_time(c.waited), x + TICKET_W - 8, y + 14, 13, G.BROWN, 1, true);
        G.bar(x + 35, y + 27, TICKET_W - 43, 8, c.patienceLeft(), patienceColor(c), 0xFFDCCEB0);
        if (c.status == Customer.WAITING) {
            G.title("TAP: TAKE ORDER", x + TICKET_W / 2, y + 74, 15, G.KETCHUP, G.WHITE, 0);
            return;
        }
        Food.Order o = c.order;
        float rowY = y + 53;
        if (o.burger) {
            String[] d = {"", "LIGHT", "MED", "WELL"};
            boolean match = false;
            if (s.fx.ticketLv >= 2) for (Food.Held h : s.cabinet) if (h != null && h.patty != null && h.patty.stage() == o.doneness) match = true;
            int colour = o.doneness == Food.LIGHT ? 0xFFD98C78 : o.doneness == Food.MEDIUM ? 0xFFB57748 : 0xFF7E4E2C;
            G.rrect(x + 6, rowY - 9, 40, 18, 5, colour);
            G.text(d[o.doneness], x + 26, rowY, 11, G.WHITE, 0, true);
            if (match) G.circle(x + 46, rowY - 8, 5, G.GREEN);
            float cx = x + 50;
            for (String id : o.veg) { chip(id, cx + 10, rowY); cx += 21; }
            float sy = rowY + 22;
            cx = x + 8;
            for (String id : o.sauce) { chip(id, cx + 10, sy); cx += 21; }
        }
        float by = y + 97;
        float ix = x + 8;
        if (o.burger) ix = statusChip("🍔", ix, by, c.tray.burger != null);
        if (o.fries) ix = statusChip("🍟", ix, by, c.tray.fries != null);
        if (o.drink != null) {
            ix = statusChip("🥤", ix, by, c.tray.drink != null);
            String label = Content.drink(o.drink).name;
            if (s.fx.cups) label = new String[]{"S ", "M ", "L "}[o.size] + label;
            G.text(label, ix + 2, by, 12, G.BROWN, -1, true);
        }
        if (c.complete()) G.text("READY", x + TICKET_W - 8, by, 13, G.GREEN, 1, true);
    }

    private static String com_time(double sec) {
        int v = (int) sec;
        return String.format(Locale.US, "%d:%02d", v / 60, v % 60);
    }

    private float statusChip(String emoji, float x, float y, boolean done) {
        G.rrect(x, y - 11, 25, 22, 6, done ? 0xFFB8E0A0 : 0xFFEFE3C6);
        G.sprite(Art.emoji(emoji, 20), x + 2.5f, y - 11, 20, 24, done ? 1 : .55f);
        return x + 27;
    }

    private void chip(String id, float cx, float cy) {
        Content.Ingredient ing = Content.ingredient(id);
        G.circle(cx, cy, 10, G.DARK);
        G.circle(cx, cy, 9, ing.color);
        int lum = ((ing.color >> 16) & 255) + ((ing.color >> 8) & 255) + (ing.color & 255);
        G.text(ing.code, cx, cy, 10, lum > 420 ? G.INK : G.WHITE, 0, true);
    }

    private void drawCabinet() {
        G.rect(G.left, PY, 110 - G.left, PH, 0xFF8E9498);
        G.rect(106, PY, 4, PH, G.DARK);
        G.rrect(4, PY + 3, 98, 24, 8, 0xFF5A3A28);
        G.text("WARMER", 53, PY + 15, 14, G.MUSTARD, 0, true);
        for (int i = 0; i < 6; i++) {
            float y = CAB_Y + i * CAB_STEP;
            if (i >= s.cabinet.length) {
                G.rrect(6, y, 94, CAB_H, 8, 0xFF6E7478);
                G.text("Upgrade", 53, y + CAB_H / 2, 12, 0xAAFFFFFF, 0, true);
                continue;
            }
            G.rrect(6, y, 94, CAB_H, 8, 0xFF5A3A28);
            G.rrect(9, y + 3, 88, CAB_H - 6, 6, 0xFFE8A04A);
            Food.Held h = s.cabinet[i];
            if (h == null || (drag == Kind.CAB && dragIdx == i && dragMoved)) continue;
            if (h.patty != null) {
                G.spriteTinted(Art.pattyTop(), 18, y + 7, 70, 44, Art.pattyTint(h.patty.avg()));
                if (h.patty.avg() > .2) G.sprite(Art.grillMarks(), 18, y + 7, 70, 44, .4f);
                int st = h.patty.stage();
                String[] short6 = {"Raw", "Light", "Medium", "Well", "Over", "Burned"};
                G.text(short6[st], 53, y + 61, 13, st >= Food.OVER ? 0xFF8A1A10 : G.INK, 0, true);
            } else {
                G.sprite(Art.friesBox(h.fries.stage()), 30, y + 3, 46, 53);
                String[] short5 = {"Raw", "Pale", "Golden", "Over", "Burned"};
                G.text(short5[h.fries.stage()], 53, y + 63, 12, G.INK, 0, true);
            }
        }
        G.rrect(6, TRASH_Y, 96, 86, 10, 0xFF3F4347);
        G.sprite(Art.emoji("🗑️", 50), 28, TRASH_Y + 2, 50, 60);
        G.text("BIN", 53, TRASH_Y + 72, 14, G.WHITE, 0, true);
    }

    private void drawFeedback(float y) {
        for (Alert a : alerts) {
            float w = G.width(a.text, 18, true) + 40;
            int alpha = (int) (Math.min(1, a.t * 2) * 230) << 24;
            G.rrect(640 - w / 2, y, w, 34, 12, alpha | 0x3A2416);
            G.text(a.text, 640, y + 17, 18, alpha | 0xFFD98A, 0, true);
            y += 38;
        }
        for (Floater f : floaters) G.title(f.text, f.x, f.y, 26, f.color, G.WHITE, 0);
    }

    /** Draws the text hint for early shifts; returns true when it sits at the top of the playfield. */
    private boolean drawHint() {
        if (!game.settings.hints || s.number > 2 || paused) return false;
        String h = hint();
        if (h == null) return false;
        float w = Math.min(760, G.width(h, 17, true) + 44);
        float x = PX + (PW - 370 - w) / 2;
        float y;
        boolean top = panTarget == PW;
        if (top) { x = PX + (PW - w) / 2; y = PY + 3; } // above the grill, clear of the station labels
        else if (s.fridgeOpen) y = PY + 66;
        else y = PY + 160; // on the counter, clear of the bun bins

        G.rrect(x, y, w, 36, 12, 0xF01F6A6A);
        G.text(h, x + w / 2, y + 18, 17, G.WHITE, 0, true);
        return top;
    }

    // ---------------------------------------------------------------- tutorial

    private static final int SAY = 0, TAP = 1, DRAG = 2, LOOK = 3, POUR = 4;
    private static final String WARMER_WHY = "The WARMER on the left is shared by both stations: it carries food from the kitchen to the counter";

    /** One tutorial instruction: what to do, why, and the gesture to demonstrate in screen coordinates. */
    private static final class Guide {
        final String text, sub;
        final int gesture;
        final float fx, fy, tx, ty;
        String item; // the sauce a POUR gesture demonstrates
        Guide(String text, String sub, int gesture, float fx, float fy, float tx, float ty) {
            this.text = text; this.sub = sub; this.gesture = gesture; this.fx = fx; this.fy = fy; this.tx = tx; this.ty = ty;
        }
    }

    private static Guide say(String text, String sub) { return new Guide(text, sub, SAY, 0, 0, 0, 0); }
    private static Guide tapAt(String text, String sub, float x, float y) { return new Guide(text, sub, TAP, x, y, x, y); }
    private static Guide look(String text, String sub, float x, float y) { return new Guide(text, sub, LOOK, x, y, x, y); }
    private static Guide dragTo(String text, String sub, float fx, float fy, float tx, float ty) { return new Guide(text, sub, DRAG, fx, fy, tx, ty); }

    private float cabCy(int i) { return CAB_Y + i * CAB_STEP + CAB_H / 2; }
    private float grillCx(int i) { return kx() + slotX(i) + 68; }
    private float grillCy(int i) { return PY + slotY(i) + 50; }
    private float basketCx(int i) { return kx() + basketX(i) + 77; }
    private float basketCy(int i) { return PY + 150 + (s.baskets[i].state == Food.Basket.DOWN ? 180 : 74); }
    private float trayCx() { return ax() + 985; }
    private float trayCy() { return PY + 365; }
    private int warmerSlot() { return Math.max(0, s.freeCabinet()); }

    /** Carry the bottle from the shelf to above the burger, then sweep across it. */
    private Guide pourAt(String text, String sub, String sauce, float[] from, int board) {
        float finger = stackTop(board) - 30 + GHOST_LIFT - NOZZLE;
        Guide g = new Guide(text, sub, POUR, from[0], from[1], ax() + boardCx(board), finger);
        g.item = sauce;
        return g;
    }

    private Guide toKitchen(String text) { return tapAt(text, "Or swipe the background to the right", ax() + 260, PY + 561); }

    private int heldIndex(boolean patty) {
        for (int i = 0; i < s.cabinet.length; i++) {
            Food.Held h = s.cabinet[i];
            if (h != null && (patty ? h.patty != null : h.fries != null)) return i;
        }
        return -1;
    }

    /**
     * The guided first order, worked out from the live state each frame so it never gets out of step
     * with what the player actually did: urgent cooking first, then starting food, then assembly.
     */
    private Guide guide() {
        Customer c = s.active.isEmpty() ? null : s.active.get(0);
        if (c == null) return say("Welcome to your first shift!", "Your first customer is walking in...");
        if (c.status == Customer.WAITING) {
            int ti = Math.max(0, rail.indexOf(c));
            return tapAt("Tap " + c.def.name + "'s ticket to take the order", "Their patience is paused while you learn, so take your time",
                    RAIL_X + ti * TICKET_STEP + TICKET_W / 2, 74);
        }
        Food.Order o = c.order;
        boolean kitchen = panTarget == PW;
        double target = Food.targetOf(o.doneness);
        String done = Food.PATTY_STAGE[o.doneness];

        // the grill and fryer will not wait, so they come first
        for (int i = 0; i < s.grill.length; i++) {
            Food.Patty p = s.grill[i];
            if (p == null) continue;
            Guide g = null;
            if (p.stage() >= Food.OVER) {
                g = dragTo("Too far! Drag that patty to the BIN", "Then put a fresh one on the grill", grillCx(i), grillCy(i), 53, TRASH_Y + 40);
            } else if (p.down() >= target && p.up() < target) {
                g = tapAt("Tap the patty to flip it!", "Both sides need to reach " + done, grillCx(i), grillCy(i));
            } else if (p.a >= target && p.b >= target) {
                g = dragTo("It's " + done + "! Drag the patty into the WARMER", WARMER_WHY, grillCx(i), grillCy(i), 53, cabCy(warmerSlot()));
            }
            if (g != null) return kitchen ? g : toKitchen("The grill needs you: tap KITCHEN");
        }
        for (int i = 0; i < s.baskets.length; i++) {
            Food.Basket b = s.baskets[i];
            Guide g = null;
            if (b.state != Food.Basket.EMPTY && Food.fryStage(b.p) == 4) {
                g = dragTo("Burned! Drag that basket to the BIN", "Then fry a fresh batch", basketCx(i), basketCy(i), 53, TRASH_Y + 40);
            } else if (b.state == Food.Basket.DOWN && Food.fryStage(b.p) >= 2) {
                g = tapAt(Food.fryStage(b.p) == 2 ? "Golden! Tap the basket to lift it out" : "Lift the basket now, they're overcooking!",
                        "Fries keep cooking while they sit in the oil", basketCx(i), basketCy(i));
            } else if (b.state == Food.Basket.UP && b.p > 0 && s.packed == null) {
                g = dragTo("Drag the basket onto the Boxes", "Fries are served in a box, so pour them in first", basketCx(i), basketCy(i), kx() + 853, PY + 500);
            }
            if (g != null) return kitchen ? g : toKitchen("The fryer needs you: tap KITCHEN");
        }
        if (s.packed != null) {
            Guide g = dragTo("Now drag the boxed fries into the WARMER", WARMER_WHY, kx() + 853, PY + 500, 53, cabCy(warmerSlot()));
            return kitchen ? g : toKitchen("Your fries are boxed: tap KITCHEN");
        }

        // start whatever the order still needs
        boolean boardPatty = false, basketBusy = false;
        for (Food.Burger b : s.boards) if (b.patty() != null) boardPatty = true;
        for (Food.Basket b : s.baskets) if (b.state != Food.Basket.EMPTY) basketBusy = true;
        if (o.burger && c.tray.burger == null && !boardPatty && heldIndex(true) < 0 && s.grillCount() == 0) {
            if (!kitchen) return toKitchen("Burgers start on the grill. Tap KITCHEN");
            int slot = Math.max(0, s.freeGrill());
            return dragTo("Drag a raw patty onto the grill", "Patties take the longest, so always start them first",
                    kx() + 85, PY + 259, grillCx(slot), grillCy(slot));
        }
        if (o.fries && c.tray.fries == null && heldIndex(false) < 0 && !basketBusy && s.packed == null) {
            if (!kitchen) return toKitchen("Fries are made in the kitchen too. Tap KITCHEN");
            return dragTo("While it cooks, drag Raw Fries into a basket", "Cooking two things at once is the secret to speed",
                    kx() + 703, PY + 500, basketCx(0), PY + 224);
        }
        for (int i = 0; i < s.baskets.length; i++) {
            if (s.baskets[i].state == Food.Basket.UP && s.baskets[i].p == 0) {
                return kitchen ? tapAt("Tap the basket to lower it into the oil", null, basketCx(i), basketCy(i))
                        : toKitchen("Tap KITCHEN to start the fries");
            }
        }

        boolean drinkNeeded = o.drink != null && c.tray.drink == null;
        if (s.grillCount() > 0 || s.fryingCount() > 0) {
            if (!kitchen) return drinkNeeded ? drinkGuide(c) : toKitchen("Your food is cooking: tap KITCHEN to keep an eye on it");
            for (int i = 0; i < s.grill.length; i++) {
                Food.Patty p = s.grill[i];
                if (p == null) continue;
                return look(p.flips == 0 ? "Watch the gauge: flip when the marker reaches " + done : "Now the other side: wait for " + done + " again",
                        "The white marker is the side facing down. Red means burned!", grillCx(i), PY + slotY(i) + 113);
            }
            for (int i = 0; i < s.baskets.length; i++) {
                if (s.baskets[i].state != Food.Basket.DOWN) continue;
                return look("The fries are frying: lift them when the marker reaches Golden", "Golden is the bright yellow part of the gauge",
                        basketCx(i), PY + 166);
            }
        }
        if (kitchen) return tapAt("Everything's in the warmer. Tap COUNTER to build the order", "Or swipe the background to the left", kx() + 1050, PY + 510);

        // at the counter
        boolean burgerDone = !o.burger || c.tray.burger != null;
        float hx = ax() + 390, hy = PY + fridgeTop() - 19;
        if (s.fridgeOpen && !(burgerDone && drinkNeeded)) {
            return dragTo("Drag the DRINKS tab back down to close the fridge", "You can also just tap it", hx, hy, hx, PY + PH - 60);
        }
        if (!burgerDone) {
            int bi = s.focusBoard();
            Food.Burger board = s.boards[bi];
            float bx = ax() + boardCx(bi), by = PY + BOARD_BASE - 40;
            List<String> seq = o.sequence();
            int n = board.layers.size();
            for (int k = 0; k < n; k++) {
                if (k >= seq.size() || !seq.get(k).equals(board.layers.get(k).id)) {
                    return tapAt("Oops, that layer is out of order. Tap UNDO", "Stack toppings in the order the ticket lists them", bx, PY + 224);
                }
            }
            if (n == 0) return dragTo("Drag a Bottom Bun onto the board", "Burgers are built from the bottom up", ax() + 98, PY + 235, bx, by);
            if (n == 1) {
                int j = heldIndex(true);
                if (j < 0) return say("Cook another patty: this burger needs one", null);
                return dragTo("Drag the warm patty from the WARMER onto the bun", WARMER_WHY, 53, cabCy(j), bx, by);
            }
            Food.Layer last = board.layers.get(n - 1);
            if (last.pour != null && Food.pourQuality(last.pour) < .8) {
                float[] from = ingredientPoint(last.id);
                return pourAt("Keep pouring until the " + Content.ingredient(last.id).name + " covers it evenly",
                        "Sweep over the bare patches; too much will drip off the sides", last.id, from, bi);
            }
            if (n < seq.size()) {
                String id = seq.get(n);
                if (id.equals("bunT")) return dragTo("Finish it with the Top Bun", null, ax() + 278, PY + 235, bx, by);
                float[] from = ingredientPoint(id);
                if (Content.ingredient(id).sauce) {
                    return pourAt("Pour the " + Content.ingredient(id).name + ": hold the bottle over the burger and sweep side to side",
                            "Cover it evenly from edge to edge: not too little, not too much", id, from, bi);
                }
                return dragTo("Add " + Content.ingredient(id).name + " next", "Follow the ticket's toppings in order and drop them near the centre",
                        from[0], from[1], bx, by);
            }
            return dragTo("Drag the finished burger onto the tray", "The tray on the right belongs to the selected ticket", bx, PY + BOARD_BASE - 30, trayCx(), trayCy());
        }
        if (o.fries && c.tray.fries == null) {
            int j = heldIndex(false);
            if (j >= 0) return dragTo("Drag the fries from the WARMER onto the tray", null, 53, cabCy(j), trayCx(), trayCy());
        }
        if (drinkNeeded) return drinkGuide(c);
        if (c.complete()) return tapAt("Everything's on the tray. Tap SERVE!", "Fast, accurate orders earn bigger tips", ax() + 935, PY + 540);
        return say("Check the ticket: something is still missing", null);
    }

    private Guide drinkGuide(Customer c) {
        float hx = ax() + 390, hy = PY + fridgeTop() - 19;
        if (!s.fridgeOpen) return dragTo("Drag the DRINKS tab up to open the fridge", "You can also just tap it", hx, hy, hx, PY + FRIDGE_OPEN_TOP + 40);
        List<Content.Drink> list = visibleDrinks();
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).id.equals(c.order.drink)) continue;
            float dx = ax() + 30 + (i % 4) * 190 + 85, dy = PY + fridgeTop() + 30 + (i / 4) * 170 + 60;
            return dragTo("Drag the " + list.get(i).name + " onto the tray", null, dx, dy, trayCx(), trayCy());
        }
        return say("Find the drink on the ticket in the fridge", null);
    }

    private float[] ingredientPoint(String id) {
        for (int i = 0; i < Content.VEG.size(); i++) {
            if (Content.VEG.get(i).id.equals(id)) return new float[]{ax() + vegX(i) + 42, PY + vegY(i) + 31};
        }
        for (int i = 0; i < Content.SAUCE.size(); i++) {
            if (Content.SAUCE.get(i).id.equals(id)) return new float[]{ax() + sauceX(i) + 34, PY + 476};
        }
        return new float[]{ax() + 200, PY + 300};
    }

    /** Draws the instruction banner and the demonstrated gesture; returns where alerts may start. */
    private float drawGuide(Guide g) {
        // at the counter the banner stays over the prep area so the order card remains readable
        boolean counter = panTarget == 0;
        float area = counter ? 790 : 1080;
        float ts = Math.min(21, 21 * (area - 50) / G.width(g.text, 21, true));
        float ss = g.sub == null ? 15 : Math.min(15, 15 * (area - 50) / G.width(g.sub, 15, false));
        float w = Math.max(G.width(g.text, ts, true), g.sub == null ? 0 : G.width(g.sub, ss, false)) + 60;
        w = Math.max(460, Math.min(area, w));
        float h = g.sub == null ? 58 : 84;
        float x = (counter ? ax() + 400 : PX + PW / 2) - w / 2;
        float top = PY + 10, bottom = PY + PH - h - 8;
        boolean clash = g.gesture != SAY && (covers(x, top, w, h, g.fx, g.fy) || covers(x, top, w, h, g.tx, g.ty));
        float y = clash ? bottom : top;

        if (g.text.contains("WARMER")) {
            float a = .55f + .45f * (float) Math.sin(time * 6);
            G.rrectLine(1, PY + 1, 108, TRASH_Y - PY - 4, 12, ((int) (a * 255) << 24) | 0xFFD34D);
        }
        if (drag == Kind.NONE || !dragMoved) drawGesture(g); // don't demonstrate over the player's own hand

        G.panel(x, y, w, h, 16, 0xF7FFF8E6, G.DARK);
        G.rrect(x + 16, y - 11, 100, 22, 9, G.KETCHUP);
        G.text("TUTORIAL", x + 66, y, 13, G.WHITE, 0, true);
        G.text(g.text, x + w / 2, y + 28, ts, G.INK, 0, true);
        if (g.sub != null) G.text(g.sub, x + w / 2, y + 59, ss, G.BROWN, 0, false);
        return y == top ? y + h + 8 : PY + 8;
    }

    private static boolean covers(float x, float y, float w, float h, float px, float py) {
        return px > x - 40 && px < x + w + 40 && py > y - 60 && py < y + h + 50;
    }

    private void ring(float x, float y, float r, float alpha) {
        G.rrectLine(x - r, y - r, r * 2, r * 2, r, ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | 0xFFD34D);
    }

    private void hand(float x, float y, float press, float alpha) {
        float size = 64 * (1 - press * .14f);
        // the fingertip of the emoji sits near the top centre of its box
        G.sprite(Art.emoji("👆", 64), x - size * .46f, y - size * .06f, size, size * 1.2f, alpha);
    }

    private void drawGesture(Guide g) {
        if (g.gesture == SAY) return;
        float pulse = (time * 1.4f) % 1;
        if (g.gesture == LOOK) {
            ring(g.fx, g.fy, 22 + pulse * 30, 1 - pulse);
            ring(g.fx, g.fy, 24, .9f);
            hand(g.fx, g.fy + 18 + (float) Math.sin(time * 4) * 6, 0, .95f);
            return;
        }
        if (g.gesture == TAP) {
            float cyc = (time % 1.1f) / 1.1f;
            float press = cyc < .25f ? cyc / .25f : cyc < .45f ? 1 : Math.max(0, 1 - (cyc - .45f) / .2f);
            if (cyc > .3f) ring(g.fx, g.fy, 16 + (cyc - .3f) * 70, 1 - (cyc - .3f) / .7f);
            ring(g.fx, g.fy, 30, .8f);
            hand(g.fx, g.fy + 14 - press * 10, press, 1);
            return;
        }
        if (g.gesture == POUR) {
            // carry the bottle over, tip it, and sweep it from edge to edge
            float sweep = Art.LAYER_W / 2 - 8, sx = g.tx - sweep;
            G.rrect(g.tx - sweep - 6, g.ty - GHOST_LIFT + NOZZLE + 22, sweep * 2 + 12, 6, 3, 0x66FFD34D);
            ring(g.fx, g.fy, 30, .7f);
            float cyc = (time % 3.4f) / 3.4f, hx, hy, alpha = 1;
            if (cyc < .25f) {
                float t = cyc / .25f;
                t = t * t * (3 - 2 * t);
                hx = g.fx + (sx - g.fx) * t; hy = g.fy + (g.ty - g.fy) * t;
            } else if (cyc < .85f) {
                float u = (cyc - .25f) / .6f;
                hx = g.tx - sweep * (float) Math.cos(u * Math.PI * 2); hy = g.ty;
            } else {
                hx = sx; hy = g.ty;
                alpha = Math.max(0, 1 - (cyc - .85f) / .15f);
            }
            float ny = hy - GHOST_LIFT + NOZZLE;
            if (cyc >= .25f && cyc < .85f) {
                Content.Ingredient ing = Content.ingredient(g.item);
                int col = ing == null ? 0xFFCC0000 : ing.color;
                G.rrect(hx - 1.6f, ny, 3.2f, Math.max(0, g.ty - GHOST_LIFT + NOZZLE + 25 - ny), 1.6f, col);
            }
            G.tint(alpha * .85f);
            G.batch.draw(Art.bottle(g.item), hx - 20, ny - 74, 20, 37, 40, 74, 1, 1, 180);
            hand(hx, hy + 12, 1, alpha);
            return;
        }
        // DRAG: dotted path, rings at both ends, and a hand that carries the item across
        float dx = g.tx - g.fx, dy = g.ty - g.fy, len = (float) Math.sqrt(dx * dx + dy * dy);
        int dots = Math.max(2, (int) (len / 24));
        float drift = (time * 1.5f) % 1;
        for (int k = 0; k < dots; k++) {
            float t = (k + drift) / dots;
            G.circle(g.fx + dx * t, g.fy + dy * t, 6, 0x77000000);
            G.circle(g.fx + dx * t, g.fy + dy * t, 4, 0xEEFFFFFF);
        }
        ring(g.fx, g.fy, 30, .7f);
        ring(g.tx, g.ty, 34 + pulse * 10, .95f - pulse * .5f);
        float cyc = (time % 2.6f) / 2.6f;
        float hx, hy, press, alpha = 1;
        if (cyc < .18f) { hx = g.fx; hy = g.fy; press = cyc / .18f; }
        else if (cyc < .72f) {
            float t = (cyc - .18f) / .54f;
            t = t * t * (3 - 2 * t);
            hx = g.fx + dx * t; hy = g.fy + dy * t; press = 1;
        } else {
            hx = g.tx; hy = g.ty;
            press = Math.max(0, 1 - (cyc - .72f) / .1f);
            alpha = Math.max(0, 1 - (cyc - .85f) / .15f);
        }
        hand(hx, hy + 12, press, alpha);
    }

    /** First-shift coaching, derived from the actual state of the restaurant. */
    private String hint() {
        Customer waiting = null, working = null;
        for (Customer c : s.active) {
            if (c.status == Customer.WAITING && waiting == null) waiting = c;
            if (c.status == Customer.ORDERED && working == null) working = c;
        }
        if (waiting != null && working == null) return "Tap the ticket at the top to take " + waiting.def.name + "'s order";
        Customer c = s.selected != null && s.selected.status == Customer.ORDERED ? s.selected : working;
        if (c == null) return null;
        Food.Order o = c.order;
        boolean kitchen = panTarget == PW;
        if (c.complete()) return kitchen ? "Order ready: drag the background left to go to the counter" : "All done! Press SERVE";
        if (o.drink != null && c.tray.drink == null) {
            if (kitchen) return "Drag left to return to the counter for the drink";
            if (!s.fridgeOpen) return "Drag the DRINKS tab up to open the fridge";
            return "Drag the " + Content.drink(o.drink).name + " onto the tray";
        }
        if (s.fridgeOpen) return "Drag the DRINKS tab down to close the fridge";
        boolean heldFries = false, heldPatty = false;
        for (Food.Held h : s.cabinet) {
            if (h == null) continue;
            if (h.fries != null) heldFries = true; else heldPatty = true;
        }
        if (o.fries && c.tray.fries == null) {
            if (heldFries) return kitchen ? "The fries are in the warmer: drag left to go to the counter" : "Drag the fries from the warmer onto the tray";
            if (!kitchen) return "Drag the background right to reach the kitchen";
            if (s.packed != null) return "Drag the boxed fries into the warmer on the left";
            Food.Basket b = s.baskets[0];
            if (b.state == Food.Basket.EMPTY) return "Tap or drag Raw Fries into the basket";
            if (b.state == Food.Basket.DOWN) return Food.fryStage(b.p) == 2 ? "Golden! Tap the basket to lift it" : "Frying... lift it when the marker reaches gold";
            if (b.p == 0) return "Tap the basket to lower it into the oil";
            return "Drag the basket onto the Boxes";
        }
        if (o.burger && c.tray.burger == null) {
            Food.Burger board = s.boards[s.focusBoard()];
            boolean hasPatty = board.patty() != null;
            if (!hasPatty && !heldPatty) {
                if (!kitchen) return "Drag the background right to reach the grill";
                if (s.grillCount() == 0) return "Tap or drag a raw patty onto the grill";
                for (Food.Patty p : s.grill) {
                    if (p == null) continue;
                    if (p.flips == 0 && p.down() < Food.targetOf(o.doneness)) return "Cooking... flip it when the gauge reaches " + Food.PATTY_STAGE[o.doneness];
                    if (p.flips == 0) return "Tap the patty to flip it!";
                    if (p.down() < Food.targetOf(o.doneness)) return "Second side cooking: wait for the same mark";
                    return "Drag the patty into the warmer on the left";
                }
            }
            if (kitchen) return "Drag the background left to return to the counter";
            if (board.layers.isEmpty()) return "Drag a Bottom Bun onto the board";
            if (!hasPatty) return "Drag the patty from the warmer onto the bun";
            if (!board.finished()) {
                List<String> seq = o.sequence();
                int n = board.layers.size();
                if (n < seq.size()) {
                    String id = seq.get(n);
                    if (id.equals("bunT")) return "Finish with the Top Bun";
                    Content.Ingredient ing = Content.ingredient(id);
                    if (ing.sauce) return "Pour " + ing.name + ": hold the bottle over the burger and sweep across";
                    return "Add " + ing.name + " next (drag to the centre)";
                }
                return "Finish with the Top Bun";
            }
            return "Drag the finished burger onto the tray";
        }
        return null;
    }

    private void drawGhost() {
        if (drag == Kind.NONE || drag == Kind.PAN || drag == Kind.FRIDGE || !dragMoved) return;
        float x = dragX, y = dragY - GHOST_LIFT;
        switch (drag) {
            case BUN_B: case BUN_T: case ING: {
                TextureRegion t = Art.layer(dragId);
                float h = Art.layerHeight(dragId);
                G.sprite(t, x - Art.LAYER_W / 2, y - h / 2, Art.LAYER_W, h);
                break;
            }
            case BOARD:
                drawBurger(s.boards[dragIdx], x, y + 40, .9f, false);
                break;
            case CAB: {
                Food.Held h = s.cabinet[dragIdx];
                if (h == null) break;
                if (h.patty != null) G.spriteTinted(Art.layer("patty"), x - Art.LAYER_W / 2, y - 11, Art.LAYER_W, 22, Art.pattyTint(h.patty.avg()));
                else G.sprite(Art.friesBox(h.fries.stage()), x - 38, y - 44, 76, 88);
                break;
            }
            case GRILL: {
                Food.Patty p = s.grill[dragIdx];
                if (p != null) G.spriteTinted(Art.pattyTop(), x - 52, y - 36, 104, 72, Art.pattyTint(p.up()));
                break;
            }
            case RAW_PATTY:
                G.spriteTinted(Art.pattyTop(), x - 52, y - 36, 104, 72, Art.pattyTint(0));
                break;
            case RAW_FRIES:
                G.sprite(Art.friesPile(0), x - 55, y - 30, 110, 60);
                break;
            case BASKET:
                drawBasketArt(x - 55, y - 30, s.baskets[dragIdx]);
                break;
            case PACKED:
                if (s.packed != null) G.sprite(Art.friesBox(s.packed.stage()), x - 38, y - 44, 76, 88);
                break;
            case SAUCE: {
                // the bottle turns nozzle-down; while it is over a burger a stream runs to the surface
                float ny = y + NOZZLE;
                if (pourBoard >= 0) {
                    Content.Ingredient ing = Content.ingredient(dragId);
                    int col = ing == null ? 0xFFCC0000 : ing.color;
                    float wob = (float) Math.sin(time * 40) * .6f;
                    G.rrect(pourX - 2.6f + wob, ny, 5.2f, Math.max(0, pourTop - ny), 2.6f, G.mix(col, 0xFF000000, .3f));
                    G.rrect(pourX - 1.6f + wob, ny, 3.2f, Math.max(0, pourTop - ny), 1.6f, col);
                    G.oval(pourX, pourTop, 7 + (float) Math.sin(time * 25) * 1.5f, 2.6f, col);
                }
                G.spriteRot(Art.bottle(dragId), x, ny - 37, 40, 74, 180);
                break;
            }
            case DRINK:
                G.sprite(Art.drink(dragId), x - 33, y - 57, 66, 114);
                break;
            case TRAY:
                if (dragFrom == null) break;
                if (dragIdx == 0 && dragFrom.tray.burger != null) drawBurger(dragFrom.tray.burger, x, y + 40, .9f, false);
                else if (dragIdx == 1 && dragFrom.tray.fries != null) G.sprite(Art.friesBox(dragFrom.tray.fries.stage()), x - 38, y - 44, 76, 88);
                else if (dragIdx == 2 && dragFrom.tray.drink != null) G.sprite(Art.drink(dragFrom.tray.drink.id), x - 33, y - 57, 66, 114);
                break;
            default:
        }
    }
}

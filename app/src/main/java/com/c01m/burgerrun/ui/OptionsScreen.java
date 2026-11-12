package com.c01m.burgerrun.ui;

import com.c01m.burgerrun.engine.Art;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.engine.SaveSystem;
import com.c01m.burgerrun.sim.Content;
import com.c01m.burgerrun.sim.Progression;
import com.c01m.burgerrun.sim.SaveData;

/** Audio, display and gameplay settings, plus resetting the save. */
public final class OptionsScreen extends Screen {
    private static final String[] DIFF = {"Relaxed", "Normal", "Hard"};
    private static final float SX = 300, SW = 400;
    private final Screen returnTo;
    private final SaveSystem.Settings s;
    private int dragging = -1;
    private final Btn fullscreen, display, hints, difficulty;
    private Btn cheat, levelUp; // debug builds only

    public OptionsScreen(BurgerRunGame g, Screen returnTo) {
        super(g);
        this.returnTo = returnTo;
        s = g.settings;
        fullscreen = add(new Btn(760, 130, 460, 66, "", G.TEAL, () -> {
            s.fullscreen = !s.fullscreen;
            game.applyFullscreen();
            sync();
        }));
        display = add(new Btn(760, 210, 460, 66, "", G.TEAL, () -> {
            s.stretch = !s.stretch;
            game.applyViewport();
            sync();
        }));
        hints = add(new Btn(760, 290, 460, 66, "", G.TEAL, () -> { s.hints = !s.hints; sync(); }));
        difficulty = add(new Btn(760, 370, 460, 66, "", G.TEAL, () -> { s.difficulty = (s.difficulty + 1) % 3; sync(); }));
        add(new Btn(760, 450, 460, 66, "REPLAY TUTORIAL", G.MUSTARD, () -> {
            SaveData d = game.save != null ? game.save : game.saves.load();
            if (d == null) { game.notice("Start a new game and the tutorial plays automatically."); return; }
            d.tutorialDone = false;
            game.saves.save(d);
            s.hints = true;
            sync();
            game.notice("The guided first order will play at the start of your next shift.");
        }));
        add(new Btn(760, 530, 460, 66, "RESET SAVE DATA", G.KETCHUP, () -> {
            if (!game.saves.exists()) { game.notice("There is no save data to reset."); return; }
            game.confirm("Permanently delete your save, including all progress and statistics?", "DELETE", "CANCEL", () -> {
                game.saves.wipe();
                game.save = null;
                game.show(new MenuScreen(game));
            });
        }));
        add(new Btn(60, 590, 300, 80, "BACK", 0xFF8A6A50, this::back));
        if (g.saves.debugBuild) {
            cheat = add(new Btn(380, 590, 360, 80, "", 0xFF7A4FB0, () -> {
                s.infiniteMoney = !s.infiniteMoney;
                game.topUpCheat();
                game.persist();
                sync();
            }));
            levelUp = add(new Btn(760, 610, 460, 66, "", 0xFF7A4FB0, this::cheatLevelUp));
        }
        for (Btn b : buttons) b.textSize = 24;
        sync();
    }

    private void sync() {
        fullscreen.label = "Full screen: " + (s.fullscreen ? "ON" : "OFF");
        display.label = "Display: " + (s.stretch ? "Stretch to fill" : "Fill (keep shape)");
        hints.label = "Tutorial & hints: " + (s.hints ? "ON" : "OFF");
        difficulty.label = "Patience: " + DIFF[s.difficulty];
        if (cheat != null) cheat.label = "DEBUG Infinite money: " + (s.infiniteMoney ? "ON" : "OFF");
        if (levelUp != null) {
            SaveData d = game.save != null ? game.save : game.saves.load();
            levelUp.label = d == null ? "DEBUG Level up (no save yet)"
                    : d.level >= Content.MAX_LEVEL ? "DEBUG Level " + d.level + " (max)"
                    : "DEBUG Level up (now Lv " + d.level + ")";
        }
        game.saves.saveSettings();
    }

    /** Debug cheat: jump one level, as if enough XP had been earned, and say what it unlocked. */
    private void cheatLevelUp() {
        SaveData d = game.save != null ? game.save : game.saves.load();
        if (d == null) { game.notice("Start a new game first, then come back to level up."); return; }
        if (d.level >= Content.MAX_LEVEL) { game.notice("Already at the max level (" + Content.MAX_LEVEL + ")."); return; }
        d.level++;
        d.xp = 0;
        game.saves.save(d);
        game.audio.play("buy");
        sync();
        java.util.List<String> unlocks = Progression.unlocksAt(d.level);
        game.notice("Now level " + d.level + " (" + Progression.rank(d.level) + ")."
                + (unlocks.isEmpty() ? "" : " Unlocked: " + String.join(", ", unlocks) + "."));
    }

    @Override public void back() {
        game.saves.saveSettings();
        if (returnTo instanceof MenuScreen) game.show(new MenuScreen(game));
        else game.show(returnTo);
    }

    private float rowY(int i) { return 170 + i * 110; }

    @Override public void down(float x, float y) {
        super.down(x, y);
        if (anyButtonPressed()) return;
        for (int i = 0; i < 3; i++) {
            if (x > SX - 30 && x < SX + SW + 30 && Math.abs(y - rowY(i)) < 40) {
                dragging = i;
                move(x, y);
            }
        }
    }

    @Override public void move(float x, float y) {
        if (dragging < 0) return;
        float v = Math.max(0, Math.min(1, (x - SX) / SW));
        if (dragging == 0) s.master = v;
        else if (dragging == 1) s.music = v;
        else s.sfx = v;
        game.audio.applyVolumes();
    }

    @Override public void up(float x, float y) {
        if (dragging == 2 || dragging == 0) game.audio.play("ding");
        if (dragging >= 0) game.saves.saveSettings();
        dragging = -1;
        super.up(x, y);
    }

    @Override public void draw() {
        G.backdrop(Art.menuBackground());
        G.title("OPTIONS", 640, 60, 62, G.MUSTARD, G.DARK, 0);
        G.panel(50, 110, 680, 450, 24, G.CREAM, G.DARK);
        String[] names = {"Master", "Music", "Sound FX"};
        float[] vals = {s.master, s.music, s.sfx};
        for (int i = 0; i < 3; i++) {
            float y = rowY(i);
            G.text(names[i], 75, y, 26, G.INK, -1, true);
            G.bar(SX, y - 9, SW, 18, vals[i], G.KETCHUP, 0xFFD4C6A8);
            G.circle(SX + SW * vals[i], y, 20, G.DARK);
            G.circle(SX + SW * vals[i], y, 16, G.MUSTARD);
            G.text(Math.round(vals[i] * 100) + "%", SX + SW + 15, y, 20, G.INK, -1, true);
        }
        drawButtons();
    }
}

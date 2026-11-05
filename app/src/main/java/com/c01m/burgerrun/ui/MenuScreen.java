package com.c01m.burgerrun.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.c01m.burgerrun.engine.Art;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.sim.SaveData;

/** Main menu: PLAY (New Game / Continue), OPTIONS, STATS. */
public final class MenuScreen extends Screen {
    private float t;
    private final Btn play, options, stats, newGame, cont, playBack, quit;
    private boolean playMode;
    private final SaveData existing;

    public MenuScreen(BurgerRunGame g) {
        super(g);
        existing = g.saves.load();
        float x = 700, w = 400;
        play = add(new Btn(x, 250, w, 92, "PLAY", G.KETCHUP, () -> setPlay(true)));
        options = add(new Btn(x, 358, w, 80, "OPTIONS", G.TEAL, () -> g.show(new OptionsScreen(g, new MenuScreen(g)))));
        stats = add(new Btn(x, 454, w, 80, "STATS", G.TEAL, () -> g.show(new StatsScreen(g))));
        quit = add(new Btn(x, 550, w, 70, "QUIT", 0xFF8A6A50, () -> Gdx.app.exit()));
        cont = add(new Btn(x, 250, w, 92, existing != null ? "CONTINUE" : "NO SAVE YET", G.GREEN, this::doContinue));
        cont.enabled = existing != null;
        newGame = add(new Btn(x, 358, w, 80, "NEW GAME", G.KETCHUP, this::doNew));
        playBack = add(new Btn(x, 454, w, 80, "BACK", 0xFF8A6A50, () -> setPlay(false)));
        setPlay(false);
    }

    private void setPlay(boolean on) {
        playMode = on;
        play.visible = options.visible = stats.visible = quit.visible = !on;
        cont.visible = newGame.visible = playBack.visible = on;
    }

    private void doContinue() {
        game.save = game.saves.load();
        if (game.save == null) return;
        game.show(new BriefingScreen(game));
        game.collectOffline();
    }

    private void doNew() {
        Runnable start = () -> {
            game.save = new SaveData();
            game.persist();
            game.show(new BriefingScreen(game));
        };
        if (existing != null) {
            game.confirm("Start a new game? This overwrites your save (Shift " + existing.shift + ", Level " + existing.level + ").",
                    "OVERWRITE", "CANCEL", start);
        } else {
            start.run();
        }
    }

    @Override public void back() {
        if (playMode) setPlay(false);
        else Gdx.app.exit();
    }

    @Override public void update(float dt) { t += dt; }

    @Override public void draw() {
        G.backdrop(Art.menuBackground());
        G.title("BURGER", 340, 112, 104, G.MUSTARD, G.DARK, 0);
        G.title("RUN!", 340, 212, 104, G.KETCHUP, G.WHITE, 0);
        drawHeroBurger(340, 610 + (float) Math.sin(t * 2) * 8);
        if (existing != null && !playMode) {
            G.text("Shift " + existing.shift + "  ·  Level " + existing.level + "  ·  " + G.money(existing.money), 900, 210, 24, G.DARK, 0, true);
        }
        drawButtons();
    }

    private void drawHeroBurger(float cx, float base) {
        String[] layers = {"bunB", "patty", "lettuce", "tomato", "onion", "pickle", "mustard", "ketchup", "bunT"};
        float scale = 2.4f, y = base;
        G.oval(cx, base + 8, 170, 18, 0x44000000);
        for (String id : layers) {
            TextureRegion r = Art.layer(id);
            float h = Art.layerHeight(id) * scale, w = Art.LAYER_W * scale;
            float top = y - h + Art.layerSink(id) * scale;
            if (id.equals("patty")) G.spriteTinted(r, cx - w / 2, top, w, h, Art.pattyTint(.5));
            else G.sprite(r, cx - w / 2, top, w, h);
            y -= Art.layerStep(id) * scale;
        }
    }
}

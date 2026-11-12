package com.c01m.burgerrun.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.c01m.burgerrun.engine.AudioManager;
import com.c01m.burgerrun.engine.G;
import com.c01m.burgerrun.engine.SaveSystem;
import com.c01m.burgerrun.engine.Sprites;
import com.c01m.burgerrun.sim.Progression;
import com.c01m.burgerrun.sim.SaveData;

import java.util.function.Consumer;

/** GameManager: owns the render loop, current screen, save data, audio and modal dialogs. */
public final class BurgerRunGame implements ApplicationListener {
    public final SaveSystem saves;
    public final SaveSystem.Settings settings;
    public AudioManager audio;
    public SaveData save;
    public double fps;

    private final Consumer<Boolean> fullscreen;
    private final Vector2 tmp = new Vector2();
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private Sprites sprites;
    private Screen screen;
    private Screen.Modal modal;

    public BurgerRunGame(SaveSystem saves, Consumer<Boolean> fullscreen) {
        this.saves = saves;
        this.settings = saves.settings;
        this.fullscreen = fullscreen;
    }

    @Override public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(true, G.WIDTH, G.HEIGHT);
        applyViewport();
        batch = new SpriteBatch(4000);
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA); // atlases are premultiplied
        sprites = new Sprites();
        G.init(batch, sprites);
        audio = new AudioManager(settings);
        fullscreen.accept(settings.fullscreen);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                if (pointer != 0) return false;
                viewport.unproject(tmp.set(x, y));
                if (modal != null) modal.down(tmp.x, tmp.y);
                else screen.down(tmp.x, tmp.y);
                return true;
            }
            @Override public boolean touchDragged(int x, int y, int pointer) {
                if (pointer != 0) return false;
                viewport.unproject(tmp.set(x, y));
                if (modal == null) screen.move(tmp.x, tmp.y);
                return true;
            }
            @Override public boolean touchUp(int x, int y, int pointer, int button) {
                if (pointer != 0) return false;
                viewport.unproject(tmp.set(x, y));
                if (modal != null) modal.up(tmp.x, tmp.y);
                else screen.up(tmp.x, tmp.y);
                return true;
            }
            @Override public boolean keyDown(int key) {
                if (key == Input.Keys.BACK || key == Input.Keys.ESCAPE) {
                    if (modal != null) modal.cancel();
                    else screen.back();
                    return true;
                }
                if (modal == null) screen.key(key);
                return true;
            }
        });
        show(new MenuScreen(this));
    }

    public void applyViewport() {
        // Extend keeps the shape and fills wide phones with extra background instead of black bars
        viewport = settings.stretch ? new StretchViewport(G.WIDTH, G.HEIGHT, camera)
                : new ExtendViewport(G.WIDTH, G.HEIGHT, G.WIDTH * 3, G.HEIGHT, camera);
        updateViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void updateViewport(int w, int h) {
        viewport.update(w, h, true);
        // keep the 1280x720 layout centred, with any extra width split evenly to both sides
        camera.position.set(G.WIDTH / 2f, G.HEIGHT / 2f, 0);
        camera.update();
        G.left = G.WIDTH / 2f - viewport.getWorldWidth() / 2;
        G.right = G.WIDTH / 2f + viewport.getWorldWidth() / 2;
    }

    public void applyFullscreen() { fullscreen.accept(settings.fullscreen); }

    public void show(Screen s) {
        screen = s;
        modal = null;
    }

    public void modal(Screen.Modal m) { modal = m; }
    public void closeModal() { modal = null; }

    public void confirm(String text, String yes, String no, Runnable onYes) {
        modal(new Screen.Modal(this, text, yes, no, onYes));
    }

    public void notice(String text) {
        modal(new Screen.Modal(this, text, "OK", null, null));
    }

    public void persist() {
        if (save != null) {
            save.lastSeen = System.currentTimeMillis();
            saves.save(save);
        }
    }

    /** The idle layer: pays out whatever the Night Crew earned while the player was away. */
    public void collectOffline() {
        if (save == null) return;
        double earned = Progression.offlineEarnings(save, System.currentTimeMillis());
        save.lastSeen = System.currentTimeMillis();
        if (earned > 0) {
            save.money += earned;
            save.stats.money += earned;
            save.stats.offlineMoney += earned;
            saves.save(save);
            audio.play("coin");
            notice("Welcome back! Your Night Crew earned " + G.money(earned) + " while you were away.");
        }
    }

    public static final double CHEAT_MONEY = 1_000_000;

    /** Debug cheat: is infinite money active right now? Never true in release builds. */
    public boolean infiniteMoney() { return saves.debugBuild && settings.infiniteMoney; }

    /** Keeps cash topped up while the debug cheat is on (does not count toward stats). */
    public void topUpCheat() {
        if (save != null && infiniteMoney() && save.money < CHEAT_MONEY) save.money = CHEAT_MONEY;
    }

    @Override public void resize(int w, int h) { updateViewport(w, h); }

    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .05f);
        Gdx.gl.glClearColor(.16f, .1f, .06f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        topUpCheat();
        if (modal == null) screen.update(dt);
        viewport.apply();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        screen.draw();
        if (modal != null) modal.draw();
        batch.end();
    }

    @Override public void pause() {
        persist();
        screen.onAppPause();
        audio.onPause();
    }

    @Override public void resume() {
        audio.onResume();
        collectOffline();
    }

    @Override public void dispose() {
        persist();
        audio.dispose();
        batch.dispose();
        sprites.dispose();
    }
}

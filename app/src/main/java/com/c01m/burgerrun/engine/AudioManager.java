package com.c01m.burgerrun.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** All audio is synthesised to WAV files on first run, so the APK ships no sound assets. */
public final class AudioManager {
    private static final int RATE = 22050;
    private final Map<String, Sound> sounds = new HashMap<>();
    private final SaveSystem.Settings settings;
    private Music music;
    private long sizzleId = -1, bubbleId = -1;
    private float sizzleLevel, bubbleLevel;
    private volatile boolean ready;

    public AudioManager(SaveSystem.Settings settings) {
        this.settings = settings;
        new Thread(this::synthesise, "audio-synth").start();
    }

    private void synthesise() {
        Map<String, short[]> all = new LinkedHashMap<>();
        all.put("tap", tone(660, 880, .05, .5, 1));
        all.put("drop", thud());
        all.put("flip", tone(300, 700, .09, .6, 2));
        all.put("sizzle", noise(.25, .35, .9));
        all.put("tick", tone(1200, 1200, .05, .25, 1));
        all.put("ding", chord(.3, 1320, 1760));
        all.put("warn", beeps(740, 2, .09));
        all.put("burn", burn());
        all.put("bell", chord(.35, 988, 1319));
        all.put("ticket", tone(520, 780, .08, .5, 1));
        all.put("squirt", noise(.14, .4, .2));
        all.put("can", chord(.08, 1500, 2100));
        all.put("tray", tone(420, 620, .1, .5, 2));
        all.put("trash", tone(220, 90, .16, .55, 2));
        all.put("splash", noise(.22, .45, .5));
        all.put("serve", chord(.25, 660, 990));
        all.put("coin", arpeggio(.07, 1320, 1760));
        all.put("wrong", tone(240, 170, .22, .5, 2));
        all.put("star", arpeggio(.08, 880, 1175, 1568));
        all.put("level", arpeggio(.11, 523, 659, 784, 1047, 1319));
        all.put("whoosh", noise(.2, .22, .7));
        all.put("fridge", tone(120, 180, .3, .35, 2));
        all.put("buy", arpeggio(.07, 660, 880, 1320));
        all.put("loop_sizzle", noise(2, .5, .9));
        all.put("loop_bubble", bubbles());
        try {
            for (Map.Entry<String, short[]> e : all.entrySet()) writeWav(Gdx.files.local("sfx/" + e.getKey() + ".wav"), e.getValue());
            writeWav(Gdx.files.local("sfx/music.wav"), musicLoop());
        } catch (IOException e) {
            return; // the game plays on silently if storage refuses the files
        }
        Gdx.app.postRunnable(() -> {
            for (String name : all.keySet()) sounds.put(name, Gdx.audio.newSound(Gdx.files.local("sfx/" + name + ".wav")));
            music = Gdx.audio.newMusic(Gdx.files.local("sfx/music.wav"));
            music.setLooping(true);
            music.setVolume(settings.master * settings.music);
            music.play();
            ready = true;
        });
    }

    private static void writeWav(FileHandle f, short[] pcm) throws IOException {
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(f.write(false)))) {
            int bytes = pcm.length * 2;
            o.writeBytes("RIFF"); le32(o, 36 + bytes); o.writeBytes("WAVEfmt "); le32(o, 16);
            le16(o, 1); le16(o, 1); le32(o, RATE); le32(o, RATE * 2); le16(o, 2); le16(o, 16);
            o.writeBytes("data"); le32(o, bytes);
            for (short s : pcm) le16(o, s);
        }
    }
    private static void le32(DataOutputStream o, int v) throws IOException {
        o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24);
    }
    private static void le16(DataOutputStream o, int v) throws IOException {
        o.write(v); o.write(v >> 8);
    }

    // ---------------------------------------------------------------- synthesis

    private static short[] buf(double sec) { return new short[(int) (sec * RATE)]; }
    private static double env(double t, double dur) { return Math.min(1, t * 200) * Math.pow(1 - t / dur, 1.5); }
    private static short pcm(double v) { return (short) (Math.max(-1, Math.min(1, v)) * 32000); }

    private static short[] tone(double f0, double f1, double dur, double vol, int harmonics) {
        short[] b = buf(dur);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / RATE;
            ph += 2 * Math.PI * (f0 + (f1 - f0) * t / dur) / RATE;
            double v = Math.sin(ph);
            if (harmonics > 1) v += .4 * Math.sin(ph * 2);
            b[i] = pcm(v * vol * env(t, dur));
        }
        return b;
    }
    private static short[] thud() {
        short[] b = tone(180, 70, .1, .8, 1);
        Random r = new Random(1);
        for (int i = 0; i < b.length / 4; i++) b[i] += (short) (r.nextGaussian() * 2500);
        return b;
    }
    private static short[] chord(double dur, double... freqs) {
        short[] b = buf(dur);
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / RATE, v = 0;
            for (double f : freqs) v += Math.sin(2 * Math.PI * f * t);
            b[i] = pcm(v / freqs.length * .7 * env(t, dur));
        }
        return b;
    }
    private static short[] arpeggio(double step, double... freqs) {
        short[] out = buf(step * freqs.length + .25);
        for (int n = 0; n < freqs.length; n++) {
            int start = (int) (n * step * RATE);
            short[] note = tone(freqs[n], freqs[n], Math.min(.25, step * 2), .5, 2);
            for (int i = 0; i < note.length && start + i < out.length; i++) out[start + i] = pcm((out[start + i] + note[i]) / 32000.0);
        }
        return out;
    }
    private static short[] beeps(double f, int count, double each) {
        short[] out = buf(each * 2 * count);
        for (int n = 0; n < count; n++) {
            short[] b = tone(f, f, each, .55, 2);
            int at = (int) (n * 2 * each * RATE);
            System.arraycopy(b, 0, out, at, Math.min(b.length, out.length - at));
        }
        return out;
    }
    /** bright 0..1 sets how hissy the noise is. Long sounds are for looping, so stay flat. */
    private static short[] noise(double dur, double vol, double bright) {
        short[] b = buf(dur);
        Random r = new Random(2);
        double last = 0;
        boolean loop = dur >= 1;
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / RATE;
            double n = r.nextDouble() * 2 - 1;
            last = last * (1 - bright) + n * bright;
            double shape = loop ? (r.nextDouble() < .002 ? 1.5 : 1) : env(t, dur);
            b[i] = pcm((n * bright + last * (1 - bright)) * vol * shape);
        }
        return b;
    }
    private static short[] burn() {
        short[] b = noise(.5, .45, .8), t = tone(300, 90, .5, .4, 2);
        for (int i = 0; i < b.length && i < t.length; i++) {
            b[i] = pcm((b[i] * Math.pow(1 - (double) i / b.length, 1.5) + t[i]) / 32000.0);
        }
        return b;
    }
    private static short[] bubbles() {
        short[] b = buf(2);
        Random r = new Random(3);
        for (int k = 0; k < 26; k++) {
            int start = r.nextInt(b.length - 2500);
            double f = 280 + r.nextDouble() * 500;
            for (int i = 0; i < 2200; i++) {
                double t = (double) i / RATE;
                double v = Math.sin(2 * Math.PI * (f + 2500 * t) * t) * Math.pow(1 - i / 2200.0, 2) * .35;
                b[start + i] = pcm(b[start + i] / 32000.0 + v);
            }
        }
        return b;
    }

    /** A short jaunty diner loop: walking bass, off-beat organ chords, simple lead. */
    private static short[] musicLoop() {
        double beat = 60.0 / 124;
        int bars = 8;
        int samples = (int) (beat * 4 * bars * RATE);
        float[] mix = new float[samples];
        int[][] chords = {{0, 4, 7}, {0, 4, 7}, {5, 9, 12}, {0, 4, 7}, {7, 11, 14}, {5, 9, 12}, {0, 4, 7}, {7, 11, 14}};
        int[] walk = {0, 4, 7, 9};
        int[] lead = {12, -1, 16, 14, 12, -1, 9, 7, 9, 12, -1, 14, 16, -1, 19, -1};
        double root = 130.81;
        Random r = new Random(5);
        for (int bar = 0; bar < bars; bar++) {
            int[] ch = chords[bar];
            for (int q = 0; q < 4; q++) {
                double start = (bar * 4 + q) * beat;
                note(mix, start, beat * .8, root / 2 * semi(ch[0] + walk[q]), .26, 2);
                for (int n : ch) note(mix, start + beat / 2, beat * .28, root * 2 * semi(n), .05, 3);
            }
            for (int e = 0; e < 8; e++) {
                int l = lead[(bar * 8 + e) % lead.length];
                if (l >= 0 && (bar % 2 == 0 || r.nextDouble() < .55)) {
                    note(mix, (bar * 4 + e * .5) * beat, beat * .4, root * 2 * semi(l + ch[0] % 5), .075, 1);
                }
            }
        }
        short[] pcm = new short[samples];
        for (int i = 0; i < samples; i++) pcm[i] = pcm(mix[i]);
        return pcm;
    }
    private static double semi(int n) { return Math.pow(2, n / 12.0); }
    private static void note(float[] mix, double start, double dur, double f, double vol, int harm) {
        int s = (int) (start * RATE), n = (int) (dur * RATE);
        for (int i = 0; i < n && s + i < mix.length; i++) {
            double t = (double) i / RATE, v = 0;
            for (int h = 1; h <= harm; h++) v += Math.sin(2 * Math.PI * f * h * t) / h;
            mix[s + i] += (float) (v * vol * Math.min(1, t * 150) * Math.pow(1 - t / dur, 1.2));
        }
    }

    // ---------------------------------------------------------------- playback

    public void play(String name) {
        if (!ready) return;
        Sound s = sounds.get(name);
        if (s != null) s.play(settings.master * settings.sfx);
    }

    /** Kitchen ambience, 0..1 each. Called every frame while on shift. */
    public void setAmbience(float sizzle, float bubble) {
        if (!ready) return;
        sizzleLevel = sizzle;
        bubbleLevel = bubble;
        sizzleId = loop("loop_sizzle", sizzleId, sizzle * .4f);
        bubbleId = loop("loop_bubble", bubbleId, bubble * .5f);
    }

    private long loop(String name, long id, float level) {
        Sound s = sounds.get(name);
        float v = settings.master * settings.sfx * level;
        if (level <= 0) {
            if (id != -1) s.stop(id);
            return -1;
        }
        if (id == -1) return s.loop(v);
        s.setVolume(id, v);
        return id;
    }

    public void applyVolumes() {
        if (music != null) music.setVolume(settings.master * settings.music);
        if (ready) setAmbience(sizzleLevel, bubbleLevel);
    }

    public void onPause() {
        if (!ready) return;
        setAmbience(0, 0);
        music.pause();
    }

    public void onResume() {
        if (ready) music.play();
    }

    /** Releases every sound; without this a rebuilt app would play the old music on top of the new. */
    public void dispose() {
        if (!ready) return;
        ready = false;
        music.dispose();
        for (Sound s : sounds.values()) s.dispose();
        sounds.clear();
    }
}

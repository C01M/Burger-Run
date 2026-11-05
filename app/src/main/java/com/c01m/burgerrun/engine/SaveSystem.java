package com.c01m.burgerrun.engine;

import android.content.Context;
import android.util.AtomicFile;

import com.c01m.burgerrun.sim.SaveData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/** Persists the save file and settings as JSON in app-private storage. */
public final class SaveSystem {
    public static final class Settings {
        public float master = .8f, music = .45f, sfx = .8f;
        public boolean fullscreen = true, stretch = false, hints = true;
        public int difficulty = 1; // 0 relaxed, 1 normal, 2 hard

        public double difficultyMult() { return difficulty == 0 ? 1.25 : difficulty == 2 ? .8 : 1; }
    }

    private final AtomicFile saveFile, settingsFile;
    public final Settings settings = new Settings();

    public SaveSystem(Context ctx) {
        saveFile = new AtomicFile(new File(ctx.getFilesDir(), "save.json"));
        settingsFile = new AtomicFile(new File(ctx.getFilesDir(), "settings.json"));
        loadSettings();
    }

    private static void write(AtomicFile f, String text) {
        FileOutputStream out = null;
        try {
            out = f.startWrite();
            out.write(text.getBytes(StandardCharsets.UTF_8));
            f.finishWrite(out);
        } catch (IOException e) {
            if (out != null) f.failWrite(out);
        }
    }

    private static String read(AtomicFile f) {
        try {
            return new String(f.readFully(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public boolean exists() { return load() != null; }

    public SaveData load() {
        String text = read(saveFile);
        if (text == null) return null;
        try {
            return decode(new JSONObject(text));
        } catch (JSONException e) {
            return null;
        }
    }

    public void save(SaveData d) {
        try {
            write(saveFile, encode(d).toString());
        } catch (JSONException e) {
            // numbers are always finite here, so encoding cannot fail in practice
        }
    }

    public void wipe() { saveFile.delete(); }

    static JSONObject encode(SaveData d) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("v", 1).put("shift", d.shift).put("level", d.level).put("xp", d.xp).put("money", d.money).put("lastSeen", d.lastSeen)
                .put("tutorialDone", d.tutorialDone);
        JSONObject up = new JSONObject();
        for (String k : d.upgrades.keySet()) up.put(k, d.upgrades.get(k));
        o.put("upgrades", up);
        JSONObject regs = new JSONObject();
        for (String k : d.regulars.keySet()) {
            SaveData.Regular r = d.regulars.get(k);
            regs.put(k, new JSONObject().put("visits", r.visits).put("loyalty", r.loyalty).put("favorite", r.favorite));
        }
        o.put("regulars", regs);
        SaveData.Stats s = d.stats;
        o.put("stats", new JSONObject().put("shifts", s.shifts).put("customers", s.customers).put("burgers", s.burgers)
                .put("fries", s.fries).put("drinks", s.drinks).put("orders", s.orders).put("upgrades", s.upgrades)
                .put("bestStars", s.bestStars).put("bestCustomers", s.bestCustomers).put("burnedPatties", s.burnedPatties)
                .put("perfectOrders", s.perfectOrders).put("money", s.money).put("tips", s.tips)
                .put("satisfactionSum", s.satisfactionSum).put("accuracySum", s.accuracySum).put("bestScore", s.bestScore)
                .put("bestMoney", s.bestMoney).put("offlineMoney", s.offlineMoney));
        return o;
    }

    static SaveData decode(JSONObject o) throws JSONException {
        SaveData d = new SaveData();
        d.shift = o.getInt("shift");
        d.level = o.getInt("level");
        d.xp = o.getInt("xp");
        d.money = o.getDouble("money");
        d.lastSeen = o.optLong("lastSeen");
        d.tutorialDone = o.optBoolean("tutorialDone", d.shift > 1); // saves from before the tutorial existed
        JSONObject up = o.getJSONObject("upgrades");
        for (Iterator<String> it = up.keys(); it.hasNext(); ) {
            String k = it.next();
            d.upgrades.put(k, up.getInt(k));
        }
        JSONObject regs = o.getJSONObject("regulars");
        for (Iterator<String> it = regs.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject r = regs.getJSONObject(k);
            SaveData.Regular reg = new SaveData.Regular();
            reg.visits = r.getInt("visits");
            reg.loyalty = r.getInt("loyalty");
            reg.favorite = r.has("favorite") ? r.getString("favorite") : null;
            d.regulars.put(k, reg);
        }
        JSONObject j = o.getJSONObject("stats");
        SaveData.Stats s = d.stats;
        s.shifts = j.optInt("shifts"); s.customers = j.optInt("customers"); s.burgers = j.optInt("burgers");
        s.fries = j.optInt("fries"); s.drinks = j.optInt("drinks"); s.orders = j.optInt("orders");
        s.upgrades = j.optInt("upgrades"); s.bestStars = j.optInt("bestStars"); s.bestCustomers = j.optInt("bestCustomers");
        s.burnedPatties = j.optInt("burnedPatties"); s.perfectOrders = j.optInt("perfectOrders");
        s.money = j.optDouble("money", 0); s.tips = j.optDouble("tips", 0);
        s.satisfactionSum = j.optDouble("satisfactionSum", 0); s.accuracySum = j.optDouble("accuracySum", 0);
        s.bestScore = j.optDouble("bestScore", 0); s.bestMoney = j.optDouble("bestMoney", 0);
        s.offlineMoney = j.optDouble("offlineMoney", 0);
        return d;
    }

    private void loadSettings() {
        String text = read(settingsFile);
        if (text == null) return;
        try {
            JSONObject o = new JSONObject(text);
            settings.master = (float) o.optDouble("master", settings.master);
            settings.music = (float) o.optDouble("music", settings.music);
            settings.sfx = (float) o.optDouble("sfx", settings.sfx);
            settings.fullscreen = o.optBoolean("fullscreen", settings.fullscreen);
            settings.stretch = o.optBoolean("stretch", settings.stretch);
            settings.hints = o.optBoolean("hints", settings.hints);
            settings.difficulty = o.optInt("difficulty", settings.difficulty);
        } catch (JSONException e) {
            // keep defaults when the settings file is damaged
        }
    }

    public void saveSettings() {
        try {
            write(settingsFile, new JSONObject().put("master", settings.master).put("music", settings.music).put("sfx", settings.sfx)
                    .put("fullscreen", settings.fullscreen).put("stretch", settings.stretch).put("hints", settings.hints)
                    .put("difficulty", settings.difficulty).toString());
        } catch (JSONException e) {
            // values are always finite here
        }
    }
}

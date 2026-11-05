package com.c01m.burgerrun.sim;

/** Gameplay numbers derived from purchased upgrades. */
public final class Fx {
    public int grillSlots, baskets, boards, cabinet, trays, ticketLv, alarms, swingMs, snap, premium;
    public double grillRate, heatSlow, fryRate, goldenWiden, patience, satBonus, tipMult, payMult, cookTol, graceBonus;
    public boolean cups, plants, neon;
    public int greeter, runner, griller, fryer;
    public int makeover;

    public static Fx of(SaveData s) {
        Fx f = new Fx();
        f.grillSlots = new int[]{4, 6, 9}[s.up("grillCap")];
        f.grillRate = 1 + .15 * s.up("grillSpeed");
        f.heatSlow = new double[]{1, .7, .45}[s.up("heat")];
        f.alarms = s.up("timers");
        f.baskets = 1 + s.up("fryerCap");
        f.fryRate = 1 + .15 * s.up("fryerSpeed");
        f.goldenWiden = .03 * s.up("fryerSpeed");
        f.swingMs = new int[]{600, 390, 180}[s.up("swing")];
        f.snap = new int[]{0, 10, 20, 34}[s.up("hands")];
        f.boards = 1 + s.up("prep");
        f.cabinet = 4 + s.up("cabinet");
        f.ticketLv = s.up("tickets");
        f.trays = new int[]{2, 3, 4, 6}[s.up("trays")];
        f.patience = 1 + .08 * s.up("waiting") + .05 * s.up("decor");
        f.plants = s.up("decor") >= 1;
        f.neon = s.up("decor") >= 2;
        f.satBonus = 3 * s.up("seating");
        f.tipMult = 1 + .1 * s.up("tipjar");
        f.cookTol = .02 * s.up("cookware");
        f.makeover = s.up("makeover");
        f.payMult = 1 + .08 * f.makeover;
        f.premium = s.up("fridge");
        f.graceBonus = .1 * s.up("serving");
        f.cups = s.up("cups") >= 1;
        f.greeter = s.up("greeter");
        f.runner = s.up("runner");
        f.griller = s.up("griller");
        f.fryer = s.up("fryer");
        return f;
    }
}

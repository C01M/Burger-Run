package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.List;

/** Raw gameplay data recorded during a shift, read by PerformanceAnalyzer. */
public final class Metrics {
    public double timeAssembly, timeCooking, timeFridge;
    public int switches;
    public int burnedPatties, overcookedPatties, undercookedPatties, unevenPatties, pattiesTrashed;
    public int burnedFries, overFries, underFries, friesTrashed;
    public int wrongIngredients, missingIngredients, sequenceErrors, undos;
    public int wrongDrinks, wrongSizes, drinksTrashed;
    public int incompleteServes, cabinetFull, traysFull;
    public int peakCooking;
    public double grillIdle;          // burger orders open, nothing on the grill or in the cabinet
    public double fryerIdle;          // fries orders open, nothing frying or held
    public double missedDrinkTime;    // watching the kitchen with nothing urgent while drinks were owed
    public double overshootTime;      // seconds food spent past its best
    public double takeOrderDelaySum;
    public int ordersTaken;
    public final List<Double> checkGaps = new ArrayList<>();
    public final List<Double> waits = new ArrayList<>();
    public final List<Double> waitRatios = new ArrayList<>();
    public final List<Double> prepTimes = new ArrayList<>();
    public final List<Double> placements = new ArrayList<>();
    public final List<Double> sauces = new ArrayList<>();      // pour quality of each sauce served, 0..100
}

package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The end-of-shift coach. A local rule system: every rule reads recorded metrics, scores how much
 * the habit cost this shift, and quotes the real numbers. Always returns exactly three tips.
 */
public final class PerformanceAnalyzer {
    private PerformanceAnalyzer() {}

    private static final class Tip {
        final double weight;
        final String category, text;
        Tip(double weight, String category, String text) { this.weight = weight; this.category = category; this.text = text; }
    }

    private static double avg(List<Double> l) {
        double s = 0;
        for (double d : l) s += d;
        return l.isEmpty() ? 0 : s / l.size();
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.US, fmt, a); }

    public static List<String> tips(ShiftManager s) {
        Metrics m = s.metrics;
        int served = Math.max(1, s.served);
        double total = Math.max(1, s.time);
        List<Tip> t = new ArrayList<>();

        if (m.burnedPatties > 0)
            t.add(new Tip(40 + 18 * m.burnedPatties, "grill", f("You served %d burned patt%s, and each one costs a big chunk of satisfaction. Bin charred patties and check the grill between orders rather than waiting for the alarm.", m.burnedPatties, m.burnedPatties == 1 ? "y" : "ies")));
        if (m.overshootTime > 12)
            t.add(new Tip(14 + m.overshootTime * .9, "grill", f("Food sat past its best on the heat for %.0f seconds in total. Move finished food to the warmer as soon as it hits the target, because it stops cooking there.", m.overshootTime)));
        if (m.unevenPatties > 0)
            t.add(new Tip(24 + 12 * m.unevenPatties, "flip", f("%d patt%s came out uneven. Flip when the first gauge reaches the target so both sides cook for the same time.", m.unevenPatties, m.unevenPatties == 1 ? "y" : "ies")));
        if (m.undercookedPatties > 0)
            t.add(new Tip(18 + 10 * m.undercookedPatties, "doneness", f("%d burger%s came off the grill under the requested doneness. Read the doneness on the ticket before pulling the patty.", m.undercookedPatties, m.undercookedPatties == 1 ? "" : "s")));
        if (m.overcookedPatties > 0)
            t.add(new Tip(18 + 10 * m.overcookedPatties, "doneness", f("%d burger%s went past the requested doneness. Pull patties a touch earlier, especially for Lightly Cooked tickets.", m.overcookedPatties, m.overcookedPatties == 1 ? "" : "s")));
        if (m.burnedFries + m.overFries > 0)
            t.add(new Tip(26 + 14 * (m.burnedFries * 2 + m.overFries), "fries", f("%d portion%s of fries went out overdone. Lift the basket on the golden chime; fries stop cooking once out of the oil.", m.burnedFries + m.overFries, m.burnedFries + m.overFries == 1 ? "" : "s")));
        if (m.underFries > 0)
            t.add(new Tip(20 + 12 * m.underFries, "fries", f("%d portion%s of fries came out pale. Wait for the gauge to enter the gold band before lifting.", m.underFries, m.underFries == 1 ? "" : "s")));
        if (m.wrongIngredients + m.missingIngredients > 0)
            t.add(new Tip(26 + 9 * (m.wrongIngredients + m.missingIngredients), "ingredients", f("Burgers had %d unwanted and %d missing ingredient%s. Keep the selected ticket's list in view and work through it top to bottom.", m.wrongIngredients, m.missingIngredients, m.missingIngredients == 1 ? "" : "s")));
        if (m.sequenceErrors > 0)
            t.add(new Tip(14 + 6 * m.sequenceErrors, "ingredients", f("%d topping%s went on out of sequence. Tickets list toppings in the order they should be stacked, with sauces last.", m.sequenceErrors, m.sequenceErrors == 1 ? "" : "s")));
        double sauce = avg(m.sauces);
        if (!m.sauces.isEmpty() && sauce < 75)
            t.add(new Tip(14 + (75 - sauce) * .9, "sauce", f("Your sauces averaged %.0f%% for coverage. Hold the bottle over the burger and sweep from edge to edge at a steady pace so every part gets the same amount.", sauce)));
        double placement = avg(m.placements);
        if (!m.placements.isEmpty() && placement < 78)
            t.add(new Tip(12 + (78 - placement) * .8, "placement", f("Your toppings averaged only %.0f%% for centring. Drag them to the middle of the burger, since tapping scatters them a little.", placement)));
        if (m.wrongDrinks + m.wrongSizes > 0)
            t.add(new Tip(28 + 12 * (m.wrongDrinks + m.wrongSizes), "drinks", f("%d drink%s did not match the ticket. Check the name%s before you drag from the fridge.", m.wrongDrinks + m.wrongSizes, m.wrongDrinks + m.wrongSizes == 1 ? "" : "s", s.fx.cups ? " and cup size" : "")));
        if (m.missedDrinkTime > 6)
            t.add(new Tip(12 + m.missedDrinkTime * .9, "multitask", f("You spent %.0f seconds watching food that needed nothing while drinks were still owed. Pop back to the fridge while your patties cook.", m.missedDrinkTime)));
        if (m.grillIdle > total * .15)
            t.add(new Tip(12 + m.grillIdle / total * 60, "planning", f("For %.0f seconds, open tickets needed patties and none were cooking. Start a patty the moment you take a burger order.", m.grillIdle)));
        if (m.fryerIdle > total * .12)
            t.add(new Tip(10 + m.fryerIdle / total * 55, "planning", f("Fries orders waited %.0f seconds with nothing in the fryer. Drop a basket as soon as a fries ticket appears.", m.fryerIdle)));
        double perOrder = (double) m.switches / served;
        if (perOrder > 5)
            t.add(new Tip(8 + perOrder * 3, "switching", f("You switched stations %d times, about %.1f per customer. Stay at the grill long enough to start several patties in one trip.", m.switches, perOrder)));
        double gap = avg(m.checkGaps);
        if (gap > 22 && m.burnedPatties + m.overcookedPatties + m.burnedFries > 0)
            t.add(new Tip(12 + gap * .6, "grill", f("You left cooking food alone for %.0f seconds on average between kitchen visits. Aim to check back every 15 seconds or so.", gap)));
        if (m.ordersTaken > 0 && m.takeOrderDelaySum / m.ordersTaken > 8)
            t.add(new Tip(10 + m.takeOrderDelaySum / m.ordersTaken * 1.2, "orders", f("New customers waited %.0f seconds on average before you took their order. Tap new tickets right away, because their patience is already draining.", m.takeOrderDelaySum / m.ordersTaken)));
        double ratio = avg(m.waitRatios);
        if (ratio > .6)
            t.add(new Tip(16 + ratio * 34, "waiting", f("Customers used %.0f%% of their patience on average, waiting about %.0f seconds. Prepare common items ahead when tickets stack up.", ratio * 100, avg(m.waits))));
        if (m.incompleteServes > 0)
            t.add(new Tip(32 + 14 * m.incompleteServes, "serving", f("You served %d incomplete order%s, which is a heavy penalty. A slightly late full order nearly always scores better.", m.incompleteServes, m.incompleteServes == 1 ? "" : "s")));
        if (m.cabinetFull > 0)
            t.add(new Tip(10 + 6 * m.cabinetFull, "space", f("The warmer was full %d time%s. Assemble what's in there before cooking more, or buy a bigger warmer.", m.cabinetFull, m.cabinetFull == 1 ? "" : "s")));
        if (m.traysFull > 0)
            t.add(new Tip(10 + 6 * m.traysFull, "space", f("You ran out of trays %d time%s. Finish and serve one order before starting another, or buy more trays.", m.traysFull, m.traysFull == 1 ? "" : "s")));
        if (m.pattiesTrashed + m.friesTrashed > 1)
            t.add(new Tip(8 + 5 * (m.pattiesTrashed + m.friesTrashed), "waste", f("%d portions of food went in the bin. Match what you cook to the open tickets.", m.pattiesTrashed + m.friesTrashed)));
        if (m.undos > served)
            t.add(new Tip(6 + 3 * m.undos, "ingredients", f("You undid %d toppings. Pausing a moment on the ticket is quicker than rebuilding.", m.undos)));

        t.sort(Comparator.comparingDouble((Tip x) -> -x.weight));
        List<String> out = new ArrayList<>();
        List<String> usedCats = new ArrayList<>();
        for (Tip x : t) {
            if (out.size() == 3) break;
            if (usedCats.contains(x.category)) continue;
            out.add(x.text);
            usedCats.add(x.category);
        }
        // A clean shift still gets advice drawn from its numbers.
        if (out.size() < 3) out.add(f("Your peak was %d items on the heat at once. Try running one more alongside your assembly to push your pace.", m.peakCooking));
        if (out.size() < 3) out.add(f("Your average order took %.0f seconds from ticket to tray. Starting the slowest item first shaves off the most time.", avg(m.prepTimes)));
        if (out.size() < 3) out.add(f("You spent %.0f%% of the shift in the kitchen. Use that time to stack up patties during quiet moments.", 100 * m.timeCooking / total));
        return out;
    }
}

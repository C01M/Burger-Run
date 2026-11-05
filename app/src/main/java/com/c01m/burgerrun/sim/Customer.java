package com.c01m.burgerrun.sim;

/** A customer at the counter during a shift. */
public final class Customer {
    public static final int WAITING = 0, ORDERED = 1;

    public final int uid;
    public final Content.CustomerDef def;
    public final Food.Order order;
    public final Food.Tray tray = new Food.Tray();
    public final String greeting;
    public int status = WAITING;
    public int spot;
    public double waited, patience, takenAt = -1, enterAnim = 1;
    public double arrivedAt;
    public int loyalty;

    public Customer(int uid, Content.CustomerDef def, Food.Order order, String greeting, double patience) {
        this.uid = uid; this.def = def; this.order = order; this.greeting = greeting; this.patience = patience;
    }

    public double patienceLeft() { return Math.max(0, 1 - waited / patience); }

    public boolean complete() {
        return (!order.burger || tray.burger != null) && (!order.fries || tray.fries != null)
                && (order.drink == null || tray.drink != null);
    }
}

package com.c01m.burgerrun.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** All tunable game content. Colours are ARGB ints so this class stays free of Android imports. */
public final class Content {
    private Content() {}

    public static final class Ingredient {
        public final String id, name, code;
        public final int color, level;
        public final boolean sauce;
        Ingredient(String id, String name, String code, int color, int level, boolean sauce) {
            this.id = id; this.name = name; this.code = code; this.color = color; this.level = level; this.sauce = sauce;
        }
    }

    public static final List<Ingredient> VEG = Arrays.asList(
            new Ingredient("lettuce", "Lettuce", "LE", 0xFF6FBF3A, 1, false),
            new Ingredient("tomato", "Tomato", "TO", 0xFFE0442E, 1, false),
            new Ingredient("pickle", "Pickles", "PI", 0xFF7E9A2C, 2, false),
            new Ingredient("onion", "Onions", "ON", 0xFFB78AD0, 3, false),
            new Ingredient("olive", "Olives", "OL", 0xFF3D3A46, 5, false),
            new Ingredient("jalapeno", "Jalapeños", "JA", 0xFF2FA84F, 7, false),
            new Ingredient("avocado", "Avocado", "AV", 0xFFB6CF5A, 10, false),
            new Ingredient("mushroom", "Mushrooms", "MU", 0xFFB89A7C, 13, false));

    public static final List<Ingredient> SAUCE = Arrays.asList(
            new Ingredient("ketchup", "Ketchup", "K", 0xFFD42A1C, 1, true),
            new Ingredient("mustard", "Mustard", "M", 0xFFEEC21A, 1, true),
            new Ingredient("mayo", "Mayo", "Y", 0xFFF6EFCF, 2, true),
            new Ingredient("bbq", "Smoky BBQ", "B", 0xFF6E3517, 8, true),
            new Ingredient("hot", "Hot Sauce", "H", 0xFFF2641E, 12, true));

    public static Ingredient ingredient(String id) {
        for (Ingredient i : VEG) if (i.id.equals(id)) return i;
        for (Ingredient i : SAUCE) if (i.id.equals(id)) return i;
        return null;
    }

    public static final class Drink {
        public final String id, name;
        public final int color, label, level, fridge;
        public final double price;
        Drink(String id, String name, int color, int label, int level, double price, int fridge) {
            this.id = id; this.name = name; this.color = color; this.label = label;
            this.level = level; this.price = price; this.fridge = fridge;
        }
    }

    public static final List<Drink> DRINKS = Arrays.asList(
            new Drink("cola", "Cola", 0xFF54281A, 0xFFD8321F, 1, 2, 0),
            new Drink("water", "Water", 0xFFBFE6F5, 0xFF2E8FD0, 1, 2, 0),
            new Drink("lime", "Lemon-Lime", 0xFFD5F07B, 0xFF35A83F, 2, 2, 0),
            new Drink("orange", "Orange Soda", 0xFFF7931E, 0xFFE0620D, 4, 2.5, 0),
            new Drink("tea", "Iced Tea", 0xFFA9631F, 0xFF6D8A2A, 6, 2.5, 0),
            new Drink("lemonade", "Lemonade", 0xFFF7E463, 0xFFE6B800, 9, 3, 0),
            new Drink("rootbeer", "Root Beer", 0xFF3B1D0E, 0xFF8A5A2B, 1, 4, 1),
            new Drink("shake", "Berry Shake", 0xFFEC7FB4, 0xFFB02A78, 1, 5, 2));

    public static Drink drink(String id) {
        for (Drink d : DRINKS) if (d.id.equals(id)) return d;
        return null;
    }

    /** Customer personalities. Weight order: accuracy, cook, fry, placement, drink, wait. */
    public enum Arch {
        EASY("Easygoing", 1.35, 1.0, 0, new double[]{1, .6, .6, .4, .8, .6},
                "No rush, friend!", "Whenever you get to it.", "Lovely day for a burger."),
        IMPATIENT("Impatient", .68, 1.1, 0, new double[]{1, .8, .8, .4, .8, 2},
                "Make it quick!", "I've got a bus to catch.", "Tick tock, chef."),
        WEALTHY("Big Spender", .95, 2.2, 1, new double[]{1.3, 1.3, 1.2, 1, 1, 1},
                "Only your finest, please.", "Impress me and be rewarded.", "Quality is all I pay for."),
        GRILLER("Grill Snob", 1.0, 1.25, 0, new double[]{.8, 2.6, 1.6, .5, .6, .8},
                "Exact doneness or nothing.", "Watch that grill for me.", "I can taste ten seconds."),
        STICKLER("Stickler", 1.05, 1.2, 1, new double[]{2.6, .8, .7, 1.8, 1.8, .7},
                "Read the ticket twice.", "In that exact order.", "No substitutions."),
        BIG_EATER("Big Eater", 1.15, 1.3, 2, new double[]{1, 1, 1, .6, .8, .9},
                "I want ALL of it.", "Load it up!", "I skipped breakfast for this."),
        CRITIC("Food Critic", .85, 3.0, 2, new double[]{2, 2, 2, 2, 1.5, 1.5},
                "I will be taking notes.", "My column runs tomorrow.", "Show me what the fuss is about.");

        public final String label;
        public final double patience, tip;
        public final int complexity;
        public final double[] weights;
        public final String[] hello;
        Arch(String label, double patience, double tip, int complexity, double[] weights, String... hello) {
            this.label = label; this.patience = patience; this.tip = tip;
            this.complexity = complexity; this.weights = weights; this.hello = hello;
        }
    }

    public enum Hair { BUN, AFRO, LONG, SPIKE, BOB, BALD, CURLY, SIDE, PUFFS, MOHAWK }
    public enum Acc { NONE, GLASSES, EARRINGS, CAP, MOUSTACHE, TOPHAT, BOWTIE, BEARD, CROWN }

    public static final class CustomerDef {
        public final String id, name, drink;
        public final Arch arch;
        public final int level, skin, hairColor, shirt;
        public final Hair hair;
        public final Acc acc;
        public final String[] likes;
        CustomerDef(String id, String name, Arch arch, int level, String drink, int skin, Hair hair, int hairColor, int shirt, Acc acc, String... likes) {
            this.id = id; this.name = name; this.arch = arch; this.level = level; this.drink = drink;
            this.skin = skin; this.hair = hair; this.hairColor = hairColor; this.shirt = shirt; this.acc = acc; this.likes = likes;
        }
    }

    public static final List<CustomerDef> CUSTOMERS = Arrays.asList(
            new CustomerDef("marge", "Marge", Arch.EASY, 1, "water", 0xFFF2C9A1, Hair.BUN, 0xFFB8B8C0, 0xFF7AA6D8, Acc.GLASSES, "lettuce", "tomato"),
            new CustomerDef("theo", "Theo", Arch.EASY, 1, "cola", 0xFF8D5A3A, Hair.AFRO, 0xFF241A16, 0xFFE8A13A, Acc.NONE, "tomato", "pickle"),
            new CustomerDef("priya", "Priya", Arch.EASY, 1, "lime", 0xFFC68F62, Hair.LONG, 0xFF1D1518, 0xFFD65A8A, Acc.EARRINGS, "onion", "lettuce"),
            new CustomerDef("dash", "Dash", Arch.IMPATIENT, 1, "cola", 0xFFF0C49C, Hair.SPIKE, 0xFFD0702A, 0xFF3FB6A8, Acc.CAP, "pickle"),
            new CustomerDef("bea", "Bea", Arch.STICKLER, 1, "water", 0xFFE8B48C, Hair.BOB, 0xFF6A3A1E, 0xFF9A6AD0, Acc.GLASSES, "lettuce", "onion"),
            new CustomerDef("gus", "Gus", Arch.GRILLER, 2, "cola", 0xFFD7A27A, Hair.BALD, 0xFF3A2A20, 0xFFB0433A, Acc.MOUSTACHE, "onion", "pickle"),
            new CustomerDef("lola", "Lola", Arch.BIG_EATER, 2, "orange", 0xFFA56E48, Hair.CURLY, 0xFF3A1C12, 0xFFF2D13D, Acc.EARRINGS, "tomato", "olive", "lettuce"),
            new CustomerDef("sterling", "Mr. Sterling", Arch.WEALTHY, 3, "water", 0xFFEFC8A6, Hair.SIDE, 0xFFD9D9DE, 0xFF2A2F45, Acc.TOPHAT, "olive", "tomato"),
            new CustomerDef("nia", "Nia", Arch.IMPATIENT, 3, "lime", 0xFF6F4530, Hair.PUFFS, 0xFF17110F, 0xFFE4552D, Acc.NONE, "jalapeno", "tomato"),
            new CustomerDef("otto", "Otto", Arch.STICKLER, 4, "tea", 0xFFF4D2B4, Hair.SIDE, 0xFFCAA24A, 0xFF4C8C5C, Acc.BOWTIE, "pickle", "onion", "mushroom"),
            new CustomerDef("kiko", "Kiko", Arch.EASY, 4, "tea", 0xFFF0CFA8, Hair.BOB, 0xFF101018, 0xFF6AD0C8, Acc.NONE, "avocado", "lettuce"),
            new CustomerDef("rocco", "Rocco", Arch.GRILLER, 5, "cola", 0xFFCAA07A, Hair.SPIKE, 0xFF141414, 0xFF404A58, Acc.BEARD, "jalapeno", "onion"),
            new CustomerDef("duchess", "The Duchess", Arch.WEALTHY, 6, "lemonade", 0xFFF6D9C2, Hair.LONG, 0xFFA84A7A, 0xFF6D2A8A, Acc.CROWN, "olive", "avocado"),
            new CustomerDef("bruno", "Bruno", Arch.BIG_EATER, 6, "orange", 0xFF8A5C3E, Hair.BALD, 0xFF201510, 0xFF2F7AD0, Acc.BEARD, "onion", "mushroom", "pickle", "tomato"),
            new CustomerDef("zip", "Zip", Arch.IMPATIENT, 7, "lime", 0xFFE8BD96, Hair.MOHAWK, 0xFF22C0D8, 0xFF242424, Acc.EARRINGS, "jalapeno"),
            new CustomerDef("fern", "Prof. Fern", Arch.STICKLER, 8, "tea", 0xFFB98660, Hair.BUN, 0xFF57402E, 0xFF7F9A3E, Acc.GLASSES, "avocado", "olive", "lettuce"),
            new CustomerDef("hank", "Hank", Arch.GRILLER, 9, "cola", 0xFFE6B98E, Hair.SIDE, 0xFF7A4A24, 0xFFB5763A, Acc.CAP, "pickle", "jalapeno"),
            new CustomerDef("valentina", "Valentina", Arch.WEALTHY, 10, "lemonade", 0xFFD8A982, Hair.CURLY, 0xFF201014, 0xFFC21E4A, Acc.EARRINGS, "avocado", "mushroom", "tomato"),
            new CustomerDef("blanc", "Mme. Blanc", Arch.CRITIC, 10, "water", 0xFFF7E0CF, Hair.BOB, 0xFFF0F0F0, 0xFF17171F, Acc.GLASSES, "olive", "avocado", "mushroom"),
            new CustomerDef("tiny", "Tiny", Arch.BIG_EATER, 11, "orange", 0xFFC88F66, Hair.SIDE, 0xFF3A2415, 0xFF55B04E, Acc.BEARD, "mushroom", "onion", "olive", "tomato", "lettuce"),
            new CustomerDef("ember", "Ember", Arch.IMPATIENT, 12, "orange", 0xFFF2C6A0, Hair.LONG, 0xFFD0361E, 0xFFF08A24, Acc.NONE, "jalapeno", "onion"),
            new CustomerDef("ansel", "A. Ansel", Arch.CRITIC, 14, "tea", 0xFFA87A58, Hair.SIDE, 0xFF101010, 0xFF513A73, Acc.BOWTIE, "mushroom", "jalapeno"));

    public static CustomerDef customer(String id) {
        for (CustomerDef c : CUSTOMERS) if (c.id.equals(id)) return c;
        return null;
    }

    public enum Special {
        LUNCH("Lunch Rush", "⏰", "Office workers flood in: 40% more customers, arriving fast. Pay +25%.", 1.4, .65, 1.25, 1, 0, 0, false, 0),
        WEALTHY("High Rollers Day", "💎", "Big spenders fill the room. Larger tips, higher standards.", 1, 1, 1.1, 1.5, .6, 0, false, 0),
        WEEKEND("Weekend Crush", "🎉", "Families order full meals. More items per order, tips +20%.", 1.2, .85, 1.1, 1.2, 0, 0, true, 0),
        EVENT("Street Festival", "🎪", "A festival next door! Everyone wants extra toppings. Pay +30%.", 1.15, .9, 1.3, 1, 0, 1, false, 0),
        CRITICS("Critics' Table", "🖋️", "Food critics in town. Huge tips, zero forgiveness.", 1, 1.1, 1.2, 1.3, .65, 0, false, 10);

        public final String title, icon, desc;
        public final double countMult, intervalMult, payMult, tipMult, archChance;
        public final int complexity, minLevel;
        public final boolean multiItem;
        Special(String title, String icon, String desc, double countMult, double intervalMult, double payMult, double tipMult,
                double archChance, int complexity, boolean multiItem, int minLevel) {
            this.title = title; this.icon = icon; this.desc = desc; this.countMult = countMult; this.intervalMult = intervalMult;
            this.payMult = payMult; this.tipMult = tipMult; this.archChance = archChance; this.complexity = complexity;
            this.multiItem = multiItem; this.minLevel = minLevel;
        }
    }

    public static final class UpgradeDef {
        public final String id, cat, icon, name, desc;
        public final int level;
        public final int[] prices;
        public final String[] levels;
        UpgradeDef(String id, String cat, String icon, String name, int level, String desc, int[] prices, String... levels) {
            this.id = id; this.cat = cat; this.icon = icon; this.name = name; this.level = level;
            this.desc = desc; this.prices = prices; this.levels = levels;
        }
    }

    public static final String[][] UPGRADE_CATS = {
            {"cooking", "Cooking"}, {"efficiency", "Efficiency"}, {"customer", "Customers"},
            {"restaurant", "Restaurant"}, {"staff", "Staff"}};

    private static int[] p(int... v) { return v; }

    /** Every upgrade has a real gameplay effect, applied in Fx. */
    public static final List<UpgradeDef> UPGRADES = new ArrayList<>(Arrays.asList(
            new UpgradeDef("grillCap", "cooking", "🍔", "Extended Grill", 1, "Cook more patties at once.", p(70, 240), "6 grill spots", "9 grill spots"),
            new UpgradeDef("grillSpeed", "cooking", "♨️", "Turbo Burners", 1, "The grill cooks faster.", p(90, 220, 480), "+15% grill speed", "+30% grill speed", "+45% grill speed"),
            new UpgradeDef("heat", "cooking", "🌡️", "Heat Control", 3, "Patties burn more slowly past well done.", p(110, 300), "Burning 30% slower", "Burning 55% slower"),
            new UpgradeDef("timers", "cooking", "⏲️", "Timers & Alarms", 1, "See kitchen problems from any station.", p(60, 200), "On-screen food alarms", "Gauges mark the ticket's target"),
            new UpgradeDef("fryerCap", "cooking", "🍟", "Extra Fry Basket", 2, "Fry more portions at once.", p(100, 320), "2 baskets", "3 baskets"),
            new UpgradeDef("fryerSpeed", "cooking", "🛢️", "Improved Fryer", 2, "Faster fries that stay golden longer.", p(80, 260), "+15% speed, wider golden zone", "+30% speed, widest golden zone"),

            new UpgradeDef("swing", "efficiency", "🚪", "Swing Door Hinges", 1, "Move between stations faster.", p(50, 170), "Switching 35% faster", "Switching 70% faster"),
            new UpgradeDef("hands", "efficiency", "🧤", "Steady Gloves", 1, "Faster, neater assembly: toppings snap to the centre.", p(60, 180, 380), "Small snap zone", "Medium snap zone", "Large snap zone"),
            new UpgradeDef("prep", "efficiency", "🪵", "Extra Prep Board", 3, "Build several burgers side by side.", p(120, 340), "2 prep boards", "3 prep boards"),
            new UpgradeDef("cabinet", "efficiency", "🗄️", "Bigger Warmer", 1, "Keep more cooked food warm.", p(70, 200), "5 warmer slots", "6 warmer slots"),
            new UpgradeDef("tickets", "efficiency", "🧾", "Ticket Organiser", 2, "A smarter order rail.", p(90, 260), "Tickets sort by urgency", "Highlights next ingredient"),
            new UpgradeDef("trays", "efficiency", "🍽️", "Serving Trays", 1, "Work on more orders at the same time.", p(60, 180, 420), "3 trays", "4 trays", "6 trays"),

            new UpgradeDef("waiting", "customer", "🛋️", "Comfy Waiting Area", 1, "Customers stay patient for longer.", p(80, 220, 500), "+8% patience", "+16% patience", "+24% patience"),
            new UpgradeDef("seating", "customer", "🪑", "Better Seating", 3, "Comfortable guests rate every order higher.", p(130, 340), "+3 satisfaction", "+6 satisfaction"),
            new UpgradeDef("decor", "customer", "🪴", "Decorations", 4, "Plants and neon in the dining room lift the mood.", p(100, 280), "Plants: +5% patience", "Neon: +10% patience"),
            new UpgradeDef("tipjar", "customer", "🫙", "Golden Tip Jar", 2, "Encourages larger tips.", p(120, 300, 650), "+10% tips", "+20% tips", "+30% tips"),

            new UpgradeDef("cookware", "restaurant", "🍳", "Pro Cookware", 4, "Better equipment widens the perfect doneness zone.", p(150, 420), "Wider perfect zone", "Widest perfect zone"),
            new UpgradeDef("makeover", "restaurant", "🎨", "Restaurant Makeover", 5, "A nicer dining room commands better prices.", p(200, 600), "New paint: +8% pay", "Remodel: +16% pay"),
            new UpgradeDef("fridge", "restaurant", "🧊", "New Drink Fridge", 4, "Stocks premium drinks customers pay more for.", p(140, 380), "Adds Root Beer", "Adds Berry Shake"),
            new UpgradeDef("serving", "restaurant", "🛎️", "Serving Equipment", 3, "Heat lamps forgive longer waits.", p(110, 300), "Longer wait grace", "Longest wait grace"),
            new UpgradeDef("cups", "restaurant", "🥤", "Cup Dispenser", 6, "Adds S/M/L drinks. Large pays more; size must match.", p(180), "Unlocks cup sizes"),

            new UpgradeDef("greeter", "staff", "👋", "Gia the Greeter", 2, "Automatically takes new customers' orders.", p(150), "Takes orders for you"),
            new UpgradeDef("runner", "staff", "🏃", "Sam the Soda Runner", 2, "Automatically puts the right drink on trays.", p(180, 420), "Drinks in 8 seconds", "Drinks in 3 seconds"),
            new UpgradeDef("griller", "staff", "👨‍🍳", "Hal the Grill Hand", 3, "Watches your patties. You still put them on.", p(250, 600), "Flips at the target", "Also moves them to the warmer"),
            new UpgradeDef("fryer", "staff", "👩‍🍳", "Faye the Fry Hand", 3, "Watches your fries. You still load the basket.", p(220, 520), "Lifts when golden", "Also boxes them into the warmer"),
            new UpgradeDef("night", "staff", "🌙", "Night Crew", 2, "Earns money while the app is closed.", p(200, 500, 1200), "Up to 4 hours offline", "Up to 8 hours, +50% rate", "Up to 12 hours, +100% rate")));

    public static UpgradeDef upgrade(String id) {
        for (UpgradeDef u : UPGRADES) if (u.id.equals(id)) return u;
        return null;
    }

    public static final Object[][] RANKS = {
            {1, "Fresh Apron"}, {3, "Bun Toaster"}, {5, "Patty Flipper"}, {8, "Line Cook"}, {11, "Grill Hand"},
            {14, "Shift Lead"}, {17, "Sous Chef"}, {20, "Head Chef"}, {24, "Burger Baron"}, {28, "Legend of the Run"}};
    public static final int MAX_LEVEL = 30;
}

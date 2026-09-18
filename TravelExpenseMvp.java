import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;


public class TravelExpenseMvp {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            new ConsoleApp(new InputReader(scanner)).run();
        }
    }
}


final class Range {
    private static final Locale IN = Locale.forLanguageTag("en-IN");
    final double min, max;

    Range(double min, double max) {
        this.min = Math.max(0, min);
        this.max = Math.max(this.min, max);
    }

    static String fmt(double v) { return String.format(IN, "Rs. %,.0f", v); }
    static Range around(double point, double spread) { return new Range(point * (1 - spread), point * (1 + spread)); }

    double mid() { return (min + max) / 2.0; }
    Range plus(Range o) { return new Range(min + o.min, max + o.max); }
    Range scale(double f) { return new Range(min * f, max * f); }

    @Override public String toString() { return fmt(min) + " - " + fmt(max); }
}

enum Category {
    TRANSPORT("Transport"), ACCOMMODATION("Accommodation"), FOOD("Food"),
    ACTIVITIES("Activities"), LOCAL_TRANSPORT("Local transport");

    final String label;
    Category(String label) { this.label = label; }
}

enum TransportMode {
    BUS("Bus", 1.9, 45, false, 150, "bus stand"),
    TRAIN("Train", 1.3, 70, false, 200, "railway station"),
    CAR("Car / cab (shared by the group)", 11.0, 55, true, 500, "pickup point"),
    FLIGHT("Flight", 6.0, 650, false, 1200, "airport");

    final String label, terminal;
    final double ratePerKm, speedKmph, overhead;
    final boolean perVehicle; 
    TransportMode(String label, double ratePerKm, double speedKmph, boolean perVehicle,
                  double overhead, String terminal) {
        this.label = label; this.ratePerKm = ratePerKm; this.speedKmph = speedKmph;
        this.perVehicle = perVehicle; this.overhead = overhead; this.terminal = terminal;
    }

    double costPerPerson(double km, int travellers) {
        double total = ratePerKm * km + overhead;
        return perVehicle ? total / Math.max(1, travellers) : total;
    }

    double hours(double km) { return km / speedKmph + (this == FLIGHT ? 3.0 : 0.75); }
}

enum BudgetTier {
    LOW("Low", 0.80, 350, 750, 250, 130,
            "hostels and lodges, street food, buses and shared autos, mostly free sights"),
    MEDIUM("Medium", 1.00, 800, 1900, 650, 300,
            "3-star hotels, sit-down restaurants, app cabs, ticketed attractions"),
    HIGH("High", 1.65, 1800, 4500, 1600, 750,
            "4-star and above, fine dining, private cabs, guided experiences");

    final String label, blurb;
    final double transportFactor, foodPerDay, stayPerNight, activityPerDay, localPerDay;

    BudgetTier(String label, double transportFactor, double foodPerDay, double stayPerNight,
               double activityPerDay, double localPerDay, String blurb) {
        this.label = label; this.transportFactor = transportFactor; this.foodPerDay = foodPerDay;
        this.stayPerNight = stayPerNight; this.activityPerDay = activityPerDay;
        this.localPerDay = localPerDay; this.blurb = blurb;
    }

    
    BudgetTier cheaper() { return this == LOW ? LOW : values()[ordinal() - 1]; }
}

final class Trip {
    String origin, destination;
    TransportMode mode;
    double distanceKm;
    int travellers, nights;
    BudgetTier tier;

    int days() { return nights + 1; }
}


final class Routes {
    private static final Map<String, Double> KM = new LinkedHashMap<>();

    static {
        put("Delhi", "Jaipur", 280);  put("Delhi", "Manali", 535);
        put("Delhi", "Agra", 233);    put("Mumbai", "Goa", 590);
        put("Bhopal", "Indore", 195); put("Bhopal", "Goa", 1220);
        put("Bhopal", "Delhi", 780);  put("Bengaluru", "Goa", 560);
    }

    private Routes() { }
    private static void put(String a, String b, double km) { KM.put(key(a, b), km); }

    private static String key(String a, String b) {
        String x = a.trim().toLowerCase(Locale.ROOT), y = b.trim().toLowerCase(Locale.ROOT);
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    /** Returns -1 when the pair is unknown. */
    static double lookup(String from, String to) {
        Double km = KM.get(key(from, to));
        return km == null ? -1 : km;
    }
}

final class BudgetEngine {
    private static final double BUFFER = 0.12;
    private BudgetEngine() { }
    /** Per-person point estimate for the whole one-way trip at a given tier. */
    static double perPersonPoint(Trip trip, BudgetTier tier) {
        int days = trip.days();
        double transport = trip.mode.costPerPerson(trip.distanceKm, trip.travellers) * tier.transportFactor;
        int transitMeals = (int) Math.max(1, Math.round(trip.mode.hours(trip.distanceKm) / 6.0));
        double food = tier.foodPerDay * days + transitMeals * tier.foodPerDay / 3.0;
        double stay = tier.stayPerNight * trip.nights;
        double activities = tier.activityPerDay * days;
        double local = tier.localPerDay * (days + 0.8);
        return (transport + food + stay + activities + local) * (1 + BUFFER);
    }

    static Range perPersonRange(Trip t, BudgetTier tier) { return Range.around(perPersonPoint(t, tier), 0.15); }

    /** Whichever band the actual per-person spend landed closest to. */
    static BudgetTier closestTier(Trip trip, double perPerson) {
        BudgetTier best = BudgetTier.LOW;
        double bestGap = Double.MAX_VALUE;
        for (BudgetTier t : BudgetTier.values()) {
            double gap = Math.abs(perPersonPoint(trip, t) - perPerson);
            if (gap < bestGap) { bestGap = gap; best = t; }
        }
        return best;
    }
}

final class Segment {
    final String title, description;
    final String city; // whose venues to suggest here; null while in motion
    final Map<Category, Range> lines = new EnumMap<>(Category.class); // per person

    Segment(String title, String description, String city) {
        this.title = title; this.description = description; this.city = city;
    }
    void add(Category c, Range r) { lines.merge(c, r, Range::plus); }

    Range perPersonTotal() {
        Range total = new Range(0, 0);
        for (Range r : lines.values()) total = total.plus(r);
        return total;
    }

    Range groupRange(Category c, int travellers) {
        Range r = lines.get(c);
        return r == null ? new Range(0, 0) : r.scale(travellers);
    }

    Range groupTotal(int travellers) { return perPersonTotal().scale(travellers); }
}

final class SegmentPlanner {
    private SegmentPlanner() { }

    static List<Segment> plan(Trip trip) {
        List<Segment> out = new ArrayList<>();
        BudgetTier t = trip.tier;
        int n = 1;

        Segment pre = new Segment("Segment " + n++ + ": Pre-departure in " + trip.origin,
                "Packing and the local hop to the " + trip.mode.terminal + ".", trip.origin);
        pre.add(Category.LOCAL_TRANSPORT, Range.around(t.localPerDay * 0.6, 0.25));
        pre.add(Category.FOOD, Range.around(t.foodPerDay * 0.25, 0.30));
        out.add(pre);

        double transportPP = trip.mode.costPerPerson(trip.distanceKm, trip.travellers) * t.transportFactor;
        double hours = trip.mode.hours(trip.distanceKm);
        int meals = (int) Math.max(1, Math.round(hours / 6.0));
        Segment leg = new Segment(
                "Segment " + n++ + ": " + trip.origin + " -> " + trip.destination + " by " + trip.mode.label,
                String.format(Locale.ROOT, "About %.0f km, roughly %.1f hours, about %d meal stop(s).",
                        trip.distanceKm, hours, meals), null);
        leg.add(Category.TRANSPORT, Range.around(transportPP, 0.18));
        leg.add(Category.FOOD, Range.around(meals * t.foodPerDay / 3.0, 0.25));
        out.add(leg);

        Segment arrival = new Segment("Segment " + n++ + ": Arrival day in " + trip.destination,
                "Transfer from the " + trip.mode.terminal + ", check-in and an easy first evening.",
                trip.destination);
        arrival.add(Category.LOCAL_TRANSPORT, Range.around(t.localPerDay * 0.9, 0.25));
        arrival.add(Category.FOOD, Range.around(t.foodPerDay * 0.6, 0.20));
        arrival.add(Category.ACTIVITIES, Range.around(t.activityPerDay * 0.4, 0.35));
        if (trip.nights >= 1) arrival.add(Category.ACCOMMODATION, Range.around(t.stayPerNight, 0.20));
        out.add(arrival);

        for (int day = 2; day <= trip.nights; day++) {
            Segment full = new Segment("Segment " + n++ + ": Day " + day + " in " + trip.destination,
                    "A full sightseeing day plus one more night of stay.", trip.destination);
            full.add(Category.FOOD, Range.around(t.foodPerDay, 0.20));
            full.add(Category.ACTIVITIES, Range.around(t.activityPerDay, 0.30));
            full.add(Category.LOCAL_TRANSPORT, Range.around(t.localPerDay, 0.25));
            full.add(Category.ACCOMMODATION, Range.around(t.stayPerNight, 0.20));
            out.add(full);
        }

        Segment wrap = new Segment("Segment " + n + ": Final day in " + trip.destination,
                "Check-out, a last stop or two and shopping.", trip.destination);
        wrap.add(Category.FOOD, Range.around(t.foodPerDay * 0.7, 0.20));
        wrap.add(Category.ACTIVITIES, Range.around(t.activityPerDay * 0.5, 0.35));
        wrap.add(Category.LOCAL_TRANSPORT, Range.around(t.localPerDay * 0.8, 0.25));
        out.add(wrap);
        return out;
    }
}

final class Suggestion {
    final String name, note;
    final Category category;
    final BudgetTier tier;
    final double perPerson;

    Suggestion(String name, Category category, BudgetTier tier, double perPerson, String note) {
        this.name = name; this.category = category; this.tier = tier;
        this.perPerson = perPerson; this.note = note;
    }
    @Override public String toString() {
        return String.format("%-36s ~%s pp  (%s)", name, Range.fmt(perPerson), note);
    }
}


final class SuggestionCatalog {
    private static final String ANY = "*";
    private static final Map<String, List<Suggestion>> BY_CITY = new LinkedHashMap<>();

    private SuggestionCatalog() { }

    private static void add(String city, String name, Category c, BudgetTier t, double cost, String note) {
        BY_CITY.computeIfAbsent(city.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(new Suggestion(name, c, t, cost, note));
    }

    static {
        add(ANY, "Local thali / street-food lane", Category.FOOD, BudgetTier.LOW, 150, "cheap and filling");
        add(ANY, "Mid-range multi-cuisine restaurant", Category.FOOD, BudgetTier.MEDIUM, 450, "sit-down dinner");
        add(ANY, "Popular cafe with regional dishes", Category.FOOD, BudgetTier.MEDIUM, 380, "good for breakfast");
        add(ANY, "Rooftop fine-dining restaurant", Category.FOOD, BudgetTier.HIGH, 1500, "book ahead");
        add(ANY, "Hostel dorm bed", Category.ACCOMMODATION, BudgetTier.LOW, 600, "shared room");
        add(ANY, "Budget lodge near the centre", Category.ACCOMMODATION, BudgetTier.LOW, 900, "basic, walkable");
        add(ANY, "3-star hotel or guesthouse", Category.ACCOMMODATION, BudgetTier.MEDIUM, 1900, "breakfast included");
        add(ANY, "4-star city hotel", Category.ACCOMMODATION, BudgetTier.HIGH, 5000, "central location");
        add(ANY, "Free heritage walking trail", Category.ACTIVITIES, BudgetTier.LOW, 0, "download a map");
        add(ANY, "City museum, general ticket", Category.ACTIVITIES, BudgetTier.LOW, 100, "half a morning");
        add(ANY, "Guided half-day city tour", Category.ACTIVITIES, BudgetTier.MEDIUM, 700, "covers the main sights");
        add(ANY, "Private guide for a full day", Category.ACTIVITIES, BudgetTier.HIGH, 3000, "car and guide");
        add(ANY, "City bus / shared auto day pass", Category.LOCAL_TRANSPORT, BudgetTier.LOW, 80, "cheapest option");
        add(ANY, "Metered auto with a few app rides", Category.LOCAL_TRANSPORT, BudgetTier.MEDIUM, 300, "flexible");
        add(ANY, "Private cab on call all day", Category.LOCAL_TRANSPORT, BudgetTier.HIGH, 1200, "door to door");

        // City-specific entries are offered before the generic ones.
        add("Goa", "Beach shack fish thali", Category.FOOD, BudgetTier.LOW, 220, "Palolem side");
        add("Goa", "Beachfront seafood grill", Category.FOOD, BudgetTier.MEDIUM, 700, "sunset seating");
    }

    static List<Suggestion> forTier(String city, Category c, BudgetTier tier, int limit) {
        List<Suggestion> picks = collect(city, c, tier);
        for (Suggestion s : collect(ANY, c, tier)) {
            if (picks.size() >= limit) break;
            picks.add(s);
        }
        picks.sort((a, b) -> Double.compare(a.perPerson, b.perPerson));
        return picks.size() > limit ? new ArrayList<>(picks.subList(0, limit)) : picks;
    }

    /** Cheaper alternatives, taken from the tier below the chosen one. */
    static List<Suggestion> cheaperThan(String city, Category c, BudgetTier t, int n) { return forTier(city, c, t.cheaper(), n); }

    private static List<Suggestion> collect(String city, Category c, BudgetTier tier) {
        List<Suggestion> out = new ArrayList<>();
        List<Suggestion> all = BY_CITY.get(city.trim().toLowerCase(Locale.ROOT));
        if (all != null) {
            for (Suggestion s : all) if (s.category == c && s.tier == tier) out.add(s);
        }
        return out;
    }
}

enum AlertLevel { OVER, TRENDING_HIGH, ON_TRACK }

final class Alert {
    final Category category;
    final AlertLevel level;
    final String message;
    Alert(Category c, AlertLevel l, String m) { category = c; level = l; message = m; }
}

final class ExpenseTracker {
    private final Trip trip;
    private final List<Segment> segments;
    private final List<Map<Category, Double>> actuals = new ArrayList<>(); // group spend

    ExpenseTracker(Trip trip, List<Segment> segments) {
        this.trip = trip;
        this.segments = segments;
        for (int i = 0; i < segments.size(); i++) actuals.add(new EnumMap<>(Category.class));
    }

    void record(int seg, Category c, double groupAmount) { actuals.get(seg).merge(c, groupAmount, Double::sum); }

    double segmentActual(int segment) {
        double sum = 0;
        for (double v : actuals.get(segment).values()) sum += v;
        return sum;
    }

    /** Category-by-category verdict for one finished segment. */
    List<Alert> review(int index, boolean hasNext) {
        List<Alert> alerts = new ArrayList<>();
        Segment segment = segments.get(index);
        String tail = hasNext
                ? "Consider the following lower-cost options for the next segment."
                : "Worth noting: this is where the trip drifted from the plan.";

        for (Category c : segment.lines.keySet()) {
            Range planned = segment.groupRange(c, trip.travellers);
            double spent = actuals.get(index).getOrDefault(c, 0.0);
            String name = c.label.toLowerCase(Locale.ROOT);

            if (spent > planned.max) {
                double over = spent - planned.max;
                alerts.add(new Alert(c, AlertLevel.OVER, String.format(Locale.ROOT,
                        "You are spending above your %s-budget plan in the %s category: %s against a planned "
                                + "%s (%s over, +%.0f%%). %s",
                        trip.tier.label, name, Range.fmt(spent), planned, Range.fmt(over),
                        over / planned.max * 100, tail)));
            } else if (spent > planned.mid() * 1.05) {
                alerts.add(new Alert(c, AlertLevel.TRENDING_HIGH, String.format(
                        "You are spending slightly above your %s-budget plan in the %s category: %s against a "
                                + "planned %s. Still inside the band, but keep an eye on it.",
                        trip.tier.label, name, Range.fmt(spent), planned)));
            } else {
                alerts.add(new Alert(c, AlertLevel.ON_TRACK, String.format(
                        "On track on %s: %s against a planned %s.", name, Range.fmt(spent), planned)));
            }
        }
        return alerts;
    }

    Range plannedGroupTotal() {
        Range total = new Range(0, 0);
        for (Segment s : segments) total = total.plus(s.groupTotal(trip.travellers));
        return total;
    }

    double actualGroupTotal() {
        double sum = 0;
        for (int i = 0; i < segments.size(); i++) sum += segmentActual(i);
        return sum;
    }
}

final class InputReader {
    private final Scanner scanner;
    InputReader(Scanner scanner) { this.scanner = scanner; }

    String optional(String prompt) {
        System.out.print(prompt);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    String line(String prompt) {
        while (true) {
            String s = optional(prompt);
            if (!s.isEmpty()) return s;
            System.out.println("  Please type something.");
        }
    }

    int integer(String prompt, int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(line(prompt));
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) { /* reprompt */ }
            System.out.println("  Enter a whole number between " + min + " and " + max + ".");
        }
    }

    double decimal(String prompt, double min, double max) {
        while (true) {
            try {
                double v = Double.parseDouble(line(prompt));
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) { /* reprompt */ }
            System.out.println("  Enter a number between " + min + " and " + max + ".");
        }
    }

    
    double decimalOrDefault(String prompt, double fallback) {
        while (true) {
            String s = optional(prompt);
            if (s.isEmpty()) return fallback;
            try {
                double v = Double.parseDouble(s);
                if (v >= 0) return v;
            } catch (NumberFormatException ignored) { /* reprompt */ }
            System.out.println("  Enter a non-negative number, or press Enter to skip.");
        }
    }

    <T> T choose(String heading, List<T> options, Function<T, String> label) {
        System.out.println(heading);
        for (int i = 0; i < options.size(); i++) System.out.printf("  [%d] %s%n", i + 1, label.apply(options.get(i)));
        return options.get(integer("  Your choice: ", 1, options.size()) - 1);
    }
}

final class ConsoleApp {
    private static final String RULE = "----------------------------------------------------------------------";
    private final InputReader in;

    ConsoleApp(InputReader in) { this.in = in; }

    void run() {
        System.out.println(RULE + "\n  ONE-WAY TRAVEL GUIDE AND EXPENSE MANAGER\n" + RULE);
        Trip trip = new Trip();

        section("STEP 1  -  Route and transport");
        trip.origin = in.line("Starting point: ");
        trip.destination = in.line("Destination   : ");
        trip.mode = in.choose("\nHow are you travelling?", Arrays.asList(TransportMode.values()), m -> m.label);

        double known = Routes.lookup(trip.origin, trip.destination);
        if (known > 0) {
            trip.distanceKm = known;
            System.out.printf("%nStored distance: %.0f km.%n", known);
        } else {
            trip.distanceKm = in.decimal("\nApproximate distance in km: ", 1, 20000);
        }

        section("STEP 2  -  Travellers and budget band");
        trip.travellers = in.integer("Number of travellers: ", 1, 40);
        trip.nights = in.integer("Nights at the destination (0 for a same-day trip): ", 0, 60);
        System.out.printf("%n%s -> %s, %.0f km by %s, about %.1f hours.%n%d traveller(s), %d night(s), %d day(s).%n%n",
                trip.origin, trip.destination, trip.distanceKm, trip.mode.label,
                trip.mode.hours(trip.distanceKm), trip.travellers, trip.nights, trip.days());

        System.out.println("Per-person budget bands for this trip:");
        for (BudgetTier tier : BudgetTier.values()) {
            Range pp = BudgetEngine.perPersonRange(trip, tier);
            System.out.printf("  %-7s %-26s group total %s%n    %s%n",
                    tier.label, pp, pp.scale(trip.travellers), tier.blurb);
        }
        trip.tier = in.choose("\nPick the band you want to plan against:", Arrays.asList(BudgetTier.values()),
                t -> t.label + "  (" + BudgetEngine.perPersonRange(trip, t) + " per person)");

        List<Segment> segments = SegmentPlanner.plan(trip);
        section("STEP 3  -  Segment plan for a " + trip.tier.label + " budget");
        printPlan(trip, segments);

        section("STEP 4  -  Expense tracking");
        ExpenseTracker tracker = new ExpenseTracker(trip, segments);
        track(trip, segments, tracker);

        section("STEP 5  -  Summary");
        printSummary(trip, segments, tracker);
    }

    private void section(String title) { System.out.println("\n" + RULE + "\n  " + title + "\n" + RULE); }

    private void printPlan(Trip trip, List<Segment> segments) {
        Range perPerson = new Range(0, 0);
        for (Segment segment : segments) {
            perPerson = perPerson.plus(segment.perPersonTotal());
            System.out.println("\n" + segment.title + "\n  " + segment.description);
            for (Map.Entry<Category, Range> e : segment.lines.entrySet()) {
                System.out.printf("  %-18s %-26s group %s%n",
                        e.getKey().label, e.getValue(), e.getValue().scale(trip.travellers));
            }
            System.out.printf("  %-18s %-26s group %s%n", "SEGMENT TOTAL",
                    segment.perPersonTotal(), segment.groupTotal(trip.travellers));
            printSuggestions(trip, segment);
        }
        System.out.printf("%n%s%n  PLANNED TOTAL  per person %s | group %s%n%s%n",
                RULE, perPerson, perPerson.scale(trip.travellers), RULE);
    }

    private void printSuggestions(Trip trip, Segment segment) {
        if (segment.city == null) return;
        boolean heading = false;
        for (Category c : Arrays.asList(Category.ACCOMMODATION, Category.FOOD,
                Category.ACTIVITIES, Category.LOCAL_TRANSPORT)) {
            if (!segment.lines.containsKey(c)) continue;
            List<Suggestion> picks = SuggestionCatalog.forTier(segment.city, c, trip.tier, 2);
            if (picks.isEmpty()) continue;
            if (!heading) {
                System.out.println("  Suggested for a " + trip.tier.label + " budget:");
                heading = true;
            }
            System.out.println("    " + c.label + ":");
            for (Suggestion s : picks) System.out.println("      - " + s);
        }
    }

    private void track(Trip trip, List<Segment> segments, ExpenseTracker tracker) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            boolean hasNext = i < segments.size() - 1;
            System.out.println("\n" + RULE + "\n  NOW TRACKING -> " + segment.title + "\n" + RULE);

            for (Category c : segment.lines.keySet()) {
                Range planned = segment.groupRange(c, trip.travellers);
                tracker.record(i, c, in.decimalOrDefault(String.format(
                        "  %s - planned %s. Actual group spend (Enter = %s): ",
                        c.label, planned, Range.fmt(planned.mid())), planned.mid()));
            }
            System.out.printf("%n  Segment actual: %s against a planned %s%n%n  Tracker feedback:%n",
                    Range.fmt(tracker.segmentActual(i)), segment.groupTotal(trip.travellers));

            List<Category> flagged = new ArrayList<>();
            for (Alert alert : tracker.review(i, hasNext)) {
                System.out.println("    [" + alert.level + "] " + alert.message);
                if (alert.level == AlertLevel.OVER) flagged.add(alert.category);
            }

            if (hasNext && !flagged.isEmpty()) {
                String city = segments.get(i + 1).city;
                System.out.println("\n  Cheaper options for the next segment:");
                for (Category c : flagged) {
                    List<Suggestion> cheaper = SuggestionCatalog.cheaperThan(
                            city == null ? trip.destination : city, c, trip.tier, 3);
                    if (cheaper.isEmpty()) continue;
                    System.out.println("    " + c.label + ":");
                    for (Suggestion s : cheaper) System.out.println("      - " + s);
                }
                in.optional("\n  Press Enter for the next segment...");
            }
        }
    }

    private void printSummary(Trip trip, List<Segment> segments, ExpenseTracker tracker) {
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            double actual = tracker.segmentActual(i);
            Range planned = s.groupTotal(trip.travellers);
            System.out.printf("  %-44s %-26s %s%s%n",
                    s.title.length() > 44 ? s.title.substring(0, 41) + "..." : s.title,
                    planned, Range.fmt(actual), actual > planned.max ? "  <-- over" : "");
        }

        Range planned = tracker.plannedGroupTotal();
        double actual = tracker.actualGroupTotal(), perPerson = actual / trip.travellers;
        System.out.println(RULE);
        System.out.printf("PLANNED (group) : %s%nACTUAL  (group) : %s   |   per person %s%n",
                planned, Range.fmt(actual), Range.fmt(perPerson));
        if (actual > planned.max) {
            System.out.printf("You finished %s above the %s band. A %s plan would have matched your actual "
                            + "spending more closely.%n", Range.fmt(actual - planned.max), trip.tier.label,
                    BudgetEngine.closestTier(trip, perPerson).label);
        } else if (actual < planned.min) {
            System.out.printf("You finished %s under the band; a %s plan would still have fit.%n",
                    Range.fmt(planned.min - actual), trip.tier.cheaper().label);
        } else {
            System.out.println("You stayed inside your chosen band for the trip overall.");
        }
        System.out.println(RULE);
    }
}

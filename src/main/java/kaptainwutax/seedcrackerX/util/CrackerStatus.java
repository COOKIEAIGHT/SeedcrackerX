package kaptainwutax.seedcrackerX.util;

import com.seedfinding.mcfeature.Feature;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.cracker.decorator.Decorator;
import kaptainwutax.seedcrackerX.cracker.storage.DataStorage;
import kaptainwutax.seedcrackerX.cracker.storage.TimeMachine;

import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * one place that works out "where is the cracker at", used by the
 * on-screen panel, the menu and the /seedcracker status command so they all agree.
 */
public final class CrackerStatus {

    public static final double LIFT_BITS_NEEDED = 40.0;
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.US);

    private CrackerStatus() {
    }

    public record Snapshot(TimeMachine.Status status, double liftingBits, int structureCount, String structureSummary,
                           boolean hasHashedSeed, int structureSeeds, int checkDone, int checkTotal, long foundSeed,
                           long liftingStartedAt) {

        /** short one-line "what's happening" */
        public String stage() {
            return switch (status) {
                case FOUND -> "Seed found!";
                case CHECKING -> "Step 3/3: Checking seeds (" + percent(checkDone, checkTotal) + "%)";
                case LIFTING -> "Step 2/3: Working out possible seeds";
                case NO_MATCH -> "No seed matched";
                default -> structureSeeds > 0 ? "Waiting for more info" : "Step 1/3: Finding structures";
            };
        }

        /** what the player should do next */
        public String nextStep() {
            return switch (status) {
                case FOUND -> "Click the seed in chat to copy it, or use /seedcracker seed";
                case CHECKING -> "Keep playing, it's checking in the background";
                case LIFTING -> "Keep playing, this takes a minute or two";
                case NO_MATCH -> "A structure was probably wrong. Use /seedcracker clear and find 5 new ones";
                default -> {
                    if (structureSeeds > 0) {
                        yield hasHashedSeed ? "Find 1 more structure to narrow it down" : "Rejoin the server so it can grab the hashed seed";
                    }
                    yield "Explore and find shipwrecks, temples, igloos or witch huts";
                }
            };
        }

        public String possibleSeeds() {
            return switch (status) {
                case FOUND -> "1";
                case CHECKING -> NF.format(Math.max(1, checkTotal - checkDone));
                default -> structureSeeds > 0 ? NF.format(structureSeeds) : "~" + bigNumber(estimateSeeds(liftingBits));
            };
        }

        public String bitsText() {
            return (int) liftingBits + " / " + (int) LIFT_BITS_NEEDED;
        }

        public double bitsFraction() {
            return Math.min(1.0, liftingBits / LIFT_BITS_NEEDED);
        }
    }

    public static Snapshot snapshot() {
        SeedCracker sc = SeedCracker.get();
        if (sc == null) {
            return new Snapshot(TimeMachine.Status.COLLECTING, 0, 0, "", false, 0, 0, 0, 0, 0);
        }
        DataStorage data = sc.getDataStorage();
        TimeMachine tm = data.getTimeMachine();
        double bits = 0;
        int count = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        try {
            bits = data.getLiftingBits();
            for (DataStorage.Entry<Feature.Data<?>> e : data.baseSeedData) {
                if (e.data.feature instanceof Decorator) continue;
                counts.merge(prettyName(e.data.feature.getName()), 1, Integer::sum);
                count++;
            }
        } catch (Exception ignored) {
            // data changed while reading, the next refresh will get it
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((k, v) -> summary.append(summary.length() > 0 ? ", " : "")
                .append(v).append(" ").append(k).append(v > 1 && !k.endsWith("s") ? "s" : ""));
        boolean hashed = data.hashedSeedData != null && data.hashedSeedData.getHashedSeed() != 0;
        int seeds = 0;
        try {
            seeds = tm.structureSeeds.size();
        } catch (Exception ignored) {
        }
        return new Snapshot(tm.status, bits, count, summary.toString(), hashed, seeds, tm.checkDone, tm.checkTotal,
                tm.foundSeed, tm.liftingStartedAt);
    }

    public static String prettyName(String name) {
        return switch (name) {
            case "desert_pyramid" -> "desert temple";
            case "jungle_pyramid" -> "jungle temple";
            case "swamp_hut" -> "witch hut";
            case "pillager_outpost" -> "outpost";
            case "buried_treasure" -> "treasure";
            case "end_city" -> "end city";
            case "trial_chambers" -> "trial chamber";
            default -> name.replace('_', ' ');
        };
    }

    /** rough guess of how many structure seeds are left, tuned so it lines up with what lifting really finds */
    public static double estimateSeeds(double bits) {
        return Math.max(1, Math.pow(2, 48 - 0.75 * bits));
    }

    public static String bigNumber(double v) {
        if (v >= 1e12) return String.format(Locale.US, "%.0f trillion", v / 1e12);
        if (v >= 1e9) return String.format(Locale.US, "%.1f billion", v / 1e9);
        if (v >= 1e6) return String.format(Locale.US, "%.1f million", v / 1e6);
        return NF.format((long) v);
    }

    public static String format(long v) {
        return NF.format(v);
    }

    private static int percent(int done, int total) {
        return total <= 0 ? 0 : (int) (100L * done / total);
    }
}

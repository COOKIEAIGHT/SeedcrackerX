package kaptainwutax.seedcrackerX;

import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.Feature;
import com.seedfinding.mcfeature.decorator.DesertWell;
import com.seedfinding.mcfeature.decorator.EndGateway;
import com.seedfinding.mcfeature.structure.BuriedTreasure;
import com.seedfinding.mcfeature.structure.DesertPyramid;
import com.seedfinding.mcfeature.structure.EndCity;
import com.seedfinding.mcfeature.structure.Igloo;
import com.seedfinding.mcfeature.structure.JunglePyramid;
import com.seedfinding.mcfeature.structure.Monument;
import com.seedfinding.mcfeature.structure.PillagerOutpost;
import com.seedfinding.mcfeature.structure.RegionStructure;
import com.seedfinding.mcfeature.structure.Shipwreck;
import com.seedfinding.mcfeature.structure.SwampHut;
import kaptainwutax.seedcrackerX.cracker.decorator.DeepDungeon;
import kaptainwutax.seedcrackerX.cracker.decorator.Dungeon;
import kaptainwutax.seedcrackerX.cracker.decorator.EmeraldOre;
import kaptainwutax.seedcrackerX.cracker.decorator.WarpedFungus;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.structures.TrialChambers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class Features {
    public static final ArrayList<RegionStructure<?, ?>> STRUCTURE_TYPES = new ArrayList<>();

    public static BuriedTreasure BURIED_TREASURE;
    public static DesertPyramid DESERT_PYRAMID;
    public static EndCity END_CITY;
    public static JunglePyramid JUNGLE_PYRAMID;
    public static Monument MONUMENT;
    public static Shipwreck SHIPWRECK;
    public static SwampHut SWAMP_HUT;
    public static PillagerOutpost PILLAGER_OUTPOST;
    public static Igloo IGLOO;
    public static TrialChambers TRIAL_CHAMBERS;

    public static EndGateway END_GATEWAY;
    public static DesertWell DESERT_WELL;
    public static EmeraldOre EMERALD_ORE;
    public static Dungeon DUNGEON;
    public static DeepDungeon DEEP_DUNGEON;
    public static WarpedFungus WARPED_FUNGUS;

    /**
     * Finders whose feature doesn't exist in the selected version (e.g. trial chambers before 1.21).
     * They're skipped while scanning. The player's own on/off toggles are left alone, so switching
     * back to a newer version brings them back.
     */
    public static final Set<Finder.Type> UNAVAILABLE = EnumSet.noneOf(Finder.Type.class);

    public static boolean isAvailable(Finder.Type type) {
        return !UNAVAILABLE.contains(type);
    }

    public static void init(MCVersion version) {
        STRUCTURE_TYPES.clear();
        UNAVAILABLE.clear();

        BURIED_TREASURE = safe(STRUCTURE_TYPES, version, Finder.Type.BURIED_TREASURE, BuriedTreasure::new);
        DESERT_PYRAMID = safe(STRUCTURE_TYPES, version, Finder.Type.DESERT_TEMPLE, DesertPyramid::new);
        END_CITY = safe(STRUCTURE_TYPES, version, Finder.Type.END_CITY, EndCity::new);
        JUNGLE_PYRAMID = safe(STRUCTURE_TYPES, version, Finder.Type.JUNGLE_TEMPLE, JunglePyramid::new);
        MONUMENT = safe(STRUCTURE_TYPES, version, Finder.Type.MONUMENT, Monument::new);
        SHIPWRECK = safe(STRUCTURE_TYPES, version, Finder.Type.SHIPWRECK, Shipwreck::new);
        SWAMP_HUT = safe(STRUCTURE_TYPES, version, Finder.Type.SWAMP_HUT, SwampHut::new);
        PILLAGER_OUTPOST = safe(STRUCTURE_TYPES, version, Finder.Type.PILLAGER_OUTPOST, PillagerOutpost::new);
        IGLOO = safe(STRUCTURE_TYPES, version, Finder.Type.IGLOO, Igloo::new);
        TRIAL_CHAMBERS = safe(STRUCTURE_TYPES, version, Finder.Type.TRIAL_CHAMBERS, TrialChambers::new);

        END_GATEWAY = safe(Finder.Type.END_GATEWAY, version, EndGateway::new);
        DESERT_WELL = safe(Finder.Type.DESERT_WELL, version, DesertWell::new);
        EMERALD_ORE = safe(Finder.Type.EMERALD_ORE, version, EmeraldOre::new);
        DUNGEON = safe(Finder.Type.DUNGEON, version, Dungeon::new);
        DEEP_DUNGEON = safe(Finder.Type.DUNGEON, version, DeepDungeon::new, false); // deep dungeons only exist from 1.18, normal dungeons still work
        WARPED_FUNGUS = safe(Finder.Type.WARPED_FUNGUS, version, WarpedFungus::new);

        STRUCTURE_TYPES.trimToSize();
    }

    private static <F extends Feature<?, ?>> F safe(Finder.Type finderType, MCVersion version, Function<MCVersion, F> lambda) {
        return safe(finderType, version, lambda, true);
    }

    private static <F extends Feature<?, ?>> F safe(Finder.Type finderType, MCVersion version, Function<MCVersion, F> lambda, boolean markUnavailable) {
        try {
            return lambda.apply(version);
        } catch (Throwable t) {
            if (markUnavailable && UNAVAILABLE.add(finderType)) {
                SeedCracker.LOGGER.warn("Skipping {} because it doesn't exist in version {}", finderType.nameKey, version);
            }
            try {
                return lambda.apply(MCVersion.latest());
            } catch (Throwable w) {
                SeedCracker.LOGGER.error("Exception thrown loading feature,", t);
            }
        }
        return null;
    }

    private static <F extends RegionStructure<?, ?>> F safe(List<RegionStructure<?, ?>> list, MCVersion version, Finder.Type finderType, Function<MCVersion, F> lambda) {
        F initializedFeature = safe(finderType, version, lambda);
        if (initializedFeature != null) list.add(initializedFeature);

        return initializedFeature;
    }

}

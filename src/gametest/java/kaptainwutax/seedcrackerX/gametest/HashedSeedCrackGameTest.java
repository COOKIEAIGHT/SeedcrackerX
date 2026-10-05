package kaptainwutax.seedcrackerX.gametest;

import com.seedfinding.mcfeature.structure.RegionStructure;
import kaptainwutax.seedcrackerX.Features;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.cracker.storage.TimeMachine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end test on a real 26.3 dedicated server:
 *  1. join, feed the mod 6 structure positions worked out by the server itself
 *  2. it should lift to more than 1000 structure seeds, then check them all against the hashed seed and find the world seed
 *  3. disconnect (saves structures), rejoin, /seedcracker data restore: it should find the seed again
 */
public class HashedSeedCrackGameTest implements FabricClientGameTest {

    private static final int TIMEOUT = 20 * 60 * 15; // 15 minutes of ticks

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("SKIP_CRACK_TEST") != null) {
            log("skipping crack test");
            return;
        }
        try (TestDedicatedServerContext server = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(s -> s.setSeed("123456789"))
                .createServer()) {

            long seed = server.computeOnServer(s -> s.overworld().getSeed());
            log("world seed on server = " + seed);

            List<Object[]> spots = server.computeOnServer(s -> {
                var sets = s.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
                List<Object[]> out = new ArrayList<>();
                Object[][] wanted = {
                        {"shipwrecks", "shipwreck", 0, 0}, {"shipwrecks", "shipwreck", 1, -1},
                        {"desert_pyramids", "desert_pyramid", -1, 0}, {"swamp_huts", "swamp_hut", 0, 1},
                        {"igloos", "igloo", 1, 1}, {"jungle_temples", "jungle_pyramid", -1, -1}};
                for (Object[] w : wanted) {
                    StructureSet set = sets.getValueOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace((String) w[0])));
                    RandomSpreadStructurePlacement p = (RandomSpreadStructurePlacement) set.placement();
                    ChunkPos c = p.getPotentialStructureChunk(seed, (int) w[2] * p.spacing(), (int) w[3] * p.spacing());
                    out.add(new Object[]{w[1], c.x(), c.z()});
                }
                return out;
            });

            // ---------- session 1 ----------
            try (TestDedicatedServerConnection conn = server.connect()) {
                conn.waitForChunksRender();
                context.waitTicks(40);
                for (Object[] spot : spots) {
                    log("adding " + spot[0] + " at chunk " + spot[1] + "," + spot[2]);
                    context.runOnClient(mc -> SeedCracker.get().getDataStorage().addBaseData(feature((String) spot[0]).at((int) spot[1], (int) spot[2]), DataAddedEvent.POKE_LIFTING));
                    context.waitTicks(20);
                }
                waitFound(context, "session 1");
                int structureSeeds = tm().structureSeeds.size();
                log("structure seeds checked against the hashed seed: " + structureSeeds);
                if (structureSeeds <= 1000) log("WARNING: only " + structureSeeds + " seeds, the no-limit path was not exercised");
                check(seed);
                context.takeScreenshot("crack_found");
            }

            // ---------- session 2: restore ----------
            try (TestDedicatedServerConnection conn = server.connect()) {
                conn.waitForChunksRender();
                context.waitTicks(20);
                context.runOnClient(mc -> mc.getConnection().sendCommand("seedcracker data restore"));
                waitFound(context, "session 2 (data restore)");
                check(seed);
            }
            log("ALL GOOD");
        }
    }

    private static void waitFound(ClientGameTestContext context, String what) {
        long start = System.currentTimeMillis();
        context.waitFor(mc -> tm().worldSeeds.size() == 1, TIMEOUT);
        log("found (" + what + ") after " + (System.currentTimeMillis() - start) / 1000 + "s");
    }

    private static void check(long seed) {
        long found = tm().worldSeeds.iterator().next();
        log("FOUND " + found + " expected " + seed);
        if (found != seed) throw new AssertionError("wrong seed: " + found + " != " + seed);
    }

    private static TimeMachine tm() {
        return SeedCracker.get().getDataStorage().getTimeMachine();
    }

    private static RegionStructure<?, ?> feature(String name) {
        return switch (name) {
            case "shipwreck" -> Features.SHIPWRECK;
            case "desert_pyramid" -> Features.DESERT_PYRAMID;
            case "swamp_hut" -> Features.SWAMP_HUT;
            case "igloo" -> Features.IGLOO;
            case "jungle_pyramid" -> Features.JUNGLE_PYRAMID;
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static void log(String s) {
        System.out.println("[CRACK-TEST] " + s);
    }
}

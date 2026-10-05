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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end test on a real 26.3 dedicated server:
 *  1. join, feed the mod 5 real structure spots worked out by the 26.3 server itself
 *  2. it should lift, then check ALL structure seeds against the hashed seed (no 1000 limit) and find the seed
 *  3. disconnect (saves structures), rejoin: it should reload them by itself and find the seed again
 */
public class HashedSeedCrackGameTest implements FabricClientGameTest {

    private static final long TIMEOUT = 20L * 60 * 15; // 15 minutes of ticks

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
                context.takeScreenshot("crack_1_start");

                // ---- commands ----
                command(context, "seedcracker");
                context.takeScreenshot("ui_1_overview");
                command(context, "seedcracker help");
                context.takeScreenshot("ui_2_help");
                command(context, "seedcracker render");
                command(context, "seedcracker cracker debug off");
                command(context, "seedcracker seed");
                context.takeScreenshot("ui_3_render_debug_seed");

                // ---- menu via the J hotkey ----
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_J);
                context.waitFor(mc -> mc.gui.screen() != null, 100);
                context.waitTicks(10);
                log("menu opened with J: " + context.computeOnClient(mc -> mc.gui.screen() == null ? "NO" : mc.gui.screen().getClass().getSimpleName()));
                context.takeScreenshot("ui_4_menu_status");
                for (String tab : new String[]{"What to look for", "Display", "Advanced"}) {
                    context.setScreen(() -> new kaptainwutax.seedcrackerX.config.ConfigScreen().getConfigScreenByCloth(null, tab));
                    context.waitTicks(10);
                    context.takeScreenshot("ui_5_menu_" + tab.replace(' ', '_'));
                }
                context.setScreen(() -> null);
                context.waitTicks(10);

                for (int i = 0; i < spots.size(); i++) {
                    Object[] spot = spots.get(i);
                    log("adding " + spot[0] + " at chunk " + spot[1] + "," + spot[2]);
                    context.runOnClient(mc -> SeedCracker.get().getDataStorage().addBaseData(feature((String) spot[0]).at((int) spot[1], (int) spot[2]), DataAddedEvent.POKE_LIFTING));
                    context.waitTicks(20);
                    if (i == 2) context.takeScreenshot("crack_2_collecting");
                }

                waitStatus(context, TimeMachine.Status.CHECKING, "checking");
                context.waitTicks(100);
                context.takeScreenshot("crack_3_checking");
                log("possible seeds being checked: " + tm().checkTotal);

                waitStatus(context, TimeMachine.Status.FOUND, "found (session 1)");
                context.waitTicks(20);
                context.takeScreenshot("crack_4_found");
                command(context, "seedcracker seed");
                context.takeScreenshot("ui_6_seed_command");
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_J);
                context.waitFor(mc -> mc.gui.screen() != null, 100);
                context.waitTicks(10);
                context.takeScreenshot("ui_7_menu_found");
                context.setScreen(() -> null);
                context.waitTicks(5);
                long found = tm().foundSeed;
                log("FOUND " + found + " expected " + seed);
                if (found != seed) throw new AssertionError("wrong seed: " + found + " != " + seed);
                if (tm().checkTotal <= 1000) log("WARNING: only " + tm().checkTotal + " seeds, the no-limit path was not exercised");
            }

            Path file = FabricLoader.getInstance().getGameDir().resolve("seedcracker-found-seed.txt");
            log("found-seed file: " + (Files.exists(file) ? readQuiet(file).trim() : "MISSING"));
            if (!Files.exists(file)) throw new AssertionError("found seed file missing");

            // ---------- session 2: auto restore ----------
            try (TestDedicatedServerConnection conn = server.connect()) {
                conn.waitForChunksRender();
                waitStatus(context, TimeMachine.Status.FOUND, "found (session 2, auto-restore)");
                context.waitTicks(20);
                context.takeScreenshot("crack_5_found_after_rejoin");
                command(context, "seedcracker status");
                context.takeScreenshot("ui_8_status_after_rejoin");
                if (tm().foundSeed != seed) throw new AssertionError("wrong seed after rejoin");
                log("auto-restore OK");
            }
            log("ALL GOOD");
        }
    }

    private static void waitStatus(ClientGameTestContext context, TimeMachine.Status want, String what) {
        long start = System.currentTimeMillis();
        context.waitFor(mc -> tm().status == want || (want != TimeMachine.Status.NO_MATCH && tm().status == TimeMachine.Status.NO_MATCH), (int) TIMEOUT);
        if (tm().status != want && !(want == TimeMachine.Status.CHECKING && tm().status == TimeMachine.Status.FOUND)) {
            throw new AssertionError("expected " + want + " but was " + tm().status);
        }
        log(what + " after " + (System.currentTimeMillis() - start) / 1000 + "s");
    }

    private static void command(ClientGameTestContext context, String cmd) {
        log("running /" + cmd);
        context.runOnClient(mc -> mc.getConnection().sendCommand(cmd));
        context.waitTicks(15);
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

    private static String readQuiet(Path p) {
        try { return Files.readString(p); } catch (Exception e) { return "?"; }
    }

    private static void log(String s) {
        System.out.println("[CRACK-TEST] " + s);
    }
}

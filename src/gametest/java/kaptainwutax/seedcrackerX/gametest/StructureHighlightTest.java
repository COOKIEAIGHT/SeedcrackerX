package kaptainwutax.seedcrackerX.gametest;

import com.mojang.datafixers.util.Pair;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.finder.FinderQueue;
import kaptainwutax.seedcrackerX.render.Cuboid;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Visits one of every structure type in a real 26.3 world and checks whether SeedCracker
 * detects it and has an outline to draw for it. Also checks the version selector and that
 * the mod lets go of the old level after a dimension change.
 */
public class StructureHighlightTest implements FabricClientGameTest {

    record Case(String structureId, Finder.Type type, ResourceKey<Level> dim) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("SKIP_HIGHLIGHT_TEST") != null) {
            log("skipping highlight test");
            return;
        }
        List<Case> cases = List.of(
                new Case("desert_pyramid", Finder.Type.DESERT_TEMPLE, Level.OVERWORLD),
                new Case("jungle_pyramid", Finder.Type.JUNGLE_TEMPLE, Level.OVERWORLD),
                new Case("swamp_hut", Finder.Type.SWAMP_HUT, Level.OVERWORLD),
                new Case("igloo", Finder.Type.IGLOO, Level.OVERWORLD),
                new Case("shipwreck", Finder.Type.SHIPWRECK, Level.OVERWORLD),
                new Case("shipwreck_beached", Finder.Type.SHIPWRECK, Level.OVERWORLD),
                new Case("monument", Finder.Type.MONUMENT, Level.OVERWORLD),
                new Case("pillager_outpost", Finder.Type.PILLAGER_OUTPOST, Level.OVERWORLD),
                new Case("trial_chambers", Finder.Type.TRIAL_CHAMBERS, Level.OVERWORLD),
                new Case("buried_treasure", Finder.Type.BURIED_TREASURE, Level.OVERWORLD),
                new Case("end_city", Finder.Type.END_CITY, Level.END));

        List<String> report = new ArrayList<>();
        try (TestSingleplayerContext sp = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(s -> s.setSeed("123456789"))
                .create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("gamemode spectator @a");

            // ---- version selector ----
            List<String> versionReport = new ArrayList<>();
            context.runOnClient(mc -> {
                var versionNode = net.fabricmc.fabric.api.client.command.v2.ClientCommands.getActiveDispatcher().getRoot().getChild("seedcracker").getChild("version");
                List<String> literals = versionNode.getChildren().stream().map(n -> n.getName()).toList();
                versionReport.add("version choices: " + literals.size() + ", newest " + literals.get(0) + ", oldest " + literals.get(literals.size() - 1));
                for (String want : new String[]{"26.3", "26.2", "26.1", "1.21.11", "1.21.4", "1.21.3", "1.16.5", "1.8"}) {
                    if (!literals.contains(want)) versionReport.add("MISSING version " + want);
                }
            });
            for (String[] pick : new String[][]{{"1.16.5", "1.16.5"}, {"26.2", "1.21.3"}, {"26.3", "1.21.3"}}) {
                context.runOnClient(mc -> mc.getConnection().sendCommand("seedcracker version " + pick[0]));
                context.waitTicks(10);
                String got = context.computeOnClient(mc -> kaptainwutax.seedcrackerX.config.Config.get().getServerVersion() + " -> rules " + kaptainwutax.seedcrackerX.config.Config.get().getVersion().name);
                versionReport.add("picked " + pick[0] + ": " + got + (got.endsWith("rules " + pick[1]) ? "  OK" : "  WRONG"));
                String avail = context.computeOnClient(mc -> "trial chambers " + (kaptainwutax.seedcrackerX.Features.isAvailable(Finder.Type.TRIAL_CHAMBERS) ? "available" : "skipped")
                        + " (toggle " + (Finder.Type.TRIAL_CHAMBERS.enabled.get() ? "on" : "OFF") + "), dungeons "
                        + (kaptainwutax.seedcrackerX.Features.isAvailable(Finder.Type.DUNGEON) && Finder.Type.DUNGEON.enabled.get() ? "available" : "NOT available"));
                versionReport.add("    " + avail);
            }
            context.runOnClient(mc -> mc.getConnection().sendCommand("seedcracker version"));
            context.waitTicks(10);
            context.takeScreenshot("version_command");
            context.setScreen(() -> new kaptainwutax.seedcrackerX.config.ConfigScreen().getConfigScreenByCloth(null));
            context.waitTicks(10);
            context.takeScreenshot("version_menu");
            context.setScreen(() -> null);
            versionReport.forEach(StructureHighlightTest::log);

            java.lang.ref.WeakReference<?> overworld = null;
            String only = System.getenv("HIGHLIGHT_ONLY");
            for (Case c : cases) {
                if (only != null && !java.util.Arrays.asList(only.split(",")).contains(c.structureId())) continue;
                if (c.dim() == Level.END && overworld == null) {
                    // remember the overworld so we can check the mod lets go of it after leaving
                    overworld = new java.lang.ref.WeakReference<>(context.computeOnClient(mc -> mc.level));
                }
                String result;
                try {
                    result = check(context, sp, c);
                } catch (Throwable t) {
                    result = "ERROR " + t;
                }
                log(c.structureId() + ": " + result);
                report.add(String.format("%-18s %s", c.structureId(), result));
            }

            // ---- revisit: a structure it already knows should still be outlined, and only once ----
            String again = check(context, sp, cases.get(0));
            int sets = context.computeOnClient(mc -> FinderQueue.get().finderControl.getActiveFinders(Finder.Type.DESERT_TEMPLE).size());
            report.add(String.format("%-18s %s (outline sets for desert temples now: %d)", "desert REVISIT", again, sets));

            if (overworld != null) {
                Object old = overworld.get();
                int refsOld = old == null ? 0 : modRefsTo(old);
                old = null;
                report.add("LEAK CHECK: references the mod still holds to the old overworld: " + refsOld);
            }
        }
        log("===== STRUCTURE HIGHLIGHT REPORT =====");
        report.forEach(StructureHighlightTest::log);
    }

    private static String check(ClientGameTestContext context, TestSingleplayerContext sp, Case c) {
        BlockPos pos = sp.getServer().computeOnServer(server -> {
            ServerLevel level = server.getLevel(c.dim());
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var holder = registry.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(c.structureId())));
            BlockPos origin = c.dim() == Level.END ? new BlockPos(1200, 0, 0) : BlockPos.ZERO;
            Pair<BlockPos, ?> r = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), origin, 100, false);
            return r == null ? null : r.getFirst();
        });
        if (pos == null) return "SKIP (none found nearby)";

        if (c.type() == Finder.Type.PILLAGER_OUTPOST) {
            String biome = sp.getServer().computeOnServer(server -> server.getLevel(c.dim()).getBiome(pos.atY(70)).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
            log("outpost biome: " + biome);
        }
        String dim = c.dim() == Level.END ? "minecraft:the_end" : "minecraft:overworld";
        sp.getServer().runCommand("execute in " + dim + " run tp @a " + pos.getX() + " 140 " + pos.getZ() + " 0 90");
        context.waitFor(mc -> mc.player != null && Math.abs(mc.player.getX() - pos.getX()) < 8 && Math.abs(mc.player.getZ() - pos.getZ()) < 8, 200);
        sp.getConnection().waitForChunksRender();
        context.waitTicks(20);

        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        try {
            context.waitFor(mc -> found(c.type(), cx, cz), 300);
        } catch (AssertionError timedOut) {
            // not found in time, reported below
        }
        boolean detected = context.computeOnClient(mc -> found(c.type(), cx, cz));
        boolean renders = context.computeOnClient(mc -> FinderQueue.get().finderControl.getActiveFinders(c.type()).stream()
                .anyMatch(f -> near(f, cx, cz) && f.shouldRender()));
        context.runOnClient(mc -> {
            log("  player at " + mc.player.blockPosition().toShortString() + " in " + mc.level.dimension().identifier());
            for (Finder f : FinderQueue.get().finderControl.getActiveFinders(c.type())) {
                if (!near(f, cx, cz)) continue;
                log("  finder " + f.getClass().getSimpleName() + " centers=" + f.outlineKey() + " current=" + f.isFromCurrentWorld() + " render=" + f.shouldRender());
            }
            if (c.type() == Finder.Type.PILLAGER_OUTPOST) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    var b = mc.level.getNoiseBiome(((cx + dx) << 2) + 2, 64, ((cz + dz) << 2) + 2).value();
                    var sb = kaptainwutax.seedcrackerX.util.BiomeFixer.swap(b);
                    log("  chunk+" + dx + "," + dz + " biome(y256)=" + sb.getName() + " validForOutpost=" + kaptainwutax.seedcrackerX.Features.PILLAGER_OUTPOST.isValidBiome(sb));
                }
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("highlight_" + c.structureId());
        return (detected ? "DETECTED" : "MISSED") + (detected ? (renders ? ", outline drawn" : ", NOT drawn") : "") + "  at " + pos.toShortString();
    }


    /** count references to target reachable from SeedCracker's own objects (not the rest of the game) */
    private static int modRefsTo(Object target) {
        java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
        java.util.ArrayDeque<Object> queue = new java.util.ArrayDeque<>();
        queue.add(kaptainwutax.seedcrackerX.SeedCracker.get());
        queue.add(FinderQueue.get());
        int refs = 0;
        while (!queue.isEmpty() && seen.size() < 2_000_000) {
            Object o = queue.poll();
            if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
            if (o == target) { refs++; continue; }
            if (o instanceof net.minecraft.world.level.Level || o instanceof net.minecraft.client.Minecraft || o instanceof Class<?>) continue;
            try {
                if (o instanceof java.util.Map<?, ?> m) { for (var e : m.entrySet()) { queue.add(e.getKey()); queue.add(e.getValue()); } continue; }
                if (o instanceof Iterable<?> it) { for (Object x : it) queue.add(x); continue; }
                if (o instanceof Object[] arr) { for (Object x : arr) queue.add(x); continue; }
            } catch (Exception ignored) { continue; }
            String cn = o.getClass().getName();
            if (!cn.startsWith("kaptainwutax") && !cn.startsWith("com.seedfinding")) continue;
            for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (var f : k.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    try { f.setAccessible(true); queue.add(f.get(o)); } catch (Exception ignored) { }
                }
            }
        }
        return refs;
    }

    private static void gc(ClientGameTestContext context) {
        for (int i = 0; i < 5; i++) {
            context.runOnClient(mc -> System.gc());
            context.waitTicks(20);
        }
    }

    private static boolean found(Finder.Type type, int cx, int cz) {
        return FinderQueue.get().finderControl.getActiveFinders(type).stream().anyMatch(f -> near(f, cx, cz));
    }

    private static boolean near(Finder f, int cx, int cz) {
        for (Cuboid cuboid : f.getCuboids()) {
            BlockPos p = cuboid.getCenterPos();
            if (Math.abs((p.getX() >> 4) - cx) <= 4 && Math.abs((p.getZ() >> 4) - cz) <= 4) return true;
        }
        return false;
    }

    private static void log(String s) {
        System.out.println("[HIGHLIGHT-TEST] " + s);
    }
}

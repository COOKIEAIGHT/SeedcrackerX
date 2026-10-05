package kaptainwutax.seedcrackerX.cracker.storage;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.rand.seed.PillarSeed;
import com.seedfinding.mccore.rand.seed.StructureSeed;
import com.seedfinding.mccore.rand.seed.WorldSeed;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.Feature;
import com.seedfinding.mcfeature.structure.OldStructure;
import com.seedfinding.mcfeature.structure.PillagerOutpost;
import com.seedfinding.mcfeature.structure.Shipwreck;
import com.seedfinding.mcfeature.structure.UniformStructure;
import com.seedfinding.mcseed.lcg.LCG;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.cracker.BiomeData;
import kaptainwutax.seedcrackerX.cracker.FastHashedSeedSearch;
import kaptainwutax.seedcrackerX.cracker.decorator.Decorator;
import kaptainwutax.seedcrackerX.util.Database;
import kaptainwutax.seedcrackerX.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class TimeMachine {
    private static final Logger logger = LoggerFactory.getLogger("timeMachine");

    // shared pool (the old code made a new 10-thread pool on every reset and never closed the old one)
    public static final ExecutorService SERVICE = Executors.newFixedThreadPool(10, Thread.ofPlatform().daemon().name("SeedCrackerX-cracker-", 0).factory());
    private final LCG inverseLCG = LCG.JAVA.combine(-2);
    public boolean isRunning = false;
    public boolean shouldTerminate = false;
    public List<Integer> pillarSeeds = null;
    public Set<Long> structureSeeds = new HashSet<>();
    public Set<Long> worldSeeds = new HashSet<>();

    // status for the on-screen panel (CrackerHud)
    public enum Status { COLLECTING, LIFTING, CHECKING, FOUND, NO_MATCH }
    public volatile Status status = Status.COLLECTING;
    public volatile long liftingStartedAt = 0;
    public volatile int checkDone = 0;
    public volatile int checkTotal = 0;
    public volatile long foundSeed = 0;
    protected DataStorage dataStorage;

    public TimeMachine(DataStorage dataStorage) {
        this.dataStorage = dataStorage;
    }

    public void poke(Phase phase) {
        if (this.worldSeeds.size() == 1) return;
        this.isRunning = true;

        while (phase != null && !this.shouldTerminate) {
            if (phase != Phase.BIOMES && pokeStructureReduce()) {
                phase = Phase.BIOMES;
                continue;

            } else if (phase == Phase.STRUCTURES) {
                if (!pokeStructures()) break;

            } else if (phase == Phase.LIFTING) {
                if (!pokeStructures() && !pokeLifting()) break;

            } else if (phase == Phase.PILLARS) {
                if (!this.pokePillars()) break;

            } else if (phase == Phase.BIOMES) {
                if (!this.pokeBiomes()) break;
            }

            phase = phase.nextPhase();
        }
        if (this.worldSeeds.size() == 1 && !this.shouldTerminate) {
            long seed = worldSeeds.stream().findFirst().get();
            SeedCracker.entrypoints.forEach(entrypoint -> entrypoint.pushWorldSeed(seed));
            Minecraft client = Minecraft.getInstance();
            if (Config.get().databaseSubmits && client.getConnection().getOnlinePlayers().size() > 10 &&
                    !client.getConnection().getConnection().isMemoryConnection()) {
                Component text = Database.joinFakeServerForAuth();
                if (text == null) {
                    Database.handleDatabaseCall(seed);
                } else {
                    Log.error(text.getString());
                }
            }
        }
    }

    protected boolean pokePillars() {
        if (this.pillarSeeds != null || this.dataStorage.pillarData == null) return false;
        this.pillarSeeds = new ArrayList<>();

        Log.debug("====================================");
        Log.warn("tmachine.lookingForPillarSeed");

        for (int pillarSeed = 0; pillarSeed < 1 << 16 && !this.shouldTerminate; pillarSeed++) {
            if (this.dataStorage.pillarData.test(pillarSeed)) {
                Log.printSeed("tmachine.foundPillarSeed", pillarSeed);
                this.pillarSeeds.add(pillarSeed);
            }
        }

        if (!this.pillarSeeds.isEmpty()) {
            Log.warn("tmachine.pillarSeedSearchFinished");
        } else {
            Log.error("finishedSearchNoResult");
        }

        return true;
    }

    protected boolean pokeLifting() {
        if (!this.structureSeeds.isEmpty() || this.dataStorage.getLiftingBits() < 40F) return false;
        List<UniformStructure.Data<?>> dataList = new ArrayList<>();

        for (DataStorage.Entry<Feature.Data<?>> e : this.dataStorage.baseSeedData) {
            if (e.data.feature instanceof OldStructure || e.data.feature instanceof Shipwreck) {
                dataList.add((UniformStructure.Data<?>) e.data);
            }
        }
        List<Feature.Data<?>> cache = new ArrayList<>();

        for (DataStorage.Entry<Feature.Data<?>> entry : this.dataStorage.baseSeedData) {
            if (!(entry.data.feature instanceof Decorator) || entry.data.feature.getVersion().isOlderThan(MCVersion.v1_18)) {
                if (!(entry.data.feature instanceof PillagerOutpost)) {
                    cache.add(entry.data);
                }
            }
        }
        Log.warn("tmachine.startLifting", dataList.size());
        this.status = Status.LIFTING;
        this.liftingStartedAt = System.currentTimeMillis();

        // You could first lift on 1L<<18 with %2 since that would be a smaller range
        // Then lift on 1<<19 with those 1<<18 fixed with % 4 and for nextInt(24)
        // You can even do %8 on 1<<20 (however we included shipwreck so only nextInt(20) so 1<<19 is the max here
        Stream<Long> lowerBitsStream = LongStream.range(0, 1L << 19).boxed().filter(lowerBits -> {
            ChunkRand rand = new ChunkRand();
            for (UniformStructure.Data<?> data : dataList) {
                rand.setRegionSeed(lowerBits, data.regionX, data.regionZ, data.feature.getSalt(), Config.get().getVersion());
                if (rand.nextInt(((UniformStructure<?>)data.feature).getOffset()) % 4 != data.offsetX % 4 ||
                        rand.nextInt(((UniformStructure<?>)data.feature).getOffset()) % 4 != data.offsetZ % 4) {
                    return false;
                }
            }
            return true;
        });

        Stream<Long> seedStream = lowerBitsStream.flatMap(lowerBits ->
                LongStream.range(0, 1L << (48 - 19))
                        .boxed()
                        .map(upperBits -> (upperBits << 19) | lowerBits)
        );

        Stream<Long> strutureSeedStream = seedStream.filter(seed -> {
            ChunkRand rand = new ChunkRand();
            for (Feature.Data<?> data : cache) {
                if (!data.testStart(seed, rand)) {
                    return false;
                }
            }
            return true;
        });

        try {
            this.structureSeeds = strutureSeedStream.parallel().collect(Collectors.toSet());
        } finally {
            if (this.status == Status.LIFTING) this.status = Status.COLLECTING;
        }

        if (!this.structureSeeds.isEmpty()) {
            Log.warn("tmachine.structureSeedSearchFinished");
        } else {
            Log.error("finishedSearchNoResult");
        }

        return !this.structureSeeds.isEmpty();
    }


    protected boolean pokeStructures() {
        if (this.pillarSeeds == null || !this.structureSeeds.isEmpty() ||
                this.dataStorage.getBaseBits() < this.dataStorage.getWantedBits()) return false;

        List<Feature.Data<?>> cache = new ArrayList<>();

        for (DataStorage.Entry<Feature.Data<?>> entry : this.dataStorage.baseSeedData) {
            if (!(entry.data.feature instanceof Decorator) || entry.data.feature.getVersion().isOlderThan(MCVersion.v1_18)) {
                if (!(entry.data.feature instanceof PillagerOutpost)) {
                    cache.add(entry.data);
                }
            }
        }

        for (int pillarSeed : this.pillarSeeds) {
            Log.debug("====================================");
            Log.warn("tmachine.lookingForStructureSeeds", pillarSeed);

            AtomicInteger completion = new AtomicInteger();
            ProgressListener progressListener = new ProgressListener();

            for (int threadId = 0; threadId < 4; threadId++) {
                int fThreadId = threadId;

                SERVICE.submit(() -> {
                    ChunkRand rand = new ChunkRand();

                    long lower = (long) fThreadId * (1L << 30);
                    long upper = (long) (fThreadId + 1) * (1L << 30);

                    for (long partialWorldSeed = lower; partialWorldSeed < upper && !this.shouldTerminate; partialWorldSeed++) {
                        if ((partialWorldSeed & ((1 << 27) - 1)) == 0) {
                            progressListener.addPercent(3.125F, true);
                        }

                        long seed = this.timeMachine(partialWorldSeed, pillarSeed);

                        boolean matches = true;

                        for (Feature.Data<?> baseSeedDatum : cache) {
                            if (!baseSeedDatum.testStart(seed, rand)) {
                                matches = false;
                                break;
                            }
                        }

                        if (matches) {
                            this.structureSeeds.add(seed);
                            Log.printSeed("foundStructureSeed", seed);
                        }

                    }

                    completion.getAndIncrement();
                });
            }

            while (completion.get() != 4) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (this.shouldTerminate) {
                    return false;
                }
            }

            progressListener.addPercent(0.0F, true);
        }

        if (!this.structureSeeds.isEmpty()) {
            Log.warn("tmachine.structureSeedSearchFinished");
        } else {
            Log.error("finishedSearchNoResult");
        }

        return true;
    }

    // remembers which set of structure seeds was already checked against the hashed seed
    private long lastFastSearchKey = 0;

    /**
     * check every structure seed against the hashed seed the server sent.
     * The original mod only did this with under 1000 structure seeds.
     * @return true if a world seed was found, false if not, null if this exact set was already checked
     */
    protected Boolean fastHashedSeedSearch() {
        if (this.dataStorage.hashedSeedData == null || this.dataStorage.hashedSeedData.getHashedSeed() == 0) return null;
        long hashed = this.dataStorage.hashedSeedData.getHashedSeed();
        long[] seeds = this.structureSeeds.stream().mapToLong(Long::longValue).toArray();

        long key = hashed * 31 + seeds.length;
        for (long s : seeds) key ^= s * 0x9E3779B97F4A7C15L;
        if (key == this.lastFastSearchKey) return null;
        this.lastFastSearchKey = key;

        int threads = Math.max(1, Math.min(32, Runtime.getRuntime().availableProcessors() - 1));
        Log.warn("tmachine.hashedSearchStart", seeds.length);
        long start = System.currentTimeMillis();
        this.checkDone = 0;
        this.checkTotal = seeds.length;
        this.status = Status.CHECKING;
        AtomicInteger doneCounter = new AtomicInteger();
        List<Long> found;
        try {
            found = FastHashedSeedSearch.search(seeds, hashed, threads, () -> this.shouldTerminate,
                    percent -> Log.warn("tmachine.hashedSearchProgress", percent),
                    done -> this.checkDone = done);
        } finally {
            if (this.status == Status.CHECKING) this.status = Status.COLLECTING;
        }
        if (this.shouldTerminate) return false;
        logger.info("Hashed seed search over {} structure seeds took {} ms", seeds.length, System.currentTimeMillis() - start);

        if (found.isEmpty()) {
            this.status = Status.NO_MATCH;
            Log.error("tmachine.hashedSearchNoResult");
            return false;
        }

        this.foundSeed = found.get(0);
        this.status = Status.FOUND;

        this.worldSeeds.clear();
        this.worldSeeds.addAll(found);
        for (long worldSeed : found) {
            announceWorldSeed(worldSeed);
        }
        return true;
    }

    private void announceWorldSeed(long worldSeed) {
        Log.warn("==============================");
        Log.printSeed("tmachine.foundWorldSeedBanner", worldSeed);
        Log.warn("==============================");
        try {
            Path file = FabricLoader.getInstance().getGameDir().resolve("seedcracker-found-seed.txt");
            String server = "unknown";
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() != null) {
                server = client.getConnection().getConnection().getRemoteAddress().toString();
            }
            Files.writeString(file, LocalDateTime.now() + "  server: " + server + "  world seed: " + worldSeed + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Log.warn("tmachine.savedSeedToFile");
        } catch (Exception e) {
            logger.error("Couldn't save found seed to file", e);
        }
        logger.info("FOUND WORLD SEED: {}", worldSeed);
    }

    protected boolean pokeBiomes() {
        if (this.structureSeeds.isEmpty() || this.worldSeeds.size() == 1) return false;

        // try the hashed seed first, on any number of structure seeds
        Boolean fast = fastHashedSeedSearch();
        if (Boolean.TRUE.equals(fast)) return true;

        if (this.structureSeeds.size() > 1000) return false;

        Log.debug("====================================");

        worldSeeds.clear();
        dataStorage.baseSeedData.dump();
        if (Config.get().getVersion().isNewerOrEqualTo(MCVersion.v1_18) && dataStorage.getDecoratorBits() > 32F) {
            Log.warn("tmachine.decoratorWorldSeedSearch");
            WorldgenRandom rand = new WorldgenRandom(new XoroshiroRandomSource(0));


            for (long structureSeed : this.structureSeeds) {
                for (long upperBits = 0; upperBits < 1 << 16 && !this.shouldTerminate; upperBits++) {
                    long worldSeed = (upperBits << 48) | structureSeed;

                    boolean matches = true;

                    for (DataStorage.Entry<Feature.Data<?>> e : this.dataStorage.baseSeedData.getBaseSet()) {
                        if (e.data.feature instanceof Decorator && !((Decorator.Data<?>) e.data).testStart(worldSeed, rand)) {
                            matches = false;
                            break;
                        }
                    }

                    if (matches) {
                        this.worldSeeds.add(worldSeed);
                        if (this.worldSeeds.size() < 10) {
                            Log.printSeed("tmachine.foundWorldSeed", worldSeed);
                            if (this.worldSeeds.size() == 9) {
                                Log.warn("tmachine.printSeedsInConsole");
                            }
                        } else {
                            logger.info("Found world seed " + worldSeed);
                        }
                    }

                    if (this.shouldTerminate) {
                        return false;
                    }
                }
            }
            if (!this.worldSeeds.isEmpty()) {
                Log.warn("tmachine.worldSeedSearchFinished");
                return true;
            } else {
                Log.warn("finishedSearchNoResult");
            }

        }

        // the hashed seed check now happens in fastHashedSeedSearch() at the top of pokeBiomes()

        this.dataStorage.biomeSeedData.dump();
        if (this.dataStorage.notEnoughBiomeData()) {
            Log.error("tmachine.moreBiomesNeeded");
            return false;
        }

        Log.warn("tmachine.biomeWorldSeedSearch", this.dataStorage.biomeSeedData.size());
        Log.warn("tmachine.fuzzyBiomeSearch");
        MCVersion version = Config.get().getVersion();
        for (long structureSeed : this.structureSeeds) {
            for (long worldSeed : StructureSeed.toRandomWorldSeeds(structureSeed)) {
                OverworldBiomeSource source = new OverworldBiomeSource(version, worldSeed);

                boolean matches = true;

                for (DataStorage.Entry<BiomeData> e : this.dataStorage.biomeSeedData) {
                    if (!e.data.test(source)) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                    this.worldSeeds.add(worldSeed);
                    Log.printSeed("tmachine.foundWorldSeed", worldSeed);
                }
                if (this.shouldTerminate) {
                    return false;
                }
            }
        }

        if (!this.worldSeeds.isEmpty()) return true;
        if (this.structureSeeds.size() > 10) return false;
        Log.warn("tmachine.deepBiomeSearch");
        for (long structureSeed : this.structureSeeds) {
            for (long upperBits = 0; upperBits < 1 << 16 && !this.shouldTerminate; upperBits++) {
                long worldSeed = (upperBits << 48) | structureSeed;

                OverworldBiomeSource source = new OverworldBiomeSource(version, worldSeed);

                boolean matches = true;

                for (DataStorage.Entry<BiomeData> e : this.dataStorage.biomeSeedData) {
                    if (!e.data.test(source)) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                    this.worldSeeds.add(worldSeed);
                    if (this.worldSeeds.size() < 10) {
                        Log.printSeed("tmachine.foundWorldSeed", worldSeed);
                        if (this.worldSeeds.size() == 9) {
                            Log.warn("tmachine.printSeedsInConsole");
                        }
                    } else {
                        logger.info("Found world seed " + worldSeed);
                    }
                }

                if (this.shouldTerminate) {
                    return false;
                }
            }
        }

        dispSearchEnd();

        if (!this.worldSeeds.isEmpty()) return true;

        Log.error("tmachine.deleteBiomeInformation");
        this.dataStorage.biomeSeedData.getBaseSet().clear();

        Log.warn("tmachine.randomSeedSearch");
        for (long structureSeed : this.structureSeeds) {
            StructureSeed.toRandomWorldSeeds(structureSeed).forEach(s ->
                    Log.printSeed("tmachine.foundWorldSeed", s));

        }

        return true;
    }

    protected boolean pokeStructureReduce() {
        if (shouldTerminate) return false;
        if (!this.worldSeeds.isEmpty() || this.structureSeeds.size() < 2) return false;
        if (Config.get().getVersion().isOlderThan(MCVersion.v1_13)) return false;

        Set<Long> result = new HashSet<>();
        Log.debug("====================================");
        Log.verbose("tmachine.reduceSeeds", this.structureSeeds.size());

        if (this.pillarSeeds != null) {
            structureSeeds.forEach(seed -> {
                if (this.pillarSeeds.contains((int) PillarSeed.fromStructureSeed(seed))) {
                    result.add(seed);
                }
            });
        }

        if (result.size() != 1) {
            this.dataStorage.baseSeedData.dump();
            this.dataStorage.baseSeedData.getBaseSet().removeIf(dataEntry -> !dataEntry.data.feature.getVersion().equals(Config.get().getVersion()));
            List<Feature.Data<?>> cache = new ArrayList<>();

            for (DataStorage.Entry<Feature.Data<?>> entry : this.dataStorage.baseSeedData) {
                if (!(entry.data.feature instanceof Decorator) || entry.data.feature.getVersion().isOlderThan(MCVersion.v1_18)) {
                    //todo remove this when libs are updated
                    if (!(entry.data.feature instanceof PillagerOutpost)) {
                        cache.add(entry.data);
                    }
                }
            }
            ChunkRand rand = new ChunkRand();

            for (Long seed : this.structureSeeds) {
                boolean matches = true;

                for (Feature.Data<?> baseSeedDatum : cache) {
                    if (!baseSeedDatum.testStart(seed, rand)) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                    result.add(seed);
                }
            }
        }

        if (!result.isEmpty() && this.structureSeeds.size() > result.size()) {
            if (result.size() < 10) {
                result.forEach(seed -> Log.printSeed("foundStructureSeed", seed));
            } else {
                Log.warn("tmachine.succeedReducing", result.size());
            }

            this.structureSeeds = result;
            return true;
        } else {
            Log.verbose("tmachine.failedReducing");
        }
        return false;
    }

    private void dispSearchEnd() {
        if (!this.worldSeeds.isEmpty()) {
            Log.warn("tmachine.worldSeedSearchFinished");
        } else {
            Log.error("finishedSearchNoResult");
        }
    }

    public long timeMachine(long partialWorldSeed, int pillarSeed) {
        long currentSeed = 0L;
        currentSeed |= (partialWorldSeed & 0xFFFF0000L) << 16;
        currentSeed |= (long) pillarSeed << 16;
        currentSeed |= partialWorldSeed & 0xFFFFL;

        currentSeed = this.inverseLCG.nextSeed(currentSeed);
        currentSeed ^= LCG.JAVA.multiplier;
        return currentSeed;
    }

    public enum Phase {
        BIOMES(null), STRUCURE_REDUCE(BIOMES), STRUCTURES(BIOMES), LIFTING(STRUCURE_REDUCE), PILLARS(STRUCTURES);

        private final Phase nextPhase;

        Phase(Phase nextPhase) {
            this.nextPhase = nextPhase;
        }

        public Phase nextPhase() {
            return this.nextPhase;
        }
    }

}

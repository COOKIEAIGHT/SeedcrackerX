package kaptainwutax.seedcrackerX.cracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Servers (Realms included) send the client a "hashed seed": the first 8 bytes of
 * SHA-256(worldSeed), read little-endian. Once we know the lower 48 bits of the
 * world seed (the structure seed), only 65,536 world seeds are left per structure
 * seed, and the hash tells us which one it is.
 *
 * The original mod only tried this with fewer than 1000 structure seeds. This class
 * does the same check, but fast enough (allocation-free single-block SHA-256 on
 * several threads) to run on tens of thousands of structure seeds.
 */
public final class FastHashedSeedSearch {

    private static final int[] K = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

    private static final int IV0 = 0x6a09e667, IV1 = 0xbb67ae85, IV2 = 0x3c6ef372, IV3 = 0xa54ff53a,
            IV4 = 0x510e527f, IV5 = 0x9b05688c, IV6 = 0x1f83d9ab, IV7 = 0x5be0cd19;

    private FastHashedSeedSearch() {
    }

    /** Same result as Minecraft's BiomeManager.obfuscateSeed / seedfinding WorldSeed.toHash. */
    public static long hash(long worldSeed) {
        int[] w = new int[64];
        w[0] = Integer.reverseBytes((int) worldSeed);
        w[1] = Integer.reverseBytes((int) (worldSeed >>> 32));
        w[2] = 0x80000000;
        w[15] = 64;
        for (int i = 16; i < 64; i++) w[i] = ss1(w[i - 2]) + w[i - 7] + ss0(w[i - 15]) + w[i - 16];
        int a = IV0, b = IV1, c = IV2, d = IV3, e = IV4, f = IV5, g = IV6, h = IV7;
        for (int i = 0; i < 64; i++) {
            int t1 = h + bs1(e) + ((e & f) ^ (~e & g)) + K[i] + w[i];
            int t2 = bs0(a) + ((a & b) ^ (a & c) ^ (b & c));
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
        }
        int h0 = IV0 + a, h1 = IV1 + b;
        return (Integer.reverseBytes(h0) & 0xFFFFFFFFL) | ((long) Integer.reverseBytes(h1) << 32);
    }

    /**
     * @param structureSeeds lower 48 bits of candidate world seeds
     * @param hashedSeed     the hashed seed the server sent
     * @param threads        worker threads to use
     * @param stop           checked often; return true to cancel
     * @param progress       called with 25, 50, 75 as the search goes
     * @return every world seed that matches (normally exactly one)
     */
    public static List<Long> search(long[] structureSeeds, long hashedSeed, int threads,
                                    BooleanSupplier stop, IntConsumer progress) {
        return search(structureSeeds, hashedSeed, threads, stop, progress, d -> { });
    }

    /** same as above, plus doneCount is told how many structure seeds have been checked so far */
    public static List<Long> search(long[] structureSeeds, long hashedSeed, int threads,
                                    BooleanSupplier stop, IntConsumer progress, IntConsumer doneCount) {
        final int tH0 = Integer.reverseBytes((int) hashedSeed);
        final int tH1 = Integer.reverseBytes((int) (hashedSeed >>> 32));
        final List<Long> found = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger next = new AtomicInteger();
        final AtomicInteger done = new AtomicInteger();
        final AtomicInteger lastQuarter = new AtomicInteger();
        final int n = structureSeeds.length;

        Thread[] workers = new Thread[Math.max(1, threads)];
        for (int t = 0; t < workers.length; t++) {
            workers[t] = new Thread(() -> {
                int[] w = new int[64];
                int i;
                while ((i = next.getAndIncrement()) < n) {
                    if (stop.getAsBoolean()) return;
                    searchOne(structureSeeds[i] & 0xFFFFFFFFFFFFL, tH0, tH1, w, found);
                    int d = done.incrementAndGet();
                    doneCount.accept(d);
                    int quarter = (int) (4L * d / n);
                    int prev = lastQuarter.get();
                    if (quarter > prev && quarter < 4 && lastQuarter.compareAndSet(prev, quarter)) {
                        progress.accept(quarter * 25);
                    }
                }
            }, "SeedCrackerX-hash-search-" + t);
            workers[t].setDaemon(true);
            workers[t].setPriority(Thread.MIN_PRIORITY);
            workers[t].start();
        }
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new ArrayList<>(found);
    }

    private static void searchOne(long s, int tH0, int tH1, int[] w, List<Long> found) {
        final int w0 = Integer.reverseBytes((int) s);
        final int hi16 = (int) ((s >>> 32) & 0xFFFF);
        final int w1base = ((hi16 & 0xFF) << 24) | ((hi16 >>> 8) << 16);

        // round 0 only depends on w0, so do it once per structure seed
        int t1r0 = IV7 + bs1(IV4) + ((IV4 & IV5) ^ (~IV4 & IV6)) + K[0] + w0;
        int t2r0 = bs0(IV0) + ((IV0 & IV1) ^ (IV0 & IV2) ^ (IV1 & IV2));
        final int a1 = t1r0 + t2r0, b1 = IV0, c1 = IV1, d1 = IV2, e1 = IV3 + t1r0, f1 = IV4, g1 = IV5, h1 = IV6;

        w[0] = w0;
        w[2] = 0x80000000;
        for (int i = 3; i < 15; i++) w[i] = 0;
        w[15] = 64;

        for (int u = 0; u < 65536; u++) {
            w[1] = w1base | ((u & 0xFF) << 8) | (u >>> 8);
            for (int i = 16; i < 64; i++) w[i] = ss1(w[i - 2]) + w[i - 7] + ss0(w[i - 15]) + w[i - 16];

            int a = a1, b = b1, c = c1, d = d1, e = e1, f = f1, g = g1, h = h1;
            for (int i = 1; i < 63; i++) {
                int t1 = h + bs1(e) + ((e & f) ^ (~e & g)) + K[i] + w[i];
                int t2 = bs0(a) + ((a & b) ^ (a & c) ^ (b & c));
                h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
            }
            // after round 63, b will hold this a -> second digest word
            if (IV1 + a != tH1) continue;
            int t1 = h + bs1(e) + ((e & f) ^ (~e & g)) + K[63] + w[63];
            int t2 = bs0(a) + ((a & b) ^ (a & c) ^ (b & c));
            if (IV0 + t1 + t2 == tH0) {
                found.add(((long) u << 48) | s);
            }
        }
    }

    private static int bs0(int x) { return Integer.rotateRight(x, 2) ^ Integer.rotateRight(x, 13) ^ Integer.rotateRight(x, 22); }
    private static int bs1(int x) { return Integer.rotateRight(x, 6) ^ Integer.rotateRight(x, 11) ^ Integer.rotateRight(x, 25); }
    private static int ss0(int x) { return Integer.rotateRight(x, 7) ^ Integer.rotateRight(x, 18) ^ (x >>> 3); }
    private static int ss1(int x) { return Integer.rotateRight(x, 17) ^ Integer.rotateRight(x, 19) ^ (x >>> 10); }
}

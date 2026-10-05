package kaptainwutax.seedcrackerX.finder;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class FinderControl {

    private final Map<Finder.Type, ConcurrentLinkedQueue<Finder>> activeFinders = new ConcurrentHashMap<>();

    public void deleteFinders() {
        this.activeFinders.clear();
    }

    public List<Finder> getActiveFinders() {
        this.activeFinders.values().forEach(finders -> {
            finders.removeIf(f -> f.isUseless() || !f.isFromCurrentWorld()); // also drop old-world finders (memory leak fix)
        });

        return this.activeFinders.values().stream()
                .flatMap(Queue::stream).collect(Collectors.toList());
    }

    // used by the tests to see what each finder picked up
    public List<Finder> getActiveFinders(Finder.Type type) {
        ConcurrentLinkedQueue<Finder> q = this.activeFinders.get(type);
        if (q == null) return List.of();
        q.removeIf(f -> f.isUseless() || !f.isFromCurrentWorld());
        return List.copyOf(q);
    }

    public synchronized void addFinder(Finder.Type type, Finder finder) {
        if (finder.isUseless() || !finder.isFromCurrentWorld()) return;

        ConcurrentLinkedQueue<Finder> finders = this.activeFinders.computeIfAbsent(type, t -> new ConcurrentLinkedQueue<>());
        // structures now get outlined every time they're seen, so skip ones already outlined
        List<net.minecraft.core.BlockPos> key = finder.outlineKey();
        for (Finder existing : finders) {
            if (existing.isFromCurrentWorld() && existing.outlineKey().equals(key)) return;
        }
        finders.add(finder);
    }
}

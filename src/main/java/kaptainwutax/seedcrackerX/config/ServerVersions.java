package kaptainwutax.seedcrackerX.config;

import com.seedfinding.mccore.version.MCVersion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every server version the version selector offers, newest first.
 *
 * The seed-finding libraries only know versions up to 1.21.3. Releases after that use exactly the same
 * structure rules (structure_set spacing/separation/salt and the RandomSpreadStructurePlacement maths were
 * compared against Mojang's data for 1.21.4 to 26.3; 26.3 only adds abandoned camps, which aren't used),
 * so they map to the 1.21.3 rules.
 */
public final class ServerVersions {

    /** releases newer than the libraries know about, newest first */
    public static final List<String> NEWER_THAN_LIBRARY = List.of(
            "26.3", "26.2", "26.1.2", "26.1.1", "26.1",
            "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4");

    /** the rules used for {@link #NEWER_THAN_LIBRARY} */
    public static final MCVersion RULES_FOR_NEWER = MCVersion.v1_21_3;

    public static final String DEFAULT = NEWER_THAN_LIBRARY.get(0);

    private ServerVersions() {
    }

    /** all selectable versions, newest first */
    public static List<String> all() {
        Set<String> out = new LinkedHashSet<>(NEWER_THAN_LIBRARY);
        List<MCVersion> library = new ArrayList<>();
        for (MCVersion v : MCVersion.values()) {
            if (!v.isOlderThan(MCVersion.v1_8)) library.add(v);
        }
        library.sort((a, b) -> a.isNewerThan(b) ? -1 : (b.isNewerThan(a) ? 1 : 0));
        for (MCVersion v : library) out.add(v.name);
        return new ArrayList<>(out);
    }

    public static boolean isSupported(String version) {
        return version != null && all().contains(version);
    }

    /** the structure rules to use for a server version */
    public static MCVersion rulesFor(String version) {
        MCVersion exact = MCVersion.fromString(version);
        if (exact != null) return exact;
        if (NEWER_THAN_LIBRARY.contains(version)) return RULES_FOR_NEWER;
        return MCVersion.latest();
    }

    /** "1.8 to 26.3" */
    public static String range() {
        List<String> all = all();
        return all.get(all.size() - 1) + " to " + all.get(0);
    }
}

package kaptainwutax.seedcrackerX.render;

import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.cracker.storage.TimeMachine;
import kaptainwutax.seedcrackerX.init.SeedCrackerKeys;
import kaptainwutax.seedcrackerX.util.CrackerStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * an always-on panel in the top-left corner that shows what the cracker is doing,
 * a progress bar, how many structures it has, and how many possible seeds are left.
 * Toggle with /seedcracker hud
 */
public final class CrackerHud {

    private static final int WHITE = 0xFFFFFFFF, GREY = 0xFFAAAAAA, GREEN = 0xFF55FF55, GOLD = 0xFFFFCC33,
            RED = 0xFFFF5555, AQUA = 0xFF55FFFF, BG = 0xA0000000, BAR_BG = 0xFF3A3A3A;
    private static final String[] SPINNER = {"|", "/", "-", "\\"};

    private static long lastRefresh = 0;
    private static CrackerStatus.Snapshot snap = null;

    private CrackerHud() {
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!Config.get().active || !Config.get().hud) return;
        if (mc.gui.screen() != null) return; // don't draw over menus

        long now = System.currentTimeMillis();
        if (snap == null || now - lastRefresh > 250) {
            snap = CrackerStatus.snapshot();
            lastRefresh = now;
        }
        CrackerStatus.Snapshot s = snap;
        String spin = SPINNER[(int) ((now / 150) % SPINNER.length)];

        Font font = mc.font;
        List<Line> lines = new ArrayList<>();
        double barFill = -1;
        String barText = null;
        int barColor = GREEN;

        lines.add(new Line("SeedCrackerX", GREEN));

        switch (s.status()) {
            case FOUND -> {
                lines.add(new Line("SEED FOUND!", GREEN));
                lines.add(new Line(String.valueOf(s.foundSeed()), GOLD));
                lines.add(new Line("Click it in chat to copy", GREY));
                lines.add(new Line("or type /seedcracker seed", GREY));
            }
            case CHECKING -> {
                lines.add(new Line("Step 3/3  Checking seeds " + spin, AQUA));
                int total = Math.max(1, s.checkTotal());
                barFill = (double) s.checkDone() / total;
                barText = (int) (barFill * 100) + "%";
                barColor = AQUA;
                lines.add(new Line("Checked " + CrackerStatus.format(s.checkDone()) + " / " + CrackerStatus.format(total), WHITE));
                lines.add(new Line("Possible seeds left: " + s.possibleSeeds(), WHITE));
            }
            case LIFTING -> {
                long secs = Math.max(0, (now - s.liftingStartedAt()) / 1000);
                lines.add(new Line("Step 2/3  Working out seeds " + spin, GOLD));
                lines.add(new Line("Takes a minute or two (" + secs + "s)", GREY));
                lines.add(new Line("Structures used: " + s.structureCount(), WHITE));
            }
            case NO_MATCH -> {
                lines.add(new Line("No seed matched", RED));
                lines.add(new Line("A structure was probably wrong.", GREY));
                lines.add(new Line("Type /seedcracker clear", GREY));
                lines.add(new Line("and find 5 new ones.", GREY));
            }
            default -> {
                if (s.structureSeeds() > 0) {
                    lines.add(new Line("Waiting for more info " + spin, GOLD));
                    lines.add(new Line("Possible seeds: " + s.possibleSeeds(), WHITE));
                    lines.add(new Line(s.nextStep(), GREY));
                } else {
                    lines.add(new Line("Step 1/3  Finding structures", WHITE));
                    barFill = s.bitsFraction();
                    barText = s.bitsText();
                    lines.add(new Line("Structures: " + s.structureCount()
                            + (s.structureSummary().isEmpty() ? "" : "  (" + s.structureSummary() + ")"), WHITE));
                    lines.add(new Line("Possible seeds: " + s.possibleSeeds(), WHITE));
                    lines.add(new Line("Look for: shipwrecks, temples, igloos, witch huts", GREY));
                }
            }
        }
        lines.add(new Line("Hashed seed: ", GREY, s.hasHashedSeed() ? "got it" : "not yet", s.hasHashedSeed() ? GREEN : RED));
        String key = SeedCrackerKeys.openMenuKeyName();
        if (key != null) lines.add(new Line("Press " + key + " for the menu", GREY));

        // layout
        int pad = 4, lineH = font.lineHeight + 2, barH = 8;
        int width = 150;
        for (Line l : lines) width = Math.max(width, l.width(font));
        int height = lines.size() * lineH + (barFill >= 0 ? barH + 4 : 0);
        int x = 4, y = 4;

        g.fill(x, y, x + width + pad * 2, y + height + pad * 2, BG);
        g.fill(x, y, x + 2, y + height + pad * 2, s.status() == TimeMachine.Status.NO_MATCH ? RED : GREEN);

        int cy = y + pad;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            l.draw(g, font, x + pad + 2, cy);
            cy += lineH;
            if (i == 1 && barFill >= 0) {
                int bx = x + pad + 2, bw = width - 2;
                g.fill(bx, cy, bx + bw, cy + barH, BAR_BG);
                g.fill(bx, cy, bx + (int) (bw * Math.max(0, Math.min(1, barFill))), cy + barH, barColor);
                if (barText != null) {
                    g.text(font, barText, bx + bw / 2 - font.width(barText) / 2, cy, WHITE, true);
                }
                cy += barH + 4;
            }
        }
    }

    private record Line(String a, int colorA, String b, int colorB) {
        Line(String a, int colorA) {
            this(a, colorA, null, 0);
        }

        int width(Font font) {
            return font.width(a) + (b == null ? 0 : font.width(b));
        }

        void draw(GuiGraphicsExtractor g, Font font, int x, int y) {
            g.text(font, a, x, y, colorA, true);
            if (b != null) g.text(font, b, x + font.width(a), y, colorB, true);
        }
    }
}

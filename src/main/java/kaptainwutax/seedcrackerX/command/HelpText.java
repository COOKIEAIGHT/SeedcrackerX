package kaptainwutax.seedcrackerX.command;

import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.cracker.storage.TimeMachine;
import kaptainwutax.seedcrackerX.init.SeedCrackerKeys;
import kaptainwutax.seedcrackerX.util.CrackerStatus;
import kaptainwutax.seedcrackerX.util.Log;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/** friendly status / help messages for chat, with clickable commands */
public final class HelpText {

    /** command -> what it does (shown in help and on hover) */
    public static final Map<String, String> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("status", "Where it's at: stage, structures, possible seeds");
        COMMANDS.put("seed", "Show the world seed once it's found (click to copy)");
        COMMANDS.put("menu", "Open the menu (or press the hotkey)");
        COMMANDS.put("hud", "Turn the on-screen panel on/off");
        COMMANDS.put("restore", "Load the structures you found last time");
        COMMANDS.put("clear", "Throw away everything and start again");
        COMMANDS.put("rescan", "Look through nearby chunks again for structures it missed");
        COMMANDS.put("render", "Structure outlines: off / on / xray");
        COMMANDS.put("finder", "Choose which structures it looks for");
        COMMANDS.put("on", "Turn SeedCracker on");
        COMMANDS.put("off", "Turn SeedCracker off");
        COMMANDS.put("debug", "Turn extra debug messages on/off");
        COMMANDS.put("version", "Show or set the server version (" + kaptainwutax.seedcrackerX.config.ServerVersions.range() + ")");
        COMMANDS.put("help", "Show this list");
    }

    private HelpText() {
    }

    private static MutableComponent text(String s, ChatFormatting... style) {
        return Component.literal(s).withStyle(style);
    }

    private static MutableComponent clickable(String command) {
        String full = "/seedcracker " + command;
        return text("[" + command + "]", ChatFormatting.AQUA).withStyle(st -> st
                .withClickEvent(new ClickEvent.SuggestCommand(full))
                .withHoverEvent(new HoverEvent.ShowText(text(full + "\n", ChatFormatting.AQUA)
                        .append(text(COMMANDS.getOrDefault(command, ""), ChatFormatting.GRAY)))));
    }

    public static void status() {
        CrackerStatus.Snapshot s = CrackerStatus.snapshot();
        Log.send(text("--- SeedCrackerX ---", ChatFormatting.GREEN));
        ChatFormatting stageColor = switch (s.status()) {
            case FOUND -> ChatFormatting.GREEN;
            case NO_MATCH -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
        Log.send(text(s.stage(), stageColor, ChatFormatting.BOLD));
        if (s.status() == TimeMachine.Status.FOUND) {
            Log.printSeed("tmachine.foundWorldSeedBanner", s.foundSeed());
        }
        Log.send(text("Structures: ", ChatFormatting.GRAY).append(text(s.structureCount()
                + (s.structureSummary().isEmpty() ? "" : " (" + s.structureSummary() + ")"), ChatFormatting.WHITE)));
        if (s.structureSeeds() == 0 && s.status() == TimeMachine.Status.COLLECTING) {
            Log.send(text("Progress: ", ChatFormatting.GRAY).append(text(s.bitsText() + " bits (starts cracking at 40)", ChatFormatting.WHITE)));
        }
        Log.send(text("Possible seeds: ", ChatFormatting.GRAY).append(text(s.possibleSeeds(), ChatFormatting.WHITE)));
        Log.send(text("Server version: ", ChatFormatting.GRAY).append(text(kaptainwutax.seedcrackerX.config.Config.get().getServerVersion(), ChatFormatting.WHITE)));
        Log.send(text("Hashed seed: ", ChatFormatting.GRAY).append(s.hasHashedSeed()
                ? text("got it", ChatFormatting.GREEN) : text("not yet", ChatFormatting.RED)));
        Log.send(text("Next: ", ChatFormatting.GRAY).append(text(s.nextStep(), ChatFormatting.YELLOW)));
    }

    /** what /seedcracker on its own shows */
    public static void overview() {
        status();
        MutableComponent row = text("Commands: ", ChatFormatting.GRAY);
        for (String c : new String[]{"status", "seed", "menu", "hud", "restore", "clear", "help"}) {
            row.append(clickable(c)).append(text(" "));
        }
        Log.send(row);
        String key = SeedCrackerKeys.openMenuKeyName();
        if (key != null) Log.send(text("Tip: press " + key + " to open the menu", ChatFormatting.GRAY));
    }

    public static void help() {
        Log.send(text("--- SeedCracker commands (click one) ---", ChatFormatting.GREEN));
        COMMANDS.forEach((c, what) -> Log.send(clickable(c).append(text("  " + what, ChatFormatting.GRAY))));
        Log.send(text("Also: /seedcracker database (opens the public seed sheet)", ChatFormatting.DARK_GRAY));
    }

    public static void seed() {
        CrackerStatus.Snapshot s = CrackerStatus.snapshot();
        if (s.status() == TimeMachine.Status.FOUND) {
            Log.printSeed("tmachine.foundWorldSeedBanner", s.foundSeed());
        } else {
            Log.send(text("No seed yet. ", ChatFormatting.YELLOW).append(text(s.stage() + ". " + s.nextStep(), ChatFormatting.GRAY)));
        }
    }

    public static void rescan() {
        new kaptainwutax.seedcrackerX.finder.ReloadFinders().reload();
        Log.send(text("Rescanning nearby chunks for structures...", ChatFormatting.GREEN));
    }

    public static void setActive(boolean on) {
        kaptainwutax.seedcrackerX.config.Config.get().active = on;
        kaptainwutax.seedcrackerX.config.Config.save();
        Log.send(text("SeedCracker: " + (on ? "ON" : "OFF"), on ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    public static void toggleDebug() {
        var config = kaptainwutax.seedcrackerX.config.Config.get();
        config.debug = !config.debug;
        kaptainwutax.seedcrackerX.config.Config.save();
        Log.send(text("Debug messages: " + (config.debug ? "ON" : "OFF"), ChatFormatting.AQUA));
    }

    public static void clear() {
        SeedCracker.get().reset();
        Log.send(text("Cleared everything. Start finding structures again.", ChatFormatting.GREEN));
    }
}

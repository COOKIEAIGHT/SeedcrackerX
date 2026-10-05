package kaptainwutax.seedcrackerX.config;

import com.seedfinding.mcfeature.structure.RegionStructure;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.command.DatabaseCommand;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import kaptainwutax.seedcrackerX.cracker.storage.TimeMachine;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.init.SeedCrackerKeys;
import kaptainwutax.seedcrackerX.util.CrackerStatus;
import kaptainwutax.seedcrackerX.util.Log;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * the menu, reorganised into Status / Structures / Display / Advanced
 * with plain-English labels and tooltips. Open with /seedcracker menu or the hotkey (J).
 */
public class ConfigScreen {

    private static final Config config = Config.get();

    // what each finder does, shown as a tooltip and used to group them
    private static final Map<Finder.Type, String> FINDER_HELP = Map.ofEntries(
            Map.entry(Finder.Type.SHIPWRECK, "Main clue. Easy to find around oceans and beaches."),
            Map.entry(Finder.Type.DESERT_TEMPLE, "Main clue. Sandstone pyramids in deserts."),
            Map.entry(Finder.Type.JUNGLE_TEMPLE, "Main clue. Mossy stone temples in jungles."),
            Map.entry(Finder.Type.SWAMP_HUT, "Main clue. Witch huts in swamps."),
            Map.entry(Finder.Type.IGLOO, "Main clue. Igloos in snowy plains and taigas."),
            Map.entry(Finder.Type.MONUMENT, "Extra clue. Big ocean monuments."),
            Map.entry(Finder.Type.BURIED_TREASURE, "Extra clue. Found from the buried chest, no digging needed."),
            Map.entry(Finder.Type.END_CITY, "Extra clue. End cities in the outer End islands."),
            Map.entry(Finder.Type.TRIAL_CHAMBERS, "Extra clue. Underground trial chambers (spawners and vaults get outlined too)."),
            Map.entry(Finder.Type.PILLAGER_OUTPOST, "Collected but not used for cracking yet."),
            Map.entry(Finder.Type.END_PILLARS, "Big shortcut! Look at the obsidian pillars in the End."),
            Map.entry(Finder.Type.DUNGEON, "Mob spawner rooms underground."),
            Map.entry(Finder.Type.END_GATEWAY, "Off by default. Can give wrong info, leave it off."),
            Map.entry(Finder.Type.EMERALD_ORE, "Off by default. Rarely useful."),
            Map.entry(Finder.Type.DESERT_WELL, "Off by default. Rarely useful."),
            Map.entry(Finder.Type.WARPED_FUNGUS, "Off by default. Rarely useful."),
            Map.entry(Finder.Type.BIOME, "Off by default. Only needed if there's no hashed seed."));

    private static final List<Finder.Type> MAIN_CLUES = List.of(Finder.Type.SHIPWRECK, Finder.Type.DESERT_TEMPLE,
            Finder.Type.JUNGLE_TEMPLE, Finder.Type.SWAMP_HUT, Finder.Type.IGLOO);



    private static MutableComponent text(String s, ChatFormatting... style) {
        return Component.literal(s).withStyle(style);
    }

    private static MutableComponent label(String name, String value, ChatFormatting valueColor) {
        return text(name, ChatFormatting.GRAY).append(text(value, valueColor));
    }

    public Screen getConfigScreenByCloth(Screen parent) {
        return getConfigScreenByCloth(parent, null);
    }

    /** @param startTab name of the tab to open on (Status, What to look for, Display, Advanced), or null */
    public Screen getConfigScreenByCloth(Screen parent, String startTab) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title").withStyle(ChatFormatting.GREEN))
                .setDefaultBackgroundTexture(Identifier.parse("minecraft:textures/block/blackstone.png"))
                .setTransparentBackground(true);
        ConfigEntryBuilder eb = builder.entryBuilder();
        CrackerStatus.Snapshot s = CrackerStatus.snapshot();
        TimeMachine tm = SeedCracker.get().getDataStorage().getTimeMachine();

        //============================= STATUS =============================
        ConfigCategory status = builder.getOrCreateCategory(text("Status"));

        ChatFormatting stageColor = switch (s.status()) {
            case FOUND -> ChatFormatting.GREEN;
            case NO_MATCH -> ChatFormatting.RED;
            case CHECKING -> ChatFormatting.AQUA;
            case LIFTING -> ChatFormatting.GOLD;
            default -> ChatFormatting.WHITE;
        };
        status.addEntry(eb.startTextDescription(text(s.stage(), stageColor, ChatFormatting.BOLD)).build());
        status.addEntry(eb.startTextDescription(text(s.nextStep(), ChatFormatting.GRAY)).build());

        if (s.status() == TimeMachine.Status.FOUND) {
            status.addEntry(eb.startStrField(text("World seed (select it and Ctrl+C)", ChatFormatting.GREEN), String.valueOf(s.foundSeed()))
                    .setTooltip(text("Easiest way: click the seed in chat, or type /seedcracker seed"))
                    .build());
        }

        status.addEntry(eb.startTextDescription(label("Structures found: ", s.structureCount()
                + (s.structureSummary().isEmpty() ? "" : "  (" + s.structureSummary() + ")"), ChatFormatting.WHITE)).build());
        if (s.structureSeeds() == 0 && s.status() == TimeMachine.Status.COLLECTING) {
            status.addEntry(eb.startTextDescription(label("Progress: ", s.bitsText() + " bits (it starts cracking at 40)", ChatFormatting.WHITE)).build());
        }
        status.addEntry(eb.startTextDescription(label("Possible seeds: ", s.possibleSeeds(), ChatFormatting.WHITE)).build());
        status.addEntry(eb.startTextDescription(label("Server version: ", config.getServerVersion(), ChatFormatting.WHITE)
                .append(text("  (change it under Advanced)", ChatFormatting.DARK_GRAY))).build());
        status.addEntry(eb.startTextDescription(label("Hashed seed: ", s.hasHashedSeed() ? "got it" : "not yet",
                s.hasHashedSeed() ? ChatFormatting.GREEN : ChatFormatting.RED)).build());

        status.addEntry(eb.startBooleanToggle(text("Show the progress panel on screen"), config.hud)
                .setDefaultValue(true)
                .setTooltip(text("The box in the top-left corner. Same as /seedcracker hud"))
                .setSaveConsumer(val -> config.hud = val).build());
        status.addEntry(eb.startBooleanToggle(text("Load my saved structures now"), false)
                .setTooltip(text("Turn on and press Save. Normally this happens by itself when you join."))
                .setSaveConsumer(val -> {
                    if (val) restoreStructures();
                }).build());
        status.addEntry(eb.startBooleanToggle(text("Clear everything and start again", ChatFormatting.RED), false)
                .setTooltip(text("Turn on and press Save. Use this if it says no seed matched."))
                .setSaveConsumer(val -> {
                    if (val) {
                        SeedCracker.get().reset();
                        Log.warn("data.clearData");
                    }
                }).build());

        // details, tucked away
        SubCategoryBuilder details = eb.startSubCategory(text("Details (for nerds)", ChatFormatting.GRAY)).setExpanded(false);
        HashedSeedData hashedSeed = SeedCracker.get().getDataStorage().hashedSeedData;
        details.add(eb.startStrField(text("Hashed seed from server"), hashedSeed == null ? "none yet" : String.valueOf(hashedSeed.getHashedSeed())).build());
        Set<Long> structureSeeds = tm.structureSeeds;
        if (structureSeeds.isEmpty()) {
            details.add(eb.startTextDescription(text("No structure seeds yet", ChatFormatting.GRAY)).build());
        } else if (structureSeeds.size() <= 50) {
            for (long seed : structureSeeds) {
                details.add(eb.startStrField(text("Structure seed"), String.valueOf(seed)).build());
            }
        } else {
            details.add(eb.startTextDescription(text(CrackerStatus.format(structureSeeds.size())
                    + " structure seeds (too many to list)", ChatFormatting.GRAY)).build());
        }
        if (config.debug && tm.pillarSeeds != null) {
            for (long pillarSeed : tm.pillarSeeds) {
                details.add(eb.startStrField(text("Pillar seed"), String.valueOf(pillarSeed)).build());
            }
        }
        status.addEntry(details.build());

        //============================= STRUCTURES =============================
        ConfigCategory structures = builder.getOrCreateCategory(text("What to look for"));
        structures.addEntry(eb.startTextDescription(text("Leave these on unless something's going wrong. Hover for what each one is.", ChatFormatting.GRAY)).build());

        SubCategoryBuilder main = eb.startSubCategory(text("Main clues (these do the cracking)", ChatFormatting.GREEN)).setExpanded(true);
        for (Finder.Type finder : MAIN_CLUES) main.add(finderToggle(eb, finder));
        structures.addEntry(main.build());

        SubCategoryBuilder extra = eb.startSubCategory(text("Extra clues")).setExpanded(true);
        for (Finder.Type finder : Finder.Type.values()) {
            if (MAIN_CLUES.contains(finder)) continue;
            extra.add(finderToggle(eb, finder));
        }
        structures.addEntry(extra.build());

        //============================= DISPLAY =============================
        ConfigCategory display = builder.getOrCreateCategory(text("Display"));
        display.addEntry(eb.startBooleanToggle(text("Progress panel (top-left)"), config.hud)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.hud = val).build());
        display.addEntry(eb.startEnumSelector(text("Structure outlines"), Config.RenderType.class, config.render)
                .setDefaultValue(Config.RenderType.XRAY)
                .setEnumNameProvider(e -> switch ((Config.RenderType) e) {
                    case OFF -> text("Off");
                    case ON -> text("On");
                    case XRAY -> text("On, see through walls");
                })
                .setTooltip(text("Draws a box around structures it has found, so you know they counted."))
                .setSaveConsumer(val -> config.render = val).build());
        String key = SeedCrackerKeys.openMenuKeyName();
        display.addEntry(eb.startTextDescription(label("Menu hotkey: ", key == null ? "not set" : key, ChatFormatting.WHITE)
                .append(text("  (change it in Options > Controls > Key Binds)", ChatFormatting.GRAY))).build());

        //============================= ADVANCED =============================
        ConfigCategory advanced = builder.getOrCreateCategory(text("Advanced"));
        advanced.addEntry(eb.startBooleanToggle(text("Cracker on"), config.active)
                .setDefaultValue(true)
                .setTooltip(text("Turn the whole mod off without removing it."))
                .setSaveConsumer(val -> config.active = val).build());
        advanced.addEntry(eb.startDropdownMenu(text("Server version"),
                        DropdownMenuBuilder.TopCellElementBuilder.of(config.getServerVersion(), v -> v))
                .setSelections(ServerVersions.all())
                .setSuggestionMode(false)
                .setDefaultValue(ServerVersions.DEFAULT)
                .setTooltip(text("The Minecraft version of the server you're cracking (" + ServerVersions.range() + ")."),
                        text("1.21.4 to " + ServerVersions.DEFAULT + " use the same structure rules as 1.21.3."))
                .setSaveConsumer(config::setServerVersion)
                .build());
        advanced.addEntry(eb.startBooleanToggle(text("Show debug messages in chat"), config.debug)
                .setDefaultValue(false)
                .setTooltip(text("Extra behind-the-scenes messages. Leave off unless you're troubleshooting."))
                .setSaveConsumer(val -> config.debug = val).build());
        advanced.addEntry(eb.startBooleanToggle(text("Anti-xray workaround (1.17 and older)"), config.antiXrayBypass)
                .setDefaultValue(true)
                .setTooltip(text("Asks the server to resend dungeon floor blocks if an anti-xray plugin hid them."),
                        text("Only used for dungeons on 1.17 and older worlds; has no effect on newer versions."))
                .setSaveConsumer(val -> config.antiXrayBypass = val).build());

        SubCategoryBuilder db = eb.startSubCategory(text("Public seed database")).setExpanded(false);
        db.add(eb.startBooleanToggle(text("Share found seeds publicly"), config.databaseSubmits)
                .setDefaultValue(false)
                .setTooltip(text("Posts the seed AND the server address to a public Google sheet."),
                        text("Only for servers with 10+ players online."))
                .setSaveConsumer(val -> config.databaseSubmits = val).build());
        db.add(eb.startBooleanToggle(text("Hide my name when sharing"), config.anonymusSubmits)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.anonymusSubmits = val).build());
        db.add(eb.startTextDescription(text("Open the public database").withStyle(st -> st
                        .withClickEvent(new ClickEvent.OpenUrl(DatabaseCommand.DATABASE_URL))
                        .withHoverEvent(new HoverEvent.ShowText(text("Google sheet")))
                        .withColor(ChatFormatting.BLUE)
                        .withUnderlined(true)))
                .build());
        advanced.addEntry(db.build());

        if (startTab != null) {
            for (ConfigCategory c : List.of(status, structures, display, advanced)) {
                if (c.getCategoryKey().getString().equals(startTab)) builder.setFallbackCategory(c);
            }
        }

        builder.setSavingRunnable(Config::save);
        return builder.build();
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> finderToggle(ConfigEntryBuilder eb, Finder.Type finder) {
        boolean defaultOn = switch (finder) {
            case END_GATEWAY, EMERALD_ORE, DESERT_WELL, WARPED_FUNGUS, BIOME -> false;
            default -> true;
        };
        MutableComponent name = Component.translatable(finder.nameKey).copy();
        if (!kaptainwutax.seedcrackerX.Features.isAvailable(finder)) {
            name.append(text("  (not in " + config.getServerVersion() + ")", ChatFormatting.DARK_GRAY));
        }
        return eb.startBooleanToggle(name, finder.enabled.get())
                .setDefaultValue(defaultOn)
                .setTooltip(text(FINDER_HELP.getOrDefault(finder, "")))
                .setSaveConsumer(val -> finder.enabled.set(val))
                .build();
    }

    public static void restoreStructures() {
        var preloaded = StructureSave.loadStructures();
        if (preloaded.isEmpty()) {
            Log.warn("data.restoreFailed");
            return;
        }
        for (RegionStructure.Data<?> data : preloaded) {
            SeedCracker.get().getDataStorage().addBaseData(data, DataAddedEvent.POKE_LIFTING);
        }
        Log.warn("data.restoreStructures", preloaded.size());
    }
}

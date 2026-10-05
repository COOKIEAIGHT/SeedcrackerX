package kaptainwutax.seedcrackerX.init;

import com.mojang.brigadier.CommandDispatcher;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.command.ClientCommand;
import kaptainwutax.seedcrackerX.command.DatabaseCommand;
import kaptainwutax.seedcrackerX.command.FinderCommand;
import kaptainwutax.seedcrackerX.command.HelpText;
import kaptainwutax.seedcrackerX.command.HudCommand;
import kaptainwutax.seedcrackerX.command.QuickCommand;
import kaptainwutax.seedcrackerX.command.RenderCommand;
import kaptainwutax.seedcrackerX.command.VersionCommand;
import kaptainwutax.seedcrackerX.config.ConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;

/**
 * one command per job, no doubles.
 *   /seedcracker            status + clickable commands
 *   status | seed | menu | hud | restore | clear | rescan | help
 *   render off|on|xray      finder <structure> on|off
 *   on | off | debug        version <v> | database
 */
public class ClientCommands {

    public static final String PREFIX = "seedcracker";
    public static final List<ClientCommand> COMMANDS = new ArrayList<>();

    static {
        COMMANDS.add(new QuickCommand("status", HelpText::status));
        COMMANDS.add(new QuickCommand("seed", HelpText::seed));
        COMMANDS.add(new QuickCommand("menu", () -> SeedCracker.get().getDataStorage().openGui = true));
        COMMANDS.add(new HudCommand());
        COMMANDS.add(new QuickCommand("restore", ConfigScreen::restoreStructures));
        COMMANDS.add(new QuickCommand("clear", HelpText::clear));
        COMMANDS.add(new QuickCommand("rescan", HelpText::rescan));
        COMMANDS.add(new RenderCommand());
        COMMANDS.add(new FinderCommand());
        COMMANDS.add(new QuickCommand("on", () -> HelpText.setActive(true)));
        COMMANDS.add(new QuickCommand("off", () -> HelpText.setActive(false)));
        COMMANDS.add(new QuickCommand("debug", HelpText::toggleDebug));
        COMMANDS.add(new VersionCommand());
        COMMANDS.add(new DatabaseCommand());
        COMMANDS.add(new QuickCommand("help", HelpText::help));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        COMMANDS.forEach(clientCommand -> clientCommand.register(dispatcher));
    }
}

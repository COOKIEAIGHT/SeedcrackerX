package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.config.ServerVersions;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** /seedcracker version shows the server version, /seedcracker version <version> sets it (1.8 to 26.3) */
public class VersionCommand extends ClientCommand {

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.executes(context -> this.printVersion());
        for (String version : ServerVersions.all()) {
            builder.then(literal(version).executes(context -> this.setVersion(version)));
        }
    }

    private int printVersion() {
        Config config = Config.get();
        sendFeedback("Server version: " + config.getServerVersion() + " (structure rules: " + config.getVersion().name + ")", ChatFormatting.AQUA);
        sendFeedback("Supported: " + ServerVersions.range() + ". Change with /seedcracker version <version>", ChatFormatting.GRAY);
        return 0;
    }

    private int setVersion(String version) {
        Config config = Config.get();
        config.setServerVersion(version);
        Config.save();
        String rules = config.getVersion().name;
        sendFeedback("Server version: " + version + (rules.equals(version) ? "" : " (uses " + rules + " structure rules)"), ChatFormatting.AQUA);
        return 0;
    }
}

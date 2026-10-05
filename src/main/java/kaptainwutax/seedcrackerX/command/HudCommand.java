package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kaptainwutax.seedcrackerX.config.Config;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;

/** /seedcracker hud turns the on-screen panel on or off */
public class HudCommand extends ClientCommand {

    @Override
    public String getName() {
        return "hud";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.executes(context -> {
            Config.get().hud = !Config.get().hud;
            Config.save();
            sendFeedback("SeedCracker panel " + (Config.get().hud ? "ON" : "OFF"), ChatFormatting.AQUA);
            return 0;
        });
    }
}

package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.util.Log;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * /seedcracker finder lists what it looks for (click to toggle),
 * /seedcracker finder shipwreck on|off turns one on or off.
 */
public class FinderCommand extends ClientCommand {

    @Override
    public String getName() {
        return "finder";
    }

    public static String id(Finder.Type type) {
        return type.name().toLowerCase();
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.executes(context -> this.list());
        for (Finder.Type type : Finder.Type.values()) {
            builder.then(literal(id(type))
                    .then(literal("on").executes(context -> this.set(type, true)))
                    .then(literal("off").executes(context -> this.set(type, false)))
                    .executes(context -> this.set(type, !type.enabled.get())));
        }
    }

    private int list() {
        Log.send(Component.literal("Looking for (click to turn on/off):").withStyle(ChatFormatting.GREEN));
        for (Finder.Type type : Finder.Type.values()) {
            boolean on = type.enabled.get();
            String cmd = "/seedcracker finder " + id(type) + (on ? " off" : " on");
            MutableComponent line = Component.literal(on ? " [ON]  " : " [OFF] ").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(Component.literal(Log.translate(type.nameKey)).withStyle(ChatFormatting.WHITE))
                    .withStyle(st -> st.withClickEvent(new ClickEvent.SuggestCommand(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd))));
            Log.send(line);
        }
        return 0;
    }

    private int set(Finder.Type type, boolean on) {
        type.enabled.set(on);
        Config.save();
        sendFeedback(Log.translate(type.nameKey) + ": " + (on ? "ON" : "OFF"), on ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return 0;
    }
}

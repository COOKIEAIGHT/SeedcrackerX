package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kaptainwutax.seedcrackerX.config.Config;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** /seedcracker render shows the outline mode, /seedcracker render off|on|xray changes it */
public class RenderCommand extends ClientCommand {

    @Override
    public String getName() {
        return "render";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.executes(context -> this.printRenderMode());
        for (Config.RenderType renderType : Config.RenderType.values()) {
            builder.then(literal(renderType.toString().toLowerCase()).executes(context -> this.setRenderMode(renderType)));
        }
    }

    private static String describe(Config.RenderType type) {
        return switch (type) {
            case OFF -> "off";
            case ON -> "on";
            case XRAY -> "xray (see through walls)";
        };
    }

    private int printRenderMode() {
        sendFeedback("Structure outlines: " + describe(Config.get().render), ChatFormatting.AQUA);
        sendFeedback("Change with /seedcracker render off | on | xray", ChatFormatting.GRAY);
        return 0;
    }

    private int setRenderMode(Config.RenderType renderType) {
        Config.get().render = renderType;
        Config.save();
        sendFeedback("Structure outlines: " + describe(renderType), ChatFormatting.AQUA);
        return 0;
    }
}

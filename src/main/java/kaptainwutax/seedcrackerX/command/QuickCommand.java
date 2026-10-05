package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** a simple one-word /seedcracker command */
public class QuickCommand extends ClientCommand {

    private final String name;
    private final Runnable action;

    public QuickCommand(String name, Runnable action) {
        this.name = name;
        this.action = action;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.executes(context -> {
            this.action.run();
            return 1;
        });
    }
}

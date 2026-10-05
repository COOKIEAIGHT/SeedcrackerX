package kaptainwutax.seedcrackerX.init;

import com.mojang.blaze3d.platform.InputConstants;
import kaptainwutax.seedcrackerX.config.ConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** a hotkey (J by default, change it in Controls) that opens the SeedCracker menu */
public final class SeedCrackerKeys {

    private static KeyMapping openMenu;

    private SeedCrackerKeys() {
    }

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("seedcrackerx", "main"));
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.seedcrackerx.open_menu",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenu.consumeClick()) {
                if (mc.gui.screen() == null && mc.level != null) {
                    mc.gui.setScreen(new ConfigScreen().getConfigScreenByCloth(null));
                }
            }
        });
    }

    /** the key's display name, or null if it isn't bound */
    public static String openMenuKeyName() {
        if (openMenu == null || openMenu.isUnbound()) return null;
        return openMenu.getTranslatedKeyMessage().getString();
    }
}

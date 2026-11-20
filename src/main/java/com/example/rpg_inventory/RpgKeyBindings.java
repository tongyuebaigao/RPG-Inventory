package com.example.rpg_inventory;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = RpgInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RpgKeyBindings {

    // 默认 B 键
    public static final KeyMapping OPEN_RPG_INVENTORY = new KeyMapping(
            "key.rpg_inventory.open",
            GLFW.GLFW_KEY_B,
            "key.categories.rpg_inventory");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_RPG_INVENTORY);
    }
}

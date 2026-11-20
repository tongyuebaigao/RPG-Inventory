package com.example.rpg_inventory;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RpgInventory.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // B 键：已开着本界面则关闭，否则发包开窗。
        // 注意：界面开着时 KeyMapping.click 不触发（KeyboardHandler 仅在无界面时计数），
        // 真正的"按键关闭"在 RPGInventoryScreen.keyPressed 里处理，这里仅作无界面时的开窗与兜底。
        while (RpgKeyBindings.OPEN_RPG_INVENTORY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RPGInventoryScreen) {
                mc.setScreen(null);
            } else {
                RpgInventory.CHANNEL.sendToServer(new EmptyPacket());
            }
        }
    }
}

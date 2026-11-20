package com.example.rpg_inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// 基础网络包（无数据）：客户端B键发给服务端 -> 服务端读玩家Capability -> openMenu 下发开窗包弹出GUI（Phase 4 开窗路径）
public class EmptyPacket {

    public static void encode(EmptyPacket packet, FriendlyByteBuf buf) {
        // 无数据可写
    }

    public static EmptyPacket decode(FriendlyByteBuf buf) {
        return new EmptyPacket();
    }

    public static void handle(EmptyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 只在服务端处理：包由客户端发来，sender 为空直接丢弃
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            RpgInventoryData data = RpgPlayerCapability.getInventory(sender);
            sender.openMenu(new SimpleMenuProvider(
                    (id, inv, buf) -> new RPGInventoryMenu(id, inv, data),
                    Component.literal("RPG库存")));
        });
        context.setPacketHandled(true);
    }
}

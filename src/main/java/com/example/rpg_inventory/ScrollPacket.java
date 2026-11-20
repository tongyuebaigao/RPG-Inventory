package com.example.rpg_inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// 平滑滚动包（客户端->服务端）：滚轮/拖拽滚动条时上报 scrollOffset(0.0~1.0)，
// 服务端据此逐行切片重算过滤视图并广播。过滤总数同步走 Menu 的 DataSlot，无需额外回包。
public class ScrollPacket {

    private final double offset;

    public ScrollPacket(double offset) {
        this.offset = offset;
    }

    public static void encode(ScrollPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.offset);
    }

    public static ScrollPacket decode(FriendlyByteBuf buf) {
        return new ScrollPacket(buf.readDouble());
    }

    public static void handle(ScrollPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (sender.containerMenu instanceof RPGInventoryMenu menu) {
                menu.selectScroll(packet.offset);
            }
        });
        context.setPacketHandled(true);
    }
}
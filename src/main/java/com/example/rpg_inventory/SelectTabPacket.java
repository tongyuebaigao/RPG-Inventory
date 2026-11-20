package com.example.rpg_inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// 选页包（客户端->服务端）：玩家点击分类标签页时上报索引，服务端据此重算过滤视图并广播。
// 服务端->客户端的选中态同步走 Menu 的 DataSlot，无需额外回包。
public class SelectTabPacket {

    private final int tabIndex;

    public SelectTabPacket(int tabIndex) {
        this.tabIndex = tabIndex;
    }

    public static void encode(SelectTabPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.tabIndex);
    }

    public static SelectTabPacket decode(FriendlyByteBuf buf) {
        return new SelectTabPacket(buf.readVarInt());
    }

    public static void handle(SelectTabPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (sender.containerMenu instanceof RPGInventoryMenu menu) {
                menu.selectTab(packet.tabIndex);
            }
        });
        context.setPacketHandled(true);
    }
}
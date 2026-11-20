package com.example.rpg_inventory;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RpgInventory.MODID) // Forge 总线，双方生效
public class RpgPlayerCapability {

    // 1.20.1 用 Token 方式获取 Capability，无需手动 register
    public static final Capability<RpgInventoryData> INVENTORY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation CAP_NAME =
            new ResourceLocation(RpgInventory.MODID, "rpg_inventory");

    // 数据层统一入口：取玩家的库存容器
    public static RpgInventoryData getInventory(Player player) {
        return player.getCapability(INVENTORY)
                .orElseThrow(() -> new IllegalStateException("rpg_inventory capability missing"));
    }

    // 挂载到每个玩家，Forge 自动经 serializeNBT 持久化进玩家的 ForgeCaps
    // 1.20.1 实体挂载事件泛型恒为 Entity（Entity 构造 super(Entity.class)），必须监听 <Entity> 再自行过滤 Player，
    // 监听 <Player> 会被事件总线按泛型精确匹配直接跳过（1.16 时代的 <Player> 写法在本版本失效）
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(CAP_NAME, new Provider());
        }
    }

    // 死亡重生时复制旧库存数据
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        RpgInventoryData oldInv = event.getOriginal().getCapability(INVENTORY).orElse(null);
        RpgInventoryData newInv = event.getEntity().getCapability(INVENTORY).orElse(null);
        if (oldInv != null && newInv != null) {
            newInv.deserializeNBT(oldInv.serializeNBT());
        }
    }

    // 读档/登录时预热：提前建分类内容 + 算好上次标签页缓存，使读档后第一次按 B 即秒开
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        RpgInventoryData data = player.getCapability(INVENTORY).orElse(null);
        if (data != null) {
            RPGInventoryMenu.preheatCache(player, data);
        }
    }

    private static class Provider implements ICapabilitySerializable<CompoundTag> {

        private final RpgInventoryData data = new RpgInventoryData();
        private final LazyOptional<RpgInventoryData> lazy = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return cap == INVENTORY ? lazy.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.deserializeNBT(nbt);
        }
    }
}

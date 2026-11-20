package com.example.rpg_inventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(RpgInventory.MODID)
public class RpgInventory {

    public static final String MODID = "rpg_inventory";

    // 网络通道
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    // 轻量级预留开关：true = 共享库存，false = 玩家独立库存
    public static boolean isSharedInventory = false;

    // GUI 用的空壳 MenuType
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final RegistryObject<MenuType<RPGInventoryMenu>> RPG_MENU =
            MENU_TYPES.register("rpg_inventory", () -> IForgeMenuType.create((id, inv, buf) -> new RPGInventoryMenu(id, inv)));

    public RpgInventory() {
        // 47.4.x 的 FML 只支持无参构造（或 FMLJavaModLoadingContext 参数），mod总线要自己拿
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MENU_TYPES.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 注册网络包，ID 0（块状lambda绕开 Forge 该版本返回类型不可访问的编译问题）
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(
                    0,
                    EmptyPacket.class,
                    EmptyPacket::encode,
                    EmptyPacket::decode,
                    EmptyPacket::handle);
            CHANNEL.registerMessage(
                    1,
                    SelectTabPacket.class,
                    SelectTabPacket::encode,
                    SelectTabPacket::decode,
                    SelectTabPacket::handle);
            CHANNEL.registerMessage(
                    2,
                    ScrollPacket.class,
                    ScrollPacket::encode,
                    ScrollPacket::decode,
                    ScrollPacket::handle);
        });
    }
}

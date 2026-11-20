package com.example.rpg_inventory.mixin;

import com.example.rpg_inventory.RPGInventoryScreen;
import com.example.rpg_inventory.util.NumberFormatUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// 数量文本格式化：原版 renderSlot 是 private 且无 Forge 钩子，只能 Mixin 接管它内部那一次
// renderItemDecorations 调用。第5个 String 参数本就是数量文本覆盖位（非 null 时原版直接用它）。
@Mixin(AbstractContainerScreen.class)
public abstract class RPGScreenCountMixin {

    @Redirect(
            method = "renderSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"
            )
    )
    private void rpg$formatSlotCount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, String text) {
        // 严格限定：只对本模组界面生效；text 非 null（拖拽预览的黄字上限）原样保留，绝不覆盖
        if ((Object) this instanceof RPGInventoryScreen && text == null && stack.getCount() >= 1000) {
            text = NumberFormatUtil.format(stack.getCount());
        }
        graphics.renderItemDecorations(font, stack, x, y, text);
    }
}
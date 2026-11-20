package com.example.rpg_inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public class RPGInventorySlot extends SlotItemHandler {

    private final RpgInventoryData data;
    private final RPGInventoryMenu menu;

    public RPGInventorySlot(RPGInventoryMenu menu, RpgInventoryData data, int index, int x, int y) {
        super(data, index, x, y);
        this.menu = menu;
        this.data = data;
    }

    // 视图位 -> 数据真实索引（仅服务端用）：按当前分类过滤映射。
    // 返回 -1 表示该视图格当前无对应物品（过滤后不足45个时的尾部空格）。
    private int resolveIndex() {
        return menu.mapToData(getSlotIndex());
    }

    @Override
    public ItemStack getItem() {
        // 客户端读固定镜像数组（绝不压缩，同步按视图位写入天然对齐，杜绝幽灵物品）
        if (menu.isClientSide()) {
            return menu.getClientStack(getSlotIndex());
        }
        int d = resolveIndex();
        return d >= 0 ? data.getStackInSlot(d) : ItemStack.EMPTY;
    }

    @Override
    public void set(ItemStack stack) {
        if (menu.isClientSide()) {
            menu.setClientStack(getSlotIndex(), stack);
        } else {
            int d = resolveIndex();
            if (d >= 0) {
                data.setStackInSlot(d, stack);
            }
        }
        setChanged();
    }

    // 归位闸门：物品只许落到"寻位算出来的那一格"（同类未满槽 > 第一空隙 > 表尾新槽）。
    // 客户端预测放行，服务端用真实数据索引权威比对，保证禁止乱放的核心逻辑不被动态映射破坏。
    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (menu.isClientSide()) {
            return true;
        }
        int d = resolveIndex();
        return d >= 0 && data.findInsertIndex(stack) == d;
    }

    @Override
    public boolean mayPickup(Player player) {
        if (menu.isClientSide()) {
            return !getItem().isEmpty();
        }
        int d = resolveIndex();
        return d >= 0 && !data.extractItem(d, 1, true).isEmpty();
    }

    // 出容器闸门：玩家拿取/丢弃每次最多带"该物品原版堆叠上限"（通常64），杜绝生存手持超堆叠。
    // 原版所有拿出路径(左键/右键/丢弃/双击收拢)都汇聚到 remove，这一处封顶即可；
    // 容器内部合并走 safeInsert + getMaxStackSize()=SLOT_LIMIT，不受此限。
    @Override
    public ItemStack remove(int amount) {
        if (menu.isClientSide()) {
            ItemStack cur = getItem();
            if (cur.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack out = cur.split(Math.min(amount, cur.getMaxStackSize()));
            setChanged();
            return out;
        }
        int d = resolveIndex();
        if (d < 0) {
            return ItemStack.EMPTY;
        }
        return data.extractItem(d, Math.min(amount, getItem().getMaxStackSize()), false);
    }

    // 显式放开原版槽位上限到 SLOT_LIMIT，打破64限制（原版拆分/合并/双击收拢的数量数学都走这两个方法）
    @Override
    public int getMaxStackSize() {
        return RpgInventoryData.SLOT_LIMIT;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return RpgInventoryData.SLOT_LIMIT;
    }
}
package com.example.rpg_inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;

// RPG库存数据层：List 存槽位、无固定上限（满了自动加槽）；单格上限100万，不受原版64堆叠限制
// 实现 IItemHandlerModifiable：SlotItemHandler 的写入路径强依赖 setStackInSlot，缺了它界面收不到同步数据
public class RpgInventoryData implements IItemHandlerModifiable {

    // 单格上限：改这个数字即可调整每格容量
    public static final int SLOT_LIMIT = 1_000_000;

    private final List<ItemStack> slots = new ArrayList<>();

    // 寻位：先找同类未满槽合并，否则第一个空槽，都没有则返回表尾追加位
    public int findInsertIndex(ItemStack stack) {
        int firstEmpty = -1;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack slot = slots.get(i);
            if (slot.isEmpty()) {
                if (firstEmpty < 0) {
                    firstEmpty = i;
                }
                continue;
            }
            if (ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < SLOT_LIMIT) {
                return i;
            }
        }
        return firstEmpty >= 0 ? firstEmpty : slots.size();
    }

    // 推荐插入入口：始终归位到 findInsertIndex 指定的槽（同类合并优先，空隙回填，尾部自动加槽）
    public ItemStack insertStack(ItemStack stack) {
        ItemStack rest = stack.copy();
        while (!rest.isEmpty()) {
            int target = findInsertIndex(rest);
            ItemStack current = getStackInSlot(target);
            int moved = Math.min(SLOT_LIMIT - current.getCount(), rest.getCount());
            if (moved <= 0) {
                return rest; // 理论不可达：findInsertIndex 保证目标槽必有空位
            }
            if (current.isEmpty()) {
                setStackInSlot(target, rest.copyWithCount(moved));
            } else {
                current.grow(moved);
            }
            rest.shrink(moved);
        }
        return rest;
    }

    @Override
    public int getSlots() {
        return slots.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : ItemStack.EMPTY;
    }

    // 打破64限制：本方法按 SLOT_LIMIT 合并，slot 越界时自动扩槽（只允许追加到末尾+1）
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot > slots.size()) {
            return stack;
        }
        while (slot >= slots.size()) {
            slots.add(ItemStack.EMPTY);
        }
        ItemStack current = slots.get(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, stack)) {
            return stack;
        }
        int moved = Math.min(SLOT_LIMIT - current.getCount(), stack.getCount());
        if (!simulate) {
            if (current.isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(moved);
                slots.set(slot, copy);
            } else {
                current.setCount(current.getCount() + moved);
            }
        }
        ItemStack leftover = stack.copy();
        leftover.shrink(moved);
        return leftover;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = slots.get(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, current.getCount());
        ItemStack out = current.copy();
        out.setCount(taken);
        if (!simulate) {
            current.shrink(taken);
            compact();
        }
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    // 直接整格写入（原版容器同步、Slot.set 都走这里）；越界时自动扩槽
    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        while (slot >= slots.size()) {
            slots.add(ItemStack.EMPTY);
        }
        slots.set(slot, stack.copy());
        compact();
    }

    // 紧凑排列：一旦某格被掏空，移除所有 EMPTY 占位，后面非空物品依次前移，列表永远不留空隙
    private void compact() {
        slots.removeIf(ItemStack::isEmpty);
    }

    // 自定义NBT：Count 用 Int 写，绕开原版 short 的 32767 上限
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            tag.putInt("Count", stack.getCount());
            if (stack.hasTag()) {
                tag.put("ItemTag", stack.getTag().copy());
            }
            list.add(tag);
        }
        root.put("Slots", list);
        return root;
    }

    public void deserializeNBT(CompoundTag root) {
        slots.clear();
        ListTag list = root.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("id"))));
            stack.setCount(tag.getInt("Count"));
            if (tag.contains("ItemTag", Tag.TAG_COMPOUND)) {
                stack.setTag(tag.getCompound("ItemTag").copy());
            }
            slots.add(stack);
        }
    }
}

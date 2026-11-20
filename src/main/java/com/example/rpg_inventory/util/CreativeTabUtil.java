package com.example.rpg_inventory.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.CreativeModeTabRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 分类数据工具：动态取所有已加载模组注册的创造标签页（原版分类 + 模组独立页），并提供物品归属判定。
// getSortedCreativeModeTabs 由 Forge 拓扑排序，已排除 搜索/快捷栏/生存背包/OP 四个特殊默认页，
// 两端同源同序，selectedTabIndex 据此对齐；那四个特殊页对应的列 5-9 留空给别的功能。
// 关键：tryRebuildTabContents 原版只在客户端创造界面调用，服务端分类内容默认为空，
// 因此服务端过滤前必须用玩家自身的 特性开关/权限/注册表 主动构建一次（带缓存，参数不变则跳过）。
public final class CreativeTabUtil {

    // 每个分类页对应的物品种类集合（按 Item 判定，忽略 NBT/数量，适配无限库存里的任意变体）
    private static final Map<CreativeModeTab, Set<Item>> ITEM_SETS = new HashMap<>();

    // 基准权重表：每个分类页里 Item -> 它在 tab.getDisplayItems() 中首次出现的序号（创造标签页顺序）。
    // 排序据此严格对齐原版展示序；不在基准列表里的物品取 MAX_VALUE 排到最后。
    private static final Map<CreativeModeTab, Map<Item, Integer>> ITEM_WEIGHTS = new HashMap<>();

    private CreativeTabUtil() {
    }

    // 全部已排序创造标签页（原版分类 + 模组独立页）。返回 Forge 的不可变实时视图，资源重载后自动更新。
    public static List<CreativeModeTab> getTabs() {
        return CreativeModeTabRegistry.getSortedCreativeModeTabs();
    }

    // 确保分类内容已构建，并在内容变化时刷新物品种类/权重缓存。服务端每次过滤前调用。
    // 返回 true 表示本次重建了内容（权重表已变），调用方据此作废排序缓存。
    public static boolean ensureContentsBuilt(Player player) {
        boolean rebuilt = CreativeModeTabs.tryRebuildTabContents(
                player.level().enabledFeatures(),
                player.hasPermissions(2),
                player.level().registryAccess());
        if (rebuilt || ITEM_SETS.isEmpty()) {
            ITEM_SETS.clear();
            ITEM_WEIGHTS.clear();
            for (CreativeModeTab tab : getTabs()) {
                Set<Item> items = new HashSet<>();
                Map<Item, Integer> weights = new HashMap<>();
                int w = 0;
                // getDisplayItems 含 PARENT_TAB_ONLY 物品（如满级附魔书），比搜索集更忠实于"分类展示"
                for (ItemStack stack : tab.getDisplayItems()) {
                    items.add(stack.getItem());
                    // 首次出现序号即基准权重，重复 Item（不同NBT展示变体）沿用首序
                    weights.putIfAbsent(stack.getItem(), w);
                    w++;
                }
                ITEM_SETS.put(tab, items);
                ITEM_WEIGHTS.put(tab, weights);
            }
            return true;
        }
        return false;
    }

    // 物品是否属于某分类页（按物品种类，O(1)）
    public static boolean belongsTo(CreativeModeTab tab, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Set<Item> set = ITEM_SETS.get(tab);
        return set != null && set.contains(stack.getItem());
    }

    // 物品在创造标签页基准列表里的排序权重；找不到（特殊变体/未构建）返回 MAX_VALUE 排到最后
    public static int getWeight(CreativeModeTab tab, ItemStack stack) {
        Map<Item, Integer> weights = ITEM_WEIGHTS.get(tab);
        if (weights == null) {
            return Integer.MAX_VALUE;
        }
        Integer w = weights.get(stack.getItem());
        return w == null ? Integer.MAX_VALUE : w;
    }
}
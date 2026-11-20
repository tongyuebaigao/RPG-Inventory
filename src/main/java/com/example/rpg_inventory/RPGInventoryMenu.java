package com.example.rpg_inventory;

import com.example.rpg_inventory.util.CreativeTabUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class RPGInventoryMenu extends AbstractContainerMenu {

    // 物品页：5行 x 9列 = 45 格
    public static final int PAGE_ROWS = 5;
    public static final int PAGE_COLS = 9;
    public static final int PAGE_SIZE = PAGE_ROWS * PAGE_COLS;

    // 对齐 195x136 创造底图：格子区起点 (9,18)，每格间距 18px
    public static final int GRID_X = 9;
    public static final int GRID_Y = 18;
    public static final int CELL = 18;

    // 玩家快捷栏行：原版创造界面坐标 y=112
    public static final int HOTBAR_Y = 112;

    // 排序模式：默认创造标签页顺序；数量升/降序优先级高于创造顺序（选择器UI留待下阶段）
    public static final int SORT_CREATIVE = 0;
    public static final int SORT_COUNT_ASC = 1;
    public static final int SORT_COUNT_DESC = 2;

    // 一、状态保持：记住上次选中的标签页（静态，跨开关界面保留），构造时恢复，不每次默认第一页
    private static int lastTabIndex = 0;

    // 二、秒开缓存（静态，服务端权威）：缓存"某玩家数据 + 某标签页"过滤+排序后的真实索引列表。
    // 关闭时预热(removed)，下次同标签页打开直接命中跳过计算；底层数据一变即作废(clicked/内容重建)。
    // TODO: 未来支持多人服务器时，改为按 UUID 区分的 Map 缓存，并添加登出清理钩子
    private static RpgInventoryData cacheOwner = null;
    private static int cacheTab = -1;
    private static int[] cacheIndices = null;
    private static int cacheCount = 0;
    private static boolean cacheDirty = true;

    private final RpgInventoryData data;
    private final boolean serverSide;
    private final Player player;

    // 当前选中的分类页索引：构造时恢复上次记忆（对应 CreativeTabUtil.getTabs() 的全局序）
    private int selectedTabIndex = lastTabIndex;
    // 平滑滚动偏移 0.0~1.0（替代旧的 currentPage 按钮翻页）：客户端驱动滚动条，服务端据此逐行切片
    private double scrollOffset = 0.0;
    // 当前分类过滤后的匹配总数（服务端算，DataSlot 同步给客户端用于滚动上限/滚动条）
    private int filteredCount = 0;
    // 当前排序模式（默认创造顺序）
    private int sortMode = SORT_CREATIVE;

    // 服务端过滤视图：visibleIndices[视图位] = 无限库存里的真实数据索引；-1 表示该格无物品。
    // 仅服务端维护：过滤 -> 权重表排序 -> 按 scrollOffset 逐行切片。点击拿取/存入据此映射真实索引。
    private final int[] visibleIndices = new int[PAGE_SIZE];

    // 客户端显示镜像：固定 45 格、绝不压缩（仿原版 ItemPickerMenu 的 CONTAINER）。
    // 同步按视图位写入数组，列表收缩也不会错位 —— 这是消除"幽灵物品"的关键。
    private final ItemStack[] clientView = new ItemStack[PAGE_SIZE];

    // 客户端构造：本地显示容器，内容由服务端同步覆盖
    public RPGInventoryMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new RpgInventoryData(), false);
    }

    // 服务端构造：挂载玩家的 Capability 数据
    public RPGInventoryMenu(int windowId, Inventory playerInventory, RpgInventoryData data) {
        this(windowId, playerInventory, data, true);
    }

    private RPGInventoryMenu(int windowId, Inventory playerInventory, RpgInventoryData data, boolean serverSide) {
        super(RpgInventory.RPG_MENU.get(), windowId);
        this.data = data;
        this.serverSide = serverSide;
        this.player = playerInventory.player;
        Arrays.fill(visibleIndices, -1);
        Arrays.fill(clientView, ItemStack.EMPTY);
        for (int row = 0; row < PAGE_ROWS; row++) {
            for (int col = 0; col < PAGE_COLS; col++) {
                this.addSlot(new RPGInventorySlot(this, data, row * PAGE_COLS + col,
                        GRID_X + col * CELL, GRID_Y + row * CELL));
            }
        }
        // 玩家快捷栏9格（索引45-53），坐标同原版创造界面 Slot(inv, i, 9 + i*18, 112)
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, GRID_X + i * CELL, HOTBAR_Y));
        }
        // 选中页/过滤总数同步：服务端->客户端走原版 DataSlot 机制（两端添加顺序/数量一致）
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return selectedTabIndex;
            }

            @Override
            public void set(int value) {
                selectedTabIndex = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return filteredCount;
            }

            @Override
            public void set(int value) {
                filteredCount = value;
            }
        });
        if (serverSide) {
            recomputeView();
        }
    }

    public RpgInventoryData getData() {
        return data;
    }

    public boolean isClientSide() {
        return !serverSide;
    }

    public int getSelectedTabIndex() {
        return selectedTabIndex;
    }

    public int getFilteredCount() {
        return filteredCount;
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    public int getSortMode() {
        return sortMode;
    }

    // 记录上次选中标签页（selectTab / 客户端 onClose 调用），下次构造据此恢复
    public static void rememberTab(int index) {
        lastTabIndex = index;
    }

    // 作废秒开缓存：底层无限库存数据改变（放入/取出/合并/紧凑前移）或分类内容重建时调用
    public static void invalidateCache() {
        cacheDirty = true;
    }

    // 可滚动行数（原版 ItemPickerMenu.calculateRowCount）：总行数向上取整 - 可见5行
    public int getRowCount() {
        return Math.max(0, Mth.positiveCeilDiv(filteredCount, PAGE_COLS) - PAGE_ROWS);
    }

    public boolean canScroll() {
        return filteredCount > PAGE_SIZE;
    }

    // 滚动偏移 -> 行号（原版 getRowIndexForScroll）
    public int getRowIndexForScroll(double offset) {
        return Math.max((int) (offset * getRowCount() + 0.5), 0);
    }

    // 滚轮输入 -> 新偏移（原版 subtractInputFromScroll）：每格滚动 1/rowCount
    public double subtractInputFromScroll(double offset, double delta) {
        int rowCount = getRowCount();
        if (rowCount <= 0) {
            return offset;
        }
        return Mth.clamp(offset - delta / rowCount, 0.0, 1.0);
    }

    // 客户端镜像读写（固定数组，按视图位直存直取，绝不压缩）
    public ItemStack getClientStack(int viewIndex) {
        return viewIndex >= 0 && viewIndex < PAGE_SIZE ? clientView[viewIndex] : ItemStack.EMPTY;
    }

    public void setClientStack(int viewIndex, ItemStack stack) {
        if (viewIndex >= 0 && viewIndex < PAGE_SIZE) {
            clientView[viewIndex] = stack;
        }
    }

    // 视图位 -> 数据真实索引。服务端用过滤映射；客户端恒等（镜像数组天然按视图位对齐）
    public int mapToData(int viewIndex) {
        if (!serverSide) {
            return viewIndex;
        }
        if (viewIndex < 0 || viewIndex >= PAGE_SIZE) {
            return -1;
        }
        return visibleIndices[viewIndex];
    }

    // 切换分类页：客户端本地立即更新高亮并发包；服务端重算视图并广播。切分类滚动归零。
    public void selectTab(int index) {
        List<CreativeModeTab> tabs = CreativeTabUtil.getTabs();
        if (tabs.isEmpty()) {
            return;
        }
        if (index < 0) {
            index = 0;
        }
        if (index >= tabs.size()) {
            index = tabs.size() - 1;
        }
        this.selectedTabIndex = index;
        rememberTab(index);
        this.scrollOffset = 0.0;
        if (serverSide) {
            recomputeView();
            broadcastFullState();
        }
    }

    // 平滑滚动：客户端本地立即更新偏移（滚动条即时跟手）并发包；服务端按行切片重算视图并广播
    public void selectScroll(double offset) {
        this.scrollOffset = Mth.clamp(offset, 0.0, 1.0);
        if (serverSide) {
            recomputeView();
            broadcastFullState();
        }
    }

    // 切换排序模式（选择器UI下阶段接入）：服务端重排视图并广播
    public void selectSort(int mode) {
        this.sortMode = Mth.clamp(mode, SORT_CREATIVE, SORT_COUNT_DESC);
        if (serverSide) {
            recomputeView();
            broadcastFullState();
        }
    }

    // 排序比较器：数量模式优先（升/降），其次创造标签页权重，最后按真实索引（存入顺序）稳定兜底
    private static Comparator<Integer> buildComparator(RpgInventoryData data, int sortMode, CreativeModeTab tab) {
        return (a, b) -> {
            ItemStack sa = data.getStackInSlot(a);
            ItemStack sb = data.getStackInSlot(b);
            if (sortMode == SORT_COUNT_ASC || sortMode == SORT_COUNT_DESC) {
                int c = Integer.compare(sa.getCount(), sb.getCount());
                if (sortMode == SORT_COUNT_DESC) {
                    c = -c;
                }
                if (c != 0) {
                    return c;
                }
            }
            int w = Integer.compare(CreativeTabUtil.getWeight(tab, sa), CreativeTabUtil.getWeight(tab, sb));
            if (w != 0) {
                return w;
            }
            return Integer.compare(a, b);
        };
    }

    // 过滤+排序，返回真实索引数组（前提：分类内容已构建）。recomputeView 未命中与登录预热共用。
    private static int[] buildSortedIndices(RpgInventoryData data, int tabIndex, int sortMode) {
        List<CreativeModeTab> tabs = CreativeTabUtil.getTabs();
        if (tabs.isEmpty() || tabIndex < 0 || tabIndex >= tabs.size()) {
            return new int[0];
        }
        CreativeModeTab tab = tabs.get(tabIndex);
        int total = data.getSlots();
        List<Integer> filtered = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            ItemStack stack = data.getStackInSlot(i);
            if (!stack.isEmpty() && CreativeTabUtil.belongsTo(tab, stack)) {
                filtered.add(i);
            }
        }
        filtered.sort(buildComparator(data, sortMode, tab));
        int[] sorted = new int[filtered.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = filtered.get(i);
        }
        return sorted;
    }

    // 登录(读档)预热：提前构建分类内容 + 算好上次标签页的过滤排序缓存，
    // 使读档后第一次按 B 即命中缓存秒开（否则首次要建表+全量过滤排序，卡顿）。
    public static void preheatCache(Player player, RpgInventoryData data) {
        if (CreativeTabUtil.ensureContentsBuilt(player)) {
            invalidateCache();
        }
        List<CreativeModeTab> tabs = CreativeTabUtil.getTabs();
        if (tabs.isEmpty()) {
            return;
        }
        int tabIndex = Mth.clamp(lastTabIndex, 0, tabs.size() - 1);
        int[] sorted = buildSortedIndices(data, tabIndex, SORT_CREATIVE);
        cacheOwner = data;
        cacheTab = tabIndex;
        cacheIndices = sorted;
        cacheCount = sorted.length;
        cacheDirty = false;
    }

    // 先把无限库存里属于当前分类的物品过滤成子列表，按权重表+排序模式排序，
    // 再按 scrollOffset 逐行（9个/行）切出 45 格映射到视图。过滤、排序、滚动同时生效。
    // 秒开缓存：同一玩家数据+同一标签页且未作废时，直接复用已过滤排序的真实索引，跳过遍历与排序。
    private void recomputeView() {
        if (!serverSide) {
            return;
        }
        // 分类内容/权重重建则作废缓存（排序基准已变）
        if (CreativeTabUtil.ensureContentsBuilt(player)) {
            invalidateCache();
        }
        Arrays.fill(visibleIndices, -1);
        filteredCount = 0;
        List<CreativeModeTab> tabs = CreativeTabUtil.getTabs();
        if (tabs.isEmpty()) {
            return;
        }
        if (selectedTabIndex < 0 || selectedTabIndex >= tabs.size()) {
            selectedTabIndex = 0;
        }
        int[] sorted;
        if (!cacheDirty && cacheOwner == data && cacheTab == selectedTabIndex && cacheIndices != null) {
            // 缓存命中：跳过过滤+排序（0 计算秒开）
            sorted = cacheIndices;
            filteredCount = cacheCount;
        } else {
            sorted = buildSortedIndices(data, selectedTabIndex, sortMode);
            filteredCount = sorted.length;
            // 写入静态缓存，供关闭预热/下次同页打开复用
            cacheOwner = data;
            cacheTab = selectedTabIndex;
            cacheIndices = sorted;
            cacheCount = filteredCount;
            cacheDirty = false;
        }
        int rowCount = getRowCount();
        int rowIndex = Math.min(getRowIndexForScroll(scrollOffset), rowCount);
        int start = rowIndex * PAGE_COLS;
        for (int v = 0; v < PAGE_SIZE && start + v < filteredCount; v++) {
            visibleIndices[v] = sorted[start + v];
        }
    }

    // 点击落点改写。doClick 是 private，clicked 是唯一公开入口。
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // 任何槽位点击都可能改动底层数据（放入/取出/合并/紧凑前移）：先作废秒开缓存，
        // 后续 recomputeView 必重算并刷新缓存，杜绝复用过期索引导致映射错位。
        if (serverSide) {
            invalidateCache();
        }
        // F 键（副手交换 button=40）双向存入：客户端不做预测，服务端权威处理后全量同步
        if (type == ClickType.SWAP && button == 40) {
            if (serverSide) {
                storeByOffhandKey(slotId);
                recomputeView();
                broadcastFullState();
            }
            return;
        }
        if (serverSide && slotId >= 0 && slotId < PAGE_SIZE) {
            if (type == ClickType.PICKUP && !getCarried().isEmpty()) {
                // 手持物品点格子：自动归位并入无限库存（不必属于当前分类，存入即可，过滤后可能不显示）
                setCarried(data.insertStack(getCarried()));
                recomputeView();
                broadcastFullState();
                return;
            }
            if (type == ClickType.SWAP && button >= 0 && button < 9) {
                // 数字键双向交换（恢复原版直觉），快捷栏永远只进原版上限以内的数量
                int d = mapToData(slotId);
                ItemStack hot = player.getInventory().getItem(button);
                ItemStack grid = d >= 0 ? data.getStackInSlot(d) : ItemStack.EMPTY;
                if (hot.isEmpty() && grid.isEmpty()) {
                    return; // 空格子 + 空快捷栏：直接取消
                }
                if (!hot.isEmpty()) {
                    // 进格子方向：快捷栏物品并入无限库存（同类合并优先、自动归位），快捷栏清空
                    player.getInventory().setItem(button, data.insertStack(hot));
                }
                // 进快捷栏方向：从被点格子最多取该物品原版上限（通常64），多余留在库存
                grid = d >= 0 ? data.getStackInSlot(d) : ItemStack.EMPTY;
                if (!grid.isEmpty() && player.getInventory().getItem(button).isEmpty()) {
                    int take = Math.min(grid.getCount(), grid.getMaxStackSize());
                    player.getInventory().setItem(button, grid.copyWithCount(take));
                    data.extractItem(d, take, false);
                }
                recomputeView();
                broadcastFullState();
                return;
            }
            // 其余点击（拿取/丢弃/双击收拢/拖拽）交给原版 doClick，槽位已动态映射到真实数据
            super.clicked(slotId, button, type, player);
            recomputeView();
            broadcastFullState();
            return;
        }
        super.clicked(slotId, button, type, player);
        if (serverSide) {
            recomputeView();
            broadcastFullState();
        }
    }

    // F 键双向存入：指格子->收玩家背包/快捷栏(36格，不含盔甲/副手)里同类物品；指快捷栏->存该格物品
    private void storeByOffhandKey(int slotId) {
        Inventory inv = player.getInventory();
        if (slotId >= 0 && slotId < PAGE_SIZE) {
            int d = mapToData(slotId);
            ItemStack target = d >= 0 ? data.getStackInSlot(d) : ItemStack.EMPTY;
            if (target.isEmpty()) {
                return;
            }
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, target)) {
                    inv.setItem(i, data.insertStack(s));
                }
            }
        } else if (slotId >= PAGE_SIZE && slotId < PAGE_SIZE + 9) {
            int hot = slotId - PAGE_SIZE;
            ItemStack s = inv.getItem(hot);
            if (!s.isEmpty()) {
                inv.setItem(hot, data.insertStack(s));
            }
        }
    }

    // SHIFT 快速转移。格子->玩家：一次点击只搬"一组"（原版上限，通常64），装不下的原封不动
    // 留在无限库存，绝不溢出掉落；玩家->格子：整组并入（快捷栏本身≤64，动态容量必全收）
    // 客户端不做预测（返回 EMPTY），服务端权威搬运后全量同步，避免镜像与真实数据不一致。
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!serverSide) {
            return ItemStack.EMPTY;
        }
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        ItemStack start = slot.getItem();
        if (start.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (index < PAGE_SIZE) {
            // 格子 -> 玩家：本次最多搬 start.getMaxStackSize() 个（一组），用真实数据索引扣减
            int d = mapToData(index);
            if (d < 0) {
                return ItemStack.EMPTY;
            }
            int limit = Math.min(start.getCount(), start.getMaxStackSize());
            ItemStack toMove = start.copyWithCount(limit);
            int before = toMove.getCount();
            moveItemStackTo(toMove, PAGE_SIZE, PAGE_SIZE + 9, false); // 先并入快捷栏行
            if (!toMove.isEmpty()) {
                player.getInventory().add(toMove); // 再进背包其余格（内部合并/填空位）
            }
            int moved = before - toMove.getCount(); // 实际进入玩家背包的数量
            if (moved > 0) {
                data.extractItem(d, moved, false); // 只扣实际搬走的，触发紧凑前移
                slot.onTake(player, start.copyWithCount(moved));
            }
            return ItemStack.EMPTY; // 终止原版 QUICK_MOVE 循环：一次点击只搬一组，余量留库存
        }
        // 玩家侧(快捷栏) -> 格子：同类合并优先、自动归位、表尾加槽
        ItemStack remainder = data.insertStack(start.copy());
        slot.set(remainder);
        if (remainder.getCount() == start.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, remainder);
        return remainder;
    }

    // 二、关闭预热：服务端关窗钩子（客户端 onClose -> ContainerClosePacket -> doCloseContainer -> removed）。
    // 客户端 onClose 拿不到真实 Capability 数据，故过滤+排序缓存的预热放在服务端 removed：
    // 确保当前标签页结果已缓存，下次同页打开 0 计算秒开。
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (serverSide) {
            recomputeView();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
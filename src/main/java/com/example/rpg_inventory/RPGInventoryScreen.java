package com.example.rpg_inventory;

import com.example.rpg_inventory.util.CreativeTabUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RPGInventoryScreen extends AbstractContainerScreen<RPGInventoryMenu> {

    // 无选中标签时的回退底图（原版创造物品栏）
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tab_items.png");

    // 滚动条滑块贴图：固定用原版 tabs.png（u=232 启用 / 244 禁用，v=0，12x15），
    // 不随模组标签自定义贴图变化，保证滑块精灵一定存在。
    private static final ResourceLocation SCROLLER_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");

    // 每页标签数（原版 Forge：上排5 + 下排5 = 10），占列 0-4；列 5-9 留空给别的功能
    private static final int TABS_PER_PAGE = 10;

    // 数字键按住状态：GLFW 长按会持续派发 REPEAT 事件，用此表把每次按下限制为只触发一次交换
    private final boolean[] hotbarHeld = new boolean[9];
    // 副手交换键(F)按住状态：与数字键同根因，同样限制为单次触发
    private boolean offhandHeld = false;

    // 标签页分页（纯客户端显示）：tabPages.get(currentTabPage) 是当前可见的 ≤10 个标签
    private final List<List<CreativeModeTab>> tabPages = new ArrayList<>();
    private int currentTabPage = 0;

    // 滚动条拖拽状态（原版 scrolling）：按下滑块区域置真，拖拽时据鼠标Y算 scrollOffset
    private boolean scrolling = false;

    public RPGInventoryScreen(RPGInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // 与原版创造界面同尺寸：宽195 高136
        this.imageWidth = 195;
        this.imageHeight = 136;
    }

    private List<CreativeModeTab> allTabs() {
        return CreativeTabUtil.getTabs();
    }

    @Override
    protected void init() {
        super.init(); // AbstractContainerScreen.init 设定 leftPos/topPos
        buildTabPages();
        // < > 翻"标签页"按钮（仅多页时显示），位置/尺寸同原版创造界面
        if (tabPages.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> scrollTabPage(-1))
                    .pos(this.leftPos, this.topPos - 50).size(20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> scrollTabPage(1))
                    .pos(this.leftPos + this.imageWidth - 20, this.topPos - 50).size(20, 20).build());
        }
    }

    // 把全局已排序标签按 10 个一页分组；初始定位到选中标签所在页
    private void buildTabPages() {
        tabPages.clear();
        List<CreativeModeTab> all = allTabs();
        for (int i = 0; i < all.size(); i += TABS_PER_PAGE) {
            tabPages.add(new ArrayList<>(all.subList(i, Math.min(i + TABS_PER_PAGE, all.size()))));
        }
        if (tabPages.isEmpty()) {
            tabPages.add(new ArrayList<>());
        }
        int sel = this.menu.getSelectedTabIndex();
        currentTabPage = Math.max(0, Math.min(sel / TABS_PER_PAGE, tabPages.size() - 1));
    }

    private void scrollTabPage(int delta) {
        currentTabPage = Math.max(0, Math.min(currentTabPage + delta, tabPages.size() - 1));
    }

    private List<CreativeModeTab> currentPageTabs() {
        return tabPages.get(currentTabPage);
    }

    // 原版 getTabX：非右对齐列*27；右对齐(特殊默认页)镜像到右侧。我们已排除特殊默认页，
    // 故只走 col*27（列 0-4），列 5-9 天然留空。
    private int getTabX(CreativeModeTab tab, int col) {
        if (tab.isAlignedRight()) {
            return this.imageWidth - 27 * (7 - col) + 1;
        }
        return col * 27;
    }

    // 原版 getTabY（命中区）：上排 -32，下排 +imageHeight
    private int getTabY(boolean top) {
        return top ? -32 : this.imageHeight;
    }

    private boolean checkTabClicked(CreativeModeTab tab, int localIndex, double localX, double localY) {
        int x = getTabX(tab, localIndex % 5);
        int y = getTabY(localIndex < 5);
        return localX >= x && localX <= x + 26 && localY >= y && localY <= y + 32;
    }

    // 命中测试：返回鼠标下标签的全局索引（用于 selectTab/发包），无则 -1
    private int tabAt(double localX, double localY) {
        List<CreativeModeTab> page = currentPageTabs();
        for (int j = 0; j < page.size(); j++) {
            CreativeModeTab tab = page.get(j);
            if (tab.shouldDisplay() && checkTabClicked(tab, j, localX, localY)) {
                return currentTabPage * TABS_PER_PAGE + j;
            }
        }
        return -1;
    }

    // 原版 insideScrollbar：右侧滚动条命中区 x[175,189) y[18,130)
    private boolean insideScrollbar(double mouseX, double mouseY) {
        int k = this.leftPos + 175;
        int l = this.topPos + 18;
        return mouseX >= k && mouseY >= l && mouseX < k + 14 && mouseY < l + 112;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        List<CreativeModeTab> all = allTabs();
        int sel = this.menu.getSelectedTabIndex();
        List<CreativeModeTab> page = currentPageTabs();
        // 先画当前页未选中标签（绝不画其他隐藏页的图标）
        for (int j = 0; j < page.size(); j++) {
            int global = currentTabPage * TABS_PER_PAGE + j;
            CreativeModeTab tab = page.get(j);
            if (global != sel && tab.shouldDisplay()) {
                renderTabButton(g, tab, j, false);
            }
        }
        // 底图：用选中标签自己的背景（原版一致），无选中则回退创造物品栏底图
        ResourceLocation bg = BACKGROUND;
        if (sel >= 0 && sel < all.size()) {
            bg = all.get(sel).getBackgroundLocation();
        }
        g.blit(bg, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        // 右侧滚动条滑块（原版几何：x=175，y=18+(int)(95*scrollOffset)，精灵12x15）
        int j = this.leftPos + 175;
        int k = this.topPos + 18;
        int i = k + 112;
        int scrollerY = k + (int) ((float) (i - k - 17) * this.menu.getScrollOffset());
        g.blit(SCROLLER_TEXTURE, j, scrollerY, 232 + (this.menu.canScroll() ? 0 : 12), 0, 12, 15);
        // 选中标签压底图边缘（仅当它在当前页）
        if (sel >= 0 && sel < all.size() && sel / TABS_PER_PAGE == currentTabPage) {
            renderTabButton(g, all.get(sel), sel % TABS_PER_PAGE, true);
        }
    }

    // 原版 renderTabButton：u=列*26，v=(上排0/下排64)+(选中32)，blit 26x32，图标抬高 z=100
    private void renderTabButton(GuiGraphics g, CreativeModeTab tab, int localIndex, boolean selected) {
        boolean top = localIndex < 5;
        int col = localIndex % 5;
        int x = this.leftPos + getTabX(tab, col);
        int y = this.topPos + (top ? -28 : this.imageHeight - 4);
        int u = col * 26;
        int v = (top ? 0 : 64) + (selected ? 32 : 0);
        RenderSystem.enableBlend();
        g.blit(tab.getTabsImage(), x, y, u, v, 26, 32);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 100.0F);
        int ix = x + 5;
        int iy = y + 8 + (top ? 1 : -1);
        ItemStack icon = tab.getIconItem();
        g.renderItem(icon, ix, iy);
        g.renderItemDecorations(this.font, icon, ix, iy);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        List<CreativeModeTab> all = allTabs();
        int sel = this.menu.getSelectedTabIndex();
        if (sel >= 0 && sel < all.size()) {
            CreativeModeTab tab = all.get(sel);
            if (tab.showTitle()) {
                g.drawString(this.font, tab.getDisplayName(), 8, 6, tab.getLabelColor(), false);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // 原版容器界面 render 不铺暗色背景，世界会直接透出；这里 fill 一层半透明纯黑（类似箱子暗化）
        g.fill(0, 0, this.width, this.height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g); // AbstractContainerScreen.render 不会主动调它，须自己先铺背景
        super.render(g, mouseX, mouseY, partialTick);
        // 标签页名称提示
        int i = tabAt(mouseX - this.leftPos, mouseY - this.topPos);
        if (i >= 0) {
            g.renderTooltip(this.font, allTabs().get(i).getDisplayName(), mouseX, mouseY);
        }
        // 标签页页码（原版位置：< > 两按钮之间，topPos-44）
        if (tabPages.size() > 1) {
            Component page = Component.literal((currentTabPage + 1) + " / " + tabPages.size());
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            g.drawString(this.font, page.getVisualOrderText(),
                    this.leftPos + (this.imageWidth / 2) - (this.font.width(page) / 2), this.topPos - 44, -1);
            g.pose().popPose();
        }
        this.renderTooltip(g, mouseX, mouseY); // 格子物品提示
    }

    @Override
    public void onClose() {
        // 一、关闭时记住当前标签页（静态），下次打开恢复；服务端缓存预热在 Menu.removed 触发
        RPGInventoryMenu.rememberTab(this.menu.getSelectedTabIndex());
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // B 键关闭：界面开着时按键走 screen.keyPressed（KeyMapping.click 仅在无界面时触发），故关闭须在此处理
        if (RpgKeyBindings.OPEN_RPG_INVENTORY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        // 副手交换键 F 单次触发（与数字键同根因：长按 REPEAT 连发）
        if (this.minecraft.options.keySwapOffhand.matches(keyCode, scanCode)) {
            if (offhandHeld) {
                return true;
            }
            offhandHeld = true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // 数字键 1-9 单次触发：长按的 REPEAT 事件直接吞掉，直到抬起才允许下一次交换
        for (int i = 0; i < 9; i++) {
            if (this.minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
                if (hotbarHeld[i]) {
                    return true;
                }
                hotbarHeld[i] = true;
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft.options.keySwapOffhand.matches(keyCode, scanCode)) {
            offhandHeld = false;
        }
        for (int i = 0; i < 9; i++) {
            if (this.minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
                hotbarHeld[i] = false;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int i = tabAt(mouseX - this.leftPos, mouseY - this.topPos);
            if (i >= 0) {
                // 本地立即更新高亮，再上报服务端重算过滤视图（拦截在 super 之前，避免误触槽位/界外丢弃）
                this.menu.selectTab(i);
                RpgInventory.CHANNEL.sendToServer(new SelectTabPacket(i));
                return true;
            }
            // 按下滚动条区域 -> 进入拖拽态（原版 scrolling），随后 mouseDragged 据鼠标Y更新偏移
            if (insideScrollbar(mouseX, mouseY) && this.menu.canScroll()) {
                this.scrolling = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 鼠标悬停在物品格子(0-44)上滚动 -> 平滑滚动（原版逐行：每格 1/rowCount 偏移）
        if (this.hoveredSlot != null && this.hoveredSlot.index < RPGInventoryMenu.PAGE_SIZE && this.menu.canScroll()) {
            double offset = this.menu.subtractInputFromScroll(this.menu.getScrollOffset(), delta);
            this.menu.selectScroll(offset); // 本地立即更新，滚动条即时跟手
            RpgInventory.CHANNEL.sendToServer(new ScrollPacket(offset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling) {
            // 原版拖拽公式：(mouseY - (topPos+18) - 7.5) / (112 - 15)，clamp 0..1
            int i = this.topPos + 18;
            int j = i + 112;
            double offset = ((float) mouseY - (float) i - 7.5F) / ((float) (j - i) - 15.0F);
            offset = Mth.clamp(offset, 0.0, 1.0);
            this.menu.selectScroll(offset);
            RpgInventory.CHANNEL.sendToServer(new ScrollPacket(offset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.scrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int leftPos, int topPos, int button) {
        boolean outside = mouseX < leftPos || mouseY < topPos
                || mouseX >= leftPos + this.imageWidth || mouseY >= topPos + this.imageHeight;
        // 标签页在底图矩形之外（上排顶部/下排底部），点它们不算"界外"，避免触发丢弃手持物品
        return outside && tabAt(mouseX - leftPos, mouseY - topPos) < 0;
    }
}
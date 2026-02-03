package com.littletomato.warehouse;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WarehouseScreen extends Screen {

    private double scrollAmount = 0;
    private final int ENTRY_HEIGHT = 20;
    private final int LIST_TOP = 65;
    private final int LIST_BOTTOM_MARGIN = 45;

    private final int TEXT_COLOR_WHITE = 0xFFFFFFFF;
    private final int TEXT_COLOR_GRAY = 0xFFAAAAAA;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private EditBox searchBox;
    private Button storeAllButton;

    public WarehouseScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new WarehousePayloads.RequestWarehouseDataC2SPayload());

        int dividerX = this.width / 2;

        // 1. 搜索框
        this.searchBox = new EditBox(this.font, 20, 40, dividerX - 40, 20, Component.literal("Search"));
        this.searchBox.setResponder(text -> {
            // 搜索内容改变时，重置滚动并强制检查边界
            this.scrollAmount = 0;
        });
        this.addRenderableWidget(this.searchBox);

        // 2. Store All 按钮
        int buttonWidth = 100;
        int buttonX = dividerX + (dividerX - buttonWidth) / 2;
        this.storeAllButton = Button.builder(Component.literal("Store All"), button -> {
            // TODO: 发送 DepositAllC2SPayload
        }).bounds(buttonX, this.height - 45, buttonWidth, 20).build();
        this.addRenderableWidget(this.storeAllButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int dividerX = this.width / 2;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        graphics.drawString(this.font, "Cloud Warehouse", 20, 15, TEXT_COLOR_WHITE, true);
        graphics.drawString(this.font, "Your Inventory", dividerX + 20, 15, TEXT_COLOR_WHITE, true);
        graphics.fill(dividerX - 1, 10, dividerX, this.height - 10, 0x88FFFFFF);

        // --- 左半边渲染 ---
        renderWarehousePart(graphics, dividerX, listBottom);

        // --- 右半边渲染 ---
        renderPlayerInventory(graphics, dividerX + 20, 40);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderWarehousePart(GuiGraphics graphics, int dividerX, int listBottom) {
        // 1. 过滤与排序
        String filter = searchBox.getValue().toLowerCase();
        List<Map.Entry<Item, Integer>> sortedItems = ClientWarehouseCache.getItems().entrySet().stream()
                .filter(entry -> entry.getKey().getName().getString().toLowerCase().contains(filter))
                .sorted(Comparator.comparing(entry -> entry.getKey().getName().getString()))
                .collect(Collectors.toList());

        // 2. 核心：计算最大滚动值并钳制当前滚动量
        // 可视区域高度
        int visibleHeight = listBottom - LIST_TOP;
        // 总内容高度
        int totalContentHeight = sortedItems.size() * ENTRY_HEIGHT;
        // 最大滚动位移 = 总高 - 可视高 (不能小于0)
        int maxScroll = Math.max(0, totalContentHeight - visibleHeight);

        // 实时修正滚动位置（防止搜索导致列表变短后出现空白）
        if (this.scrollAmount > maxScroll) {
            this.scrollAmount = maxScroll;
        }

        // 3. 渲染列表
        graphics.enableScissor(0, LIST_TOP, dividerX - 5, listBottom);
        int currentY = LIST_TOP - (int) scrollAmount;

        for (int i = 0; i < sortedItems.size(); i++) {
            Map.Entry<Item, Integer> entry = sortedItems.get(i);
            Item item = entry.getKey();

            if (currentY + ENTRY_HEIGHT > LIST_TOP && currentY < listBottom) {
                graphics.drawString(this.font, (i + 1) + ".", 15, currentY + 6, TEXT_COLOR_GRAY, false);
                graphics.renderItem(new ItemStack(item), 35, currentY + 2);
                graphics.drawString(this.font, item.getName(), 55, currentY + 6, TEXT_COLOR_WHITE, false);

                String countText = "x" + entry.getValue();
                int nameWidth = this.font.width(item.getName());
                graphics.drawString(this.font, countText, 58 + nameWidth, currentY + 6, TEXT_COLOR_GRAY, false);
            }
            currentY += ENTRY_HEIGHT;
        }
        graphics.disableScissor();

        // 4. 底部信息
        long ts = ClientWarehouseCache.getLastUpdated();
        String timeStr = (ts <= 0) ? "Never" : DATE_FORMATTER.format(Instant.ofEpochMilli(ts));
        String statusText = "Updated: " + timeStr;
        graphics.drawString(this.font, statusText, 20, this.height - 20, TEXT_COLOR_GRAY, false);
    }

    private void renderPlayerInventory(GuiGraphics graphics, int startX, int startY) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        Inventory inv = this.minecraft.player.getInventory();
        int slotSize = 18;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                renderSlot(graphics, inv.getItem(index), startX + col * slotSize, startY + row * slotSize);
            }
        }
        int hotbarY = startY + (3 * slotSize) + 10;
        for (int col = 0; col < 9; col++) {
            renderSlot(graphics, inv.getItem(col), startX + col * slotSize, hotbarY);
        }
    }

    private void renderSlot(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, 0x33FFFFFF);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(this.font, stack, x, y);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < this.width / 2.0) {
            // 计算当前条件下的 maxScroll
            String filter = searchBox.getValue().toLowerCase();
            long filteredCount = ClientWarehouseCache.getItems().entrySet().stream()
                    .filter(entry -> entry.getKey().getName().getString().toLowerCase().contains(filter))
                    .count();

            int listBottom = this.height - LIST_BOTTOM_MARGIN;
            int totalContentHeight = (int) filteredCount * ENTRY_HEIGHT;
            int maxScroll = Math.max(0, totalContentHeight - (listBottom - LIST_TOP));

            // 更新并钳制滚动值
            this.scrollAmount -= verticalAmount * 20;
            this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
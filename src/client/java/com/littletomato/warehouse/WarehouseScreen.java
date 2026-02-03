package com.littletomato.warehouse;

import com.mojang.blaze3d.platform.InputConstants; // 必须导入
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent; // 对应你提供的源码
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW; // 必须导入

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

        this.searchBox = new EditBox(this.font, 20, 40, dividerX - 40, 20, Component.literal("Search"));
        this.searchBox.setResponder(text -> this.scrollAmount = 0);
        this.addRenderableWidget(this.searchBox);

        int buttonWidth = 100;
        int buttonX = dividerX + (dividerX - buttonWidth) / 2;
        this.storeAllButton = Button.builder(Component.literal("Store All"), button -> {
            ClientPlayNetworking.send(new WarehousePayloads.DepositAllC2SPayload());
        }).bounds(buttonX, this.height - 45, buttonWidth, 20).build();
        this.addRenderableWidget(this.storeAllButton);
    }

    private List<Map.Entry<Item, Integer>> getFilteredItems() {
        String filter = searchBox.getValue().toLowerCase();
        return ClientWarehouseCache.getItems().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .filter(entry -> entry.getKey().getName().getString().toLowerCase().contains(filter))
                .sorted(Comparator.comparing(entry -> entry.getKey().getName().getString()))
                .collect(Collectors.toList());
    }

    // 辅助方法：检查当前是否按下了 Shift 键
    private boolean isShiftDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int dividerX = this.width / 2;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        graphics.drawString(this.font, "Cloud Warehouse", 20, 15, TEXT_COLOR_WHITE, true);
        graphics.drawString(this.font, "Your Inventory", dividerX + 20, 15, TEXT_COLOR_WHITE, true);
        graphics.fill(dividerX - 1, 10, dividerX, this.height - 10, 0x88FFFFFF);

        renderWarehousePart(graphics, dividerX, listBottom);
        renderPlayerInventory(graphics, dividerX + 20, 40);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderWarehousePart(GuiGraphics graphics, int dividerX, int listBottom) {
        List<Map.Entry<Item, Integer>> sortedItems = getFilteredItems();
        int visibleHeight = listBottom - LIST_TOP;
        int totalContentHeight = sortedItems.size() * ENTRY_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - visibleHeight);

        if (this.scrollAmount > maxScroll) this.scrollAmount = maxScroll;

        graphics.enableScissor(0, LIST_TOP, dividerX - 5, listBottom);
        int currentY = LIST_TOP - (int) scrollAmount;

        for (int i = 0; i < sortedItems.size(); i++) {
            Map.Entry<Item, Integer> entry = sortedItems.get(i);
            Item item = entry.getKey();
            if (currentY + ENTRY_HEIGHT > LIST_TOP && currentY < listBottom) {
                // 1. 绘制序号
                graphics.drawString(this.font, (i + 1) + ".", 15, currentY + 6, TEXT_COLOR_GRAY, false);

                // 2. 绘制物品图标 (位置保持在 35)
                graphics.renderItem(new ItemStack(item), 35, currentY + 2);

                // 3. 准备数量字符串 (例如 "64 x ")
                String countText = entry.getValue() + " x ";
                int countWidth = this.font.width(countText);

                // 4. 先绘制数量 (灰色)
                // 起始 X 坐标定在 55 (图标后面)
                graphics.drawString(this.font, countText, 55, currentY + 6, TEXT_COLOR_GRAY, false);

                // 5. 再绘制物品名称 (白色)
                // X 坐标 = 55 + 数量文字的宽度
                graphics.drawString(this.font, item.getName(), 55 + countWidth, currentY + 6, TEXT_COLOR_WHITE, false);
            }
            currentY += ENTRY_HEIGHT;
        }
        graphics.disableScissor();

        long ts = ClientWarehouseCache.getLastUpdated();
        String timeStr = (ts <= 0) ? "Never" : DATE_FORMATTER.format(Instant.ofEpochMilli(ts));
        graphics.drawString(this.font, "Updated: " + timeStr, 20, this.height - 20, TEXT_COLOR_GRAY, false);
    }

    private void renderPlayerInventory(GuiGraphics graphics, int startX, int startY) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        Inventory inv = this.minecraft.player.getInventory();
        int slotSize = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                renderSlot(graphics, inv.getItem(9 + row * 9 + col), startX + col * slotSize, startY + row * slotSize);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean isFirstClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int dividerX = this.width / 2;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        // 1. 仓库列表交互 (取回)
        if (mouseX < dividerX && mouseY >= LIST_TOP && mouseY <= listBottom) {
            List<Map.Entry<Item, Integer>> sortedItems = getFilteredItems();
            int currentY = LIST_TOP - (int) scrollAmount;

            for (Map.Entry<Item, Integer> entry : sortedItems) {
                if (mouseY >= currentY && mouseY < currentY + ENTRY_HEIGHT) {
                    Item item = entry.getKey();
                    // 如果按下 Shift，取出一组；否则取出一个
                    int count = isShiftDown() ? Math.min(entry.getValue(), item.getDefaultMaxStackSize()) : 1;
                    ClientPlayNetworking.send(new WarehousePayloads.WithdrawItemC2SPayload(item, count));
                    return true;
                }
                currentY += ENTRY_HEIGHT;
            }
        }

        // 2. 玩家背包交互 (存储)
        if (mouseX >= dividerX + 20 && this.minecraft != null && this.minecraft.player != null) {
            Inventory inv = this.minecraft.player.getInventory();
            int startX = dividerX + 20;
            int startY = 40;
            int slotSize = 18;

            // 主背包 (索引 9-35)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotId = 9 + row * 9 + col; // 计算正确的槽位索引
                    if (isMouseInSlot(mouseX, mouseY, startX + col * slotSize, startY + row * slotSize)) {
                        handleDeposit(slotId, inv.getItem(slotId));
                        return true;
                    }
                }
            }
            // 快捷栏 (索引 0-8)
            int hotbarY = startY + (3 * slotSize) + 10;
            for (int col = 0; col < 9; col++) {
                int slotId = col; // 快捷栏索引
                if (isMouseInSlot(mouseX, mouseY, startX + col * slotSize, hotbarY)) {
                    handleDeposit(slotId, inv.getItem(slotId));
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isFirstClick);
    }

    private boolean isMouseInSlot(double mouseX, double mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
    }

    private void handleDeposit(int slotId, ItemStack stack) {
        if (stack.isEmpty()) return;
        int count = isShiftDown() ? stack.getCount() : 1;
        ClientPlayNetworking.send(new WarehousePayloads.DepositItemC2SPayload(slotId, count));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < this.width / 2.0) {
            int listBottom = this.height - LIST_BOTTOM_MARGIN;
            int totalContentHeight = getFilteredItems().size() * ENTRY_HEIGHT;
            int maxScroll = Math.max(0, totalContentHeight - (listBottom - LIST_TOP));
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
package com.littletomato.warehouse;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack; // 必须导入

import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class WarehouseScreen extends Screen {

    private double scrollAmount = 0;
    private final int ENTRY_HEIGHT = 20;
    private final int LIST_TOP = 40;
    private final int LIST_BOTTOM_MARGIN = 25;

    private final int TEXT_COLOR_WHITE = 0xFFFFFFFF;
    private final int TEXT_COLOR_GRAY = 0xFFAAAAAA;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    public WarehouseScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new WarehousePayloads.RequestWarehouseDataC2SPayload());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawString(this.font, "Cloud Warehouse Status (Scroll to view)", 20, 15, TEXT_COLOR_WHITE, true);

        Map<Item, Integer> items = ClientWarehouseCache.getItems();
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        // 开启剪裁，确保图标和文字不会超出边界
        graphics.enableScissor(0, LIST_TOP, this.width, listBottom);

        int currentY = LIST_TOP - (int) scrollAmount;
        int index = 0;

        for (Map.Entry<Item, Integer> entry : items.entrySet()) {
            Item item = entry.getKey();
            // 获取物品的显示名称
            Component itemName = item.getName();
            String countText = " x" + entry.getValue();

            // 只有在可见范围内才进行渲染
            if (currentY + ENTRY_HEIGHT > LIST_TOP && currentY < listBottom) {
                // 1. 绘制索引编号
                graphics.drawString(this.font, (index + 1) + ".", 20, currentY + 6, TEXT_COLOR_WHITE, false);

                // 2. 绘制物品图标
                ItemStack stack = new ItemStack(item);
                graphics.renderItem(stack, 40, currentY + 2);

                // 3. 绘制物品名称和数量
                graphics.drawString(this.font, itemName, 60, currentY + 6, TEXT_COLOR_WHITE, false);
                int nameWidth = this.font.width(itemName);
                graphics.drawString(this.font, countText, 60 + nameWidth, currentY + 6, TEXT_COLOR_GRAY, false);
            }

            currentY += ENTRY_HEIGHT;
            index++;
        }

        graphics.disableScissor();

        // 绘制底部状态栏
        long ts = ClientWarehouseCache.getLastUpdated();
        String timeStr = (ts <= 0) ? "Never" : DATE_FORMATTER.format(Instant.ofEpochMilli(ts));
        graphics.drawString(this.font,
                "Total Types: " + items.size() + " | Last Sync: " + timeStr,
                5,
                this.height - 15,
                TEXT_COLOR_GRAY,
                false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollAmount -= verticalAmount * 15;
        int totalContentHeight = ClientWarehouseCache.getItems().size() * ENTRY_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - (this.height - LIST_TOP - LIST_BOTTOM_MARGIN));
        this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, maxScroll));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
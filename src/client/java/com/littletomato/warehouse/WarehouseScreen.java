package com.littletomato.warehouse;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class WarehouseScreen extends Screen {

    private double scrollAmount = 0;
    private final int ENTRY_HEIGHT = 12;
    private final int LIST_TOP = 40;
    private final int LIST_BOTTOM_MARGIN = 25;

    private final int TEXT_COLOR_WHITE = 0xFFFFFFFF;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault()); // 使用系统本地时区

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

        // 开启剪裁
        graphics.enableScissor(0, LIST_TOP, this.width, listBottom);

        int currentY = LIST_TOP - (int) scrollAmount;
        int index = 0;
        for (Map.Entry<Item, Integer> entry : items.entrySet()) {
            String itemName = entry.getKey().getDescriptionId();
            String fullText = (index + 1) + ". " + itemName + " x" + entry.getValue();

            if (currentY + ENTRY_HEIGHT > LIST_TOP && currentY < listBottom) {
                graphics.drawString(this.font, fullText, 30, currentY, TEXT_COLOR_WHITE, false);
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
                TEXT_COLOR_WHITE, // 这里建议直接用颜色代码或定义的常量
                false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollAmount -= verticalAmount * 15;
        int totalContentHeight = ClientWarehouseCache.getItems().size() * ENTRY_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - (this.height - LIST_TOP - LIST_BOTTOM_MARGIN));
        this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, maxScroll));
        return true; // 返回 true 表示已处理该事件
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

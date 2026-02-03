package com.littletomato.warehouse;

import com.littletomato.LittleTomato;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Map;

public class WarehousePayloads {

    // 1. S2C: 服务端同步数据到客户端
    public record WarehouseDataS2CPayload(Map<Item, Integer> items, long lastUpdated) implements CustomPacketPayload {
        public static final Type<WarehouseDataS2CPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath(LittleTomato.MOD_ID, "warehouse_data"));

        // 使用 ByteBufCodecs.map 构建复杂的 StreamCodec
        public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseDataS2CPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.INT),
                WarehouseDataS2CPayload::items,
                ByteBufCodecs.VAR_LONG,
                WarehouseDataS2CPayload::lastUpdated,
                WarehouseDataS2CPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // 2. C2S: 客户端请求数据
    public record RequestWarehouseDataC2SPayload() implements CustomPacketPayload {
        public static final Type<RequestWarehouseDataC2SPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath(LittleTomato.MOD_ID, "request_warehouse_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestWarehouseDataC2SPayload> CODEC =
                StreamCodec.unit(new RequestWarehouseDataC2SPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
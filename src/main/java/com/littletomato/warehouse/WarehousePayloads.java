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

    // 服务端同步数据到客户端
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

    // 客户端请求数据
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

    // 存入指定物品
    public record DepositItemC2SPayload(int slotId, int count) implements CustomPacketPayload {
        public static final Type<DepositItemC2SPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath(LittleTomato.MOD_ID, "deposit_item"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DepositItemC2SPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DepositItemC2SPayload::slotId,
                ByteBufCodecs.VAR_INT, DepositItemC2SPayload::count,
                DepositItemC2SPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // 存入所有物品
    public record DepositAllC2SPayload() implements CustomPacketPayload {
        public static final Type<DepositAllC2SPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath(LittleTomato.MOD_ID, "deposit_all"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DepositAllC2SPayload> CODEC =
                StreamCodec.unit(new DepositAllC2SPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // 取出指定物品
    public record WithdrawItemC2SPayload(Item item, int count) implements CustomPacketPayload {
        public static final Type<WithdrawItemC2SPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath(LittleTomato.MOD_ID, "withdraw_item"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawItemC2SPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), WithdrawItemC2SPayload::item,
                ByteBufCodecs.VAR_INT, WithdrawItemC2SPayload::count,
                WithdrawItemC2SPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
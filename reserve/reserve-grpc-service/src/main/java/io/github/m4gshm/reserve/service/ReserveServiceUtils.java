package io.github.m4gshm.reserve.service;

import io.github.m4gshm.reserve.data.WarehouseItemStorage.ItemOp;
import io.github.m4gshm.reserve.data.model.Reserve;
import lombok.experimental.UtilityClass;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse;

import java.util.List;

import static reserve.v1.ReserveOuterClass.Reserve.Item;
import static reserve.v1.ReserveOuterClass.Reserve.Status;
import static reserve.v1.ReserveOuterClass.Reserve.newBuilder;

@UtilityClass
public class ReserveServiceUtils {

    public static ReserveApproveResponse newApproveResponse(
                                                            List<ItemOp.ReserveResult> reserveResults,
                                                            String reserveId
    ) {
        var reservedItems = reserveResults.stream().map(ReserveServiceUtils::toResponseItemProto).toList();
        var allReserved = reservedItems.stream().allMatch(ReserveApproveResponse.Item::getReserved);
        return ReserveApproveResponse.newBuilder()
                .setId(reserveId)
                .addAllItems(reservedItems)
                .setStatus(allReserved
                        ? Status.APPROVED
                        : Status.INSUFFICIENT)
                .build();
    }

    static List<ItemOp> toItemOps(List<Reserve.Item> items) {
        return items.stream().map(item -> {
            return ItemOp.builder()
                    .id(item.id())
                    .amount(item.amount())
                    .build();
        }).toList();
    }

    public static ReserveOuterClass.Reserve toReserveProto(Reserve reserve) {
        return newBuilder()
                .setId(reserve.id())
                .setExternalRef(reserve.externalRef())
                .setStatus(toStatusProto(reserve.status()))
                .addAllItems(reserve.items().stream().map(item -> {
                    var builder = Item.newBuilder()
                            .setId(item.id())
                            .setAmount(item.amount())
                            .setReserved(item.reserved());
                    var insufficient = item.insufficient();
                    if (insufficient != null) {
                        builder.setInsufficient(insufficient);
                    }
                    return builder.build();
                }).toList())
                .build();
    }

    public static Status toStatusProto(Reserve.Status status) {
        return status == null ? null : switch (status) {
            case CREATED -> Status.CREATED;
            case APPROVED -> Status.APPROVED;
            case RELEASED -> Status.RELEASED;
            case CANCELLED -> Status.CANCELLED;
            case INSUFFICIENT -> Status.INSUFFICIENT;
        };
    }

    public static ReserveApproveResponse.Item toResponseItemProto(ItemOp.ReserveResult result) {
        var reserved = result.reserved();
        var builder = ReserveApproveResponse.Item.newBuilder()
                .setId(result.id())
                .setReserved(reserved);

        if (!reserved) {
            builder.setInsufficientQuantity(-(int) result.remainder());
        }
        return builder
                .build();
    }
}

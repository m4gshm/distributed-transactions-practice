package io.github.m4gshm.reserve.service;

import io.github.m4gshm.reserve.data.WarehouseItemStorage.ItemOp;
import io.github.m4gshm.reserve.data.model.Reserve;
import lombok.experimental.UtilityClass;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse.Status;

import java.util.List;

@UtilityClass
public class ReserveServiceUtils {

    public static ReserveOuterClass.Reserve toReserve(Reserve reserve) {
        return ReserveOuterClass.Reserve.newBuilder()
                .setId(reserve.id())
                .setExternalRef(reserve.externalRef())
                .addAllItems(reserve.items().stream().map(item -> {
                    var builder = ReserveOuterClass.Reserve.Item.newBuilder()
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

    public static ReserveApproveResponse newApproveResponse(
            List<ItemOp.ReserveResult> reserveResults, String reserveId
    ) {
        var reservedItems = reserveResults.stream().map(ReserveServiceUtils::toResponseItem).toList();
        var allReserved = reservedItems.stream().allMatch(ReserveApproveResponse.Item::getReserved);
        return ReserveApproveResponse.newBuilder()
                .setId(reserveId)
                .addAllItems(reservedItems)
                .setStatus(allReserved
                        ? Status.APPROVED
                        : Status.INSUFFICIENT_QUANTITY)
                .build();
    }

    public static ReserveApproveResponse.Item toResponseItem(ItemOp.ReserveResult result) {
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

    static List<ItemOp> toItemOps(List<Reserve.Item> items) {
        return items.stream().map(item -> {
            return ItemOp.builder()
                    .id(item.id())
                    .amount(item.amount())
                    .build();
        }).toList();
    }
}

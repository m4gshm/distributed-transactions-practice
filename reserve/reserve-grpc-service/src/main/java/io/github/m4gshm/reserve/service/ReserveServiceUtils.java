package io.github.m4gshm.reserve.service;

import io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem;
import io.github.m4gshm.reserve.data.model.Reserve;
import lombok.experimental.UtilityClass;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@UtilityClass
public class ReserveServiceUtils {

    public static ReserveOuterClass.Reserve toReserve(Reserve reserve) {
        return ReserveOuterClass.Reserve.newBuilder()
                .setId(reserve.id())
                .setExternalRef(reserve.externalRef())
                .addAllItems(reserve.items().stream().map(item -> ReserveOuterClass.Reserve.Item.newBuilder()
                        .setId(item.id())
                        .setAmount(item.amount())
                        .setStatus(getItemStatus(item))
                        .build()).toList())
                .build();
    }

    public static ReserveApproveResponse newApproveResponse(List<ReserveItem.Result> reserveResults,
                                                            String reserveId) {
        var reservedItems = reserveResults.stream().map(ReserveServiceUtils::toResponseItem).toList();
        var statuses = reservedItems.stream().map(ReserveApproveResponse.Item::getStatus).collect(toSet());
        return ReserveApproveResponse.newBuilder()
                .setId(reserveId)
                .addAllItems(reservedItems)
                .setStatus(getStatus(statuses))
                .build();
    }

    public static ReserveApproveResponse.Status getStatus(Set<ReserveOuterClass.Reserve.Item.Status> statuses) {
        if (statuses.size() == 1) {
            return switch (statuses.iterator().next()) {
                case RESERVED -> ReserveApproveResponse.Status.APPROVED;
                case INSUFFICIENT_QUANTITY -> ReserveApproveResponse.Status.INSUFFICIENT_QUANTITY;
                case UNRECOGNIZED -> ReserveApproveResponse.Status.UNRECOGNIZED;
            };
        } else {
            return statuses.contains(ReserveOuterClass.Reserve.Item.Status.INSUFFICIENT_QUANTITY)
                    ? ReserveApproveResponse.Status.PARTIAL_INSUFFICIENT_QUANTITY
                    : ReserveApproveResponse.Status.UNRECOGNIZED;
        }
    }


    public static ReserveOuterClass.Reserve.Item.Status getItemStatus(Reserve.Item item) {
        return item.reserved() == null
                ? ReserveOuterClass.Reserve.Item.Status.UNRECOGNIZED : item.reserved()
                ? ReserveOuterClass.Reserve.Item.Status.RESERVED : ReserveOuterClass.Reserve.Item.Status.INSUFFICIENT_QUANTITY;
    }


    public static ReserveApproveResponse.Item toResponseItem(ReserveItem.Result result) {
        return ReserveApproveResponse.Item.newBuilder()
                .setId(result.id())
                .setInsufficientQuantity(-(int) result.remainder())
                .setStatus(switch (result.status()) {
                    case reserved -> ReserveOuterClass.Reserve.Item.Status.RESERVED;
                    case insufficient_quantity -> ReserveOuterClass.Reserve.Item.Status.INSUFFICIENT_QUANTITY;
                })
                .build();
    }

    static List<ReserveItem> toItemReserves(List<Reserve.Item> items) {
        return items.stream().map(item -> ReserveItem.builder()
                .id(item.id()).amount(item.amount())
                .build()
        ).toList();
    }
}

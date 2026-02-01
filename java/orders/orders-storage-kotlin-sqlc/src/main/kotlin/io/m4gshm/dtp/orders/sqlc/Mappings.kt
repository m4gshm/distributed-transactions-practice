package io.m4gshm.dtp.orders.sqlc

import io.m4gshm.dtp.orders.sqlc.gen.Delivery
import io.m4gshm.dtp.orders.sqlc.gen.DeliveryType
import io.m4gshm.dtp.orders.sqlc.gen.Order
import io.m4gshm.dtp.orders.sqlc.gen.OrderStatus
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun ResultSet.getDelivery(): Delivery = Delivery(
    orderId = this.getString("order_id"),
    address = this.getString("address"),
    type = DeliveryType.valueOf(this.getString("type"))
)

fun ResultSet.getOrder(): Order = Order(
    id = this.getString("id"),
    createdAt = this.getTimestamp("created_at").offsetDateTime(),
    updatedAt = this.getTimestamp("updated_at").offsetDateTime(),
    status = OrderStatus.valueOf(this.getString("status")),
    customerId = this.getString("customer_id"),
    reserveId = this.getString("reserve_id"),
    paymentId = this.getString("payment_id"),
    paymentTransactionId = this.getString("payment_transaction_id"),
    reserveTransactionId = this.getString("reserve_transaction_id")
)

fun Timestamp.offsetDateTime(): OffsetDateTime =
    this.toLocalDateTime().atZone(ZoneOffset.systemDefault()).toOffsetDateTime()

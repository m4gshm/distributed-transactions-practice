package io.m4gshm.dtp.orders.sqlc

import io.github.m4gshm.orders.data.access.jooq.enums.DeliveryType
import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus
import io.github.m4gshm.orders.data.model.Order
import io.github.m4gshm.orders.data.storage.OrderStorage
import io.github.m4gshm.storage.Page
import io.m4gshm.dtp.orders.sqlc.gen.Delivery
import io.m4gshm.dtp.orders.sqlc.gen.QueriesImpl
import jakarta.validation.Valid
import java.sql.Connection
import javax.sql.DataSource

class OrderStorageSqlcImpl(val dataSource: DataSource) : OrderStorage {
    private fun connection(): Connection = dataSource.connection

    override fun getEntityClass(): Class<Order> = Order::class.java

    private fun <T> query(block: QueriesImpl.() -> T): T = connection().use { connection ->
        return block.invoke(QueriesImpl(connection))
    }

    override fun findAll(page: Page?, status: OrderStatus?): List<Order> = query {
        val num = Page.getNum(page)
        val size = Page.getSize(page)
        Page.validatePaging(num, size)
        val offs = size * num
        findOrdersPaged(status = status.toSqlc(), size, offs).map { row -> toJooq(row.orders!!, row.delivery!!) }
    }

    override fun findByClientIdAndStatuses(
        clientId: String, statuses: Collection<OrderStatus>
    ): List<Order> = query {
        val orderStatuses = statuses.map { it.toSqlc()!! }
        this.findOrdersByClientAndStatuses(clientId, orderStatuses).map { row -> toJooq(row.orders!!, row.delivery!!) }
    }

    override fun findAll(): List<Order> = query {
        this.findAllOrders().map { row -> toJooq(row.orders!!, row.delivery!!) }
    }

    override fun findById(id: String): Order? = query {
        val row = this.findOrderById(id)
        if (row != null) toJooq(row.orders!!, row.delivery!!) else null
    }

    override fun findAll(page: Page?): List<Order> {
        TODO("Not yet implemented")
    }

    override fun save(order: @Valid Order): Order = query {
        insertOrUpdateOrder(order)
        val delivery = order.delivery()
        val deliveryType = delivery.type()
        val orderId = order.id()
        insertOrUpdateDelivery(orderId, delivery?.address() ?: "", deliveryType.toSqlc())
        order.items().map { item ->
            insertOrUpdateItem(orderId, item.id(), item.amount())
        }
        order
    }

    override fun saveOrderOnly(@Valid order: Order): Order = query {
        insertOrUpdateOrder(order)
        order
    }

    private fun QueriesImpl.insertOrUpdateOrder(order: Order) {
        this.insertOrUpdateOrder(
            id = order.id(),
            status = order.status().toSqlc()!!,
            createdAt = order.createdAt(),
            customerId = order.customerId(),
            updatedAt = order.updatedAt(),
            reserveId = order.reserveId(),
            paymentId = order.paymentId(),
            paymentTransactionId = order.paymentTransactionId(),
            reserveTransactionId = order.reserveTransactionId(),
        )
    }
}

private fun toJooq(
    order: io.m4gshm.dtp.orders.sqlc.gen.Order, delivery: Delivery
): Order = Order(
    order.id,
    order.status.toJooq(),
    order.customerId,
    order.paymentId,
    order.reserveId,
    order.createdAt,
    order.updatedAt,
    delivery.toJooq(),
    listOf(),
    order.paymentTransactionId,
    order.reserveTransactionId,
)

private fun Delivery.toJooq(): Order.Delivery = Order.Delivery(
    this.address, null, this.type.toJooq()
)

private fun io.m4gshm.dtp.orders.sqlc.gen.OrderStatus.toJooq(): OrderStatus {
    return OrderStatus.lookupLiteral(this.name)!!
}

private fun io.m4gshm.dtp.orders.sqlc.gen.DeliveryType.toJooq(): DeliveryType {
    return DeliveryType.lookupLiteral(this.name)
}

private fun DeliveryType.toSqlc(): io.m4gshm.dtp.orders.sqlc.gen.DeliveryType {
    return io.m4gshm.dtp.orders.sqlc.gen.DeliveryType.lookup(name)!!
}

private fun OrderStatus?.toSqlc(): io.m4gshm.dtp.orders.sqlc.gen.OrderStatus? {
    return if (this == null) null else io.m4gshm.dtp.orders.sqlc.gen.OrderStatus.lookup(this.literal)!!
}


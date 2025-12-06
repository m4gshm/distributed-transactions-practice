-- name: FindAllOrders :many
SELECT
  sqlc.embed(o),
  sqlc.embed(d)
FROM
  orders o
  LEFT JOIN delivery d ON o.id = d.order_id;

-- name: FindOrdersByClientAndStatuses :many
SELECT
  sqlc.embed(o),
  sqlc.embed(d)
FROM
  orders o
  LEFT JOIN delivery d ON o.id = d.order_id
WHERE
  o.customer_id = $1
  AND o.status = ANY(sqlc.arg(orderStatus)::order_status[]);


-- name: FindOrderById :one
SELECT
  sqlc.embed(o),
  sqlc.embed(d)
FROM
  orders o
  LEFT JOIN delivery d ON o.id = d.order_id
WHERE
  o.id = $1;


-- name: FindItemsByOrderId :many
SELECT
  *
FROM
  item i
WHERE
  i.order_id = $1;


-- name: InsertOrUpdateOrder :exec
INSERT INTO
  orders (
    id,
    status,
    created_at,
    updated_at,
    customer_id,
    reserve_id,
    payment_id,
    payment_transaction_id,
    reserve_transaction_id
  )
VALUES
  ($1, $2, $3, $4, $5, $6, $7, $8, $9) ON CONFLICT(id) DO
UPDATE
SET
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at,
  reserve_id = COALESCE(EXCLUDED.reserve_id, orders.reserve_id),
  payment_id = COALESCE(EXCLUDED.payment_id, orders.payment_id);


-- name: UpdateOrderStatus :exec
UPDATE
  orders
SET
  status = $1,
  updated_at = COALESCE($2, CURRENT_TIMESTAMP)
WHERE id = $3;


-- name: InsertOrUpdateDelivery :exec
INSERT INTO
  delivery (order_id, address, type)
VALUES
  ($1, $2, $3) ON CONFLICT(order_id) DO
UPDATE
SET
  address = EXCLUDED.address,
  type = EXCLUDED.type;


-- name: DeleteItemsByOrderId :exec
DELETE FROM
  item
WHERE
  order_id = $1;


-- name: InsertOrUpdateItem :exec
INSERT INTO
  item (order_id, id, amount)
VALUES
  ($1, $2, $3) ON CONFLICT(id, order_id) DO
UPDATE
SET
  amount = EXCLUDED.amount;
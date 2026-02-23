-- name: SelectAllItems :many
SELECT
  *
FROM
  warehouse_item;


-- name: SelectItemByID :one
SELECT
  *
FROM
  warehouse_item
WHERE
  id = $1;


-- name: SelectItemByIDForUpdate :one
SELECT
  *
FROM
  warehouse_item
WHERE
  id = $1 FOR NO KEY UPDATE
;


-- name: IncrementAmount :one
UPDATE
  warehouse_item
SET
  amount = amount + $2
WHERE
  id = $1
RETURNING amount;


-- name: IncrementReserved :exec
UPDATE
  warehouse_item
SET
  reserved = reserved + $2
WHERE
  id = $1;


-- name: DecrementReserved :exec
UPDATE
  warehouse_item
SET
  reserved = reserved - $2
WHERE
  id = $1;


-- name: DecrementAmountAndReserved :exec
UPDATE
  warehouse_item
SET
  amount = amount - $2,
  reserved = reserved - $2
WHERE
  id = $1;
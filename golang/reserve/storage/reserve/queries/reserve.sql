-- name: FindAllReserves :many
SELECT
  *
FROM
  reserve;


-- name: FindReserveByID :one
SELECT
  *
FROM
  reserve
WHERE
  id = $1;


-- name: UpsertReserve :exec
INSERT INTO
  reserve (id, created_at, external_ref, status, updated_at)
VALUES
  ($1, $2, $3, $4, $5) ON CONFLICT (id) DO
UPDATE
SET
  status = EXCLUDED.status,
  updated_at = COALESCE(EXCLUDED.updated_at, reserve.updated_at);


-- name: DeleteReserveItems :exec
DELETE FROM
  reserve_item
WHERE
  reserve_id = $1;


-- name: UpsertReserveItem :exec
INSERT INTO
  reserve_item (id, reserve_id, reserved, amount, insufficient)
VALUES
  ($1, $2, $3, $4, $5) ON CONFLICT (id, reserve_id) DO
UPDATE
SET
  amount = COALESCE(EXCLUDED.amount, reserve_item.amount),
  insufficient = COALESCE(EXCLUDED.insufficient, reserve_item.insufficient),
  reserved = COALESCE(EXCLUDED.reserved, reserve_item.reserved);


-- name: FindItemsByReserveID :many
SELECT
  *
FROM
  reserve_item
WHERE
  reserve_id = $1;
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
  reserve (id, external_ref, status, created_at, updated_at)
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
  reserve_item (id, reserve_id, amount, reserved, insufficient)
VALUES
  ($1, $2, $3, $4, $5) ON CONFLICT (id, reserve_id) DO
UPDATE
SET
  reserved = COALESCE($4, reserve_item.reserved),
  insufficient = COALESCE($5, reserve_item.insufficient);


-- name: FindItemsByReserveID :many
SELECT
  *
FROM
  reserve_item
WHERE
  reserve_id = $1;


-- name: FindItemsByReserveIDOrderByID :many
SELECT
  *
FROM
  reserve_item
WHERE
  reserve_id = $1
ORDER BY id;
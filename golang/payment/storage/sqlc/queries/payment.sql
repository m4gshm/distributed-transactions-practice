-- name: FindAllPayments :many
SELECT *
FROM payment;

-- name: FindPaymentByID :one
SELECT *
FROM payment
WHERE id = $1;

-- name: UpsertPayment :exec
INSERT INTO payment (
        id,
        created_at,
        external_ref,
        client_id,
        status,
        amount,
        insufficient
    )
VALUES ($1, $2, $3, $4, $5, $6, $7) ON CONFLICT(id) DO
UPDATE
SET updated_at = COALESCE($8, CURRENT_TIMESTAMP),
    status = $5,
    amount = $6,
    insufficient = $7;

-- name: UpdatePaymentStatus :exec
UPDATE payment
SET status = $1,
    insufficient = $2,
    updated_at = COALESCE($3, CURRENT_TIMESTAMP)
WHERE id = $4;
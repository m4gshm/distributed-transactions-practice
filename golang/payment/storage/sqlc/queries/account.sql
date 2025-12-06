-- name: FindAllAccounts :many
SELECT *
FROM account;

-- name: FindAccountById :one
SELECT *
FROM account
WHERE client_id = $1;

-- name: FindAccountByIdForUpdate :one
SELECT *
FROM account
WHERE client_id = $1 FOR NO KEY UPDATE;

-- name: AddAmount :one
UPDATE account
SET amount = amount + $2,
    updated_at = COALESCE($3, CURRENT_TIMESTAMP)
WHERE client_id = $1
RETURNING amount,
    locked,
    updated_at;

-- name: AddLock :one
UPDATE account
SET locked = locked + $2,
    updated_at = COALESCE($3, CURRENT_TIMESTAMP)
WHERE client_id = $1
RETURNING amount,
    locked,
    updated_at;

-- name: Unlock :one
UPDATE account
SET locked = locked - $2,
    updated_at = COALESCE($3, CURRENT_TIMESTAMP)
WHERE client_id = $1
RETURNING amount,
    locked,
    updated_at;

-- name: WriteOff :one
UPDATE account
SET amount = amount - $2,
    locked = locked - $2,
    updated_at = COALESCE($3, CURRENT_TIMESTAMP)
WHERE client_id = $1
RETURNING amount,
    locked,
    updated_at;
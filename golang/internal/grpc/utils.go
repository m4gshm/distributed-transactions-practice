package grpc

import (
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/m4gshm/gollections/op"
	"google.golang.org/grpc/codes"
)

func Status(err error) codes.Code {
	return op.IfElse(errors.Is(err, pgx.ErrNoRows), codes.NotFound, codes.Internal)
}

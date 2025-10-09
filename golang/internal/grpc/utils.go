package grpc

import (
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/m4gshm/gollections/op"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func Status(err error) codes.Code {
	if st, ok := status.FromError(err); ok {
		return st.Code()
	}
	return op.IfElse(errors.Is(err, pgx.ErrNoRows), codes.NotFound, codes.Internal)
}

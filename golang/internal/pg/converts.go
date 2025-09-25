package pg

import (
	"time"

	"github.com/jackc/pgx/v5/pgtype"
)

func String[S ~string](s S) pgtype.Text {
	return pgtype.Text{String: string(s), Valid: true}
}

func Timestamptz(t time.Time) pgtype.Timestamptz {
	return pgtype.Timestamptz{Time: t, Valid: true}
}
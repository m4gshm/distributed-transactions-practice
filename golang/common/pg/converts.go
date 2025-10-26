package pg

import (
	"time"

	"github.com/jackc/pgx/v5/pgtype"
)

func FromString[S ~string](s S) pgtype.Text {
	return pgtype.Text{String: string(s), Valid: true}
}

func FromStringPtr[S ~string](s *S) pgtype.Text {
	if s == nil {
		return pgtype.Text{}
	}
	return FromString(*s)
}

func ToString(text pgtype.Text) *string {
	if text.Valid {
		return &text.String
	}
	return nil
}

func Timestamptz(t time.Time) pgtype.Timestamptz {
	return pgtype.Timestamptz{Time: t, Valid: true}
}

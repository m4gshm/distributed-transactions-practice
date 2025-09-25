package check

import (
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func Status[S comparable](entityName string, actual S, expecteds ...S) error {
	if slice.Contains(expecteds, actual) {
		return nil
	}
	return status.Errorf(codes.FailedPrecondition, "%s in unexpected status %v, extected one of %v", entityName, actual, expecteds)
}

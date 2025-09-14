module github.com/m4gshm/distributed-transactions-practice/golang

go 1.24.0

require (
	// gen/v1/account v0.0.0
	// gen/v1/orders v0.0.0
	// gen/v1/payment v0.0.0
	// gen/v1/reserve v0.0.0
	// gen/v1/tcp v0.0.0
	github.com/google/uuid v1.6.0
	github.com/lib/pq v1.10.9
	google.golang.org/grpc v1.75.1
	google.golang.org/protobuf v1.36.9
)

require (
	buf.build/gen/go/bufbuild/protovalidate/protocolbuffers/go v1.36.9-20250912141014-52f32327d4b0.1
	github.com/grpc-ecosystem/grpc-gateway/v2 v2.27.2
	google.golang.org/genproto/googleapis/api v0.0.0-20250908214217-97024824d090
)

require (
	golang.org/x/net v0.44.0 // indirect
	golang.org/x/sys v0.36.0 // indirect
	golang.org/x/text v0.29.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20250908214217-97024824d090 // indirect
)

// replace (
// 	gen/v1/account => ./gen/v1/account
// 	gen/v1/orders => ./gen/v1/orders
// 	gen/v1/payment => ./gen/v1/payment
// 	gen/v1/reserve => ./gen/v1/reserve
// 	gen/v1/tcp => ./gen/v1/tcp
// )

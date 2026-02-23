#!/bin/sh

: "${USERS:=30}"
: "${DURATION:=30}"

echo start
curl -X POST http://localhost:8001/profile/start

k6 run -e ORDER_ADDRESS=localhost:9001 --vus "$USERS" --duration "${DURATION}"s list-orders.js

echo finish
curl -X PUT --output profile.pprof http://localhost:8001/profile/stop

go tool pprof --http :9999 profile.pprof

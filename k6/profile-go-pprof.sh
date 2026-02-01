#!/bin/sh

echo start
curl -X POST http://localhost:8001/profile/start

k6 run -e ORDER_ADDRESS=localhost:9001 --vus 30 --duration 30s list-orders.js
# k6 run -e ORDER_ADDRESS=localhost:9001 --vus 1 --duration 1s list-orders.js

echo finish
curl -X PUT --output profile.pprof http://localhost:8001/profile/stop

go tool pprof --http :9999 profile.pprof
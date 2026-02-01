#!/bin/sh

echo start
curl -X POST http://localhost:7080/asyncprof?event=cpu\&format=flamegraph\&options=threads

k6 run --vus 30 --duration 30s list-orders.js
# k6 run --vus 1 --duration 1s list-orders.js

echo finish
curl -X PUT --output flamegraph-java.html http://localhost:7080/asyncprof

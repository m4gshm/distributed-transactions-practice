#!/bin/sh

echo start
curl -X POST http://localhost:7080/api/v1/jfr?config=profile

k6 run --vus 30 --duration 30s list-orders.js
#k6 run --vus 1 list-orders.js

echo finish
curl -X PUT --output profile.jfr http://localhost:7080/api/v1/jfr

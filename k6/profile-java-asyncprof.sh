#!/bin/sh

: "${USERS:=30}"
: "${DURATION:=30}"
: "${EVENT:=cpu}"
: "${FORMAT:=flamegraph}"
: "${OPTIONS:=threads}"

echo start

curl -X POST http://localhost:7080/asyncprof?event="${EVENT}"\&format="${FORMAT}"\&options="${OPTIONS}"

k6 run --vus "$USERS" --duration "${DURATION}"s list-orders.js

echo finish
curl -X PUT --output "${FORMAT}"-java.html http://localhost:7080/asyncprof

#!/usr/bin/env sh


curl -X 'POST' \
  'http://localhost:8080/api/v1/order?twoPhaseCommit=true' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "items": [
    {
      "id": "string",
      "name": "string",
      "count": 0.1,
      "cost": 0.1
    }
  ],
  "customerId": "string",
  "delivery": {
    "dateTime": "2025-07-20T21:14:39.610Z",
    "address": "string",
    "type": "TYPE_PICKUP"
  }
}'
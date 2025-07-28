#!/usr/bin/env sh

docer-compose down
docker volume rm distributed-transactions-practice_db-data

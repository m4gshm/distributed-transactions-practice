#!/usr/bin/env sh

docker-compose down
docker volume rm distributed-transactions-practice_db-data
docker-compose up -d
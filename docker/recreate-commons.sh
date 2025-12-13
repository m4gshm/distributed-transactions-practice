#!/usr/bin/env sh


docker-compose -f ./docker-compose-commons.yaml down
docker volume rm -f distributed-transactions-practice_db-data
docker-compose -f ./docker-compose-commons.yaml up -d
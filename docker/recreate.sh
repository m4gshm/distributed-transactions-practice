#!/usr/bin/env sh


docker-compose -f ./docker-compose-commons.yaml -f ./docker-compose-go.yaml -f ./docker-compose-java.yaml down
docker volume rm -f distributed-transactions-practice_db-data
docker-compose -f ./docker-compose-commons.yaml -f ./docker-compose-go.yaml -f ./docker-compose-java.yaml up -d
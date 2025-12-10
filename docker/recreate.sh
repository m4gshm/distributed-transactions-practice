#!/usr/bin/env sh

DOCKER_COMPOSE_YAML=./docker-compose-go.yaml

docker-compose -f ./docker-compose-go.yaml -f ./docker-compose-java.yaml down
docker volume rm -f distributed-transactions-practice_db-data
docker-compose -f ./docker-compose-go.yaml -f ./docker-compose-java.yaml up -d
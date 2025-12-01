#!/usr/bin/env sh

DOCKER_COMPOSE_YAML=./docker-compose-java.yaml

docker-compose -f $DOCKER_COMPOSE_YAML down
docker volume rm -f distributed-transactions-practice_db-data
docker-compose -f $DOCKER_COMPOSE_YAML up -d
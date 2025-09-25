#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE DATABASE orders;
	CREATE DATABASE payment;
	CREATE DATABASE reserve;
	CREATE DATABASE idempotent_consumer;
EOSQL
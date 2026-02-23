#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE DATABASE jvm_orders;
	CREATE DATABASE jvm_payments;
	CREATE DATABASE jvm_reserve;
	CREATE DATABASE idempotent_consumer;

	CREATE DATABASE go_orders;
	CREATE DATABASE go_payment;
	CREATE DATABASE go_reserve;
EOSQL
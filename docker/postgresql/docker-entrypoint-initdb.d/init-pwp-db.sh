#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE USER pwp;
    CREATE DATABASE pwp;
    GRANT ALL PRIVILEGES ON DATABASE pwp TO pwp;
    ALTER USER pwp WITH PASSWORD 'pwp';
EOSQL

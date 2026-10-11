-- One-time local setup. Run as the PostgreSQL superuser:
--   psql -U postgres -f scripts/db/create-database.sql
-- Change the password for anything other than local development.

CREATE ROLE chequetrack WITH LOGIN PASSWORD 'chequetrack';
CREATE DATABASE chequetrack OWNER chequetrack ENCODING 'UTF8' TEMPLATE template0;

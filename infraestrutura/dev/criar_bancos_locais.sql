SELECT 'CREATE DATABASE eickrono_autorizacao'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'eickrono_autorizacao'
)\gexec

SELECT 'CREATE DATABASE eickrono_identidade'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'eickrono_identidade'
)\gexec

SELECT 'CREATE DATABASE eickrono_contas'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'eickrono_contas'
)\gexec

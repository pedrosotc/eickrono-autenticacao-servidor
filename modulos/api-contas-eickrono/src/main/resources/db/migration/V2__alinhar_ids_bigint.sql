ALTER TABLE contas
    ALTER COLUMN id TYPE BIGINT;

ALTER TABLE transacoes
    ALTER COLUMN id TYPE BIGINT;

ALTER TABLE auditoria_eventos_contas
    ALTER COLUMN id TYPE BIGINT;

ALTER TABLE auditoria_acessos_contas
    ALTER COLUMN id TYPE BIGINT;

ALTER SEQUENCE IF EXISTS contas_id_seq AS BIGINT;
ALTER SEQUENCE IF EXISTS transacoes_id_seq AS BIGINT;
ALTER SEQUENCE IF EXISTS auditoria_eventos_contas_id_seq AS BIGINT;
ALTER SEQUENCE IF EXISTS auditoria_acessos_contas_id_seq AS BIGINT;

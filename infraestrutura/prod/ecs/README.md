# ECS HML

Esta pasta guarda os `task definitions` base do ambiente `hml` na AWS.

Arquivos:

- `auth-task-definition.hml.json`
- `identidade-task-definition.hml.json`
- `thimisu-backend-task-definition.hml.json`

Regras:

- os placeholders `__...__` sao resolvidos pelo script `rollout_hml_service.sh`
- os arquivos assumem `ECS + Fargate`
- os certificados de `mTLS` estao modelados como volume `EFS`
- a explicacao operacional resumida fica em `../runbook_hml_aws_operacional.md`
- o historico ampliado continua em `../guia_subida_hml_aws.md`
- o caminho operacional preferencial de rollout agora e `rollout_hml_service.sh`

## Estado atual da separacao de banco em HML

Hoje os `task definitions` de `hml` ja estao separados por servico no nivel de
nome de banco:

- `auth` usa `keycloak_hml`
- `identidade` usa `eickrono_identidade_hml`
- `thimisu-backend` usa `eickrono_thimisu_hml`

Mas essa separacao ainda acontece no mesmo host RDS:

- `eickrono-hml-postgres.cdu8yi4qkl16.sa-east-1.rds.amazonaws.com`

Ou seja:

- a separacao atual de `hml` na AWS ja evita mistura por banco;
- a separacao fisica completa por host/instancia ainda continua como etapa
  futura da migracao.

Para preparar essa evolucao sem novo refactor estrutural, o script de rollout
ja aceita overrides por servico:

- `AUTH_KC_DB_HOST`
- `AUTH_KC_DB_PORT`
- `AUTH_KC_DB_NAME`
- `AUTH_KC_DB_USERNAME`
- `AUTH_KC_DB_PASSWORD_SECRET_ARN`
- `IDENTIDADE_DB_HOST`
- `IDENTIDADE_DB_PORT`
- `IDENTIDADE_DB_NAME`
- `IDENTIDADE_DB_USERNAME`
- `IDENTIDADE_DB_PASSWORD_SECRET_ARN`
- `THIMISU_DB_HOST`
- `THIMISU_DB_PORT`
- `THIMISU_DB_NAME`
- `THIMISU_DB_USERNAME`
- `THIMISU_DB_PASSWORD_SECRET_ARN`

Se nada for informado, o script usa o host compartilhado atual de `hml` como
default.

Arquivo exemplo:

- `hml-db-overrides.example.env`

Uso rapido:

```bash
bash infraestrutura/prod/ecs/rollout_hml_service.sh \
  --service identidade \
  --db-overrides-file infraestrutura/prod/ecs/hml-db-overrides.example.env \
  --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:hml-20260429-001 \
  --dry-run
```

## Script oficial de rollout

Arquivo:

- `build_push_hml_image.sh`
- `rollout_hml_service.sh`
- `summarize_hml_db_config.sh`
- `validate_hml_task_templates.sh`

### Build e push da imagem

Exemplo de validacao segura:

```bash
bash ./infraestrutura/prod/ecs/build_push_hml_image.sh \
  --service identidade \
  --tag hml-20260429-001 \
  --dry-run
```

Exemplo de execucao real:

```bash
export EICKRONO_HML_HISTORICO="/caminho/para/historico.md"

bash ./infraestrutura/prod/ecs/build_push_hml_image.sh \
  --service identidade \
  --tag hml-20260429-001 \
  --profile Codex-cli_aws
```

Exemplo de validacao segura:

```bash
bash ./infraestrutura/prod/ecs/rollout_hml_service.sh \
  --service identidade \
  --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:hml-20260429-001 \
  --dry-run
```

Exemplo de execucao real:

```bash
export EICKRONO_HML_HISTORICO="/caminho/para/historico.md"

bash ./infraestrutura/prod/ecs/rollout_hml_service.sh \
  --service identidade \
  --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:hml-20260429-001
```

## Teste local do script

Arquivo:

- `../tests/build_push_hml_image_test.sh`
- `../tests/rollout_hml_service_test.sh`

Execucao:

```bash
bash infraestrutura/prod/tests/build_push_hml_image_test.sh
bash infraestrutura/prod/tests/rollout_hml_service_test.sh
bash infraestrutura/prod/tests/summarize_hml_db_config_test.sh
bash infraestrutura/prod/tests/validate_hml_task_templates_test.sh
```

# ECS HML

Esta pasta guarda os `task definitions` base do ambiente `hml` na AWS.

Arquivos:

- `auth-task-definition.hml.json`
- `identidade-task-definition.hml.json`
- `thimisu-backend-task-definition.hml.json`

Regras:

- os placeholders `__...__` devem ser substituidos antes do primeiro deploy real
- os arquivos assumem `ECS + Fargate`
- os certificados de `mTLS` estao modelados como volume `EFS`
- a explicacao operacional resumida fica em `../runbook_hml_aws_operacional.md`
- o historico ampliado continua em `../guia_subida_hml_aws.md`
- o caminho operacional preferencial de rollout agora e `rollout_hml_service.sh`

## Script oficial de rollout

Arquivo:

- `build_push_hml_image.sh`
- `rollout_hml_service.sh`

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
```

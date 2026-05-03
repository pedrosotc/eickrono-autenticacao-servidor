# Runbook Operacional de HML na AWS

## Objetivo

Este arquivo e o ponto de entrada canônico para subir, atualizar e validar o
ambiente `hml` na AWS sem depender da leitura linear do historico completo.

Estado atual da base de dados em `hml`:

- `auth`, `identidade` e `thimisu-backend` ja usam bancos diferentes;
- esses bancos ainda estao no mesmo host RDS em `hml`;
- a separacao fisica completa por host/instancia ainda nao faz parte do estado
  atual do ambiente.

## Como usar

- use este arquivo para a ordem operacional atual;
- use `guia_subida_hml_aws.md` quando precisar da trilha historica completa,
  com contexto, causas-raiz e comandos cronologicos;
- use os arquivos especializados desta pasta quando quiser aprofundar apenas um
  assunto.

Leitura complementar por assunto:

- `README.md`: indice geral da infraestrutura de `prod` e `hml`
- `ecs/README.md`: build, push e rollout dos servicos no `ECS`
- `docker/README.md`: imagem do runtime Keycloak customizado
- `cloudflare/README.md`: DNS e registros `TXT`
- `validacao_cabecalho_email_provedores.md`: entregabilidade e validacao de
  e-mail

## Credenciais e valores operacionais documentados

Este arquivo e apenas o ponto de entrada. Os valores reais e os caminhos de
credenciais usados na operacao continuam documentados nos arquivos abaixo:

- `CREDENCIAIS_RAPIDAS.md`
  Acesso direto aos valores e caminhos reais mais usados.
- `cloudflare/README.md`
  Mantem os valores reais atuais de `CLOUDFLARE_API_TOKEN`,
  `CLOUDFLARE_ACCOUNT_ID` e `zone IDs`.
- `README.md`, nesta mesma pasta
  Mantem a referencia operacional do broker Apple em
  `.local-secrets/apple/eickrono-oidc/prod/keycloak-apple.env`.
- `guia_subida_hml_aws.md`
  Mantem o detalhamento completo de secrets, `SMTP`, `KEYCLOAK_ADMIN`,
  `KEYCLOAK_ADMIN_PASSWORD`, `mTLS`, `client secrets` e exemplos operacionais
  reais usados nas rodadas anteriores.
- `historico_execucao_hml_aws_*.md`
  Mantem registros cronologicos por rodada quando a operacao exigiu consultar ou
  atualizar credenciais reais.

## Ordem operacional recomendada

1. Preparar acesso e historico local

- autenticar na AWS com o profile correto;
- definir `EICKRONO_HML_HISTORICO` antes de executar comandos sensiveis ou
  rollout;
- confirmar que os artefatos locais e segredos esperados estao disponiveis.

2. Empacotar os servicos

- no `eickrono-autenticacao-servidor`, rodar `make package-servicos` quando a
  rodada envolver autenticacao, identidade e `thimisu-backend`;
- se a mudanca for isolada, empacotar apenas o servico necessario antes do
  build de imagem.

3. Construir e publicar a imagem

- para `auth`, `identidade` ou `thimisu-backend`, usar
  `infraestrutura/prod/ecs/build_push_hml_image.sh`;
- validar primeiro com `--dry-run`;
- so depois executar o push real com `--profile Codex-cli_aws`.

4. Executar o rollout do servico

- usar `infraestrutura/prod/ecs/rollout_hml_service.sh`;
- quando precisar mudar host, porta, nome do banco ou usuario por servico,
  usar `--db-overrides-file infraestrutura/prod/ecs/hml-db-overrides.example.env`
  como base e ajustar os valores necessarios nesse arquivo ou em uma copia dele;
- registrar imagem, task definition e resultado no historico;
- acompanhar `running`, `pending` e `rolloutState` ate `COMPLETED`.

5. Validar a malha publica e interna

- confirmar:
  - `https://oidc-hml.eickrono.store/realms/eickrono/eickrono-runtime/estado`
  - `https://id-hml.eickrono.store/api/v1/estado`
  - `https://thimisu-backend-hml.eickrono.store/api/v1/estado`
- validar `issuer`, discovery OIDC e emissao de token interno;
- validar o endpoint publico de disponibilidade da identidade.

6. Validar dependencias auxiliares quando houver impacto

- DNS, certificados e `TXT`: `cloudflare/README.md`
- entregabilidade e cabecalhos: `validacao_cabecalho_email_provedores.md`
- certificados e imagem do Keycloak: `docker/README.md`

## Sinais minimos de ambiente saudavel

- `auth runtime`, `identidade` e `thimisu-backend` respondendo `200`;
- discovery OIDC publico consistente com o `issuer` canonico do ambiente;
- `client_credentials` interno emitido com `iss` e `aud` esperados;
- endpoint publico de disponibilidade retornando `disponivel=true`;
- login OIDC abrindo com `HTTP 200` em fluxo real de navegador/PKCE.

## Trilha historica associada

- `guia_subida_hml_aws.md`: runbook historico consolidado e hibrido
- `historico_execucao_hml_aws_*.md`: registros de execucao pontuais por rodada

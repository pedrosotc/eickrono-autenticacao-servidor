# Matriz de Migracao entre Autenticacao, Identidade e Thimisu-Backend

Este documento separa o que ja pode ser tratado como nomenclatura canônica
do que ainda continua preso a alias legados de wire, runtime ou infraestrutura.

O objetivo aqui nao e "renomear tudo de uma vez". O objetivo e evitar
quebrar contrato OIDC, `mTLS`, `docker-compose` e provisionamento interno
enquanto o ecossistema termina o cutover para `thimisu-backend`.

## Convencao canônica aprovada

### Superficies e hosts

- superficie do produto:
  - `thimisu-dev.eickrono.com`
  - `thimisu-hml.eickrono.com`
  - `thimisu.eickrono.com`
- backend de dominio consumido pelo app e pelo ecossistema:
  - `thimisu-backend-dev.eickrono.com`
  - `thimisu-backend-hml.eickrono.com`
  - `thimisu-backend.eickrono.com`
- borda publica de autenticacao/identidade:
  - `id-dev.eickrono.com`
  - `id-hml.eickrono.com`
  - `id.eickrono.com`
- servidor OIDC:
  - `oidc-dev.eickrono.com`
  - `oidc-hml.eickrono.com`
  - `oidc.eickrono.com`

### Identificadores canônicos de sistema

- nome logico do backend de dominio no ecossistema: `thimisu-backend`
- client id canônico de backchannel JWT: `thimisu-backend`

## Distincao importante

Nem todo nome legado precisa ser trocado na mesma rodada.

Hoje existem quatro classes diferentes de identificador:

1. nome funcional do sistema no dominio interno
2. client id OIDC usado no `client_credentials`
3. audiencia/resource client usada na validacao do JWT
4. alias tecnico de modulo, artefato, container e certificado

Misturar essas quatro coisas na mesma migracao aumenta risco sem gerar ganho
imediato. Por isso a matriz abaixo classifica o estado de cada uma.

## Estado consolidado

| Superficie | Valor atual | Alvo canônico | Estado desta rodada | Proximo passo |
| --- | --- | --- | --- | --- |
| Nome logico do sistema chamador no fluxo interno | `identidade-servidor` | `thimisu-backend` | Compatibilidade ja aplicada no `api-identidade-eickrono`; o default do cadastro interno passou a `thimisu-backend`, e a migration `V18` agora semeia `thimisu-backend` + alias legado. | Parar de abrir novos fluxos internos com `identidade-servidor`; manter leitura do alias legado ate o cutover completo. |
| Host publico do backend de dominio | sem host canônico materializado em todos os ambientes | `thimisu-backend-*` | Convenção aprovada; o app ja aceita `CONFIG_THIMISU_BASE_URL` sem fixar asset errado. | Publicar DNS/runtime por ambiente e depois cristalizar os assets do app. |
| Realm OIDC em `hml` e `prd` | valores historicos divergentes em parte do runtime | `eickrono` | Runtime principal ja alinhado para `eickrono`; `application-hml.yml`/`application-prd.yml` do backend de dominio e `hml` local agora usam `issuer` compatível com `eickrono`. | Fechar os ultimos pontos de runtime e exports ainda amarrados ao legado. |
| Client id de backchannel JWT | `identidade-servidor` | `thimisu-backend` | Compatibilidade ja entrou no `api-identidade-eickrono`, que aceita o nome canônico e os aliases legados de transição. | Ajustar secrets por ambiente e concluir o cutover do Keycloak e do runtime do backend de dominio. |
| Audience/resource client do backend de dominio | `thimisu-backend` | `thimisu-backend` | Migracao concluida no runtime. `application.yml`, exports do Keycloak, audience mappers e testes ja usam o nome canonico. | Manter como identificador de runtime. |
| Certificado `mTLS` do backend de dominio | `thimisu-backend.p12` | `thimisu-backend.p12` | Migracao concluida no runtime. Scripts de geracao, SAN, docs e `.env` ja usam o nome canonico. | Manter como identificador de runtime. |
| Repo, modulo e artifactId | `eickrono-thimisu-backend` / `thimisu-backend` | `thimisu-backend` | Rename estrutural principal aplicado em repositório, módulo Maven, `artifactId`, `Dockerfile` e `docker-compose`. | Manter apenas sincronismo documental e operacional dos caminhos novos. |

## O que ja foi refatorado com seguranca

### `eickrono-autenticacao-servidor`

- `CadastroInternoController` agora usa `thimisu-backend` como
  `sistemaSolicitante` padrao;
- `IntegracaoInternaProperties` da API de identidade aceita:
  - `thimisu-backend`
  - `identidade-servidor`
  - `servidor-autorizacao`
- `application.yml` da API de identidade passou a carregar a lista de clientes
  internos com o canônico primeiro e o alias legado ainda permitido;
- `V18__seed_catalogo_multiapp_inicial.sql` agora semeia:
  - `thimisu-backend`
  - `identidade-servidor` como alias legado documentado
- os testes de cadastro interno, envio SMTP e atestacao interna ja foram
  alinhados ao nome canônico onde isso nao quebra contrato de wire.

### `eickrono-thimisu-backend`

- a documentacao do projeto ja registra `thimisu-backend-*` como convenio
  canônico para o host publico do backend de dominio;
- o `hml` local passou a usar `issuer`/`jwk-set-uri` coerentes com
  `https://oidc-hml.eickrono.store/realms/eickrono`;
- o `docker-compose` local de `hml` passou a assumir `realm=eickrono` para o
  client interno de backchannel.

### `eickrono-thimisu-app`

- o bootstrap ja aceita sobrescritas independentes de:
  - `CONFIG_IDENTIDADE_BASE_URL`
  - `CONFIG_THIMISU_BASE_URL`
  - `CONFIG_OIDC_ISSUER`
- isso permite validar ambientes hibridos sem fixar cedo demais um host
  publico errado do backend de dominio.

## O que ainda nao deve ser trocado cegamente

### 1. `identidade-servidor`

Ainda aparece em:

- aliases ou imports antigos do Keycloak
- `docker-compose` local do backend de dominio
- segredos/client credentials de ambiente

Trocar isso so no YAML do backend quebraria a emissao do JWT interno.

### 2. `thimisu-backend`

Ja aparece de forma canonica em:

- `spring.application.name`
- `management.metrics.tags.application`
- `fapi.seguranca.audiencia-esperada`
- exports do Keycloak
- audience mappers
- testes

O nome canônico já vale tanto para runtime quanto para o módulo principal.

### 3. `thimisu-backend.p12`

Ja aparece de forma canonica em:

- scripts de geracao de certificados `dev` e `hml`
- `.env` do backend de dominio
- guias de `mTLS`
- SAN e alias dos certificados locais

Esse corte ja foi coordenado com os consumers do backchannel local.

## Ordem recomendada da migracao restante

1. manter `thimisu-backend` como nome logico canônico de sistema;
2. publicar o host `thimisu-backend-*` por ambiente;
3. criar ou alinhar o client `thimisu-backend` no Keycloak;
4. rotacionar secret e ajustar runtime do backend de dominio;
5. por ultimo, se ainda fizer sentido, renomear repo/modulo/artefato.

## Leitura em conjunto

- [Plano de Padronizacao para Realm Unico](plano-padronizacao-realm-unico.md)
- [Backlog Cross-Service de Autenticacao, OIDC e Dispositivo](backlog_cross_service_autenticacao_oidc_dispositivo.md)
- `../eickrono-thimisu-backend/README.md`

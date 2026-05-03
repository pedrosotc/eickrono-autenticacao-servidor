# Eickrono Autenticação

Projeto do ecossistema de autenticação da Eickrono que concentra:

- o runtime do servidor de autorização baseado em Keycloak customizado;
- os artefatos de runtime do Keycloak organizados em `autorizacao/`;
- a orquestração local via `docker compose`;
- certificados, scripts e documentação operacional da stack.

A decisão canônica de nomes dos serviços está em
`documentacao/decisao_nomenclatura_repositorios_servicos.md`.

Hoje a divisão correta é:

- `eickrono-autenticacao-servidor`: servidor de autorização Keycloak customizado + infraestrutura operacional da stack;
- `eickrono-identidade-servidor`: API pública de identidade/autenticação usada pelo app;
- `eickrono-contas-servidor`: API de contas.

## Diretriz de nomenclatura

Na autenticação, modelos, tabelas, contratos, enums e documentação devem
evitar nomes específicos de produto quando o conceito for compartilhado pelo
ecossistema.

Regra prática:

- usar nomes gerais como `cliente`, `sistema`, `vinculo`, `perfilSistema` e
  equivalentes;
- evitar nomes como `Thimisu` quando a regra vale para vários apps, sites ou
  softwares;
- só usar o nome de um produto quando a regra realmente for exclusiva dele.

## Documentação canônica

A documentação principal permanece em `documentacao/`, mas o índice canônico do
projeto agora fica neste `README.md`.

### Diretriz vigente para o app móvel

- cadastro, confirmação de e-mail, login e recuperação de senha entram pela API pública de identidade;
- a autenticação continua dona da conta central, das credenciais e das liberações internas;
- a identidade continua dona da `Pessoa` canônica;
- o `thimisu` recebe apenas o provisionamento do perfil daquele sistema depois que conta central e `Pessoa` já tiverem sido resolvidas;
- o `X-Device-Token` canônico nasce no próprio login público da autenticação;
- qualquer explicação antiga centrada em navegador, `OIDC` interativo no app ou autenticação pública via `thimisu` deve ser considerada legada.

### Guias principais

- `documentacao/guia-arquitetura.md`: papel de cada serviço, contratos canônicos e segurança do fluxo
- `documentacao/consolidado_migracao_autenticacao_identidade_thimisu.md`: consolidado único das responsabilidades, migrações e perguntas abertas entre autenticação, identidade e thimisu
- `documentacao/guia-seguranca-app-movel.md`: sinais locais do app, atestação e decisão de risco no backend
- `documentacao/guia-desenvolvimento.md`: ambiente local, `MailHog`, Docker e rotina de desenvolvimento
- `documentacao/guia-mtls.md`: malha `mTLS` do backchannel e geração de certificados
- `documentacao/guia-operacao-producao.md`: runtime, operação e observabilidade
- `documentacao/padrao-codigos-erro-correlacao-observabilidade.md`: padrão canônico de `error_code`, `flow_id`, logs mascarados, traces e auditoria
- `documentacao/guia-cloudflare-tunnel-google-keycloak-dev.md`: exposição temporária do Keycloak local para Google OAuth brokerado
- `documentacao/plano-padronizacao-realm-unico.md`: alvo arquitetural para padronizar o realm `OIDC`
- `documentacao/matriz_migracao_autenticacao_identidade_thimisu_backend.md`: transição consolidada entre autenticação, identidade e `thimisu-backend`
- `documentacao/analise_fronteiras_funcionais_autenticacao_identidade_thimisu_backend.md`: verificação objetiva das fronteiras funcionais
- `documentacao/runbook_migracao_multiapp_schemas.md`: ordem prática da migração do legado em `public` para o modelo por schemas
- `documentacao/backlog_cross_service_autenticacao_oidc_dispositivo.md`: backlog priorizado da coordenação entre app, autenticação, Keycloak e identidade-servidor

## Orquestração canônica

Os comandos operacionais de build, teste e subida da stack agora ficam
centralizados neste repositório:

- `make package-servicos`
- `make test-servicos`
- `make test-servicos-completo` (`Docker` acessível, porque a identidade usa `Testcontainers`)
- `make compose-config`
- `make up-dev`
- `make up-hml`

## Consulta de versão em runtime

Para conferência operacional do que está rodando:

- servidor de autorização/Keycloak customizado:
  - `GET /realms/{realm}/eickrono-runtime/estado`
  - resposta com `servico`, `status`, `versao` e `buildTime`

Esse endpoint é atendido pelo provider customizado deste projeto e devolve a
versão do artefato Java empacotado no runtime do Keycloak.

## Arquitetura canônica

- o app fala diretamente com a API de identidade/autenticação para cadastro, login e recuperação de senha;
- o `identidade-servidor` é a borda pública do app;
- a autenticação continua sendo a autoridade central de credencial, sessão e vínculo por sistema;
- o backend do produto recebe apenas provisionamento interno de perfil e contexto já autorizados;
- a confirmação de e-mail acontece na autenticação antes de qualquer provisionamento no domínio do produto;
- o app não abre uma tela dedicada de registro de dispositivo;
- se a autenticação exigir validação adicional de contato, o app reutiliza a tela de verificação já existente;
- a recuperação de senha sempre responde ao app com mensagem genérica, sem revelar se o e-mail existe.

## Responsabilidades deste repositório

- empacotar o provider/JAR do servidor de autorização;
- versionar `autorizacao/realms`, `autorizacao/temas` e `autorizacao/providers`;
- sustentar o refresh com validação de `device token` por backchannel;
- fornecer a infraestrutura local de `docker compose`, `MailHog` e certificados;
- documentar a operação da stack de autenticação.

Os endpoints públicos usados pelo app ficam no projeto irmão
`eickrono-identidade-servidor`.

## Papel na arquitetura canônica

No fluxo móvel atual:

- a borda pública do app é o `eickrono-identidade-servidor`;
- este repositório sustenta a parte de Keycloak/RH-SSO do ecossistema;
- login, recuperação de senha e demais fluxos sensíveis continuam centralizados na API pública de identidade;
- a autenticação continua como dona da conta central, de `usuario + sistema`, do refresh e das políticas de segurança;
- o provider daqui consulta a identidade por `mTLS` no refresh protegido por `device token`.

## Comunicação interna entre servidores

No fluxo canônico de cadastro, a autenticação coordena duas etapas internas:

1. a autenticação aciona a identidade para criar ou atualizar a `Pessoa` canônica;
2. depois disso, a autenticação aciona o backend do produto para criar ou atualizar o perfil daquele sistema.

Essas comunicações internas devem ser:

- autenticado por JWT de serviço;
- restrito por allowlist de `client_id`;
- protegido por `mTLS`;
- idempotentes por `cadastroId`, para que retries não dupliquem `Pessoa` ou perfil de sistema.

## Sessão e recuperação de senha

Fluxos públicos canônicos da autenticação:

- `POST /api/publica/cadastros`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email/reenvio`
- `POST /api/publica/sessoes`
- `POST /api/publica/recuperacoes-senha`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/senha`

Qualquer rota pública de autenticação existente em servidor de produto deve ser tratada como legada e não deve ser usada por novos clientes.

## Sessão, dispositivo e `X-Device-Token`

No desenho canônico atual:

- o login público já valida credenciais, atestação nativa e metadados do aparelho;
- o backend decide se o aparelho pode ser aceito silenciosamente;
- quando o contexto estiver válido, a própria autenticação emite o `X-Device-Token` na resposta de `POST /api/publica/sessoes`;
- o app apenas persiste esse token e o envia depois nas chamadas protegidas;
- o app não calcula localmente um estado de "onboarding de dispositivo";
- uma tela separada de registro de dispositivo não faz mais parte do fluxo principal.

## Derivação de senha

- a credencial efetiva não usa mais `data_nascimento` como insumo auxiliar;
- a SPI do Keycloak deriva a senha com `pepper + createdTimestamp`, usando apenas o campo nativo do usuário no Keycloak;
- o mesmo mecanismo precisa ser respeitado em reset de senha e required actions.

## Atualização local obrigatória do `docker compose`

Os containers Java locais não recompilam código automaticamente. A imagem da API de identidade copia o `jar` já empacotado em `target/`, então alteração em Java sem novo `package` deixa o ambiente rodando código antigo.

Quando mudar qualquer um dos três projetos da stack:

1. `make package-servicos`
2. `make up-dev`

Se precisar agir isoladamente em um projeto:

- identidade: `cd ../eickrono-identidade-servidor && mvn -q package -DskipTests`
- contas: `cd ../eickrono-contas-servidor && mvn -q package -DskipTests`
- autenticação/autorização: `mvn -q package -DskipTests`

Se o problema observado no app divergir do código-fonte atual, a primeira hipótese operacional deve ser container desatualizado.

## Ambientes locais

Em `dev` e `hml`, o `docker compose` inclui `MailHog` para testes locais de e-mail:

- `dev`: SMTP `localhost:1025`, UI `http://localhost:8025`
- `hml`: SMTP `localhost:11025`, UI `http://localhost:18025`

No `dev`, se o `.env` ja estiver apontando para um SMTP real, ainda e possivel
forcar o uso do MailHog sem alterar essas credenciais:

1. `cd infraestrutura/dev`
2. `docker compose -f docker-compose.yml -f docker-compose.email-fake.yml up -d --build smtp-teste api-identidade-eickrono`
3. abrir `http://localhost:8025`

O `docker compose` local usa PostgreSQL já existente no ambiente local, com bancos separados por serviço:

- `dev` Keycloak/autorização: `jdbc:postgresql://localhost:5432/eickrono_autorizacao`
- `dev` identidade: `jdbc:postgresql://localhost:5432/eickrono_identidade`
- `dev` contas: `jdbc:postgresql://localhost:5432/eickrono_contas`
- `hml` Keycloak: `jdbc:postgresql://localhost:5432/keycloak_hml`
- `hml` identidade: `jdbc:postgresql://localhost:5432/eickrono_identidade_hml`
- `hml` contas: `jdbc:postgresql://localhost:5432/eickrono_contas_hml`

## Swagger

- API identidade `dev`: `http://localhost:8081/swagger-ui/index.html`
- API identidade `dev` OpenAPI: `http://localhost:8081/v3/api-docs`
- API identidade `hml`: `http://localhost:18081/swagger-ui/index.html`
- API identidade `hml` OpenAPI: `http://localhost:18081/v3/api-docs`
- API contas `dev`: `http://localhost:8082/swagger-ui/index.html`
- API contas `dev` OpenAPI: `http://localhost:8082/v3/api-docs`
- API contas `hml`: `http://localhost:18082/swagger-ui/index.html`
- API contas `hml` OpenAPI: `http://localhost:18082/v3/api-docs`

Proteção:

- `dev`: uso local liberado;
- `hml`: `Basic Auth` + whitelist de IP;
- credenciais padrão de `hml`: usuário `swagger`, senha `swagger-hml`.

## Leitura recomendada

- `documentacao/guia-arquitetura.md`
- `documentacao/fluxogramas_fluxos_publicos_estado_atual.md`
- `documentacao/fluxogramas_fluxos_publicos_regra_funcional_em_fechamento.md`
- `documentacao/especificacao_schema_db01_db02_db03_fluxos_publicos.md`
- `documentacao/especificacao_avatar_social_e_avatar_preferido_multiapp.md`
- `documentacao/padrao-codigos-erro-correlacao-observabilidade.md`
- `documentacao/matriz_migracao_autenticacao_identidade_thimisu_backend.md`
- `documentacao/analise_fronteiras_funcionais_autenticacao_identidade_thimisu_backend.md`
- `documentacao/plano_migrations_v30_v36_db01_db02_db03_local_primeiro.md`
- `documentacao/mapeamento_tdd_componentes_migracoes_fluxos_publicos.md`
- `documentacao/runbook_migracao_multiapp_schemas.md`
- `documentacao/backlog_cross_service_autenticacao_oidc_dispositivo.md`
- `documentacao/guia-seguranca-app-movel.md`
- `documentacao/guia-desenvolvimento.md`
- `documentacao/guia-mtls.md`
- `documentacao/guia-operacao-producao.md`
- `documentacao/checklist-seguranca-fapi.md`
- `documentacao/guia-cloudflare-tunnel-google-keycloak-dev.md`
- `documentacao/plano-padronizacao-realm-unico.md`
- `infraestrutura/prod/pipeline/README.md`

> Toda a documentação, comentários e identificadores permanecem em português do Brasil, conforme diretriz organizacional.

# Eickrono Autenticação

Monorepo da plataforma de identidade, credenciais e sessão da Eickrono. Este repositório é a borda pública canônica para os fluxos sensíveis do app móvel: cadastro, confirmação de e-mail, login, recuperação de senha, refresh e emissão do `X-Device-Token`.

## Arquitetura canônica

- o app fala diretamente com a API de identidade/autenticação para cadastro, login e recuperação de senha;
- o app não envia senha, código de recuperação nem tentativa de login ao `identidade-servidor`;
- o `identidade-servidor` recebe apenas provisionamento interno de perfil e contexto já autorizados;
- a confirmação de e-mail acontece na autenticação antes de qualquer provisionamento no domínio do produto;
- o app não abre uma tela dedicada de registro de dispositivo;
- se a autenticação exigir validação adicional de contato, o app reutiliza a tela de verificação já existente;
- a recuperação de senha sempre responde ao app com mensagem genérica, sem revelar se o e-mail existe.

## Responsabilidades deste repositório

- expor os endpoints públicos de identidade para o app;
- armazenar e validar credenciais;
- enviar e validar códigos de e-mail;
- aplicar rate limit, timeout, antifraude, lockout e auditoria;
- emitir e renovar sessão;
- decidir confiança do dispositivo e emitir o `X-Device-Token` já no login;
- chamar o `eickrono-identidade-servidor` por backchannel para provisionar perfil de negócio depois da confirmação de e-mail.

## Backchannel para o servidor de identidade

O fluxo canônico de cadastro agora parte da autenticação para o `eickrono-identidade-servidor`:

1. o app cria um cadastro pendente na autenticação;
2. a autenticação envia o código de confirmação por e-mail;
3. o app confirma o código com a autenticação;
4. a autenticação provisiona o perfil do usuário no `identidade-servidor` por backchannel;
5. o `identidade-servidor` responde com os identificadores de domínio já criados;
6. a autenticação libera o login no app.

Esse provisionamento interno deve ser:

- autenticado por JWT de serviço;
- restrito por allowlist de `client_id`;
- protegido por `mTLS`;
- idempotente por `cadastroId`, para que retries não dupliquem pessoa ou usuário no `identidade-servidor`.

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

Quando mudar a API de identidade:

1. `mvn -q -pl modulos/api-identidade-eickrono -am package -DskipTests`
2. `cd infraestrutura/dev && docker compose up -d --build api-identidade-eickrono`

Quando mudar customizações do Keycloak em `modulos/servidor-autorizacao-eickrono`:

1. `mvn -q -pl modulos/servidor-autorizacao-eickrono -am package -DskipTests`
2. `cd infraestrutura/dev && docker compose up -d servidor-autorizacao`

Se o problema observado no app divergir do código-fonte atual, a primeira hipótese operacional deve ser container desatualizado.

## Ambientes locais

Em `dev` e `hml`, o envio de e-mails usa `MailHog`:

- `dev`: SMTP `localhost:1025`, UI `http://localhost:8025`
- `hml`: SMTP `localhost:11025`, UI `http://localhost:18025`

O `docker compose` local usa PostgreSQL compartilhado já existente no Docker:

- `dev`: `jdbc:postgresql://localhost:5432/eickrono_dev`
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

- `documentacao/README.md`
- `documentacao/guia-arquitetura.md`
- `documentacao/guia-seguranca-app-movel.md`
- `documentacao/guia-desenvolvimento.md`
- `documentacao/guia-mtls.md`

> Toda a documentação, comentários e identificadores permanecem em português do Brasil, conforme diretriz organizacional.

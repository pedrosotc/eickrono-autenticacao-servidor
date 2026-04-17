# Guia de Arquitetura

Este guia descreve a arquitetura canônica do ecossistema de autenticação da Eickrono para o app móvel e para os serviços internos que participam do provisionamento do perfil do usuário.

## Diretriz central

Para o app móvel:

- cadastro, confirmação de e-mail, login e recuperação de senha entram pela autenticação;
- o app não abre páginas do `realm` nem delega senha ao `identidade-servidor`;
- o `identidade-servidor` não é mais a borda pública de senha, código de recuperação ou código de confirmação de cadastro;
- o `identidade-servidor` recebe apenas provisionamento de perfil de negócio por backchannel depois que a autenticação conclui as etapas sensíveis.

## Componentes principais

- **Servidor de autorização (Keycloak/RH-SSO):** autoridade de credencial, token, required actions, derivação de senha e políticas de refresh.
- **API Identidade Eickrono:** borda pública do app para cadastro, confirmação de e-mail, login, recuperação de senha, emissão de `X-Device-Token` e integrações móveis.
- **Thimisu servidor:** downstream de domínio, provisionado por backchannel depois da confirmação de e-mail.
- **API Contas Eickrono:** domínio financeiro separado, sem receber senha ou código do app.
- **PostgreSQL + Flyway:** persistência dos estados de cadastro, dispositivos, códigos, tokens e auditoria.
- **Observabilidade:** Actuator, Micrometer, Prometheus e OpenTelemetry.

## Papéis por serviço

### Autenticação

- recebe o cadastro público do app;
- valida e guarda credenciais;
- envia e valida códigos de e-mail;
- responde de forma genérica na recuperação de senha para não enumerar usuários;
- executa login e emite sessão;
- controla confiança do dispositivo e emite `X-Device-Token` já no login;
- provisiona o perfil no thimisu depois da confirmação de e-mail.

### Thimisu

- persiste pessoa e usuário do domínio de estudo;
- recebe apenas os dados necessários para criar o perfil do produto;
- nunca recebe senha digitada, código de recuperação ou tentativa de login do app;
- deve tratar o provisionamento recebido da autenticação como operação idempotente.

## Fluxos públicos canônicos

### Cadastro

1. o app envia o cadastro para a autenticação;
2. a autenticação cria um cadastro pendente e envia o código de e-mail;
3. o app confirma o código com a autenticação;
4. a autenticação provisiona o perfil no thimisu por backchannel;
5. o thimisu devolve os identificadores de domínio criados;
6. a autenticação libera o login do usuário.

### Login

1. o app envia login, senha, atestação e metadados do aparelho para a autenticação;
2. a autenticação valida a credencial e a política de dispositivo;
3. a autenticação registra ou atualiza silenciosamente o dispositivo quando o risco permitir;
4. a autenticação emite a sessão já com `X-Device-Token`;
5. o app não abre uma tela dedicada de registro de dispositivo.

### Recuperação de senha

1. o app envia o e-mail para a autenticação;
2. a autenticação sempre devolve mensagem neutra;
3. se existir conta, a autenticação envia o código por e-mail;
4. o app confirma o código com a autenticação;
5. o app envia a nova senha para a autenticação;
6. a autenticação atualiza a credencial no Keycloak e registra auditoria.

## Contratos públicos canônicos

Para o app móvel, os contratos canônicos passam a ser:

- `POST /api/publica/cadastros`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email/reenvio`
- `POST /api/publica/sessoes`
- `POST /api/publica/recuperacoes-senha`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/senha`

Qualquer contrato antigo em que servidor de produto receba senha ou código do app deve ser tratado como legado.

## Backchannel autenticação -> thimisu

O provisionamento interno do perfil deve seguir estas regras:

- autenticação mútua por `mTLS`;
- JWT de serviço com allowlist explícita de `client_id`;
- timeout curto e retry controlado;
- idempotência por `cadastroId`;
- auditoria em ambos os lados.

### O que significa idempotência por `cadastroId`

Se a autenticação repetir o mesmo provisionamento por timeout, retry ou falha de rede:

- o thimisu não pode criar uma segunda pessoa;
- o thimisu deve reconhecer o mesmo `cadastroId`;
- o thimisu deve devolver a mesma resposta lógica da primeira criação.

Isso evita duplicidade de perfil quando a primeira resposta se perde no caminho.

## Segurança

- o app nunca recebe resposta que diga explicitamente se o e-mail existe na recuperação de senha;
- o thimisu não recebe segredo principal do usuário;
- logs precisam mascarar tokens, e-mails e identificadores sensíveis;
- rate limit, lockout, expiração de código, limite de tentativas e antifraude ficam concentrados na autenticação;
- qualquer integração interna entre serviços modernos deve usar `mTLS` + JWT de serviço.

## Dispositivo e atestação

- a atestação nativa de app/dispositivo continua centralizada na autenticação;
- o `X-Device-Token` é emitido pelo backend já no login quando o dispositivo estiver liberado;
- se houver necessidade de nova validação de contato, o app deve reutilizar a tela de verificação já existente, não uma tela separada de registro de dispositivo;
- o refresh continua condicionado ao `device_token`, validado centralmente.

Para a camada adicional de sinais locais do app além de `Play Integrity` e `App Attest`, ler também:

- `guia-seguranca-app-movel.md`

## Sobre FAPI e OIDC

O servidor de autorização continua suportando mecanismos FAPI, OIDC e client policies para clientes confidenciais, web e integrações específicas. Para o app móvel, porém, a diretriz vigente é UX nativa mediada pela API de identidade, não telas web do `realm`.

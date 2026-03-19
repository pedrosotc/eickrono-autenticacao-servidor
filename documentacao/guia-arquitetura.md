# Guia de Arquitetura

Este guia descreve a arquitetura do ecossistema de autenticação da Eickrono, destacando componentes, integrações e fluxos compatíveis com o padrão FAPI.

> Atualizacao de diretriz para o app `flashcard`: o cadastro e o login do aplicativo nao devem usar OIDC interativo com navegador. A UX passa a ser nativa, com `flashcard-servidor` orquestrando o fluxo do produto e `autenticacao-servidor` mantendo identidade, verificacao e emissao de sessao. O contrato canônico desse fluxo foi registrado em `../../eickrono-flashcard-servidor/docs/fluxo_cadastro_login_nativo.md`.

## Componentes principais

- **Servidor de autorização (Keycloak/RH-SSO):** responsável pelos realms `desenvolvimento`, `homologacao` e `producao`. Mantém configurações PAR/JAR/JARM, políticas de MFA/WebAuthn/Passkeys e rotação de chaves JWK.
- **API Identidade Eickrono:** serviço Spring Boot que expõe recursos de perfil e vínculos sociais, validando tokens JWT provenientes do servidor de autorização.
- **Atestação nativa de app/dispositivo:** a API de Identidade também centraliza a emissão autoritativa de desafios e a validação de `Play Integrity` e `App Attest`, consumidas por `backchannel` pelos servidores de produto.
- **Registro de dispositivos móveis:** conjunto de serviços na API de Identidade (`RegistroDispositivoService`, `TokenDispositivoService`, `CodigoVerificacaoService`) que gerenciam onboarding de novos aparelhos, revogação de tokens antigos e verificação por canais configuráveis, com e-mail sempre obrigatório e SMS opcional por política.
- **Política offline centralizada:** a API de Identidade publica uma política central de uso offline e recebe a reconciliação dos eventos offline do aplicativo, sempre vinculando esses eventos a uma identidade explícita de dispositivo.
- **API Contas Eickrono:** serviço Spring Boot para operações de contas e transações, com escopos e papéis específicos (`SCOPE_transacoes:ler`, `ROLE_cliente`) e auditoria detalhada.
- **PostgreSQL:** banco multi-schema, com versionamento por Flyway e separação de usuários por ambiente.
- **Caffeine Cache:** camada de cache em memória utilizada de forma consistente pelos serviços.
- **Observabilidade:** Actuator, Micrometer (Prometheus) e OpenTelemetry (OTLP) compondo o stack de métricas e tracing.
- **Infraestrutura Cloud:** AWS (EKS/ECS, RDS, ACM, KMS/HSM, Secrets Manager, ALB/NLB) protegida por Cloudflare (WAF, Rate Limit, mTLS Origin Pull).

## Fluxos FAPI

1. **Authorization Code + PKCE:**  
   - Aplicativo público (`aplicativo-flutter-eickrono`) inicia fluxo com `code_verifier` e `code_challenge`.  
   - Servidor de autorização valida `state` e `nonce` durante o retorno do `redirect_uri`.
2. **PAR (Pushed Authorization Request):**  
   - Clientes confidenciais (`bff-web-eickrono`, `apis-internas-eickrono`) enviam parâmetros de autorização de forma autenticada via `request_uri` protegida.  
   - O servidor armazena a requisição temporariamente, mitigando exposição de dados sensíveis.
3. **JAR (JWT Authorization Request):**  
   - Os parâmetros de autorização são encapsulados em JWT assinado pelo cliente, garantindo integridade e autenticação.
4. **JARM (JWT Authorization Response Mode):**  
   - As respostas de autorização são assinadas/criptografadas pelo servidor, preservando confidencialidade e integridade dos códigos de autorização.
5. **mTLS:**  
   - Clientes confidenciais utilizam certificados gerenciados (ACM/KMS em produção) para autenticação mútua.  
   - Em desenvolvimento e homologação utilizamos certificados autoassinados gerados via scripts no repositório.
6. **Registro de dispositivo com política de canais:**  
   - O App Flutter envia fingerprint, e-mail e metadados do aparelho para `POST /identidade/dispositivos/registro`. O telefone só é obrigatório quando a política de SMS estiver habilitada.  
   - A API sempre gera verificação por e-mail e, opcionalmente, por SMS, conforme `identidade.dispositivo.onboarding.sms-habilitado`.  
   - O envio de SMS passa por `CanalEnvioCodigoSms`, que delega ao `FornecedorEnvioSms` configurado em `identidade.dispositivo.onboarding.sms-fornecedor`, preparando o backend para múltiplos provedores.  
   - A confirmação via `POST /identidade/dispositivos/registro/{id}/confirmacao` valida apenas os canais efetivamente criados no registro, mantendo hashes, tentativas limitadas e expiração em 9 horas.  
   - A finalização gera `DispositivoToken` opaco e revoga tokens anteriores.
   - O refresh de sessão no Keycloak passa pelo executor de client policy `eickrono-device-token-refresh`, que consulta a API de Identidade antes de aceitar `grant_type=refresh_token`.
   - O refresh só é aceito quando o `device_token` enviado pelo cliente continua válido para o usuário e para o aparelho confiável correspondente.
7. **Política offline do aplicativo móvel:**  
   - O app consulta `GET /identidade/dispositivos/offline/politica` para descobrir se o backend permite modo offline, por quanto tempo e quais condições exigem bloqueio imediato.  
   - A API exige `Authorization` + `X-Device-Token` também nesse endpoint, preservando o mesmo contrato das demais APIs protegidas do ecossistema.  
   - O app envia `POST /identidade/dispositivos/offline/eventos` para reconciliar os eventos ocorridos fora de linha.  
   - Cada evento reconciliado é persistido em `eventos_offline_dispositivo` e auditado em `auditoria_eventos_identidade`.  
   - Nesta etapa não foi criada uma entidade de “janela offline”; o backend publica política e registra eventos, deixando a modelagem de janela explícita para quando a lógica antifraude realmente precisar de estado aberto/fechado.

## Integrações e validações

- **Audience dedicada:** cada API valida o `aud` específico esperado para evitar reuso de tokens.  
- **Validação de escopos:** o gateway e os serviços reforçam escopos, inclusive combinações escopo+papel.  
- **Anti-replay:** armazenamento temporário de `jti` e uso de PKCE e nonce reduzem ataques de repetição.  
- **Clock skew mínimo:** tolerância configurável (padrão 1 minuto) e auditoria das discrepâncias.  
- **Logs mascarados:** dados sensíveis (tokens, CPFs, e-mails) são ofuscados antes da persistência ou envio a ferramentas externas.
- **Backchannel interno de atestação:** `POST /identidade/atestacoes/interna/desafios` e `POST /identidade/atestacoes/interna/validacoes` são protegidos por `X-Eickrono-Internal-Secret` e usados pelos servidores de produto para obter/verificar o veredito de confiança do app/dispositivo.
- **Backchannel interno de sessão:** `POST /identidade/sessoes/interna` recebe login e senha do servidor de produto, autentica no Keycloak e devolve a sessão centralizada sem expor telas do `realm` ao app.

## Estratégia de chaves e segredos

- **Rotação automática:** chaves JWK e certificados TLS com rotação programada.  
- **Segregação:** segredos distintos por ambiente e uso de Secrets Manager.  
- **Backups e DR:** dumps automáticos do RDS/PostgreSQL, exportação de realms Keycloak e testes de restauração periódicos.

## Modelo de dados do registro de dispositivos

- **Tabela `registro_dispositivo`:** armazena metadados da solicitação (UUID, e-mail, telefone opcional quando SMS estiver desligado, fingerprint, status, `criado_em`, `expira_em`, `confirmado_em`).  
- **Tabela `codigo_verificacao`:** relaciona registro + canal (`EMAIL` sempre, `SMS` somente quando habilitado), hash do código, tentativas, limite e timestamps de envio/validação.  
- **Tabela `token_dispositivo`:** mantém tokens opacos vinculados ao usuário (`sub`), fingerprint, data de emissão, revogação e motivo.  
- **Tabela `dispositivos_identidade`:** modela explicitamente o aparelho confiável vinculado à `Pessoa`, com fingerprint, plataforma, versão do app, chave pública, status e último token emitido.  
- **Tabela `eventos_offline_dispositivo`:** registra a reconciliação dos eventos offline reportados pelo aplicativo, incluindo tipo do evento, token associado, instante de ocorrência e instante de registro.  
- Todas têm índices em `status`, `expira_em` e `usuario_sub` para facilitar queries do job de expiração e verificação rápida no filtro HTTP (`DeviceTokenFilter`).

## Políticas de expiração e revogação

- **Expiração automática:** job `RegistroDispositivoScheduler` roda a cada 15 minutos, usa `clockProvider` para comparar `expira_em` e marca registros/códigos como expirados.  
- **Revogação preventiva:** ao confirmar um novo dispositivo, os tokens anteriores do mesmo usuário são revogados antes da emissão do novo token.  
- **Bloqueio HTTP:** filtros Spring verificam header `X-Device-Token`, consultam `token_dispositivo` (com cache Caffeine de 5 minutos) e recusam requisições com tokens revogados (`423 Locked`).  
- **Auditoria:** todos os eventos relevantes são enviados para `AuditoriaEventoIdentidade` com detalhes do fingerprint e motivo.

## Evolução do modelo de identidade

- **Raiz nova do domínio:** a identidade do ecossistema passa a ser modelada por `Pessoa` e `FormaAcesso`, permitindo múltiplas credenciais para a mesma pessoa.
- **Tipos de acesso:** `FormaAcesso` diferencia pelo menos `EMAIL_SENHA` e `SOCIAL`, com provedor e identificador normalizados para vinculação futura com brokers externos.
- **Compatibilidade durante a migração:** `PerfilIdentidade` continua existindo como projeção legada/compatível para os pontos já integrados da API e do app.
- **Provisionamento controlado:** o serviço `ProvisionamentoIdentidadeService` cria ou atualiza a `Pessoa`, garante a forma principal `EMAIL_SENHA` e sincroniza a projeção `PerfilIdentidade` somente quando o `Jwt` chega com `sub`, `email` e `name` válidos.
- **Conflito de identidade:** o provisionamento rejeita a tentativa de associar o mesmo e-mail principal a pessoas diferentes.
- **Vínculos sociais:** a criação/listagem de vínculos sociais passa a operar sobre a pessoa provisionada e também registra `FormaAcesso` do tipo `SOCIAL`, mantendo o legado compatível enquanto o modelo antigo é retirado.

## Política offline implementada

- **Política central no backend:** as regras vivem em `identidade.dispositivo.offline.*` e são publicadas pela API de Identidade.
- **Identidade explícita do dispositivo:** a emissão do `TokenDispositivo` agora referencia `DispositivoIdentidade`, evitando que o app trabalhe apenas com fingerprint solto.
- **Reconciliação auditável:** o backend persiste os eventos do app em `eventos_offline_dispositivo` e sempre registra auditoria com o sujeito autenticado.
- **Bloqueio por confiança:** a reconciliação offline é recusada quando o token do dispositivo não está ativo ou quando o dispositivo está sem confiança e a política central determina bloqueio.
- **Sem entidade de janela offline nesta etapa:** o backend não abriu uma entidade de ciclo de vida offline; essa modelagem foi conscientemente adiada para quando a análise antifraude exigir estado aberto/fechado.

## Refresh token vinculado ao dispositivo

- **Refresh condicionado ao device token:** o servidor de autorização não aceita mais `refresh_token` sem o parâmetro adicional `device_token`.
- **Validação centralizada no backend:** o Keycloak chama `GET /identidade/dispositivos/token/validacao/interna`, autenticado pelo header `X-Eickrono-Internal-Secret`, para verificar se o `device_token` ainda está válido.
- **Códigos de falha preservados:** a API de Identidade continua classificando `DEVICE_TOKEN_MISSING`, `DEVICE_TOKEN_INVALID`, `DEVICE_TOKEN_REVOKED` e `DEVICE_TOKEN_EXPIRED`; o servidor de autorização converte isso em `invalid_grant`.
- **Escopo da política:** o bloqueio foi ligado via client policy apenas para clientes com atributo `eickrono.device-token-refresh=true`, evitando impactar clientes que não usam onboarding de dispositivo.
- **Contrato com o cliente móvel:** o app precisa anexar `device_token` no refresh OIDC. O pacote `eickrono-autenticacao-cliente` já faz isso automaticamente quando a sessão atual possui `X-Device-Token`.

## Validação executada

Para esta etapa de política offline do dispositivo, os testes executados foram:

- `mvn -U -pl modulos/api-contas-eickrono -am test-compile -DskipITs`
- `mvn -U -pl modulos/api-identidade-eickrono -am test-compile -DskipITs`
- `mvn -U -pl modulos/api-identidade-eickrono -am -Dtest=AplicacaoApiIdentidadeTest,RegistroDispositivoControllerIT,RegistroDispositivoServiceTest,OfflineDispositivoServiceTest test`
- `mvn -U -pl modulos/servidor-autorizacao-eickrono -am test`

Esses testes cobrem especificamente:

- `RegistroDispositivoService` com SMS habilitado e desabilitado por política;
- obrigatoriedade de e-mail e obrigatoriedade condicional do telefone;
- confirmação e reenvio obedecendo apenas os canais efetivamente gerados;
- delegação do canal SMS para um fornecedor configurável;
- provisionamento controlado de identidade durante o fluxo autenticado;
- publicação da política offline central do backend;
- reconciliação de eventos offline vinculados ao dispositivo autenticado;
- persistência de `dispositivos_identidade` e `eventos_offline_dispositivo`;
- bloqueio do `refresh_token` quando o `device_token` está ausente, inválido, revogado ou expirado;
- comunicação interna entre Keycloak e API de Identidade via segredo compartilhado;
- filtros e contratos de `X-Device-Token` com banco PostgreSQL real;
- compilação dos módulos impactados sem reintroduzir alertas artificiais no estilo já descrito no `guia-desenvolvimento.md`.

Observações de arquitetura e execução:

- os testes Spring Boot de `api-identidade` e `api-contas` foram migrados para PostgreSQL real via Testcontainers;
- os containers de teste podem reaproveitar `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_DB` do ambiente quando essas variáveis estiverem definidas;
- host e porta continuam efêmeros e controlados pelo Testcontainers, sem depender do `docker compose` de `dev/hml`;
- a causa histórica do falso sintoma `permission denied ... docker.sock` não era ausência de acesso ao Docker em si, mas incompatibilidade entre a stack antiga de Testcontainers e a API atual do Docker Desktop local;
- a correção adotada foi atualizar Testcontainers para `1.21.4`, mantendo PostgreSQL real e preservando o isolamento dos testes;
- os testes de integração executados nesta etapa passaram com `BUILD SUCCESS` em PostgreSQL real via Testcontainers.

## Diagramas recomendados

- Fluxo Authorization Code + PKCE com PAR/JAR/JARM.  
- Diagrama de implantação (AWS + Cloudflare).  
- Sequência de auditoria e registro de eventos.  
- Fluxo mTLS entre componentes internos e clientes confidenciais.

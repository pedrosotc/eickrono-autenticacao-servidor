# Guia de Arquitetura

Este guia descreve a arquitetura do ecossistema de autenticação da Eickrono, destacando componentes, integrações e fluxos compatíveis com o padrão FAPI.

## Componentes principais

- **Servidor de autorização (Keycloak/RH-SSO):** responsável pelos realms `desenvolvimento`, `homologacao` e `producao`. Mantém configurações PAR/JAR/JARM, políticas de MFA/WebAuthn/Passkeys e rotação de chaves JWK.
- **API Identidade Eickrono:** serviço Spring Boot que expõe recursos de perfil e vínculos sociais, validando tokens JWT provenientes do servidor de autorização.
- **Registro de dispositivos móveis:** conjunto de serviços na API de Identidade (`RegistroDispositivoService`, `TokenDispositivoService`, `CodigoVerificacaoService`) que gerenciam onboarding de novos aparelhos, revogação de tokens antigos e verificação por canais configuráveis, com e-mail sempre obrigatório e SMS opcional por política.
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
   - A finalização gera `DispositivoToken` opaco, revoga tokens anteriores e notifica o Keycloak (SPI `DeviceTokenConstraintProvider`) para impedir sessões de aparelhos não validados.

## Integrações e validações

- **Audience dedicada:** cada API valida o `aud` específico esperado para evitar reuso de tokens.  
- **Validação de escopos:** o gateway e os serviços reforçam escopos, inclusive combinações escopo+papel.  
- **Anti-replay:** armazenamento temporário de `jti` e uso de PKCE e nonce reduzem ataques de repetição.  
- **Clock skew mínimo:** tolerância configurável (padrão 1 minuto) e auditoria das discrepâncias.  
- **Logs mascarados:** dados sensíveis (tokens, CPFs, e-mails) são ofuscados antes da persistência ou envio a ferramentas externas.

## Estratégia de chaves e segredos

- **Rotação automática:** chaves JWK e certificados TLS com rotação programada.  
- **Segregação:** segredos distintos por ambiente e uso de Secrets Manager.  
- **Backups e DR:** dumps automáticos do RDS/PostgreSQL, exportação de realms Keycloak e testes de restauração periódicos.

## Modelo de dados do registro de dispositivos

- **Tabela `registro_dispositivo`:** armazena metadados da solicitação (UUID, e-mail, telefone opcional quando SMS estiver desligado, fingerprint, status, `criado_em`, `expira_em`, `confirmado_em`).  
- **Tabela `codigo_verificacao`:** relaciona registro + canal (`EMAIL` sempre, `SMS` somente quando habilitado), hash do código, tentativas, limite e timestamps de envio/validação.  
- **Tabela `token_dispositivo`:** mantém tokens opacos vinculados ao usuário (`sub`), fingerprint, data de emissão, revogação e motivo.  
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

## Validação executada

Para esta etapa do onboarding de dispositivo, os testes executados foram:

- `mvn -U -pl modulos/api-contas-eickrono -am test-compile -DskipITs`
- `mvn -U -pl modulos/api-identidade-eickrono -am test-compile -DskipITs`
- `mvn -U -pl modulos/api-identidade-eickrono -am -Dtest=AplicacaoApiIdentidadeTest,RegistroDispositivoControllerIT,RegistroDispositivoServiceTest,CanalEnvioCodigoSmsTest test`
- `mvn -U -pl modulos/api-contas-eickrono -am -Dtest=AplicacaoApiContasTest,ApiContasDeviceTokenContractTest test`

Esses testes cobrem especificamente:

- `RegistroDispositivoService` com SMS habilitado e desabilitado por política;
- obrigatoriedade de e-mail e obrigatoriedade condicional do telefone;
- confirmação e reenvio obedecendo apenas os canais efetivamente gerados;
- delegação do canal SMS para um fornecedor configurável;
- provisionamento controlado de identidade durante o fluxo autenticado;
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

# Guia de Desenvolvimento

Este guia orienta a preparação do ambiente local e o fluxo de trabalho diário para contribuir com o monorepo **Eickrono Autenticação**.

## Requisitos locais

- **Sistema operacional:** macOS, Linux ou Windows 11 com WSL2.  
- **JDK:** Temurin/Adoptium Java 21.  
- **Maven:** 3.9 ou superior.  
- **Docker + Docker Compose:** para execução dos ambientes dev/hml.  
- **Node.js (opcional):** apenas se for necessário personalizar o tema do Keycloak com toolchain front-end.  
- **Ferramentas auxiliares:** `make`, `openssl` para geração de certificados, `psql` para interações com PostgreSQL.

## Primeiros passos

1. Execute `mvn -version` para validar a instalação do Maven e do JDK 21.  
2. Rode `mvn verify` na raiz para baixar dependências e validar qualidade.  
3. Revise `infraestrutura/dev/.env` e personalize os valores locais com segurança.  
4. Execute `docker compose up` em `infraestrutura/dev` para subir Keycloak, PostgreSQL e as APIs.  
5. Acesse `http://localhost:8081/actuator/health` e `http://localhost:8082/actuator/health` para verificar se as APIs estão saudáveis.

### Regra operacional importante do `docker compose`

No ambiente local, os serviços Java rodam a partir de artefatos já empacotados:

- `api-identidade-eickrono` usa a imagem construída com o `jar` em `modulos/api-identidade-eickrono/target/`;
- `servidor-autorizacao-eickrono` é montado dentro do Keycloak a partir do `jar` em `modulos/servidor-autorizacao-eickrono/target/`.

Por isso, alteração de código sem novo `package` e sem recriar o serviço deixa o container executando a versão antiga.

Comandos canônicos:

- API identidade:
  - `mvn -q -pl modulos/api-identidade-eickrono -am package -DskipTests`
  - `cd infraestrutura/dev && docker compose up -d --build api-identidade-eickrono`
- customização do servidor de autorização:
  - `mvn -q -pl modulos/servidor-autorizacao-eickrono -am package -DskipTests`
  - `cd infraestrutura/dev && docker compose up -d servidor-autorizacao`

Se um comportamento visto no app não bater com o código atual, valide primeiro se o container local foi realmente recriado.

Observações importantes do fluxo canônico:

- a API de identidade é a borda pública do app para cadastro, confirmação de e-mail, login e recuperação de senha;
- o `flashcard-servidor` não deve mais receber senha, código de recuperação ou tentativa de login vindos do app;
- depois da confirmação de e-mail, a autenticação provisiona o perfil no `flashcard-servidor` por backchannel;
- esse provisionamento interno precisa ser idempotente por `cadastroId`;
- o login público já emite o `X-Device-Token` quando o backend aprova o aparelho;
- o app não usa mais tela dedicada de registro de dispositivo no fluxo principal;
- se o backend exigir nova validação de contato, o app deve reutilizar a tela já existente de verificação;
- em `docker compose`, a própria API de identidade precisa apontar o Keycloak interno para `http://servidor-autorizacao:8080`, não para `localhost`;
- a derivação da senha efetiva no servidor de autorização usa apenas `pepper + createdTimestamp` do usuário no Keycloak, e não mais `data_nascimento`.
- em `dev` e `hml`, o cadastro nativo agora aponta para um SMTP local de teste (`MailHog`) dentro do `docker compose`.
- quando `IDENTIDADE_CADASTRO_EMAIL_FORNECEDOR=smtp`, o envio passa a usar `JavaMailSender` com as propriedades `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` e derivados.
- os `docker-compose` locais agora sobem `MailHog` para capturar esses e-mails:
  - `dev`: UI em `http://localhost:8025`
  - `hml`: UI em `http://localhost:18025`

## Como ver códigos de e-mail no MailHog

Quando cadastro ou recuperação de senha disparam envio de e-mail, o `eickrono-autenticacao-servidor` entrega a mensagem para um SMTP fake local (`MailHog`), não para uma caixa real.

Fluxo prático em `dev`:

1. suba o ambiente local do auth:
   - `cd infraestrutura/dev`
   - `docker compose up -d`
2. confirme que o `MailHog` está ativo:
   - `docker ps | rg eickrono-mailhog-dev`
3. execute o fluxo do app até a tela de verificação do código;
4. abra a UI do `MailHog` em `http://localhost:8025`;
5. na lista de mensagens, procure o e-mail cujo destinatário é o endereço usado no fluxo;
6. abra a mensagem;
7. leia o corpo do e-mail e copie o valor da linha `Código de confirmação: XXXXXX`;
8. volte para o app e digite esse código na tela de verificação.

Fluxo prático em `hml` local:

1. suba o ambiente local de homologação:
   - `cd infraestrutura/hml`
   - `docker compose up -d`
2. abra a UI do `MailHog` em `http://localhost:18025`;
3. localize o e-mail correspondente e copie o código da mesma forma.

Observações importantes:

- o código enviado por e-mail é numérico e hoje possui 6 dígitos;
- o link `Reenviar código` do app é funcional para e-mail;
- ao reenviar, o sistema gera um código novo e invalida o anterior;
- portanto, sempre use o código do e-mail mais recente no `MailHog`;
- se nenhum e-mail aparecer, verifique primeiro se a API de identidade e o `MailHog` estão rodando no `docker compose`.

Referências do comportamento no código:

- envio SMTP do código: `modulos/api-identidade-eickrono/src/main/java/com/eickrono/api/identidade/aplicacao/servico/CanalEnvioCodigoCadastroEmailSmtp.java`
- geração e reenvio do código: `modulos/api-identidade-eickrono/src/main/java/com/eickrono/api/identidade/aplicacao/servico/CadastroContaInternaServico.java`

## PostgreSQL compartilhado em dev

O ambiente `dev` usa um PostgreSQL externo já existente no Docker local, compartilhado pelos projetos.

Credenciais de acesso manual:

- **Host:** `localhost`
- **Porta:** `5432`
- **Usuário:** `adm`
- **Senha:** `AdmDev2026!`
- **JDBC URL do autorização:** `jdbc:postgresql://localhost:5432/eickrono_dev`
- **JDBC URL do flashcard:** `jdbc:postgresql://localhost:5432/eickrono_flashcard`

Observações:

- essas credenciais servem apenas para desenvolvimento local;
- o usuário `adm` foi criado como `SUPERUSER` para facilitar inspeção e administração do banco;
- as aplicações continuam usando seus próprios usuários técnicos configurados em `infraestrutura/dev/.env`.

## Homologação local no mesmo PostgreSQL

O ambiente `hml` local também usa o mesmo servidor PostgreSQL em `localhost:5432`, mas com bancos separados para evitar mistura com o `dev`.

Bancos usados no `hml` local:

- `keycloak_hml`
- `eickrono_identidade_hml`
- `eickrono_contas_hml`

Portas publicadas no `hml` local:

- Keycloak: `18080`
- API identidade: `18081`
- API contas: `18082`

Credenciais de acesso manual ao mesmo PostgreSQL compartilhado:

- **Host:** `localhost`
- **Porta:** `5432`
- **Usuário:** `adm`
- **Senha:** `AdmDev2026!`
- **JDBC URL do Keycloak em hml:** `jdbc:postgresql://localhost:5432/keycloak_hml`
- **JDBC URL da identidade em hml:** `jdbc:postgresql://localhost:5432/eickrono_identidade_hml`
- **JDBC URL de contas em hml:** `jdbc:postgresql://localhost:5432/eickrono_contas_hml`

Observações do `hml` local:

- a malha interna `api-identidade <-> flashcard` e `servidor-autorizacao -> api-identidade` já usa `mTLS`;
- o `api-contas-eickrono` continua fora dessa malha no `docker-compose` atual;
- localmente, as APIs de identidade e contas usam `ddl-auto=update` para complementar tabelas ainda não cobertas pelas migrations atuais;
- o objetivo desse perfil local é testar o fluxo real de login sem dividir estado com o `dev`.
- detalhes de portas, stores e geração de certificados estão em `guia-mtls.md`.

## Swagger

Endereços por API:

- API identidade `dev`: `http://localhost:8081/swagger-ui/index.html`
- API identidade `dev` OpenAPI: `http://localhost:8081/v3/api-docs`
- API identidade `hml`: `http://localhost:18081/swagger-ui/index.html`
- API identidade `hml` OpenAPI: `http://localhost:18081/v3/api-docs`
- API contas `dev`: `http://localhost:8082/swagger-ui/index.html`
- API contas `dev` OpenAPI: `http://localhost:8082/v3/api-docs`
- API contas `hml`: `http://localhost:18082/swagger-ui/index.html`
- API contas `hml` OpenAPI: `http://localhost:18082/v3/api-docs`

Proteção por ambiente:

- `dev`: acesso liberado para uso local;
- `hml`: `Basic Auth` + whitelist de IP;
- credenciais padrão de `hml`: usuário `swagger`, senha `swagger-hml`.

## Fluxo Git recomendado

- Branch principal: `main`.  
- Branches de feature: `feature/<descricao-curta>`.  
- Commits pequenos e em português.  
- Pull request acompanhado do `checklist-seguranca-fapi.md` preenchido.
- Configure previamente as credenciais Git (PAT ou SSH). Um `git push --set-upstream origin main` falhará com `could not read Username` se o ambiente não puder autenticar no GitHub.

## Testes e qualidade

- `mvn verify`: executa testes, Checkstyle, SpotBugs e validações do Spring Boot.  
- `mvn -pl modulos/api-identidade-eickrono spring-boot:run`: inicia apenas a API de identidade.  
- `mvn -pl modulos/api-contas-eickrono spring-boot:run`: inicia a API de contas.  
- Testcontainers é utilizado para testes de integração com PostgreSQL real; não é necessário subir um PostgreSQL manualmente para a suíte de testes, mas o Docker local precisa estar saudável.

### PostgreSQL real nos testes

- os módulos `api-identidade-eickrono` e `api-contas-eickrono` não usam mais H2 nos perfis de teste;
- os testes Spring Boot/integração sobem PostgreSQL real com Testcontainers;
- a falha histórica de `permission denied ... docker.sock` e `Could not find a valid Docker environment` não era problema de schema, mas de compatibilidade entre a stack antiga de Testcontainers e a API atual do Docker Desktop local;
- os testes **não** reutilizam host/porta do `docker compose`; o container de teste continua sendo criado pelo Testcontainers.
- o que eles reaproveitam, quando disponível, são informações de ambiente já conhecidas do monorepo:
  - `POSTGRES_USER`
  - `POSTGRES_PASSWORD`
  - `POSTGRES_DB`
- também existem overrides específicos para os testes:
  - `EICKRONO_TEST_POSTGRES_IMAGE`
  - `EICKRONO_TEST_POSTGRES_DB_IDENTIDADE`
  - `EICKRONO_TEST_POSTGRES_DB_CONTAS`

### Diferença entre `.env` do monorepo e Testcontainers

As variáveis de [`infraestrutura/dev/.env`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env) e [`infraestrutura/hml/.env`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/hml/.env) descrevem o ambiente da aplicação.

Para a abertura de sessão interna por `backchannel`, mantenha o `client_id` do app alinhado ao realm de cada ambiente:
- `dev`: `app-flutter-local`
- `hml`: `app-flutter-hml`
- `prod`: `app-flutter-prod`

Para o provisionamento interno do cadastro nativo, mantenha tambem configurados na API de identidade:
- `identidade.cadastro.interna.keycloak.client-id`
- `identidade.cadastro.interna.keycloak.client-secret`
- `identidade.cadastro.interna.keycloak.realm`
- `identidade.cadastro.interna.keycloak.url-base`
- `identidade.cadastro.interna.keycloak.username-admin`
- `identidade.cadastro.interna.keycloak.password-admin`
- `identidade.cadastro.interna.keycloak.password-pepper`

Para o canal SMTP do cadastro nativo, as propriedades relevantes sao:
- `IDENTIDADE_CADASTRO_EMAIL_FORNECEDOR`
- `IDENTIDADE_CADASTRO_EMAIL_REMETENTE`
- `IDENTIDADE_CADASTRO_EMAIL_RESPONDER_PARA`
- `IDENTIDADE_CADASTRO_EMAIL_ASSUNTO`
- `IDENTIDADE_CADASTRO_EMAIL_NOME_APLICACAO`
- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `SPRING_MAIL_SMTP_AUTH`
- `SPRING_MAIL_SMTP_STARTTLS_ENABLE`

Já os testes de integração fazem outra coisa:

- criam um PostgreSQL efêmero por execução;
- escolhem porta dinâmica;
- isolam o banco do estado do `dev`/`hml`;
- podem reaproveitar nome de banco, usuário e senha via variáveis de ambiente para manter alinhamento sem acoplar os testes ao banco do compose.

Em outras palavras:

- `POSTGRES_PORT` do `docker compose` **não** é usado pelos testes;
- `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_DB` **podem** ser usados como defaults dos containers de teste;
- o OIDC dos testes da identidade continua simulado em memória e não depende do `OIDC_ISSUER_URI` do ambiente `dev/hml`.

### Diagnóstico reproduzível do Docker/Testcontainers

Durante esta etapa foi corrigido um sintoma enganoso:

- `permission denied while trying to connect to the docker API at unix:///Users/thiago/.docker/run/docker.sock`
- ou `Could not find a valid Docker environment`

O problema real não era ausência de rede e nem necessidade de apontar os testes para o PostgreSQL do `docker compose`. O problema era a combinação entre:

- Docker Desktop local saudável;
- socket Unix local acessível;
- stack de Testcontainers/docker-java antiga demais para conversar corretamente com a API atual do Docker.

#### Comandos executados no diagnóstico

1. Descobrir qual contexto do Docker estava ativo:

```bash
docker context show
```

Saída observada:

```text
desktop-linux
```

Interpretação:

- o CLI estava apontando para o daemon local do Docker Desktop;
- isso eliminou a hipótese de contexto remoto incorreto.

2. Confirmar que o socket local do Docker realmente respondia:

```bash
curl --silent --show-error --unix-socket "$HOME/.docker/run/docker.sock" http://localhost/version
```

Saída observada:

```json
{"Platform":{"Name":"Docker Desktop 4.64.0 (221278)"},"Version":"29.2.1","ApiVersion":"1.53","MinAPIVersion":"1.44","Os":"linux","Arch":"arm64", ...}
```

Interpretação:

- o daemon local estava vivo;
- o socket Unix local funcionava;
- a API efetiva do Docker Desktop era `1.53`, com mínimo `1.44`.

3. Comparar uma rota antiga com uma rota suportada pela API mínima do daemon:

```bash
curl --silent --show-error --unix-socket "$HOME/.docker/run/docker.sock" http://localhost/v1.41/info
curl --silent --show-error --unix-socket "$HOME/.docker/run/docker.sock" http://localhost/v1.44/info
```

Comportamento observado:

- `v1.41/info` respondeu com JSON degradado, com campos essenciais vazios;
- `v1.44/info` respondeu com dados consistentes do daemon.

Interpretação:

- o Docker Desktop atual ainda atende rotas mais antigas, mas de forma insuficiente para a descoberta automática esperada por clientes antigos;
- isso explicava por que uma stack velha de Testcontainers podia acusar "permission denied" ou "no valid Docker environment", mesmo com Docker local funcionando.

4. Confirmar a versão final da stack de testes:

```bash
rg -n "testcontainers.version|<artifactId>testcontainers|postgresql</artifactId>|h2</artifactId>" \
  pom.xml \
  modulos/api-identidade-eickrono/pom.xml \
  modulos/api-contas-eickrono/pom.xml
```

Estado final esperado:

- `pom.xml` raiz com `testcontainers.version` em `1.21.4`;
- dependência `org.testcontainers:postgresql` nos módulos de identidade e contas;
- ausência de `h2` nesses módulos.

#### Correção aplicada

A correção escolhida foi:

- manter Testcontainers;
- manter PostgreSQL real nos testes;
- atualizar Testcontainers para `1.21.4`;
- remover H2 dos testes Spring Boot de `api-identidade-eickrono` e `api-contas-eickrono`;
- manter o reaproveitamento apenas de `imagem`, `database`, `user` e `password` via variáveis de ambiente, sem reutilizar `host` e `porta` do ambiente `dev/hml`.

Arquivos centrais dessa decisão:

- [`pom.xml`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/pom.xml)
- [`pom.xml`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/api-identidade-eickrono/pom.xml)
- [`pom.xml`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/api-contas-eickrono/pom.xml)
- [`InfraestruturaTesteIdentidade.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/api-identidade-eickrono/src/test/java/com/eickrono/api/identidade/support/InfraestruturaTesteIdentidade.java)
- [`InfraestruturaTesteContas.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/api-contas-eickrono/src/test/java/com/eickrono/api/contas/support/InfraestruturaTesteContas.java)

#### Como repetir a validação final

1. Compilar os testes da identidade:

```bash
mvn -U -pl modulos/api-identidade-eickrono -am test-compile -DskipITs
```

2. Compilar os testes de contas:

```bash
mvn -U -pl modulos/api-contas-eickrono -am test-compile -DskipITs
```

3. Rodar os testes relevantes da identidade com PostgreSQL real:

```bash
mvn -U -pl modulos/api-identidade-eickrono -am \
  -Dtest=AplicacaoApiIdentidadeTest,RegistroDispositivoControllerIT,RegistroDispositivoServiceTest,CanalEnvioCodigoSmsTest \
  test
```

4. Rodar os testes relevantes de contas com PostgreSQL real:

```bash
mvn -U -pl modulos/api-contas-eickrono -am \
  -Dtest=AplicacaoApiContasTest,ApiContasDeviceTokenContractTest \
  test
```

5. Rodar os testes relevantes da política offline da identidade:

```bash
mvn -U -pl modulos/api-identidade-eickrono -am \
  -Dtest=AplicacaoApiIdentidadeTest,RegistroDispositivoControllerIT,RegistroDispositivoServiceTest,OfflineDispositivoServiceTest \
  test
```

6. Rodar os testes do servidor de autorização que bloqueiam refresh por device token:

```bash
mvn -U -pl modulos/servidor-autorizacao-eickrono -am test
```

#### Sinais esperados de sucesso

Durante a execução bem-sucedida, os logs devem conter linhas semelhantes a:

```text
Testcontainers version: 1.21.4
Found Docker environment with local Unix socket (unix:///var/run/docker.sock)
Connected to docker:
  Server Version: 29.2.1
  API Version: 1.53
Container postgres:15.5 started
BUILD SUCCESS
```

Quando a etapa de política offline estiver correta, também é esperado encontrar nos logs linhas semelhantes a:

```text
Mapped to com.eickrono.api.identidade.apresentacao.api.RegistroDispositivoController#obterPoliticaOffline()
Mapped to com.eickrono.api.identidade.apresentacao.api.RegistroDispositivoController#registrarEventosOffline(Jwt, String, RegistrarEventosOfflineRequest)
insert into eventos_offline_dispositivo
BUILD SUCCESS
```

Quando a etapa de refresh vinculado ao dispositivo estiver correta, também é esperado encontrar nos logs linhas semelhantes a:

```text
ClientPolicyEvent.TOKEN_REFRESH
GET /identidade/dispositivos/token/validacao/interna
DEVICE_TOKEN_REVOKED
BUILD SUCCESS
```

### Refresh token vinculado ao device token

O fluxo final ficou assim:

1. O app recebe uma sessão remota emitida pela autenticação já com `device_token`.
2. O `device_token` opaco passa a ser persistido junto da sessão local.
3. Ao pedir refresh, o cliente envia o parâmetro adicional `device_token`.
4. O Keycloak aplica o executor `eickrono-device-token-refresh`.
5. O executor consulta a API de Identidade em `/identidade/dispositivos/token/validacao/interna`.
6. Se a API responder que o token está revogado, expirado, inválido ou ausente, o refresh falha com `invalid_grant`.

Variáveis de ambiente relevantes para reproduzir esse fluxo:

- `EICKRONO_IDENTIDADE_API_BASE_URL`
- `EICKRONO_INTERNAL_SECRET`
- `EICKRONO_IDENTIDADE_TIMEOUT_MS`

Essas variáveis precisam existir no container do Keycloak e na API de Identidade. O `docker compose` de `dev` e `hml` já foi alinhado com isso.

#### O que esta correção não faz

- não usa o PostgreSQL já criado pelo `docker compose` de `dev`/`hml`;
- não reaproveita `POSTGRES_PORT` fixo;
- não elimina a necessidade de Docker local acessível;
- não substitui Testcontainers por conexão em banco compartilhado.

Ela apenas torna os testes de integração compatíveis com o Docker Desktop atual e com PostgreSQL real de forma isolada.

## Alertas do Java LS/JDT em testes

Em alguns testes, especialmente com `Mockito`, `Spring Data` e `ArgumentCaptor`, o Java Language Server do VS Code pode exibir alertas como:

- `Null type safety: The expression of type ... needs unchecked conversion ...`
- `Missing non-null annotation ...`
- imports aparentemente não usados após refatorações

Esses alertas nem sempre indicam erro real de compilação. O caso mais comum no monorepo é o analisador de nulidade do JDT não conseguir inferir corretamente contratos de:

- `captor.capture()`
- `captor.getValue()`
- `invocation.getArgument(...)`
- `save(any(...))`
- repositórios Spring Data/JPA usados como mocks
- serialização Jackson, por exemplo `objectMapper.writeValueAsString(...)`

### Como corrigir do jeito preferido no projeto

1. **Prefira fake/proxy em vez de Mockito** quando o teste só precisa observar `save(...)` ou manter estado em memória.
   Isso reduz ruído de nulidade e deixa o teste mais explícito.

2. **Use `Objects.requireNonNull(...)` em valores lidos**, não em matchers do Mockito.
   Exemplos bons:
   - `Objects.requireNonNull(captor.getValue())`
   - `Objects.requireNonNull(invocation.getArgument(0, MeuTipo.class))`
   - `Objects.requireNonNull(parametro, "mensagem")`

3. **Evite envolver diretamente** estes trechos com `Objects.requireNonNull(...)`:
   - `captor.capture()`
   - `any(...)`
   - `save(any(...))`

   Isso costuma agradar o compilador Maven, mas ainda pode deixar o JDT reclamando.

4. **Quando o alerta estiver em código de produção**, aplique `Objects.requireNonNull(...)` no argumento obrigatório ou no retorno que realmente não pode ser nulo.
   Exemplo válido:
   ```java
   return Objects.requireNonNull(
       repositorio.save(Objects.requireNonNull(entidade, "entidade é obrigatória")),
       "entidade salva é obrigatória");
   ```

5. **Quando a origem do valor for biblioteca externa sem anotação de nulidade confiável**, normalize o retorno no ponto de leitura.
   Exemplo típico no projeto:
   ```java
   String payload = Objects.requireNonNull(
       objectMapper.writeValueAsString(Map.of("chave", "valor")));
   ```
   Isso vale para casos como `MockMvc.content(...)`, em que a API de destino exige `@NonNull String` e o JDT não consegue provar isso a partir do Jackson.

6. **Se o teste depender só de auditoria/captura simples**, substitua `ArgumentCaptor` por uma lista/variável em memória dentro de um fake.

### Ordem prática de tratamento

Quando surgir esse tipo de alerta, siga esta ordem:

1. verificar se o código realmente compila com:
   - `mvn -pl <modulo> -am test-compile -DskipITs`
2. se o alerta vier de `Mockito`/`ArgumentCaptor`, tentar simplificar o teste com fake/proxy
3. usar `Objects.requireNonNull(...)` só nos pontos de leitura/contrato real
4. limpar o cache do editor:
   - `Java: Clean Java Language Server Workspace`
   - `Developer: Reload Window`

### Regra deste monorepo

- não usar `@SuppressWarnings("null")` como solução padrão
- não espalhar `@NonNull` artificialmente só para satisfazer a IDE
- preferir testes mais explícitos e menos dependentes de inferência do JDT

## Alertas de "never used" em testes Spring/JUnit

Além dos alertas de nulidade, o Java LS/JDT também pode marcar como "never used" elementos que são usados apenas por reflexão, por exemplo:

- classes com `@TestConfiguration`
- métodos `@Bean`
- classes auxiliares importadas por `@Import(...)`
- callbacks e estruturas que o Spring/JUnit instanciam indiretamente

Esses casos não são, por si só, erro de compilação. O problema é o analisador estático não enxergar o uso indireto feito por annotations.

### Como corrigir do jeito preferido no projeto

1. **Prefira configuração de teste top-level**, em arquivo próprio, em vez de `@TestConfiguration` interna dentro do teste.
2. **Referencie explicitamente a configuração** no teste com `@SpringBootTest(classes = ...)` e/ou `@Import(...)`.
3. **Evite depender de uso implícito por varredura** quando o objetivo é apenas fornecer beans fake de teste.
4. **Não usar `@SuppressWarnings("unused")` como padrão** para esse caso. Primeiro tente tornar a relação explícita no código.

### Exemplo aplicado no projeto

No teste de onboarding do dispositivo, a configuração interna foi extraída para uma classe própria:

- [`RegistroDispositivoControllerIT.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/api-identidade-eickrono/src/test/java/com/eickrono/api/identidade/api/RegistroDispositivoControllerIT.java)
- [`RegistroDispositivoControllerITConfiguration.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/api-identidade-eickrono/src/test/java/com/eickrono/api/identidade/api/RegistroDispositivoControllerITConfiguration.java)

Esse padrão é o preferido quando a IDE acusa que `@TestConfiguration` ou métodos `@Bean` "nunca são usados".

## Dicas adicionais

- Utilize perfis `application-dev.yml`, `application-hml.yml` e `application-prod.yml` para configurações específicas por ambiente.  
- O Swagger (springdoc) fica acessível apenas em dev/hml, protegido por Basic Auth e whitelist em homologação.  
- Certificados mTLS autoassinados podem ser regenerados com o script `infraestrutura/dev/certificados/gerar_certificados.sh`.  
- Para Keycloak, utilize os realms exportados em `modulos/servidor-autorizacao-eickrono/realms`.

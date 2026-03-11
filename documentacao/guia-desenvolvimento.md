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
- Testcontainers é utilizado para testes de integração; não é necessário subir um PostgreSQL manualmente para a suíte de testes.

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

5. **Se o teste depender só de auditoria/captura simples**, substitua `ArgumentCaptor` por uma lista/variável em memória dentro de um fake.

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

## Dicas adicionais

- Utilize perfis `application-dev.yml`, `application-hml.yml` e `application-prod.yml` para configurações específicas por ambiente.  
- O Swagger (springdoc) fica acessível apenas em dev/hml, protegido por Basic Auth e whitelist em homologação.  
- Certificados mTLS autoassinados podem ser regenerados com o script `infraestrutura/dev/certificados/gerar_certificados.sh`.  
- Para Keycloak, utilize os realms exportados em `modulos/servidor-autorizacao-eickrono/realms`.

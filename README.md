# Eickrono Autenticação

Repositório monorepo da plataforma de autenticação e APIs da Eickrono. Consulte a pasta `documentacao` para detalhes completos de arquitetura, desenvolvimento e operação.

Arquitetura adotada:

- MVC na borda HTTP
- separação interna em `apresentacao`, `aplicacao`, `dominio` e `infraestrutura`
- detalhes em `documentacao/arquitetura_mvc_clean.md`

- **Build:** `mvn verify`
- **APIs:** `modulos/api-identidade-eickrono`, `modulos/api-contas-eickrono`
- **Servidor de autorização:** `modulos/servidor-autorizacao-eickrono`
- **Infraestrutura local:** `infraestrutura/dev` e `infraestrutura/hml`

No ambiente `dev`, o `docker compose` usa um PostgreSQL externo já existente no Docker local, em vez de subir um banco próprio no stack.

Credenciais de acesso manual ao PostgreSQL compartilhado de `dev`:

- host: `localhost`
- porta: `5432`
- usuário: `adm`
- senha: `AdmDev2026!`
- JDBC URL do autorização: `jdbc:postgresql://localhost:5432/eickrono_dev`

> Toda a documentação, comentários e identificadores permanecem em português do Brasil, conforme diretriz organizacional.

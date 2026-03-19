# Eickrono Autenticação

Repositório monorepo da plataforma de autenticação e APIs da Eickrono. Consulte a pasta `documentacao` para detalhes completos de arquitetura, desenvolvimento e operação.

Arquitetura adotada:

- MVC na borda HTTP
- separação interna em `apresentacao`, `aplicacao`, `dominio` e `infraestrutura`
- detalhes em `documentacao/arquitetura_mvc_clean.md`
- a API de identidade centraliza a emissao e a validacao de atestacao nativa de app/dispositivo para os apps do ecossistema, consumida por `backchannel` pelos servidores de produto

- **Build:** `mvn verify`
- **APIs:** `modulos/api-identidade-eickrono`, `modulos/api-contas-eickrono`
- **Servidor de autorização:** `modulos/servidor-autorizacao-eickrono`
- **Infraestrutura local:** `infraestrutura/dev` e `infraestrutura/hml`

Endpoints internos relevantes da API de identidade:

- `POST /identidade/atestacoes/interna/desafios`
- `POST /identidade/atestacoes/interna/validacoes`
- `POST /identidade/sessoes/interna`

Derivacao de senha do ecossistema de autenticacao:

- a credencial efetiva nao usa mais `data_nascimento` como insumo auxiliar;
- a SPI do Keycloak agora deriva a senha com `pepper + createdTimestamp` do usuario;
- isso reduz a dependencia de um dado pessoal relativamente previsivel e usa um marcador interno que nao e exposto a terceiros.

No ambiente `dev`, o `docker compose` usa um PostgreSQL externo já existente no Docker local, em vez de subir um banco próprio no stack.

No ambiente `hml` local, o stack também usa esse mesmo servidor PostgreSQL em `localhost:5432`, mas com bancos separados de homologação:

- `keycloak_hml`
- `eickrono_identidade_hml`
- `eickrono_contas_hml`

Portas do `hml` local:

- Keycloak: `http://localhost:18080`
- API identidade: `http://localhost:18081`
- API contas: `http://localhost:18082`

Credenciais de acesso manual ao PostgreSQL compartilhado de `dev`:

- host: `localhost`
- porta: `5432`
- usuário: `adm`
- senha: `AdmDev2026!`
- JDBC URL do autorização: `jdbc:postgresql://localhost:5432/eickrono_dev`

JDBC URLs do `hml` local:

- Keycloak: `jdbc:postgresql://localhost:5432/keycloak_hml`
- API identidade: `jdbc:postgresql://localhost:5432/eickrono_identidade_hml`
- API contas: `jdbc:postgresql://localhost:5432/eickrono_contas_hml`

> Toda a documentação, comentários e identificadores permanecem em português do Brasil, conforme diretriz organizacional.

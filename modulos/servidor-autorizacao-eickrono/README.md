# Servidor de Autorização Eickrono

Esta pasta contém customizações do Keycloak/RH-SSO utilizadas pela Eickrono:

- `temas-login-ptbr`: temas de login totalmente em português.  
- `mapeamentos-atributos`: mapeamentos adicionais de claims e atributos.  
- `configuracoes-fapi`: políticas de PAR/JAR/JARM, mTLS e client policies.  
- `realms`: exports versionados dos realms `desenvolvimento`, `homologacao` e `producao`.  
- `scripts-spi`: código Java/JS para integrações via SPI.

O módulo gera um JAR com utilidades compartilhadas e serve como ponto de empacotamento para as customizações do servidor de autorização.

## SPI de senha derivada

Este módulo também fornece providers customizados para derivar a senha efetiva usada pelo Keycloak a partir de:

- `senha informada pelo usuario`
- `pepper` lido da variável de ambiente `EICKRONO_PASSWORD_PEPPER`
- `data_nascimento` armazenada no atributo de usuário `data_nascimento`

Fórmula aplicada:

- `senha_efetiva = senha + pepper + data_nascimento`

Providers registrados:

- `eickrono-username-password-form`
- `eickrono-registration-password-action`
- `eickrono-update-password`

Observação:

- os realms deste repositório já ligam os providers aos fluxos de browser, registro e `requiredActions`;
- o alias `UPDATE_PASSWORD` do realm aponta para `eickrono-update-password`, então reset de senha e required action passam pela derivação customizada;
- o realm ainda precisa garantir que `data_nascimento` exista como atributo do usuário antes da criação ou validação da credencial;
- o `docker-compose` monta o JAR gerado em `/opt/keycloak/providers/servidor-autorizacao-eickrono.jar`, então é preciso empacotar o módulo antes de subir o ambiente.

Empacotamento:

```bash
mvn -pl modulos/servidor-autorizacao-eickrono -am package -DskipITs
```

# Servidor de Autorização Eickrono

Esta pasta contém customizações do Keycloak/RH-SSO utilizadas pela Eickrono:

- `temas-login-ptbr`: temas de login totalmente em português.  
- `mapeamentos-atributos`: mapeamentos adicionais de claims e atributos.  
- `configuracoes-fapi`: políticas de PAR/JAR/JARM, mTLS e client policies.  
- `realms`: exports versionados dos realms `desenvolvimento`, `homologacao` e `producao`.  
- `scripts-spi`: código Java/JS para integrações via SPI.

O módulo gera um JAR com utilidades compartilhadas e serve como ponto de empacotamento para as customizações do servidor de autorização.

## Papel na arquitetura móvel canônica

No fluxo móvel atual:

- a borda pública do app é a API de identidade/autenticação, não este módulo diretamente;
- este módulo sustenta a parte de Keycloak/RH-SSO do ecossistema, com credenciais, tokens, `required actions`, políticas de cliente e refresh;
- o `flashcard-servidor` não deve receber senha do app;
- login, recuperação de senha e demais fluxos sensíveis continuam centralizados na autenticação.

## SPI de senha derivada

Este módulo também fornece providers customizados para derivar a senha efetiva usada pelo Keycloak a partir de:

- `senha informada pelo usuario`
- `pepper` lido da variável de ambiente `EICKRONO_PASSWORD_PEPPER`
- `createdTimestamp` interno do usuário no Keycloak

Fórmula aplicada:

- `senha_efetiva = senha + pepper + createdTimestamp`

Providers registrados:

- `eickrono-username-password-form`
- `eickrono-registration-password`
- `eickrono-update-password`

Observação:

- os realms deste repositório já ligam os providers aos fluxos de browser, registro e `requiredActions`;
- o alias `UPDATE_PASSWORD` do realm aponta para `eickrono-update-password`, então reset de senha e required action passam pela derivação customizada;
- o `createdTimestamp` é garantido na criação do usuário e, se necessário, é materializado pela própria SPI antes da primeira gravação da credencial;
- a derivação usa apenas o `createdTimestamp` nativo do usuário; atributos livres como `data_criacao_conta` não participam mais desse cálculo;
- o `docker-compose` monta o JAR gerado em `/opt/keycloak/providers/servidor-autorizacao-eickrono.jar`, então é preciso empacotar o módulo antes de subir o ambiente.

Empacotamento:

```bash
mvn -pl modulos/servidor-autorizacao-eickrono -am package -DskipITs
```

## Client policy de refresh por device token

Este módulo também publica o executor de client policy `eickrono-device-token-refresh`.

Responsabilidade:

- interceptar `grant_type=refresh_token`;
- exigir o parâmetro adicional `device_token` no refresh;
- consultar a API de Identidade em `/identidade/dispositivos/token/validacao/interna`;
- bloquear o refresh quando o dispositivo perdeu confiança.

Configuração esperada no ambiente do Keycloak:

- `EICKRONO_IDENTIDADE_API_BASE_URL`
- `EICKRONO_INTERNAL_SECRET`
- `EICKRONO_IDENTIDADE_TIMEOUT_MS`
- `EICKRONO_INTERNO_MTLS_HABILITADO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_SENHA`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_SENHA`

Observação de `mTLS`:

- este módulo usa `mTLS` como cliente ao consultar a API de identidade no refresh por `device token`;
- ele não sobe um endpoint próprio com `mTLS`;
- o detalhamento completo do fluxo e da geração de certificados está em `../../documentacao/guia-mtls.md`.

Os realms versionados já saem com:

- `clientProfiles` contendo `eickrono-device-token-refresh-profile`;
- `clientPolicies` contendo `eickrono-device-token-refresh-policy`;
- o cliente `app-flutter-local` marcado com `eickrono.device-token-refresh=true`.

Teste do módulo:

```bash
mvn -pl modulos/servidor-autorizacao-eickrono -am test
```

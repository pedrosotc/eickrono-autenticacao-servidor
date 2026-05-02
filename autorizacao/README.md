# Artefatos de Autorização

Esta pasta concentra os artefatos de runtime do servidor de autorização para
manter o projeto Java organizado.

## Estrutura

- `realms/`: exports e scripts de materialização dos realms do Keycloak
- `temas/login-ptbr/`: tema de login e páginas de erro
- `providers/configuracoes-fapi/`: configuração versionada de políticas FAPI
- `providers/mapeamentos-atributos/`: mapeamentos de atributos carregados pelo runtime
- `providers/scripts-spi/`: scripts e recursos auxiliares das SPIs

## Regra prática

O código Java do provider fica em `src/`.

Tudo o que é artefato externo montado no container do Keycloak fica em
`autorizacao/`.

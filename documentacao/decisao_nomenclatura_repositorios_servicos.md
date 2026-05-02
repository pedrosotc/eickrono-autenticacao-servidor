# Decisão de Nomenclatura dos Repositórios de Serviços

## Objetivo

Fechar a nomenclatura canônica dos serviços do ecossistema de autenticação.

Esta decisão separa claramente:

- nome do repositório
- `artifactId` raiz do projeto
- `groupId` Maven
- pacote Java raiz

## Regra base

O grupo organizacional e técnico compartilhado continua sendo:

- `groupId`: `com.eickrono`
- pacote Java raiz por serviço: `com.eickrono.*`

Ou seja: os projetos podem ser separados em repositórios independentes sem
perder a identidade comum do grupo `com.eickrono`.

## Nomes canônicos aprovados

| Serviço | Repositório canônico | `artifactId` raiz canônico | Pacote Java raiz canônico |
| --- | --- | --- | --- |
| Servidor de identidade/autenticação | `eickrono-identidade-servidor` | `eickrono-identidade-servidor` | `com.eickrono.identidade` |
| Servidor de contas | `eickrono-contas-servidor` | `eickrono-contas-servidor` | `com.eickrono.contas` |
| Servidor de autorização / Keycloak customizado | `eickrono-autenticacao-servidor` | `eickrono-autenticacao-servidor` | `com.eickrono.autorizacao` |

## O que permanece igual

- `groupId` compartilhado: `com.eickrono`
- a ideia de que os serviços pertencem ao mesmo ecossistema
- a comunicação entre serviços por HTTP, backchannel, JWT de serviço e `mTLS`

## O que deve deixar de existir

O nome `eickrono-autenticacao-servidor` volta a representar o projeto que
embarca o Keycloak customizado e, além disso, mantém a infraestrutura
operacional da stack.

## Regra de dependência entre projetos

Depois da extração:

- os serviços não devem conversar entre si como módulos Maven irmãos;
- os serviços não devem depender do `jar` uns dos outros como regra normal;
- a integração canônica entre eles continua sendo por contrato de rede e
  autenticação interna;
- só vale criar dependência Maven compartilhada se surgir uma biblioteca
  realmente reutilizável e independente de serviço.

## Mapeamento do estado atual para o alvo

| Estado atual | Alvo |
| --- | --- |
| repo agregador `eickrono-autenticacao-servidor` | `eickrono-autenticacao-servidor` com runtime de autorização incorporado |
| `artifactId` pai `eickrono-autenticacao-servidor` | `artifactId` executável do próprio servidor de autorização |
| antigo diretório `modulos/` do monorepo | `eickrono-identidade-servidor` e `eickrono-contas-servidor` como repositórios próprios |
| serviço de autorização separado em `eickrono-autenticacao-servidor` | servidor de autorização fundido de volta no `eickrono-autenticacao-servidor` |

## Próximas etapas esperadas

1. Extrair `api-identidade-eickrono` para `eickrono-identidade-servidor` — concluído
2. Extrair `api-contas-eickrono` para `eickrono-contas-servidor` — concluído
3. Fundir `servidor-autorizacao-eickrono` de volta em `eickrono-autenticacao-servidor` — concluído
4. Remover o reactor Maven que os tratava como módulos irmãos — concluído
5. Reajustar `docker-compose`, scripts, caminhos absolutos, CI e documentação — em andamento

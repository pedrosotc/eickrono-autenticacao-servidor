# Guia de mTLS

Este guia consolida como o `mTLS` funciona no monorepo `eickrono-autenticacao-servidor`, quais módulos realmente o usam hoje, quais certificados cada um consome e como gerar os artefatos locais de `dev` e `hml`.

## Visão geral

No estado atual do código, o `mTLS` é usado para proteger o `backchannel` entre serviços internos. Ele não substitui o JWT interno nem o header `X-Eickrono-Internal-Secret`.

Para a arquitetura canônica do app móvel, a direção mais importante desse `backchannel` passa a ser:

- `autenticação -> thimisu` para provisionamento de perfil depois da confirmação de e-mail.

Camadas reais do desenho:

- `mTLS`: autentica o par cliente-servidor no transporte.
- `JWT interno`: autentica a chamada na camada de aplicação.
- `X-Eickrono-Internal-Secret`: barreira adicional para rotas internas.

Serviços com participação em `mTLS` no ecossistema atual:

- `eickrono-identidade-servidor`: servidor e cliente `mTLS`.
- `eickrono-autenticacao-servidor`: cliente `mTLS` no fluxo de refresh por `device token`.
- `eickrono-contas-servidor`: suporte de servidor `mTLS`, mas sem uso ativo no `docker-compose` atual e sem cliente `mTLS`.

## api-identidade-eickrono

### Responsabilidade no mTLS

- expõe rotas internas que podem ser consumidas por outros serviços via `backchannel`;
- aceita certificado do cliente quando a porta `mTLS` está habilitada;
- também atua como cliente `mTLS` ao chamar o serviço de perfil por HTTPS interno.

### Configuração

As propriedades são carregadas por `seguranca.mtls`:

- `habilitado`
- `porta-interna`
- `keystore-arquivo`
- `keystore-senha`
- `truststore-arquivo`
- `truststore-senha`

Comportamento por ambiente:

- `application.yml`: nasce desligado por padrão.
- `application-hml.yml`: marcado como ligado.
- `application-prod.yml`: marcado como ligado.

No runtime via `docker-compose`, `dev` e `hml` sobrescrevem os caminhos para `file:/certificados/...`, que é o que realmente vale no container.

### Como o servidor sobe

Quando `seguranca.mtls.habilitado=false`, a API sobe apenas com o conector HTTP normal.

Quando `seguranca.mtls.habilitado=true`:

- a configuração valida `keystore` e `truststore`;
- se `porta-interna > 0`, o Tomcat cria um conector HTTPS adicional para o canal interno;
- esse conector exige certificado do cliente com `clientAuth=true`;
- a porta pública HTTP continua existindo para tráfego normal e Swagger;
- se `porta-interna == 0`, o servidor inteiro passa a exigir `mTLS`.

Na prática atual do monorepo:

- em `dev`, a porta pública da identidade continua em `8081`;
- a porta interna `mTLS` sobe em `8443` dentro do container e é publicada como `18481` no host;
- em `hml`, a mesma lógica vale, mas publicada como `19481`.

### Como o cliente HTTP usa mTLS

O helper `ConfiguradorRestTemplateBackchannelMtls` só ativa `mTLS` quando a `urlBase` da integração usa `https`.

Fluxo do helper:

- recebe `urlBase` e `timeout`;
- se a URL for `http`, devolve um `RestTemplateBuilder` normal;
- se a URL for `https`, exige `seguranca.mtls.habilitado=true`;
- carrega `keystore` e `truststore`;
- monta um `SSLContext` com `KeyManagerFactory` e `TrustManagerFactory`;
- injeta esse `SSLContext` no `HttpClient` usado pelo `RestTemplate`.

Isso significa que o serviço:

- apresenta o próprio certificado ao outro lado;
- valida o certificado apresentado pelo outro lado;
- falha cedo se pedirem HTTPS sem configuração válida de `mTLS`.

### Onde ele é usado hoje

No auth, o uso real do cliente `mTLS` hoje está no `backchannel` para resolver contexto de pessoa no serviço de perfil.

Na arquitetura canônica, esse mesmo mecanismo também é a base para o provisionamento `autenticação -> thimisu` depois da confirmação de e-mail do cadastro.

A chamada segue este desenho:

1. monta `RestTemplate` com `SSLContext` se a URL do perfil for HTTPS;
2. obtém um JWT interno por `client_credentials`;
3. envia `Authorization: Bearer ...`;
4. envia `X-Eickrono-Internal-Secret`;
5. envia a requisição HTTPS já autenticada por certificado.

Observação importante:

- a obtenção do JWT interno no Keycloak não usa o helper de `mTLS`; o `mTLS` aqui protege o `backchannel` entre serviços, não toda chamada feita pelo módulo.

## servidor-autorizacao-eickrono

### Responsabilidade no mTLS

Este módulo não sobe um servidor HTTP Spring para receber `mTLS`. Ele roda dentro do Keycloak e usa `mTLS` apenas como cliente quando valida `device token` na API de identidade durante o refresh.

### Configuração

A SPI lê estas variáveis:

- `EICKRONO_INTERNO_MTLS_HABILITADO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_SENHA`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_SENHA`

Além disso, ela depende de:

- `EICKRONO_IDENTIDADE_API_BASE_URL`
- `EICKRONO_KEYCLOAK_URL_BASE`
- `EICKRONO_IDENTIDADE_CLIENT_ID_INTERNO`
- `EICKRONO_IDENTIDADE_CLIENT_SECRET_INTERNO`
- `EICKRONO_INTERNAL_SECRET`

### Como o fluxo funciona

No refresh com `device token`, a SPI:

1. detecta se a URL da identidade ou do Keycloak está em HTTPS;
2. se alguma estiver em HTTPS, exige `EICKRONO_INTERNO_MTLS_HABILITADO=true`;
3. carrega `keystore` e `truststore`;
4. monta um `HttpClient` Java com `SSLContext`;
5. obtém um JWT interno por `client_credentials`;
6. chama a API de identidade com `Bearer`, `X-Eickrono-Internal-Secret`, `X-Device-Token` e `X-Usuario-Sub`.

No `docker-compose` atual:

- a URL do Keycloak interno continua em HTTP;
- a URL da API de identidade interna está em HTTPS;
- então o certificado do `servidor-autorizacao.p12` é usado para o canal até a identidade.

## api-contas-eickrono

### Responsabilidade no mTLS

O módulo tem suporte de servidor para `mTLS`, mas seu desenho ainda é mais simples do que o da identidade:

- não existe `porta-interna`;
- se o `mTLS` for habilitado, a porta inteira da aplicação passa a exigir certificado de cliente;
- não existe, hoje, helper de cliente HTTP com `mTLS` equivalente ao dos outros módulos.

### Estado atual

No código:

- `application.yml` nasce com `seguranca.mtls.habilitado=false`;
- `application-hml.yml` e `application-prod.yml` marcam `mTLS` como ligado.

No runtime dos ambientes locais:

- `dev`: o `docker-compose` não ativa `mTLS`;
- `hml`: o `docker-compose` ativa explicitamente `SEGURANCA_MTLS_HABILITADO=false`.

Na prática, hoje o `api-contas-eickrono` ainda não participa do `backchannel mTLS` ativo do ecossistema.

### Limitação atual

O validador remoto de `device token` usa `RestTemplate` simples. Então:

- ele pode chamar a API de identidade;
- mas essa chamada não usa cliente `mTLS`;
- se o serviço passar a falar com um endpoint interno somente `mTLS`, será preciso evoluir esse módulo para seguir o mesmo padrão do `api-identidade-eickrono`.

## Geração de certificados

Os scripts oficiais ficam em:

- `infraestrutura/dev/certificados/gerar_certificados.sh`
- `infraestrutura/hml/certificados/gerar_certificados.sh`

Eles geram:

- uma CA interna autoassinada;
- `api-identidade-eickrono.p12`;
- `thimisu-backend.p12`;
- `servidor-autorizacao.p12`;
- `backchannel-truststore.p12`.

### Artefatos gerados

Arquivos principais:

- `backchannel-ca.key`
- `backchannel-ca.crt`
- `backchannel-truststore.p12`
- `api-identidade-eickrono.p12`
- `thimisu-backend.p12`
- `servidor-autorizacao.p12`

### Execução em dev

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/certificados
MTLS_KEYSTORE_SENHA=senhaBackchannelDev \
MTLS_TRUSTSTORE_SENHA=senhaBackchannelDev \
./gerar_certificados.sh
```

### Execução em hml

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/hml/certificados
MTLS_KEYSTORE_SENHA=senhaBackchannelHml \
MTLS_TRUSTSTORE_SENHA=senhaBackchannelHml \
./gerar_certificados.sh
```

### O que o script faz

1. remove os artefatos antigos;
2. cria uma CA RSA interna;
3. gera chave privada e CSR por serviço;
4. assina cada CSR com a CA;
5. exporta o material em `PKCS12`;
6. importa a CA no `backchannel-truststore.p12`.

### SAN e uso estendido

O script gera:

- `api-identidade-eickrono` com `serverAuth,clientAuth`;
- `thimisu-backend` com `serverAuth,clientAuth`;
- `servidor-autorizacao` com `clientAuth`.

Os SANs incluem nomes úteis para container e host local, como:

- `api-identidade-eickrono`
- `thimisu-backend`
- `servidor-autorizacao`
- `host.docker.internal`
- `localhost`
- `127.0.0.1`

## Como os certificados entram nos containers

Em `dev` e `hml`, o `docker-compose` monta a pasta `certificados` em `/certificados`.

Exemplos de uso real:

- a identidade lê `file:/certificados/api-identidade-eickrono.p12`;
- o thimisu lê `file:/certificados/thimisu-backend.p12`;
- a SPI do Keycloak lê `/certificados/servidor-autorizacao.p12`;
- todos confiam na CA via `/certificados/backchannel-truststore.p12`.

## Limitações e observações importantes

- os caminhos `classpath:certificados/...` dos `application-*.yml` não são a fonte real usada no `docker-compose` local; os containers atuais dependem dos arquivos montados em `/certificados`;
- hoje só a malha `identidade <-> thimisu` e `servidor-autorizacao -> identidade` está realmente usando `mTLS` no fluxo local;
- o `api-contas-eickrono` ainda precisa de evolução se for entrar no mesmo padrão;
- as variáveis `SERVIDOR_AUTORIZACAO_MTLS_CERTIFICADO` e `SERVIDOR_AUTORIZACAO_MTLS_SENHA` aparecem nos `docker-compose`, mas não são consumidas pelo código Java atual.

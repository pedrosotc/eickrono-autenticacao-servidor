# Analise de Fronteiras Funcionais entre Autenticacao, Identidade e Thimisu-Backend

Este documento responde a pergunta funcional mais importante da refatoracao:

- ainda existem funcoes de autenticacao no projeto errado?
- ou o ecossistema ja esta coerente e o que resta e mais migracao tecnica do
  que refactor de responsabilidade?

## Escopo analisado

- `eickrono-identidade-servidor`
- `eickrono-thimisu-backend/modulos/thimisu-backend`
- contratos internos entre os dois servicos

Nao entram aqui:

- nomes de artefato, modulo Maven ou arquivo `.p12`
- convencoes de host e DNS
- detalhes de app Flutter

## Resultado resumido

No recorte atual, a fronteira funcional principal **esta coerente**.

Hoje:

- a borda publica de cadastro, login, recuperacao de senha, confirmacao e
  dispositivo esta no `api-identidade-eickrono`;
- o `thimisu-backend` nao recebe senha, codigo nem tentativa de login do app;
- o `thimisu-backend` ficou concentrado em contexto interno, disponibilidade de
  usuario e provisionamento de dominio por backchannel.

Entao, neste momento, o que ainda resta e majoritariamente:

- migracao tecnica de nomes e contratos internos;
- limpeza de restos legados;
- hardening operacional.

Nao sobrou, hoje, um fluxo publico grande de autenticacao claramente no
projeto errado.

## Mapa de responsabilidades por servico

## `api-identidade-eickrono`

### Deve continuar aqui

Rotas e fluxos publicos de autenticacao:

- `POST /api/publica/cadastros`
- `POST /api/publica/convites/{codigo}/cadastros`
- `GET /api/publica/cadastros/usuarios/disponibilidade`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email/reenvio`
- `DELETE /api/publica/cadastros/{cadastroId}`
- `POST /api/publica/sessoes`
- `POST /api/publica/sessoes/refresh`
- `POST /api/publica/recuperacoes-senha`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/senha`

Rotas publicas de seguranca e onboarding de dispositivo:

- `POST /api/publica/atestacoes/desafios`
- `POST /api/publica/atestacoes/validacoes`
- `POST /identidade/dispositivos/registro`
- `POST /identidade/dispositivos/registro/silencioso`
- `POST /identidade/dispositivos/registro/{id}/confirmacao`
- `POST /identidade/dispositivos/registro/{id}/reenviar`
- `POST /identidade/dispositivos/revogar`
- `GET /identidade/dispositivos/token/validacao`
- `GET /identidade/dispositivos/token/validacao/interna`

Rotas de conta autenticada que tambem pertencem a autenticacao:

- `GET /identidade/vinculos-sociais`
- `POST /identidade/vinculos-sociais/{provedor}/sincronizacao`
- `DELETE /identidade/vinculos-sociais/{provedor}`
- `GET /identidade/vinculos-organizacionais`
- `GET /api/publica/convites/{codigo}`

Backchannel interno que tambem faz sentido aqui:

- `POST /identidade/cadastros/interna`
- `POST /identidade/cadastros/interna/{cadastroId}/confirmacoes/email`
- `POST /identidade/cadastros/interna/{cadastroId}/confirmacoes/email/reenvio`
- `POST /identidade/sessoes/interna`
- `POST /identidade/atestacoes/interna/desafios`
- `POST /identidade/atestacoes/interna/validacoes`

### Leitura funcional

Esse servico e a **borda canonica** de:

- autenticacao publica;
- ciclo de vida do cadastro;
- recuperacao de senha;
- confianca do dispositivo;
- vinculo social;
- politicas de sessao.

Isso esta coerente com a arquitetura desejada.

## `thimisu-backend`

### Deve continuar aqui

Rotas efetivamente encontradas:

- `GET /api/v1/estado`
- `GET /api/interna/identidade/contexto`
- `GET /api/interna/identidade/usuarios/disponibilidade`
- `POST /api/interna/identidade/provisionamentos`

### Leitura funcional

Esse servico esta concentrado em:

- pessoa e usuario do dominio;
- resolucao de contexto interno por `pessoaId`, `sub`, `email` ou `usuario`;
- disponibilidade de `usuario` do dominio;
- provisionamento idempotente do perfil de negocio a partir do `cadastroId`.

Isso tambem esta coerente com a arquitetura alvo.

## Achados objetivos

### 1. Nao encontrei login publico no `thimisu-backend`

No modulo `thimisu-backend`, nao existe rota publica de:

- login
- senha
- recuperacao de senha
- confirmacao de codigo
- refresh de sessao

Isso significa que o principal desvio historico ja foi saneado.

### 2. A disponibilidade de usuario esta no lugar certo

`GET /api/interna/identidade/usuarios/disponibilidade` fica no
`thimisu-backend`, nao na autenticacao.

Isso faz sentido porque:

- `usuario` e regra de dominio do Thimisu;
- a autenticacao apenas consulta ou orquestra essa disponibilidade;
- o backend de dominio continua dono da unicidade semantica do identificador
  de usuario.

### 3. O provisionamento por `cadastroId` esta no lugar certo

`POST /api/interna/identidade/provisionamentos` pertence ao
`thimisu-backend`, porque o que ele faz e:

- criar ou atualizar `Pessoa`;
- criar ou atualizar `Usuario`;
- responder com ids e status do dominio.

Isso nao deve voltar para a autenticacao.

### 4. O resquicio funcional legado ja foi removido

`GET /identidade/perfil` foi removido da API de autenticacao e do pacote
compartilhado. A leitura autenticada agora deve usar contratos vivos, como
`/identidade/vinculos-sociais`, `/identidade/vinculos-organizacionais` ou o
backend de dominio propriamente dito.

### 5. O que resta hoje e mais tecnico do que funcional

Os pontos mais pendentes nao sao "funcao no projeto errado", e sim:

- audience/resource client ja alinhado como `thimisu-backend`;
- certificado tecnico ja alinhado como `thimisu-backend.p12`;
- nomes de client ids internos e exports do Keycloak em transicao;
- nomenclatura de repo/modulo ainda antiga.

## Conclusao

A refatoracao funcional principal ja esta em boa forma.

No recorte analisado:

- autenticacao publica esta no `api-identidade-eickrono`;
- dominio e provisionamento interno estao no `thimisu-backend`;
- nao encontrei um fluxo grande de login/cadastro/senha claramente perdido no
  backend de dominio.

Entao a resposta objetiva e:

- **sim**, a fronteira de funcoes entre os projetos hoje esta majoritariamente
  correta;
- o que ainda falta e principalmente migracao tecnica e limpeza de legado;
- o que sobra agora e majoritariamente legado tecnico de build e nomenclatura estrutural.

## Proximos passos recomendados

1. Concluir a migracao tecnica dos nomes internos sem `-interno`.
2. Continuar a limpeza de nomes estruturais antigos quando houver retorno real.
3. Manter `thimisu-backend` como identificador canônico de runtime do backend de domínio.

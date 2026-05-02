# TODO Local do Servidor de Autenticacao

Backlog cross-service priorizado:

- ver [backlog_cross_service_autenticacao_oidc_dispositivo.md](backlog_cross_service_autenticacao_oidc_dispositivo.md)
- este arquivo permanece como detalhamento local do `servidor de autenticacao`

Escopo deste arquivo:

- registrar pendencias locais do repositorio;
- evitar repetir aqui o backlog que ja depende de coordenacao entre multiplos
  servicos;
- usar o backlog cross-service como fonte principal quando a tarefa atravessar
  app, Keycloak, identidade ou infraestrutura.

## Fluxo De Cadastro Pendente

- Revisar a politica de expurgo de cadastros pendentes quando a validacao canonica de telefone entrar no fluxo.
- Hoje, no modelo atual, apenas `PENDENTE_EMAIL` pode ser apagado automaticamente.
- `EMAIL_CONFIRMADO` nao deve ser apagado, porque nesse ponto o fluxo ja provisiona o perfil no servidor thimisu, ativa/confirma o usuario no Keycloak e marca o cadastro como confirmado.
- Com a futura validacao de telefone, um e-mail confirmado sem telefone confirmado nao devera mais liberar o usuario imediatamente; o modelo de estados de `cadastros_conta` precisara ser revisto antes de manter essa politica de limpeza.

## Governanca De Sessao E Dispositivo

- Implementar revogacao remota de sessoes e de `X-Device-Token`.
- Permitir encerramento de todas as sessoes ativas de um usuario.
- Manter lista de dispositivos confiaveis com historico de uso e ultimo acesso.
- Introduzir bloqueio por risco, expiracao por inatividade e reautenticacao para operacoes sensiveis.

## Monitoramento E Antifraude

- Ampliar mascaramento de dados sensiveis em logs e eventos de seguranca.
- Integrar os eventos criticos de autenticacao com a camada de monitoramento e alerta.
- Implementar estrategia de antifraude operacional alem do rate limit: deteccao, bloqueio progressivo, quarentena, alertas e resposta.

## Entregabilidade E Contexto De E-mail

- Publicar `DMARC` para o dominio de envio do ecossistema.
- Validar em cabecalho real dos provedores-alvo (`Hotmail/Outlook`, `Gmail`, `Yahoo`) se `SPF=pass`, `DKIM=pass` e `DMARC=pass` antes de investir em branding do template.
- Separar o identificador tecnico interno do fluxo (`fluxoId` / `cadastroId`) de um `protocoloSuporte` externo proprio para comunicacao com o cliente.
- Evoluir o contexto exibido no e-mail para diferenciar:
  - tipo do canal (`app`, `portal`, `site`, `admin`);
  - produto (`Thimisu`, outro produto contratado);
  - empresa/marca exibida (`Eickrono` ou empresa terceira contratante);
  - ambiente (`HML` apenas quando realmente necessario).
- Persistir `locale` e `timeZone` nos fluxos publicos de cadastro e recuperacao de senha.
- Definir fallback de renderizacao de horario nesta ordem:
  - `timeZone` enviado pelo app/web;
  - ultimo `timeZone` valido conhecido da conta/fluxo;
  - `UTC`.
- Exibir no corpo do e-mail os dois horarios quando fizer sentido:
  - horario local amigavel ao usuario;
  - `UTC` como referencia tecnica para suporte e auditoria.

### TODO De Negocio: Migracao Para E-mail Transacional

- Objetivo:
  - sair de SMTP de conta pessoal/organizacional e adotar um provedor transacional com autenticacao, reputacao, webhooks e observabilidade.

- Passo 1. Autenticar o dominio no provedor escolhido:
  - cadastrar o dominio de envio (`eickrono.com` ou subdominio dedicado como `mail.eickrono.com`);
  - publicar `SPF`, `DKIM` e `DMARC` segundo o provedor;
  - validar DNS e alinhamento do `From`.

- Passo 2. Usar API transacional em vez de SMTP de conta pessoal:
  - escolher provedor (`Amazon SES`, `Postmark`, `Mailgun`, `SendGrid`, `SparkPost`, `Resend`);
  - armazenar credenciais em segredo dedicado;
  - trocar o canal de envio por adaptador HTTP/API do provedor;
  - manter fallback de log apenas para desenvolvimento local.

- Passo 3. Receber webhooks do provedor:
  - `delivered`;
  - `bounced`;
  - `complaint`;
  - `opened` apenas se houver justificativa real de produto/privacidade.

- Passo 4. Ligar webhooks a auditoria e suporte:
  - correlacionar cada evento com `protocoloSuporte`, tipo de evento e destinatario;
  - registrar falhas permanentes e temporarias;
  - permitir diagnostico operacional por fluxo sem expor `fluxoId` interno ao cliente.

- Passo 5. Evoluir templates versionados:
  - templates por evento (`cadastro`, `recuperacao`, `vinculo social`, etc.);
  - templates por idioma;
  - variacoes por ambiente;
  - variaveis de contexto com canal, produto e empresa exibida.

## Seguranca Do App Movel

- Endurecer deteccao de root/jailbreak alem da atestacao nativa.
- Adicionar sinais de deteccao de Frida/hooking e de adulteracao do app.
- Revisar protecao de armazenamento local, politica de screenshot, protecao de clipboard e minimizacao de dados em cache.

## Padronizacao OIDC Entre Ambientes

- Padronizar o nome do realm para `eickrono` em `dev`, `hml` e `prod`.
- Separar claramente os hosts publicos em `id-*` para a API e `oidc-*` para o servidor de autorizacao.
- Alinhar `issuer`, callbacks dos brokers sociais, exports de realm, configs Spring e configs do app ao novo padrao.
- Tratar a mudanca como alteracao coordenada de contrato OIDC, nao como simples troca de string.
- Ver plano detalhado em `plano-padronizacao-realm-unico.md`.

## Aliases Tecnicos Legados

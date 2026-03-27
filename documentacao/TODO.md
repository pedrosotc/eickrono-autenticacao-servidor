# TODO

## Fluxo De Cadastro Pendente

- Revisar a politica de expurgo de cadastros pendentes quando a validacao canonica de telefone entrar no fluxo.
- Hoje, no modelo atual, apenas `PENDENTE_EMAIL` pode ser apagado automaticamente.
- `EMAIL_CONFIRMADO` nao deve ser apagado, porque nesse ponto o fluxo ja provisiona o perfil no servidor flashcard, ativa/confirma o usuario no Keycloak e marca o cadastro como confirmado.
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

## Seguranca Do App Movel

- Endurecer deteccao de root/jailbreak alem da atestacao nativa.
- Adicionar sinais de deteccao de Frida/hooking e de adulteracao do app.
- Revisar protecao de armazenamento local, politica de screenshot, protecao de clipboard e minimizacao de dados em cache.

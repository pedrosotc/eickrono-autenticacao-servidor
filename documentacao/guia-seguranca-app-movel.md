# Guia de Seguranca do App Movel

Este guia descreve como o `servidor de autenticacao` deve receber, interpretar e reagir aos sinais de seguranca enviados pelo app movel alem de `Google Play Integrity` e `Apple App Attest`.

## Diretriz

O backend continua sendo a fonte de verdade.

O app:

- produz a prova oficial da plataforma;
- coleta sinais locais adicionais por plataforma;
- envia os sinais para a autenticacao.

A autenticacao:

- valida a prova oficial;
- correlaciona os sinais locais com o contexto da sessao e do aparelho;
- decide se libera, desafia, limita ou bloqueia.

## Status atual implementado

Nesta etapa, o `servidor de autenticacao` ja recebe e correlaciona:

- `aplicacaoId`;
- payload `segurancaAplicativo`;
- plataforma e provedor declarados pelo app;
- `packageName` no Android;
- `bundleIdentifier` e `teamIdentifier` no iOS;
- score e sinais locais informados pelo cliente;
- score e sinais recalculados no servidor.

Tambem ja esta implementado:

- modo observacao para `dev/hml`;
- endurecimento em `prod`;
- auditoria de eventos de risco no cadastro e no login.

## O que esta em jogo

Camadas contempladas:

- `Play Integrity` no Android;
- `App Attest` no iOS;
- deteccao local de `root/jailbreak`;
- deteccao local de `Frida/hooking/debug`;
- deteccao local de tamper ou reempacotamento;
- sinais de captura de tela e endurecimento local do app.

## Regra central

Sinais locais nao substituem validacao oficial da plataforma.

Eles devem ser usados como:

- evidencias complementares;
- insumos de score de risco;
- gatilhos de auditoria;
- material para bloqueio progressivo.

## Contrato recomendado

Os endpoints sensiveis devem aceitar, alem da atestacao oficial, um bloco de sinais locais. Exemplo conceitual:

```json
{
  "atestacao": {
    "plataforma": "ANDROID",
    "provedor": "GOOGLE_PLAY_INTEGRITY",
    "tipoComprovante": "TOKEN_INTEGRIDADE",
    "identificadorDesafio": "uuid",
    "conteudoComprovante": "eyJ..."
  },
  "segurancaAppMovel": {
    "rootOrJailbreak": false,
    "debuggerDetected": false,
    "hookingSuspected": false,
    "tamperSuspected": false,
    "screenCaptureRisk": false,
    "signatureValid": true,
    "appIdentityValid": true,
    "riskSignals": [],
    "localRiskScore": 0
  },
  "dispositivo": {
    "fingerprint": "hash",
    "plataforma": "IOS",
    "versaoAplicativo": "1.0.0"
  }
}
```

## Decisao de politica

O backend deve combinar:

- resultado de `Play Integrity` ou `App Attest`;
- sinais locais enviados pelo app;
- fingerprint e historico do dispositivo;
- status da conta;
- historico de acesso;
- politicas de risco por operacao.

Saidas canônicas sugeridas:

- `AUTENTICADO`
- `VALIDACAO_CONTATO_PENDENTE`
- `REAUTENTICACAO_EXIGIDA`
- `OPERACAO_NEGADA_POR_RISCO`
- `DISPOSITIVO_BLOQUEADO`
- `CONTA_BLOQUEADA`

## Heuristicas recomendadas

### Liberar silenciosamente

Quando:

- a atestacao oficial e valida;
- nao ha root/jailbreak;
- nao ha sinais de hooking ou tamper;
- a assinatura do app bate com o esperado;
- o aparelho esta dentro do historico aceito;
- o score agregado de risco esta abaixo do limite.

### Exigir nova validacao

Quando:

- a atestacao oficial e valida, mas ha sinais locais relevantes;
- houve mudanca forte de fingerprint;
- o dispositivo esta novo para aquela conta;
- houve risco moderado, mas sem evidencia suficiente para bloqueio direto.

### Bloquear

Quando:

- a atestacao oficial falha;
- a assinatura do app nao bate;
- ha forte indicio de tamper ou hooking;
- a politica de risco considerar o contexto inaceitavel.

## O que nao fazer

- nao confiar em um unico sinal local como verdade absoluta;
- nao liberar login apenas porque o cliente afirmou que nao ha root;
- nao vazar para o app o motivo interno detalhado do bloqueio;
- nao escrever tokens, e-mails ou artefatos sensiveis em log sem mascaramento.

## Persistencia e auditoria

O backend deve registrar, com mascaramento adequado:

- plataforma;
- provedor de atestacao;
- resultado da validacao oficial;
- sinais locais relevantes;
- score de risco agregado;
- decisao final;
- correlacao com `cadastroId`, `subjectRemoto`, `usuarioId`, `device token` e IP quando aplicavel.

Os registros devem permitir:

- auditoria de eventos criticos;
- deteccao de recorrencia por conta e por aparelho;
- integracao com monitoramento e alerta;
- suporte a bloqueio progressivo.

## Endpoints que devem aceitar esse contexto

Minimo recomendado:

- `POST /api/publica/cadastros`
- `POST /api/publica/sessoes`
- `POST /api/publica/sessoes/refresh` quando houver politica de risco
- `POST /api/publica/recuperacoes-senha`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/senha`
- qualquer endpoint de alteracao de contato ou credencial

## Backlog de implementacao

### No servidor de autenticacao

- adicionar DTO canonico para sinais locais de seguranca do app;
- incluir esses sinais na avaliacao de risco de cadastro, login, refresh e operacoes sensiveis;
- definir score de risco por combinacao de sinais;
- padronizar respostas de negocio sem expor detalhe tecnico ao cliente;
- ampliar auditoria e monitoramento com mascaramento adequado;
- integrar bloqueio progressivo, quarentena e alertas operacionais.

### No app flashcard

- coletar os sinais locais por `MethodChannel` nativo;
- enviar esses sinais junto da atestacao oficial;
- reagir apenas ao codigo de negocio devolvido pela autenticacao;
- evitar armazenamento, log e cache desnecessarios de dados sensiveis.

## Relacao com a arquitetura atual

Este guia complementa, e nao substitui:

- `guia-arquitetura.md`
- `guia-desenvolvimento.md`
- a atestacao oficial ja existente por `Play Integrity` e `App Attest`

Em resumo:

- `Play Integrity` e `App Attest` continuam sendo a base forte;
- sinais locais aumentam a visibilidade de risco;
- a decisao final continua centralizada na autenticacao.

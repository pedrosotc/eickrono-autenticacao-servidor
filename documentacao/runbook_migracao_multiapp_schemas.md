# Runbook de Migração para Schemas Multiapp

## Objetivo

Executar a transição do modelo legado em `public` para o modelo alvo por schemas:

- `catalogo`
- `autenticacao`
- `dispositivos`
- `seguranca`
- `auditoria`

Este runbook cobre:

- ordem da migração de banco;
- ordem da migração de código;
- backfill;
- cutover;
- limpeza final.

## Premissas

- a base continua única por enquanto;
- a migração inicial é aditiva;
- o legado em `public` continua existindo até o cutover;
- o `eickrono-autenticacao-servidor` não cria tabelas do schema `identidade`;
- por isso, na primeira leva, colunas como `pessoa_id`, `email_id` e `telefone_id` são criadas sem FK cruzada para `identidade`;
- as FKs cruzadas com `identidade` entram numa fase posterior, depois que o `eickrono-identidade-servidor` criar suas tabelas-alvo.

## Artefatos de referência

- DBML alvo: `documentacao/diagramas/modelo_api_autenticacao_multiapp_schemas.dbml`
- proposta de cisão: `../../eickrono-identidade-servidor/docs/proposta_cisao_identidade_thimisu.md`

## Fase 1. Criar a estrutura nova sem tocar no legado

Aplicar as migrations novas:

- `V14__criar_schemas_multiapp.sql`
- `V15__criar_catalogo_e_usuarios_multiapp.sql`
- `V16__criar_tabelas_dispositivos_multiapp.sql`
- `V17__criar_tabelas_seguranca_e_auditoria_multiapp.sql`
- `V18__seed_catalogo_multiapp_inicial.sql`
- `V19__backfill_usuarios_e_vinculos_multiapp.sql`
- `V20__backfill_cadastros_e_recuperacoes_multiapp.sql`
- `V21__backfill_dispositivos_e_atestacoes_multiapp.sql`

Resultado esperado:

- schemas novos criados;
- tabelas novas prontas para receber dados;
- nenhum fluxo legado quebrado;
- nenhuma tabela antiga removida ou reescrita.

## Fase 2. Preparar catálogos e mapeamentos

Popular minimamente:

- `catalogo.clientes_ecossistema`
- `catalogo.sistemas_origem`

Regras:

- cada app, site, BFF ou cliente externo vira um registro em `clientes_ecossistema`;
- cada backend interno chamador vira um registro em `sistemas_origem`;
- os códigos devem ser estáveis e versionados em ambiente.

## Fase 3. Backfill controlado

### 3.1 Contas centrais e vínculos

Popular:

- `autenticacao.usuarios`
- `autenticacao.usuarios_clientes_ecossistema`

Fontes legadas prováveis:

- `pessoas_identidade`
- `pessoas_formas_acesso`
- `cadastros_conta`
- tabelas locais de `usuario` do legado

Critérios:

- gerar `usuario.id` novo e estável no modelo alvo;
- enquanto o schema `identidade` ainda não existir, gerar `pessoa_id` transitório, mas determinístico, a partir do `sub` legado;
- usar namespaces distintos na derivação determinística, para não colidir `usuario.id` com `pessoa_id`;
- preservar `sub_remoto` quando existir;
- resolver um `cliente_ecossistema_id` padrão para os registros que hoje pertencem implicitamente ao thimisu;
- materializar o vínculo do usuário com esse cliente.

Implementação inicial já materializada:

- o seed do cliente padrão usa `catalogo.clientes_ecossistema.codigo = eickrono-thimisu-app`;
- o seed de `sistemas_origem` captura valores conhecidos e também qualquer `sistema_solicitante` legado observado em `public.cadastros_conta`;
- o backfill inicial cobre `autenticacao.usuarios`, `autenticacao.usuarios_formas_acesso` e `autenticacao.usuarios_clientes_ecossistema`;
- a rodada seguinte cobre `autenticacao.cadastros_conta`, `autenticacao.recuperacoes_senha`, `dispositivos.registros_dispositivo`, `dispositivos.codigos_verificacao_dispositivo`, `dispositivos.dispositivos_confiaveis`, `dispositivos.tokens_dispositivo` e `seguranca.atestacoes_app_desafios`;
- `seguranca.apple_app_attest_chaves`, `seguranca.credenciais_atestacao_dispositivo`, `auditoria.operacoes_atestadas` e `auditoria.google_play_integrity_veredictos` continuam pendentes porque o legado nao guarda contexto suficiente para backfill confiavel dessas estruturas.

### 3.2 Fluxos de cadastro e recuperação

Popular:

- `autenticacao.cadastros_conta`
- `autenticacao.recuperacoes_senha`

Fontes legadas:

- `public.cadastros_conta`
- `public.recuperacoes_senha`

Critérios:

- migrar os identificadores públicos dos fluxos;
- carregar `cliente_ecossistema_id` e `sistema_origem_id`;
- manter o status do processo compatível com o modelo novo.

### 3.3 Dispositivos

Popular:

- `dispositivos.registros_dispositivo`
- `dispositivos.dispositivos_confiaveis`
- `dispositivos.tokens_dispositivo`

Fontes legadas:

- `public.registro_dispositivo`
- `public.dispositivos_identidade`
- `public.token_dispositivo`

Critérios:

- resolver o `usuario_id` alvo antes de carregar dispositivo confiável;
- associar o cliente do ecossistema correto;
- preservar `fingerprint`, plataforma, versões e chaves públicas conhecidas;
- manter rastreabilidade entre onboarding e token.

### 3.4 Segurança e auditoria

Popular:

- `seguranca.atestacoes_app_desafios`
- `seguranca.credenciais_atestacao_dispositivo`
- `seguranca.apple_app_attest_chaves`
- `auditoria.operacoes_atestadas`
- `auditoria.google_play_integrity_veredictos`

Fontes legadas:

- `public.atestacoes_app_desafios`
- `public.apple_app_attest_chaves`
- trilhas de operação existentes no thimisu, se houver

Critérios:

- preservar o `desafio_base64`;
- amarrar a prova ao contexto do cliente, usuário e dispositivo no modelo novo;
- não inventar tabela de chave equivalente para Play Integrity;
- guardar no Android apenas verdicts e sinais operacionais relevantes.

## Fase 4. Cutover de código

Ordem recomendada:

1. publicar entidades, repositórios e mapeamentos do modelo novo;
2. fazer leitura dupla quando necessário;
3. trocar escrita dos fluxos novos para os schemas novos;
4. manter leitura legada apenas para conferência temporária;
5. desligar escrita no legado depois da validação operacional.

## Fase 5. Ajustes por módulo de código

### Cadastro e recuperação

- `cadastros_conta` e `recuperacoes_senha` passam a escrever no schema `autenticacao`;
- `sistema_solicitante` texto solto deixa de ser usado;
- `cliente_ecossistema_id` e `sistema_origem_id` passam a ser obrigatórios nos pontos de entrada internos.

### Dispositivos

- onboarding passa a escrever em `dispositivos.registros_dispositivo`;
- confiança do aparelho passa a escrever em `dispositivos.dispositivos_confiaveis`;
- emissão de token passa a usar `dispositivos.tokens_dispositivo`.

### Atestação

- emissão de desafio passa a escrever em `seguranca.atestacoes_app_desafios`;
- App Attest passa a escrever a camada comum em `seguranca.credenciais_atestacao_dispositivo` e a extensão Apple em `seguranca.apple_app_attest_chaves`;
- Play Integrity passa a consolidar o resultado em `auditoria.operacoes_atestadas` e `auditoria.google_play_integrity_veredictos`.

## Fase 6. Validação

Checklist mínimo:

- Flyway sobe sem erro em base vazia;
- Flyway sobe sem erro em base com legado já existente;
- cadastro novo cria registros apenas nos schemas novos depois do cutover;
- login e recuperação seguem íntegros;
- onboarding de dispositivo continua emitindo token;
- App Attest continua atualizando contador;
- Play Integrity continua registrando verdicts;
- logs e auditoria conseguem localizar cliente, usuário e dispositivo.

## Fase 7. Limpeza final

Somente depois do cutover estabilizado:

- remover escrita nas tabelas legadas em `public`;
- congelar backfill;
- adicionar FKs cruzadas com `identidade` se o schema já existir;
- planejar remoção ou arquivamento das tabelas legadas.

## Observação importante

As migrations desta primeira leva não fazem:

- backfill automático;
- remoção de legado;
- criação do schema `identidade`;
- FKs cruzadas com `identidade`.

Esses passos dependem da ordem real de implantação entre os serviços e devem ser feitos em fases posteriores para evitar acoplamento prematuro.

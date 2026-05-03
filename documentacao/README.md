# Eickrono Autenticação

Esta pasta reúne a documentação canônica do ecossistema de identidade da Eickrono.

## Diretriz vigente

Para o app móvel:

- cadastro, confirmação de e-mail, login e recuperação de senha entram pela API pública de identidade;
- a autenticação continua dona da conta central, das credenciais e das liberações internas;
- a identidade continua dona da `Pessoa` canônica;
- o thimisu recebe apenas o provisionamento do perfil daquele sistema depois que conta central e `Pessoa` já tiverem sido resolvidas;
- o `X-Device-Token` canônico nasce no próprio login público da autenticação;
- qualquer explicação antiga centrada em navegador, OIDC interativo no app ou autenticação pública via thimisu deve ser considerada legada.

## Diretriz de nomenclatura

Nesta documentação, quando o conceito servir para todo o ecossistema, devem
ser usados nomes gerais e não nomes de produto.

Regra prática:

- preferir termos como `cliente`, `sistema`, `vinculo`, `perfilSistema` e
  equivalentes;
- evitar termos centrados em um produto específico quando a regra vale para
  vários apps, sites ou softwares;
- manter nome de produto apenas quando o comportamento for realmente exclusivo
  daquele produto.

## Guias principais

- `guia-arquitetura.md`: papel de cada serviço, contratos canônicos e segurança do fluxo
- `consolidado_migracao_autenticacao_identidade_thimisu.md`: visão única das responsabilidades, migrações e inconsistências abertas entre autenticação, identidade e thimisu
- `classificacao_documentacao_ecossistema.md`: mapa de quais `.md` dos repositorios centrais sao canonicos, historicos ou ainda precisam alinhar
- `especificacao_scheduler_pendencias_integracao_produto.md`: especificacao funcional e tecnica da fila persistida e do scheduler de novas tentativas para entregas ao backend do produto
- `runbook_teste_integrado_dev_produto_indisponivel.md`: passo a passo validado em `dev` para cadastro confirmado com produto fora do ar, drenagem da fila ao religar o produto e login central sem dependencia do backend do produto
- `guia-seguranca-app-movel.md`: sinais locais do app, integração com atestação oficial e decisão de risco no backend
- `guia-desenvolvimento.md`: ambiente local, `MailHog`, Docker e rotina de desenvolvimento
- `guia-mtls.md`: malha mTLS do backchannel e geração de certificados
- `guia-operacao-producao.md`: runtime, operação e observabilidade
- `guia-cloudflare-tunnel-google-keycloak-dev.md`: exposição pública temporária do Keycloak local para Google OAuth brokerado no iPhone físico
- `plano-padronizacao-realm-unico.md`: alvo arquitetural para padronizar o realm OIDC em `eickrono` entre `dev`, `hml` e `prod`
- `runbook_migracao_multiapp_schemas.md`: ordem prática da migração do legado em `public` para o modelo novo por schemas

## Estrutura

- `/modulos`: servidor de autorização e APIs Spring Boot
- `/infraestrutura`: `docker compose`, variáveis e material de runtime por ambiente
- `/documentacao`: guias arquiteturais, operacionais e diagramas

## Leitura recomendada

1. `guia-arquitetura.md`
2. `guia-desenvolvimento.md`
3. `guia-mtls.md`
4. `checklist-seguranca-fapi.md`

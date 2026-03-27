# Eickrono Autenticação

Esta pasta reúne a documentação canônica do ecossistema de identidade da Eickrono.

## Diretriz vigente

Para o app móvel:

- cadastro, confirmação de e-mail, login e recuperação de senha entram pela autenticação;
- o `flashcard-servidor` não é mais a borda pública de senha ou código;
- o flashcard recebe apenas provisionamento interno depois que a autenticação conclui as etapas sensíveis;
- o `X-Device-Token` canônico nasce no próprio login público da autenticação;
- qualquer explicação antiga centrada em navegador, OIDC interativo no app ou autenticação pública via flashcard deve ser considerada legada.

## Guias principais

- `guia-arquitetura.md`: papel de cada serviço, contratos canônicos e segurança do fluxo
- `guia-seguranca-app-movel.md`: sinais locais do app, integração com atestação oficial e decisão de risco no backend
- `guia-desenvolvimento.md`: ambiente local, `MailHog`, Docker e rotina de desenvolvimento
- `guia-mtls.md`: malha mTLS do backchannel e geração de certificados
- `guia-operacao-producao.md`: runtime, operação e observabilidade

## Estrutura

- `/modulos`: servidor de autorização e APIs Spring Boot
- `/infraestrutura`: `docker compose`, variáveis e material de runtime por ambiente
- `/documentacao`: guias arquiteturais, operacionais e diagramas

## Leitura recomendada

1. `guia-arquitetura.md`
2. `guia-desenvolvimento.md`
3. `guia-mtls.md`
4. `checklist-seguranca-fapi.md`

# Cloudflare

Esta pasta guarda os scripts operacionais ligados ao DNS autoritativo em
Cloudflare.

Arquivo:

- `upsert_cloudflare_txt_record.sh`

## Estado canonico esperado das credenciais

Por decisao operacional explicita deste workspace, este arquivo registra os
nomes das variaveis canonicas e os `zone IDs`, sem versionar o token real.

```bash
export CLOUDFLARE_API_TOKEN="COLE_AQUI_O_TOKEN_REAL"
export CLOUDFLARE_ACCOUNT_ID="379021af9b15a386571caf8d28a9897a"
export CLOUDFLARE_ZONE_ID_EICKRONO_COM="49de392ce6bfaea8ab5d5783d9d36441"
export CLOUDFLARE_ZONE_ID_EICKRONO_STORE="10c5f3bb01e6a6bb969d2ea1717ebdda"
export CLOUDFLARE_ZONE_ID_EICKRONO_ONLINE="5486137573d02336a3ad5ff13dea2575"
```

## Como obter o token no site da Cloudflare

Fluxo recomendado no painel da Cloudflare, alinhado com a documentacao oficial:

1. Entrar em `https://dash.cloudflare.com/`.
2. Abrir `My Profile > API Tokens`.
3. Clicar em `Create Token`.
4. Selecionar o template `Edit zone DNS`.
5. Dar um nome claro ao token, por exemplo:
   - `eickrono-dns-edit`
6. Confirmar as permissoes.
   Recomendacao operacional para este caso:
   - `Zone > DNS > Edit`
   - `Zone > Zone > Read`
7. Restringir os recursos para a zona necessaria:
   - `Include > Specific zone > eickrono.com`
   - se precisar operar outras zonas, criar tokens separados ou incluir
     explicitamente cada zona necessaria
8. Opcionalmente restringir por IP e TTL, se isso nao atrapalhar a operacao.
9. Clicar em `Continue to summary`.
10. Revisar e clicar em `Create Token`.
11. Copiar o segredo imediatamente.

Observacoes importantes:

- o segredo do token e exibido uma unica vez;
- nao versionar esse valor em `.md`, `.env` commitado ou historico de shell;
- se o token ja tiver sido compartilhado em conversa, tratar como comprometido e
  rotacionar.

## Como verificar o token

Depois de criar, validar:

```bash
curl "https://api.cloudflare.com/client/v4/user/tokens/verify" \
  --header "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}"
```

Sinal esperado:

- `success: true`
- `status: active`

## Objetivo

Criar ou atualizar registros `TXT` de forma segura, com foco inicial em:

- `_dmarc.eickrono.com`

## Exemplo de dry-run

```bash
bash ./infraestrutura/prod/cloudflare/upsert_cloudflare_txt_record.sh \
  --zone eickrono.com \
  --name _dmarc \
  --content 'v=DMARC1; p=none; rua=mailto:dmarc@eickrono.com; adkim=r; aspf=r; fo=1' \
  --dry-run
```

## Exemplo de execucao real

```bash
export CLOUDFLARE_API_TOKEN='COLE_AQUI_O_TOKEN_REAL'

bash ./infraestrutura/prod/cloudflare/upsert_cloudflare_txt_record.sh \
  --zone eickrono.com \
  --name _dmarc \
  --content 'v=DMARC1; p=none; rua=mailto:dmarc@eickrono.com; adkim=r; aspf=r; fo=1'
```

## Teste local do script

Arquivo:

- `../tests/upsert_cloudflare_txt_record_test.sh`

Execucao:

```bash
bash infraestrutura/prod/tests/upsert_cloudflare_txt_record_test.sh
```

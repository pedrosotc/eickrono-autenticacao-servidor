# DNS e Entregabilidade

Esta pasta guarda os scripts operacionais de validacao de DNS para entregabilidade
de e-mail.

Arquivo:

- `validate_email_auth_dns.sh`

## Objetivo

Validar se o dominio emissor possui os registros minimos esperados para:

- `SPF`
- `DMARC`
- `DKIM`

O script faz apenas leitura. Ele nao altera DNS nem depende de AWS.

## Exemplo de execucao

```bash
bash ./infraestrutura/prod/dns/validate_email_auth_dns.sh \
  --domain eickrono.com
```

## Exemplo com seletores explicitos

```bash
bash ./infraestrutura/prod/dns/validate_email_auth_dns.sh \
  --domain eickrono.com \
  --selector sig1
```

Se o provedor usar mais de um seletor DKIM, adicione `--selector` repetidamente.

## Teste local do script

Arquivo:

- `../tests/validate_email_auth_dns_test.sh`

Execucao:

```bash
bash infraestrutura/prod/tests/validate_email_auth_dns_test.sh
```

## Observacao importante

O script valida apenas a presenca dos registros DNS. A confirmacao final de
entregabilidade ainda exige enviar um e-mail real e verificar no cabecalho
recebido:

- `spf=pass`
- `dkim=pass`
- `dmarc=pass`

# Pipeline CI/CD

Esta pasta documenta o fluxo canônico de build e deploy da stack depois da
separação de identidade e contas e da incorporação do servidor de autorização
no próprio `eickrono-autenticacao-servidor`.

## Repositórios fonte

- `eickrono-identidade-servidor`
- `eickrono-contas-servidor`
- `eickrono-autenticacao-servidor`

## Etapas mínimas

1. checkout dos três repositórios em um mesmo workspace, ou exportar:
   - `EICKRONO_IDENTIDADE_DIR`
   - `EICKRONO_CONTAS_DIR`
2. empacotar os três projetos da stack:
   - `make package-servicos`
3. executar testes por serviço:
   - `make test-servicos`
   - bateria representativa estável para feedback rápido
4. executar suíte completa antes de promover artefatos:
   - `make test-servicos-completo`
   - exige `Docker` acessível por causa da suíte de integração da identidade
5. validar `docker compose` dos ambientes:
   - `make compose-config`
6. publicar artefatos/imagens de cada projeto conforme seu papel de runtime
7. fazer deploy do ambiente alvo consumindo os artefatos dos três repositórios

## Scripts canônicos

- `../../scripts/pipeline/package_servicos.sh`
- `../../scripts/pipeline/test_servicos.sh`
- `../../scripts/pipeline/test_servicos_completo.sh`
- `../../scripts/pipeline/compose_config.sh`
- `../../scripts/pipeline/up_stack.sh`

## Observações

- Os nomes de serviço no `docker compose` ainda permanecem `api-identidade-eickrono`
  e `api-contas-eickrono` por compatibilidade de runtime.
- O provider do Keycloak continua sendo montado no serviço `servidor-autorizacao`
  a partir do JAR gerado em `eickrono-autenticacao-servidor/target/`.

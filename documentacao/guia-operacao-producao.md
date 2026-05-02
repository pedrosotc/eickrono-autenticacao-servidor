# Guia de Operação em Produção

Este guia descreve processos para operar o ecossistema **Eickrono Autenticação** em produção na AWS, protegido por Cloudflare.

## Arquitetura em produção

- **EKS/ECS:** hospeda os contêineres das APIs e do Keycloak (cluster gerenciado).  
- **ALB/NLB:** balanceamento de carga com suporte a mTLS e roteamento por host/path.  
- **Cloudflare:** WAF, Rate Limiting, mTLS Origin Pull e caching seletivo de recursos estáticos.  
- **RDS PostgreSQL:** instâncias multi-AZ com backups automáticos, replicação e monitoramento de performance.  
- **Secrets Manager / Parameter Store:** armazenamento de segredos, certificados e configurações sensíveis.  
- **ACM / KMS / HSM:** gestão de certificados TLS e chaves de assinatura (JWKs).

## Pipeline CI/CD

1. **Build e testes:** `mvn verify` + cobertura de testes + validações de estilo.  
2. **SCA/SAST:** varredura de dependências e código (ex.: OWASP Dependency Check, SonarQube).  
3. **Scan de imagens:** análise de vulnerabilidades nos contêineres gerados.  
4. **Deploy automatizado:** aplicação de manifests/Helm charts em EKS/ECS e execução de Flyway.  
5. **Smoke tests:** validação de saúde, fluxo Authorization Code + PKCE e endpoints críticos.  
6. **Publicação OpenAPI:** upload automático dos artefatos JSON/YAML para armazenamento versionado (S3, artefatos de pipeline).

## Operações rotineiras

- **Rotação de segredos:** programar rotação de certificados TLS, JWKs e segredos de banco a cada 90 dias ou menos.  
- **Exportação de realms:** agendar exportação dos realms Keycloak e armazenar em S3 com versionamento.  
- **Monitoramento:** utilizar Prometheus/Grafana e OpenTelemetry Collector para métricas e traces; configurar alertas críticos.  
- **Auditoria:** revisar tabelas `auditoria_eventos` e `auditoria_acessos` das APIs periodicamente; arquivar registros em storage seguro.  
- **Gestão de incidentes:** seguir runbooks, acionar comunicação e registrar lições aprendidas no pós-incidente.

## Procedimentos de emergência

- **Disaster Recovery:** gatilho para restauração em região secundária (backup de RDS + reimplantação de Keycloak).  
- **Comprometimento de chave:** revogar certificado no ACM/KMS, gerar novo par e atualizar a configuração nos serviços e na Cloudflare.  
- **Falha de autenticação:** verificar integridade do JWK endpoint (`/.well-known/jwks.json`), sincronização de relógios (NTP) e logs de auditoria.  
- **Rejeição FAPI:** validar configuração de PAR/JAR/JARM e certificados mTLS dos clientes confidenciais.

## Segurança operacional

- **Princípio do menor privilégio:** usuários e papéis IAM restritos à necessidade.  
- **Políticas de acesso Cloudflare:** whitelist de IPs e autenticação de operadores.  
- **Logging mascarado:** garantir que dados sensíveis permaneçam protegidos; mascaramento implementado nas APIs e no Keycloak.  
- **Reviews periódicos:** executar o `checklist-seguranca-fapi.md` durante janelas de manutenção e antes de releases.

## Handoff atual do Apple broker

O fluxo atual de `Sign in with Apple` do app `Thimisu` em produção depende da materialização destes segredos no runtime do Keycloak:

- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID=com.eickrono.thimisu.oidc.prd`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT=<jwt_gerado_localmente_com_a_key_principal>`

Fonte operacional local:

- [prod keycloak-apple.env](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/prod/keycloak-apple.env:1)

Pontos de controle depois do deploy:

- o Keycloak de produção precisa receber esses valores antes do `render-realms.sh` materializar o realm;
- o realm esperado é `eickrono`;
- o broker `apple` deve responder com `config.clientId = com.eickrono.thimisu.oidc.prd`;
- qualquer rotação futura do JWT deve reaproveitar a mesma key `Principal`, mudando apenas o token materializado na infraestrutura.

### Validação administrativa pós-deploy

Depois de o segredo ser materializado e o Keycloak ser reiniciado, execute dentro do runtime do Keycloak:

```bash
/opt/keycloak/bin/kcadm.sh config credentials \
  --config /tmp/kcadm-prod.config \
  --server http://localhost:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN" \
  --password "$KEYCLOAK_ADMIN_PASSWORD"

/opt/keycloak/bin/kcadm.sh get realms \
  --config /tmp/kcadm-prod.config

/opt/keycloak/bin/kcadm.sh get identity-provider/instances/apple \
  -r eickrono \
  --config /tmp/kcadm-prod.config
```

Validar manualmente:

- `eickrono` aparece na lista de realms;
- o broker retornado tem `alias = apple`;
- `enabled = true`;
- `config.clientId = com.eickrono.thimisu.oidc.prd`;
- `config.clientSecret` está mascarado.

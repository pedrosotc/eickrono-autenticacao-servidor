# Estrutura de Produção

Esta pasta deve conter os artefatos de infraestrutura usados em produção:

- `terraform/`: módulos e configurações IaC para AWS.  
- `cloudflare/`: scripts e templates de configuração Cloudflare (WAF, mTLS Origin Pull, Rate Limits).  
- `pipeline/`: definições de pipelines CI/CD (build, segurança, deploy e smoke tests).

Para o rollout dos brokers sociais no Keycloak 26.5.5, a infraestrutura de produção também precisa contemplar:

- materialização das credenciais `${KEYCLOAK_IDP_<APP>_*}` usadas nos exports dos realms;
- `--features=instagram-broker` no processo de inicialização do Keycloak, caso o broker `instagram` permaneça habilitado.

> Os arquivos sensíveis (segredos, estados de Terraform etc.) **não** devem ser versionados neste repositório.

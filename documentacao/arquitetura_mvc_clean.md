# Arquitetura MVC + Clean

Este repositório adota uma combinação entre MVC na borda HTTP e separação interna inspirada em Clean Architecture.

## Princípios

- `MVC` atende a camada web: controllers, requests e responses.
- `aplicacao` orquestra casos de uso e fluxos do sistema.
- `dominio` concentra entidades e regras de negócio.
- `infraestrutura` concentra integrações com Spring, segurança, JPA, Keycloak e detalhes técnicos.

## Convenção de pacotes

### APIs HTTP

Nos módulos `api-identidade-eickrono` e `api-contas-eickrono`, a convenção agora é:

- `apresentacao.api`: controllers Spring MVC
- `apresentacao.dto`: contratos HTTP
- `aplicacao.servico`: orquestração de casos de uso
- `dominio`: modelo de negócio
- `infraestrutura.configuracao`: segurança, JWT, CORS, Swagger e beans Spring

## Servidor de autorização

O módulo `servidor-autorizacao-eickrono` não expõe MVC HTTP próprio como as APIs. Ele passa a ser tratado como um módulo predominantemente de infraestrutura do ecossistema de autenticação.

Sua convenção principal é:

- `infraestrutura.credenciais`
- `infraestrutura.dispositivo`

## Observação importante

Alguns repositórios Spring Data continuam próximos do domínio legado por compatibilidade com a base existente. O alvo arquitetural é manter o núcleo de negócio independente e empurrar detalhes técnicos para a infraestrutura sempre que a evolução do módulo justificar essa extração.

# Especificação de avatar social por vínculo e avatar preferido por projeto

## Objetivo

Definir o desenho canônico para:

- gravar uma URL de foto própria para cada rede social vinculada à pessoa;
- permitir que o usuário escolha um avatar principal para uso dentro de um projeto/app da Eickrono;
- manter um cache local mínimo e estável no app, sem precisar persistir todas as redes sociais localmente na primeira versão.

Este documento passou a refletir a implementação da primeira entrega:

- migration única `V37__adicionar_avatar_social_e_avatar_preferido.sql`;
- DTOs e endpoints já expostos no servidor de identidade;
- cliente compartilhado e app Thimisu já ajustados para consumir o contrato;
- TDD dedicado em backend, cliente e app.

## Estado atual

Hoje o ecossistema não possui um modelo canônico de foto por rede social vinculada.

No servidor, antes da implementação:

- `public.vinculos_sociais` persiste apenas `provedor`, `identificador` e `vinculado_em`;
- `public.pessoas_formas_acesso` persiste apenas dados de acesso social, sem foto;
- `autenticacao.usuarios_formas_acesso` também não tem campos de nome/foto externa;
- `autenticacao.contextos_sociais_pendentes` guarda e-mail e nome externo, mas não guarda URL de foto.

No app, antes da implementação:

- a tela de contas vinculadas consome um snapshot remoto em memória;
- não existe tabela local persistindo todas as redes sociais vinculadas;
- existe apenas um campo `avatar` em `tb_contas_locais_dispositivo`, preenchido a partir das claims da sessão atual;
- esse `avatar` representa um único avatar efetivo da conta local, não uma coleção de fotos por provedor.

## Requisitos funcionais

1. Cada rede social vinculada deve poder guardar sua própria foto.
2. O usuário pode ter Google, Apple e outras redes vinculadas, cada uma com foto diferente.
3. O usuário pode escolher uma dessas fotos como avatar principal do projeto atual.
4. O usuário também pode, no futuro, escolher uma foto própria da aplicação, sem depender de rede social.
5. O app deve manter localmente apenas o avatar efetivo necessário para a experiência principal.
6. O fato de uma rede social ter foto não obriga essa foto a ser o avatar principal do projeto.

## Decisões de modelagem

### 1. Foto por rede social vinculada

A fonte canônica recomendada para a foto por rede social é `autenticacao.usuarios_formas_acesso`, porque ela já representa o vínculo multiapp de formas de acesso do usuário.

Campos propostos em `autenticacao.usuarios_formas_acesso`:

- `nome_exibicao_externo VARCHAR(255) NULL`
- `url_avatar_externo VARCHAR(2048) NULL`
- `avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE NULL`

Regras:

- esses campos só fazem sentido quando `tipo = 'SOCIAL'`;
- para `EMAIL`, `USUARIO` ou outros tipos locais, esses campos devem ficar nulos;
- a URL pode ser regravada quando o usuário reautenticar ou sincronizar o vínculo social;
- o `nome_exibicao_externo` é opcional, mas útil para UI e auditoria leve.

### 2. Avatar principal do usuário no projeto atual

O avatar principal não deve ser deduzido implicitamente da última rede usada. Ele deve ser uma preferência explícita do usuário no contexto do projeto/app.

A fonte recomendada para isso é `autenticacao.usuarios_clientes_ecossistema`, porque ela representa o vínculo do usuário com cada projeto do ecossistema.

Campos propostos em `autenticacao.usuarios_clientes_ecossistema`:

- `avatar_preferido_origem VARCHAR(32) NOT NULL`
- `avatar_preferido_forma_acesso_id UUID NULL REFERENCES autenticacao.usuarios_formas_acesso (id)`
- `avatar_preferido_url VARCHAR(2048) NULL`
- `avatar_preferido_atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL`
- `avatar_preferido_arquivo_id UUID NULL`

Valores sugeridos para `avatar_preferido_origem`:

- `SOCIAL`
- `UPLOAD_USUARIO`
- `URL_EXTERNA`
- `NENHUM`

Regras:

- quando `avatar_preferido_origem = 'SOCIAL'`, `avatar_preferido_forma_acesso_id` deve apontar para uma forma social do próprio usuário;
- `avatar_preferido_url` funciona como valor resolvido e pronto para consumo pelo app;
- `avatar_preferido_arquivo_id` fica reservado para a fase em que o usuário puder subir foto própria na aplicação;
- a preferência é por projeto, não global para todo o ecossistema.

### 3. Contexto social pendente

Quando o usuário entra por rede social sem vínculo final consumido, o contexto pendente já pode carregar a foto externa para melhorar a UX de:

- abrir cadastro com prefill;
- entrar e vincular;
- mostrar confirmação visual da conta social em processo.

Campos propostos em `autenticacao.contextos_sociais_pendentes`:

- `nome_exibicao_externo VARCHAR(255) NULL`
- `url_avatar_externo VARCHAR(2048) NULL`
- `avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE NULL`

Regra:

- esse dado é transitório e não substitui o vínculo final em `autenticacao.usuarios_formas_acesso`;
- serve apenas para enriquecer a jornada antes do consumo ou cancelamento do contexto.

## Modelo de banco implementado

### Migration única `V37`

A entrega atual consolidou em uma única migration:

- `vinculos_sociais`
- `pessoas_formas_acesso`
- `autenticacao.usuarios_formas_acesso`
- `autenticacao.usuarios_clientes_ecossistema`
- `autenticacao.contextos_sociais_pendentes`

DDL consolidado principal:

```sql
ALTER TABLE vinculos_sociais
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE pessoas_formas_acesso
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE autenticacao.usuarios_formas_acesso
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE autenticacao.usuarios_clientes_ecossistema
    ADD COLUMN avatar_preferido_origem VARCHAR(32) NOT NULL DEFAULT 'NENHUM',
    ADD COLUMN avatar_preferido_forma_acesso_id UUID
        REFERENCES autenticacao.usuarios_formas_acesso (id),
    ADD COLUMN avatar_preferido_url VARCHAR(2048),
    ADD COLUMN avatar_preferido_atualizado_em TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT NOW(),
    ADD COLUMN avatar_preferido_arquivo_id UUID;
```

Constraint sugerida:

```sql
ALTER TABLE autenticacao.usuarios_clientes_ecossistema
    ADD CONSTRAINT ck_usuarios_clientes_ecossistema_avatar_origem
    CHECK (avatar_preferido_origem IN ('SOCIAL', 'UPLOAD_USUARIO', 'URL_EXTERNA', 'NENHUM'));

ALTER TABLE autenticacao.contextos_sociais_pendentes
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;
```

## Compatibilidade com o legado

Enquanto o runtime ainda espelhar parte do domínio entre modelo legado e modelo multiapp, a sincronização deve seguir estas regras:

1. A gravação canônica da foto social nasce em `autenticacao.usuarios_formas_acesso`.
2. Se o legado continuar sendo usado por algum fluxo intermediário, os campos equivalentes podem ser replicados em:
   - `public.pessoas_formas_acesso`
   - ou `public.vinculos_sociais`
3. Essa replicação é transitória e não deve substituir a definição canônica do multiapp.

Recomendação:

- evitar criar duas fontes canônicas de avatar social;
- usar o legado apenas como espelho temporário, se ainda houver fluxo que dependa dele.

## Contratos de API recomendados

### 1. Leitura de vínculos sociais

A resposta de vínculos sociais deve incluir:

- `provedor`
- `suportado`
- `vinculado`
- `vinculadoEm`
- `identificadorMascarado`
- `nomeExibicaoExterno`
- `urlAvatarExterno`
- `avatarExternoAtualizadoEm`
- `avatarPrincipalNoProjeto`

Exemplo:

```json
{
  "provedor": "google",
  "suportado": true,
  "vinculado": true,
  "vinculadoEm": "2026-04-30T10:15:00Z",
  "identificadorMascarado": "jo***@gmail.com",
  "nomeExibicaoExterno": "Thiago C",
  "urlAvatarExterno": "https://lh3.googleusercontent.com/...",
  "avatarExternoAtualizadoEm": "2026-04-30T10:15:00Z",
  "avatarPrincipalNoProjeto": true
}
```

### 2. Seleção do avatar principal

Endpoint implementado:

- `PUT /identidade/vinculos-sociais/avatar-preferido`

Corpo para escolher avatar de rede social:

```json
{
  "origem": "SOCIAL",
  "provedor": "google"
}
```

Corpo para escolher URL externa já resolvida:

```json
{
  "origem": "URL_EXTERNA",
  "url": "https://cdn.eickrono.com/avatars/usuario-123.png"
}
```

Corpo para limpar escolha explícita:

```json
{
  "origem": "NENHUM"
}
```

Semântica:

- `SOCIAL` resolve a `forma_acesso` do provedor dentro do usuário autenticado;
- o servidor grava a preferência no vínculo do usuário com o projeto atual;
- a resposta pode devolver o `avatarUrlEfetivo` já resolvido.

### 3. Resposta do contexto autenticado

Nesta entrega, o app resolve o avatar efetivo a partir de `GET /identidade/vinculos-sociais?aplicacaoId=...`.

Em evolução futura, quando houver contexto autenticado enriquecido, a resposta pode expor:

- `avatarUrlEfetivo`
- `avatarOrigemEfetiva`

Isso evita que o app precise remontar prioridade localmente.

## Cache local no app

### Objetivo da primeira versão

Na primeira versão, o app não precisa persistir localmente todas as fotos de todas as redes sociais.

O mínimo recomendado é:

- continuar usando `tb_contas_locais_dispositivo.avatar` como cache do avatar efetivo da conta local;
- persistir apenas a URL efetiva já resolvida para aquele projeto;
- continuar consultando a lista de vínculos sociais do backend quando a tela específica de contas vinculadas for aberta.

### Evolução local opcional

Se o app precisar exibir a lista de redes sociais com foto em modo offline ou em reabertura rápida, pode ser criada uma tabela adicional:

- `tb_contas_locais_avatares_sociais`

Campos sugeridos:

- `conta_local_id TEXT NOT NULL`
- `provedor TEXT NOT NULL`
- `nome_exibicao_externo TEXT NULL`
- `url_avatar_externo TEXT NULL`
- `avatar_principal_no_projeto BOOLEAN NOT NULL DEFAULT FALSE`
- `atualizado_em DATETIME NOT NULL`

Recomendação:

- não criar essa tabela na primeira versão se a UX principal só precisar do avatar efetivo.

## Estratégia de resolução de avatar

Ordem sugerida para o avatar exibido no app:

1. `avatar_preferido_url` do vínculo do usuário com o projeto atual
2. se a origem for `SOCIAL`, `url_avatar_externo` da forma de acesso social escolhida
3. se existir upload próprio da aplicação, URL estável do arquivo interno
4. avatar já cacheado em `tb_contas_locais_dispositivo.avatar`
5. fallback visual para iniciais

## Risco de usar URL remota de rede social

Uma URL social externa pode:

- expirar;
- ser rotacionada pelo provedor;
- mudar após o usuário trocar foto na rede social;
- ser removida sem aviso.

Por isso, o plano recomendado é:

- fase 1: salvar a URL remota por vínculo social;
- fase 2: quando o usuário escolher aquela foto como avatar principal, opcionalmente copiar a imagem para uma mídia própria da Eickrono e gravar uma URL estável em `avatar_preferido_url`.

## Cobertura de testes

Backend:

- [VinculoSocialServiceTest.java](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/aplicacao/servico/VinculoSocialServiceTest.java:1)
- [VinculosSociaisControllerIT.java](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/apresentacao/api/VinculosSociaisControllerIT.java:1)
- [RegistroDispositivoControllerIT.java](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/apresentacao/api/RegistroDispositivoControllerIT.java:1)

Cliente compartilhado:

- [cliente_api_identidade_eickrono_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-cliente/test/cliente_api_identidade_eickrono_test.dart:1)

App:

- [controlador_contas_vinculadas_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/test/funcionalidades/autenticacao/aplicacao/controlador_contas_vinculadas_test.dart:1)
- [pagina_contas_vinculadas_widget_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/test/funcionalidades/autenticacao/apresentacao/pagina_contas_vinculadas_widget_test.dart:1)
- [catalogo_local_contas_drift_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/test/infraestrutura/autenticacao/catalogo_local_contas_drift_test.dart:1)

## Decisões ainda abertas

1. O nome da origem deve ser `SOCIAL` ou mais granular, como `SOCIAL_GOOGLE`, `SOCIAL_APPLE`, etc.?
2. O avatar principal deve ser sempre por projeto ou pode existir também uma preferência global futura?
3. O upload de foto própria será suportado já na primeira versão ou só em fase posterior?
4. Quando o usuário escolher uma foto social como principal, a plataforma vai apenas usar a URL remota ou copiar a imagem para uma mídia própria da Eickrono?

## Conclusão

O desenho recomendado separa corretamente três coisas diferentes:

- a foto de cada rede social vinculada;
- a escolha de qual foto é a principal no projeto atual;
- o cache local mínimo necessário no dispositivo.

Isso evita confundir:

- identidade federada do provedor;
- preferência visual do usuário no app;
- e persistência local do dispositivo.

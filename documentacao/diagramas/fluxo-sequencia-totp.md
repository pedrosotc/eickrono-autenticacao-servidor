# Diagrama de sequência – TOTP no aplicativo e no Keycloak

> Status deste documento: **canônico no seu escopo**.
>
> Este documento descreve a jornada funcional e tecnica do TOTP na superficie
> publicada.
>
> Ele nao detalha ownership interno de `Pessoa`, `PerfilSistema` ou separacao
> entre servicos alem do necessario para o fluxo de segundo fator.

Este documento descreve o fluxo teórico completo do TOTP na implementação única discutida para a Eickrono.

Premissa principal de UX:

- na área autenticada, o TOTP deve viver em uma única tela: `Segurança da conta`;
- o que muda não são várias telas independentes, e sim os estados da seção de TOTP dentro dessa mesma tela;
- telas separadas só fazem sentido para os fluxos fora da área autenticada, como `Login`, `Segundo fator` e `Recuperar acesso ao TOTP`.

Todos os diagramas usam sintaxe Mermaid.

## Superfícies necessárias

### Área autenticada

- `Tela Segurança da conta`
  - estado `sem TOTP`
  - estado `provisionamento`
  - estado `configuração manual expandida`
  - estado `validação do código`
  - estado `exibição dos códigos de recuperação`
  - estado `TOTP ativo`

### Área não autenticada

- `Tela Login`
- `Tela Segundo fator`
- `Tela Usar código de recuperação`
- `Tela Recuperar acesso ao TOTP`

Observações:

- `Configuração manual` não precisa ser uma tela; ela deve ser um bloco expandível dentro de `Segurança da conta`.
- `Validação do código` pode ser uma troca de estado da mesma seção, sem sair da mesma tela.
- `Códigos de recuperação` podem aparecer como etapa final dentro da mesma tela ou em modal dedicado, mas ainda como continuação do mesmo fluxo.
- no iOS, o estado de provisionamento pode mostrar `Adicionar ao app Senhas`;
- nas demais plataformas, o contrato principal continua sendo `QR + chave manual`.

## Fluxo 1 – Área autenticada: uma única tela com estados da seção TOTP

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant TelaSeg as Tela Segurança da conta
    participant APII as API Identidade
    participant DBI as PostgreSQL (identidade)
    participant SA as Servidor de autenticação (Keycloak + SPI)

    U->>TelaSeg: Abre Segurança da conta
    TelaSeg->>APII: GET /identidade/seguranca/politica
    APII->>DBI: Consultar verificações de e-mail e telefone
    APII->>DBI: Consultar catálogo local de fatores
    APII->>SA: GET /interno/clientes/{clientId}/politica-autenticacao
    APII->>SA: GET /interno/totp/usuarios/{userId}/status
    SA-->>APII: modoTOTP + statusTOTP
    APII-->>TelaSeg: contaLiberada, modoTOTP, totpHabilitado

    alt Conta não liberada
        TelaSeg-->>U: Bloquear ativação e orientar verificação pendente
    else Cliente com TOTP desabilitado
        TelaSeg-->>U: Não mostrar a seção de TOTP
    else Cliente com TOTP opcional e usuário sem TOTP
        TelaSeg-->>U: Mostrar a seção de TOTP no estado sem TOTP
        U->>TelaSeg: Toca em Ativar verificação em duas etapas
        TelaSeg->>APII: POST /identidade/fatores/totp/provisionamento
        APII->>DBI: Validar elegibilidade e reautenticação
        APII->>SA: POST /interno/totp/provisionamentos
        SA->>SA: Gerar segredo temporário
        SA-->>APII: provisionamentoId, issuer, accountName, secretBase32, otpauthUri
        APII->>DBI: Registrar fator TOTP como PENDENTE
        APII-->>TelaSeg: Dados do provisionamento
        TelaSeg->>TelaSeg: Trocar a seção para o estado provisionamento
    else Cliente com TOTP obrigatório e usuário sem TOTP
        TelaSeg-->>U: Mostrar a seção em modo bloqueante no estado sem TOTP
        U->>TelaSeg: Inicia a ativação obrigatória
        TelaSeg->>APII: POST /identidade/fatores/totp/provisionamento
        APII->>SA: POST /interno/totp/provisionamentos
        SA-->>APII: Dados do provisionamento
        APII-->>TelaSeg: Dados do provisionamento
        TelaSeg->>TelaSeg: Trocar a seção para o estado provisionamento
    else Usuário já possui TOTP ativo
        TelaSeg->>TelaSeg: Mostrar a seção no estado TOTP ativo
    end

    alt iOS no mesmo dispositivo
        U->>TelaSeg: Toca em Adicionar ao app Senhas
        TelaSeg->>TelaSeg: Abrir apple-otpauth://...
    else Autenticador compatível disponível
        U->>TelaSeg: Toca em Abrir autenticador compatível
        TelaSeg->>TelaSeg: Tentar abrir otpauth://...
    else Outro dispositivo
        U->>TelaSeg: Escaneia o QR com outro aparelho
    end

    opt Configuração manual
        U->>TelaSeg: Expande Configuração manual
        TelaSeg->>TelaSeg: Trocar a seção para o estado configuração manual expandida
        TelaSeg-->>U: Mostrar Serviço, Conta e Chave Base32
        U->>TelaSeg: Toca em Copiar chave
    end

    U->>TelaSeg: Toca em Continuar para validar
    TelaSeg->>TelaSeg: Trocar a seção para o estado validação do código
    U->>TelaSeg: Digita o código de 6 dígitos
    TelaSeg->>APII: POST /identidade/fatores/totp/confirmacao
    APII->>SA: POST /interno/totp/provisionamentos/{id}/confirmacao
    SA->>SA: Validar o código
    SA->>SA: Persistir credencial OTP definitiva
    SA->>SA: Gerar recovery codes

    alt Código válido
        SA-->>APII: TOTP ativo + recoveryCodes
        APII->>DBI: Marcar fator TOTP como ATIVO
        APII-->>TelaSeg: Recovery codes
        TelaSeg->>TelaSeg: Trocar a seção para o estado exibição dos códigos de recuperação
        U->>TelaSeg: Confirma que armazenou os códigos
        TelaSeg->>TelaSeg: Trocar a seção para o estado TOTP ativo
        TelaSeg-->>U: Mostrar TOTP ativo
    else Código inválido
        SA-->>APII: Erro TOTP_INVALIDO
        APII-->>TelaSeg: Erro específico
        TelaSeg->>TelaSeg: Manter a seção no estado validação do código
    else Provisionamento expirado
        SA-->>APII: Erro PROVISIONAMENTO_EXPIRADO
        APII-->>TelaSeg: Solicitar novo provisionamento
        TelaSeg->>TelaSeg: Voltar a seção ao estado sem TOTP
    end
```

## Fluxo 2 – Login e segunda etapa fora da área autenticada

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant TelaLogin as Tela Login
    participant Tela2F as Tela Segundo fator
    participant TelaRec as Tela Usar código de recuperação
    participant APII as API Identidade
    participant DBI as PostgreSQL (identidade)
    participant SA as Servidor de autenticação (Keycloak + SPI)

    U->>TelaLogin: Informa usuário e senha
    TelaLogin->>APII: POST /api/publica/sessoes (username, password)
    APII->>SA: grant_type=password com username e password

    alt Usuário sem TOTP e política opcional
        SA-->>APII: Tokens da sessão
        APII->>DBI: Registrar auditoria e contexto do dispositivo
        APII-->>TelaLogin: Sessão autenticada
    else Usuário com TOTP ativo ou cliente com TOTP obrigatório
        SA-->>APII: Resposta sem TOTP
        APII-->>Tela2F: SEGUNDO_FATOR_OBRIGATORIO
        U->>Tela2F: Digita TOTP
        Tela2F->>APII: POST /api/publica/sessoes (username, password, totp)
        APII->>SA: grant_type=password com username, password e otp/totp

        alt TOTP válido
            SA-->>APII: Tokens da sessão
            APII->>DBI: Registrar auditoria e contexto do dispositivo
            APII-->>Tela2F: Sessão autenticada
        else TOTP inválido
            SA-->>APII: Erro TOTP_INVALIDO
            APII-->>Tela2F: Exibir erro específico
        else Usuário escolhe recovery code
            U->>Tela2F: Toca em Usar código de recuperação
            Tela2F->>TelaRec: Navega para o fallback
            U->>TelaRec: Digita recovery code
            TelaRec->>APII: POST /api/publica/sessoes/recuperacao ou payload equivalente
            APII->>SA: Validar recovery code

            alt Recovery code válido
                SA-->>APII: Tokens da sessão
                APII->>DBI: Registrar auditoria do uso do recovery code
                APII-->>TelaRec: Sessão autenticada
            else Recovery code inválido
                SA-->>APII: Erro CODIGO_RECUPERACAO_INVALIDO
                APII-->>TelaRec: Exibir erro específico
            end
        end
    end
```

## Fluxo 3 – Desativação ou reconfiguração dentro da mesma tela Segurança da conta

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant TelaSeg as Tela Segurança da conta
    participant APII as API Identidade
    participant DBI as PostgreSQL (identidade)
    participant SA as Servidor de autenticação (Keycloak + SPI)

    U->>TelaSeg: Toca em Desativar ou Reconfigurar TOTP
    TelaSeg->>APII: DELETE /identidade/fatores/totp
    APII->>DBI: Validar reautenticação recente
    APII->>SA: DELETE /interno/totp/usuarios/{userId}
    SA->>SA: Remover credencial OTP
    SA->>SA: Invalidar recovery codes
    APII->>DBI: Marcar fator como INATIVO

    alt Política do cliente = OBRIGATORIO
        APII-->>TelaSeg: Exigir novo provisionamento
        TelaSeg->>TelaSeg: Voltar a seção para o estado sem TOTP bloqueante
    else Política do cliente = OPCIONAL
        APII-->>TelaSeg: Atualizar status
        TelaSeg->>TelaSeg: Voltar a seção para o estado sem TOTP
    end
```

## Fluxo 4 – Recuperação controlada quando a pessoa perdeu o autenticador

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant TelaLogin as Tela Login
    participant TelaRecTOTP as Tela Recuperar acesso ao TOTP
    participant APII as API Identidade
    participant DBI as PostgreSQL (identidade)
    participant Email as Provedor de e-mail
    participant SMS as Provedor SMS
    participant SA as Servidor de autenticação (Keycloak + SPI)

    U->>TelaLogin: Toca em Perdi meu autenticador
    TelaLogin->>TelaRecTOTP: Abre fluxo controlado
    TelaRecTOTP->>APII: POST /identidade/recuperacao/totp/solicitacao
    APII->>DBI: Validar conta e política
    APII->>Email: Enviar código de verificação por e-mail

    opt Telefone exigido pela política
        APII->>SMS: Enviar código de verificação por SMS
    end

    APII-->>TelaRecTOTP: Desafio criado
    U->>TelaRecTOTP: Informa os códigos recebidos
    TelaRecTOTP->>APII: POST /identidade/recuperacao/totp/confirmacao
    APII->>DBI: Validar códigos, tentativas e expiração
    APII->>SA: DELETE /interno/totp/usuarios/{userId}
    SA->>SA: Remover credencial OTP e invalidar recovery codes
    APII->>DBI: Marcar fator TOTP como INATIVO
    APII->>DBI: Registrar auditoria do reset
    APII-->>TelaRecTOTP: Recuperação concluída
    TelaRecTOTP-->>TelaLogin: Redirecionar para novo login
```

## Resumo de modelagem de UX

- `Segurança da conta` é a única tela autenticada necessária para ativação e gestão do TOTP.
- dentro dela, a seção de TOTP troca de estado ao longo do fluxo.
- `Configuração manual` não é tela; é expansão.
- `Validação do código` não precisa ser tela; pode ser o próximo estado da mesma seção.
- `Códigos de recuperação` podem ser a etapa final do mesmo fluxo, sem criar uma área paralela.
- telas separadas ficam reservadas para login, segundo fator e recuperação de acesso.

## Resumo técnico do que precisa existir

- política do cliente com `DESABILITADO | OPCIONAL | OBRIGATORIO`;
- consulta autenticada de política e status para montar `Segurança da conta`;
- provisionamento TOTP com segredo temporário;
- confirmação posterior com código de 6 dígitos;
- persistência definitiva do segredo apenas no Keycloak;
- catálogo local de fatores sem replicar segredo TOTP;
- recovery codes já na primeira entrega;
- evolução de `POST /api/publica/sessoes` para aceitar `totp`;
- erros semânticos para `SEGUNDO_FATOR_OBRIGATORIO`, `TOTP_INVALIDO` e `CODIGO_RECUPERACAO_INVALIDO`;
- reset controlado quando a pessoa perder o autenticador.

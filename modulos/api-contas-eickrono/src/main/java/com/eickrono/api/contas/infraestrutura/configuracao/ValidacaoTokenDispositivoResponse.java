package com.eickrono.api.contas.infraestrutura.configuracao;

import java.time.OffsetDateTime;

/**
 * Payload retornado pela API de Identidade ao validar um token de dispositivo.
 */
public record ValidacaoTokenDispositivoResponse(
        boolean valido,
        String codigo,
        String mensagem,
        OffsetDateTime expiraEm) {
}

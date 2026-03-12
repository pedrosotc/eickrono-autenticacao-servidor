package com.eickrono.api.contas.apresentacao.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resumo de conta retornado nos endpoints públicos.
 */
public record ContaResumoDto(
        Long id,
        String numero,
        BigDecimal saldo,
        OffsetDateTime atualizadaEm) {
}

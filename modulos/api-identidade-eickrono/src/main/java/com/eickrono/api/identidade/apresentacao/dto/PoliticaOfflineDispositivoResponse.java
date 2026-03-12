package com.eickrono.api.identidade.apresentacao.dto;

import java.util.List;

/**
 * Política central de uso offline publicada pelo backend.
 */
public record PoliticaOfflineDispositivoResponse(
        boolean permitido,
        long tempoMaximoMinutos,
        boolean exigeReconciliacao,
        List<String> condicoesBloqueio
) {
    public PoliticaOfflineDispositivoResponse {
        condicoesBloqueio = condicoesBloqueio == null ? List.of() : List.copyOf(condicoesBloqueio);
    }
}

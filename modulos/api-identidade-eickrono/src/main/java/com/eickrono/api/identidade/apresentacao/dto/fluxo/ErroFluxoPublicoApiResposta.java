package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import java.util.Map;

public record ErroFluxoPublicoApiResposta(
        String codigo,
        String mensagem,
        Map<String, Object> detalhes
) {
}

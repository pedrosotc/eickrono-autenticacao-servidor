package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.List;

public record ValidacaoOficialAtestacaoAppResultado(
        boolean executada,
        String resumo,
        String appRecognitionVerdict,
        String appLicensingVerdict,
        List<String> deviceRecognitionVerdict
) {

    public static ValidacaoOficialAtestacaoAppResultado naoExecutada(final String resumo) {
        return new ValidacaoOficialAtestacaoAppResultado(false, resumo, null, null, List.of());
    }
}

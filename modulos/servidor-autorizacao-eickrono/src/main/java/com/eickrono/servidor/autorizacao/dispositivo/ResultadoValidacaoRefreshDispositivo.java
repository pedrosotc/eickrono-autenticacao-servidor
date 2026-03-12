package com.eickrono.servidor.autorizacao.dispositivo;

/**
 * Resultado interno da validacao de confianca do dispositivo no refresh.
 */
public record ResultadoValidacaoRefreshDispositivo(
        boolean valido,
        String codigo,
        String mensagem) {
}

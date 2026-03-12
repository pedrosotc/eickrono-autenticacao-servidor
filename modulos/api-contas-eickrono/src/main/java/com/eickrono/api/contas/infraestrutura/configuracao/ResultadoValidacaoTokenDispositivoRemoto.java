package com.eickrono.api.contas.infraestrutura.configuracao;

/**
 * Resultado da validacao remota de token de dispositivo.
 */
public record ResultadoValidacaoTokenDispositivoRemoto(
        int statusHttp,
        ValidacaoTokenDispositivoResponse payload) {
}

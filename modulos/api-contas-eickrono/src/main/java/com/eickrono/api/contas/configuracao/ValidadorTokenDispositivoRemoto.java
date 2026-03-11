package com.eickrono.api.contas.configuracao;

/**
 * Porta para validacao remota do token de dispositivo.
 */
public interface ValidadorTokenDispositivoRemoto {

    ResultadoValidacaoTokenDispositivoRemoto validar(String authorizationHeader, String tokenDispositivo);
}

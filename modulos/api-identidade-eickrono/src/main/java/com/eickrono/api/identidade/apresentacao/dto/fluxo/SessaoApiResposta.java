package com.eickrono.api.identidade.apresentacao.dto.fluxo;

public record SessaoApiResposta(
        boolean autenticado,
        String tipoToken,
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenDispositivo,
        java.time.OffsetDateTime tokenDispositivoExpiraEm,
        String statusUsuario,
        boolean primeiraSessao,
        boolean podeOferecerBiometria,
        boolean podeOferecerVinculacaoSocial
) {
}

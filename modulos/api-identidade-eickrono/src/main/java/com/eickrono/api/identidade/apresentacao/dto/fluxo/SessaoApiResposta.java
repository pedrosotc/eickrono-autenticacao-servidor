package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import com.fasterxml.jackson.annotation.JsonProperty;

// Mantém statusUsuario apenas como compatibilidade de JSON; internamente o contrato já trata statusPerfilSistema.
public record SessaoApiResposta(
        boolean autenticado,
        String tipoToken,
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenDispositivo,
        java.time.OffsetDateTime tokenDispositivoExpiraEm,
        @JsonProperty("statusUsuario") String statusPerfilSistema,
        boolean primeiraSessao,
        boolean podeOferecerBiometria,
        boolean podeOferecerVinculacaoSocial
) {
}

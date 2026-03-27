package com.eickrono.api.identidade.apresentacao.dto.fluxo;

public record CadastroApiResposta(
        String cadastroId,
        String usuarioId,
        String statusUsuario,
        String emailPrincipal,
        String telefonePrincipal,
        boolean verificacaoEmailObrigatoria,
        String proximoPasso
) {
}

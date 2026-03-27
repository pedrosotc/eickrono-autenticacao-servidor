package com.eickrono.api.identidade.apresentacao.dto.fluxo;

public record ConfirmacaoEmailCadastroApiResposta(
        String cadastroId,
        String usuarioId,
        String statusUsuario,
        String emailPrincipal,
        boolean emailConfirmado,
        boolean liberadoParaLogin,
        String proximoPasso
) {
}

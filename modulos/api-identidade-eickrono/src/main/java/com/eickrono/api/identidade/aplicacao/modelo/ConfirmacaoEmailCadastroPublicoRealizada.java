package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.UUID;

public record ConfirmacaoEmailCadastroPublicoRealizada(
        UUID cadastroId,
        String subjectRemoto,
        String emailPrincipal,
        String usuarioId,
        String statusUsuario,
        boolean emailConfirmado,
        boolean podeAutenticar
) {
}

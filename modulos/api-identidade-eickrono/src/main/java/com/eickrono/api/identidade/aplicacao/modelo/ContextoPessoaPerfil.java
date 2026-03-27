package com.eickrono.api.identidade.aplicacao.modelo;

public record ContextoPessoaPerfil(
        Long pessoaId,
        String sub,
        String emailPrincipal,
        String nome,
        String usuarioId,
        String statusUsuario
) {
}

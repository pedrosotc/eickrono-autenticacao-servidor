package com.eickrono.api.identidade.aplicacao.modelo;

public record ProvisionamentoPerfilRealizado(
        Long pessoaId,
        String usuarioId,
        String statusUsuario
) {
}

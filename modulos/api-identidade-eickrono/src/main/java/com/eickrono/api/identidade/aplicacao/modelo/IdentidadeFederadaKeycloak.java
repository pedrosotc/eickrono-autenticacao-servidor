package com.eickrono.api.identidade.aplicacao.modelo;

import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import java.util.Objects;

/**
 * Representa uma identidade federada vinculada ao usuário no Keycloak.
 */
public record IdentidadeFederadaKeycloak(
        ProvedorVinculoSocial provedor,
        String identificadorExterno,
        String nomeUsuarioExterno) {

    public IdentidadeFederadaKeycloak {
        Objects.requireNonNull(provedor, "provedor é obrigatório");
    }

    public String identificadorCanonico() {
        if (identificadorExterno != null && !identificadorExterno.isBlank()) {
            return identificadorExterno.trim();
        }
        if (nomeUsuarioExterno != null && !nomeUsuarioExterno.isBlank()) {
            return nomeUsuarioExterno.trim();
        }
        throw new IllegalStateException("Identidade federada sem identificador utilizável");
    }

    public String identificadorExibicao() {
        if (nomeUsuarioExterno != null && !nomeUsuarioExterno.isBlank()) {
            return nomeUsuarioExterno.trim();
        }
        return identificadorCanonico();
    }
}

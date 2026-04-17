package com.eickrono.api.identidade.dominio.modelo;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Provedores sociais suportados pela API de identidade.
 */
public enum ProvedorVinculoSocial {
    GOOGLE("google"),
    APPLE("apple"),
    FACEBOOK("facebook"),
    LINKEDIN("linkedin"),
    INSTAGRAM("instagram");

    private final String aliasApi;

    ProvedorVinculoSocial(final String aliasApi) {
        this.aliasApi = aliasApi;
    }

    public String getAliasApi() {
        return aliasApi;
    }

    public String getAliasFormaAcesso() {
        return aliasApi.toUpperCase(Locale.ROOT);
    }

    public static Optional<ProvedorVinculoSocial> fromAlias(final String alias) {
        if (alias == null || alias.isBlank()) {
            return Optional.empty();
        }
        String aliasNormalizado = alias.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(provedor -> provedor.aliasApi.equals(aliasNormalizado))
                .findFirst();
    }
}

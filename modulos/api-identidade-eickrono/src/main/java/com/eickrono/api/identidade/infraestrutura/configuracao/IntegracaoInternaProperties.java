package com.eickrono.api.identidade.infraestrutura.configuracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracoes de integracao interna entre os servicos do ecossistema.
 */
@ConfigurationProperties(prefix = "integracao.interna")
public class IntegracaoInternaProperties {

    private String segredo = "local-internal-secret";

    public String getSegredo() {
        return segredo;
    }

    public void setSegredo(String segredo) {
        this.segredo = segredo;
    }
}

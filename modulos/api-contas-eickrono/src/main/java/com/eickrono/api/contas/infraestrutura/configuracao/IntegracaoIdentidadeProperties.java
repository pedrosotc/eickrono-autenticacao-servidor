package com.eickrono.api.contas.infraestrutura.configuracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracoes de integracao da API de Contas com a API de Identidade.
 */
@ConfigurationProperties(prefix = "integracao.identidade")
public class IntegracaoIdentidadeProperties {

    private String urlBase = "http://localhost:8081";

    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(String urlBase) {
        this.urlBase = urlBase;
    }
}

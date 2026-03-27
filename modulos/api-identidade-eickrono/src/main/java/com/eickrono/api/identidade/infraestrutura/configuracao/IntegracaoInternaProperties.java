package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracoes de integracao interna entre os servicos do ecossistema.
 */
@ConfigurationProperties(prefix = "integracao.interna")
public class IntegracaoInternaProperties {

    private String segredo = "local-internal-secret";
    private List<String> clientesPermitidos = new ArrayList<>(
            List.of("flashcard-servidor-interno", "servidor-autorizacao-interno"));

    public String getSegredo() {
        return segredo;
    }

    public void setSegredo(String segredo) {
        this.segredo = segredo;
    }

    public List<String> getClientesPermitidos() {
        return clientesPermitidos;
    }

    public void setClientesPermitidos(final List<String> clientesPermitidos) {
        this.clientesPermitidos = clientesPermitidos == null ? new ArrayList<>() : new ArrayList<>(clientesPermitidos);
    }
}

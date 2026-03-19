package com.eickrono.api.identidade.infraestrutura.configuracao;

import com.eickrono.api.identidade.infraestrutura.atestacao.GooglePlayIntegrityProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do fluxo de desafio e atestação nativa do app.
 */
@ConfigurationProperties(prefix = "identidade.atestacao.app")
public class AtestacaoAppProperties {

    private Duration duracaoDesafio = Duration.ofMinutes(5);
    private String numeroProjetoNuvemAndroid;
    private boolean permitirValidacaoLocalSemProvedorOficial = true;
    private GooglePlayIntegrityProperties google = new GooglePlayIntegrityProperties();

    public Duration getDuracaoDesafio() {
        return duracaoDesafio;
    }

    public void setDuracaoDesafio(final Duration duracaoDesafio) {
        this.duracaoDesafio = duracaoDesafio;
    }

    public String getNumeroProjetoNuvemAndroid() {
        return numeroProjetoNuvemAndroid;
    }

    public void setNumeroProjetoNuvemAndroid(final String numeroProjetoNuvemAndroid) {
        this.numeroProjetoNuvemAndroid = numeroProjetoNuvemAndroid;
    }

    public boolean isPermitirValidacaoLocalSemProvedorOficial() {
        return permitirValidacaoLocalSemProvedorOficial;
    }

    public void setPermitirValidacaoLocalSemProvedorOficial(final boolean permitirValidacaoLocalSemProvedorOficial) {
        this.permitirValidacaoLocalSemProvedorOficial = permitirValidacaoLocalSemProvedorOficial;
    }

    public GooglePlayIntegrityProperties getGoogle() {
        return google;
    }

    public void setGoogle(final GooglePlayIntegrityProperties google) {
        this.google = google;
    }
}

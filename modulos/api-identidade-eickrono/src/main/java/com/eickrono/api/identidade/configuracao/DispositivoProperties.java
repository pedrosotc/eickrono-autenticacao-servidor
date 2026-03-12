package com.eickrono.api.identidade.configuracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades relacionadas ao fluxo de registro de dispositivos.
 */
@ConfigurationProperties(prefix = "identidade.dispositivo")
public class DispositivoProperties {

    private final Token token = new Token();
    private final Codigo codigo = new Codigo();
    private final Onboarding onboarding = new Onboarding();

    public Token getToken() {
        return token;
    }

    public Codigo getCodigo() {
        return codigo;
    }

    public Onboarding getOnboarding() {
        return onboarding;
    }

    public static class Token {
        /**
         * Segredo usado para assinar o token (HMAC).
         */
        private String segredoHmac = "change-me-token";
        /**
         * Tamanho do token em bytes antes de codificar em Base64.
         */
        private int tamanhoBytes = 32;
        /**
         * Validade padrão do token em horas.
         */
        private long validadeHoras = 720;

        public String getSegredoHmac() {
            return segredoHmac;
        }

        public void setSegredoHmac(String segredoHmac) {
            this.segredoHmac = segredoHmac;
        }

        public int getTamanhoBytes() {
            return tamanhoBytes;
        }

        public void setTamanhoBytes(int tamanhoBytes) {
            this.tamanhoBytes = tamanhoBytes;
        }

        public long getValidadeHoras() {
            return validadeHoras;
        }

        public void setValidadeHoras(long validadeHoras) {
            this.validadeHoras = validadeHoras;
        }
    }

    public static class Codigo {
        /**
         * Segredo usado para gerar o hash dos códigos (HMAC).
         */
        private String segredoHmac = "change-me-codigo";
        private int tamanho = 6;
        private int tentativasMaximas = 5;
        private int reenviosMaximos = 3;
        private long expiracaoHoras = 9;

        public String getSegredoHmac() {
            return segredoHmac;
        }

        public void setSegredoHmac(String segredoHmac) {
            this.segredoHmac = segredoHmac;
        }

        public int getTamanho() {
            return tamanho;
        }

        public void setTamanho(int tamanho) {
            this.tamanho = tamanho;
        }

        public int getTentativasMaximas() {
            return tentativasMaximas;
        }

        public void setTentativasMaximas(int tentativasMaximas) {
            this.tentativasMaximas = tentativasMaximas;
        }

        public int getReenviosMaximos() {
            return reenviosMaximos;
        }

        public void setReenviosMaximos(int reenviosMaximos) {
            this.reenviosMaximos = reenviosMaximos;
        }

        public long getExpiracaoHoras() {
            return expiracaoHoras;
        }

        public void setExpiracaoHoras(long expiracaoHoras) {
            this.expiracaoHoras = expiracaoHoras;
        }
    }

    public static class Onboarding {
        private boolean smsHabilitado = false;
        private String smsFornecedor = "log";

        public boolean isSmsHabilitado() {
            return smsHabilitado;
        }

        public void setSmsHabilitado(boolean smsHabilitado) {
            this.smsHabilitado = smsHabilitado;
        }

        public String getSmsFornecedor() {
            return smsFornecedor;
        }

        public void setSmsFornecedor(String smsFornecedor) {
            this.smsFornecedor = smsFornecedor;
        }
    }
}

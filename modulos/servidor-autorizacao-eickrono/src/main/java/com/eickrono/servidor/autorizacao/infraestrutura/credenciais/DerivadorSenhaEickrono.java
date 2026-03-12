package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import org.keycloak.models.UserModel;

/**
 * Deriva a senha efetiva usada pelo Keycloak a partir da senha informada,
 * do pepper do ambiente e da data de nascimento do usuario.
 */
public final class DerivadorSenhaEickrono {

    public static final String ENV_PASSWORD_PEPPER = "EICKRONO_PASSWORD_PEPPER";
    public static final String ATRIBUTO_DATA_NASCIMENTO = "data_nascimento";
    public static final String ATRIBUTO_DATA_NASCIMENTO_FALLBACK = "birthdate";

    private DerivadorSenhaEickrono() {
    }

    public static String derivarParaUsuario(UserModel user, String senhaPura) {
        String dataNascimento = obterDataNascimento(user);
        return derivar(senhaPura, dataNascimento);
    }

    public static String derivarParaFormulario(UserModel user, MultivaluedMap<String, String> formData, String senhaPura) {
        String dataNascimento = obterDataNascimento(formData);
        if (dataNascimento == null || dataNascimento.isBlank()) {
            dataNascimento = obterDataNascimento(user);
        }
        return derivar(senhaPura, dataNascimento);
    }

    public static String derivar(String senhaPura, String dataNascimento) {
        String pepper = System.getenv(ENV_PASSWORD_PEPPER);
        return derivar(senhaPura, dataNascimento, pepper);
    }

    static String derivar(String senhaPura, String dataNascimento, String pepper) {
        if (senhaPura == null || senhaPura.isBlank()) {
            throw new IllegalArgumentException("Senha obrigatoria para derivacao.");
        }
        if (dataNascimento == null || dataNascimento.isBlank()) {
            throw new IllegalStateException("Data de nascimento obrigatoria para derivacao da senha.");
        }
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente EICKRONO_PASSWORD_PEPPER nao configurada.");
        }
        return senhaPura + pepper + dataNascimento.trim();
    }

    public static String obterDataNascimento(UserModel user) {
        if (user == null) {
            return null;
        }
        List<String> valores = user.getAttributes().get(ATRIBUTO_DATA_NASCIMENTO);
        if (valores != null && !valores.isEmpty() && !valores.get(0).isBlank()) {
            return valores.get(0);
        }
        String fallback = user.getFirstAttribute(ATRIBUTO_DATA_NASCIMENTO_FALLBACK);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    public static String obterDataNascimento(MultivaluedMap<String, String> formData) {
        if (formData == null) {
            return null;
        }
        String valor = formData.getFirst(ATRIBUTO_DATA_NASCIMENTO);
        if (valor != null && !valor.isBlank()) {
            return valor;
        }
        valor = formData.getFirst(ATRIBUTO_DATA_NASCIMENTO_FALLBACK);
        if (valor != null && !valor.isBlank()) {
            return valor;
        }
        return null;
    }
}

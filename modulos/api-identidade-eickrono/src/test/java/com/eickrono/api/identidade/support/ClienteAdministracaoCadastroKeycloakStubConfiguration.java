package com.eickrono.api.identidade.support;

import com.eickrono.api.identidade.aplicacao.modelo.CadastroKeycloakProvisionado;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoCadastroKeycloak;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Primary
@Profile("test")
public class ClienteAdministracaoCadastroKeycloakStubConfiguration implements ClienteAdministracaoCadastroKeycloak {

    private final Map<String, AtomicBoolean> usuarios = new ConcurrentHashMap<>();

    @Override
    public CadastroKeycloakProvisionado criarUsuarioPendente(final String nomeCompleto,
                                                             final String emailPrincipal,
                                                             final String senhaPura) {
        String sub = "keycloak-" + emailPrincipal.toLowerCase();
        usuarios.putIfAbsent(sub, new AtomicBoolean(false));
        return new CadastroKeycloakProvisionado(sub, emailPrincipal, nomeCompleto);
    }

    @Override
    public void confirmarEmailEAtivarUsuario(final String subjectRemoto) {
        AtomicBoolean status = usuarios.get(Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"));
        if (status == null) {
            throw new IllegalStateException("Usuário pendente não encontrado no stub do Keycloak.");
        }
        status.set(true);
    }

    @Override
    public void removerUsuarioPendente(final String subjectRemoto) {
        usuarios.remove(Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"));
    }

    @Override
    public Optional<UsuarioCadastroKeycloakExistente> buscarUsuarioPorEmail(final String emailPrincipal) {
        String emailNormalizado = Objects.requireNonNull(emailPrincipal, "emailPrincipal é obrigatório").trim().toLowerCase();
        return usuarios.entrySet().stream()
                .filter(entry -> entry.getKey().equals("keycloak-" + emailNormalizado))
                .findFirst()
                .map(entry -> new UsuarioCadastroKeycloakExistente(
                        entry.getKey(),
                        emailNormalizado,
                        entry.getValue().get(),
                        entry.getValue().get(),
                        1L
                ));
    }

    @Override
    public void redefinirSenha(final String subjectRemoto, final String senhaPura) {
        AtomicBoolean status = usuarios.get(Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"));
        if (status == null) {
            throw new IllegalStateException("Usuário não encontrado para redefinição de senha no stub do Keycloak.");
        }
    }
}

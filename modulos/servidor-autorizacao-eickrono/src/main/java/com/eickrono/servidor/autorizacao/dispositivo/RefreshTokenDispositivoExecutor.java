package com.eickrono.servidor.autorizacao.dispositivo;

import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.idm.ClientPolicyExecutorConfigurationRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyEvent;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.TokenRefreshContext;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;

/**
 * Bloqueia o refresh token quando o dispositivo perde confianca.
 */
public final class RefreshTokenDispositivoExecutor
        implements ClientPolicyExecutorProvider<ClientPolicyExecutorConfigurationRepresentation> {

    static final String PROVIDER_ID = "eickrono-device-token-refresh";

    private final KeycloakSession session;
    private final ValidadorRefreshDispositivo validador;

    public RefreshTokenDispositivoExecutor(KeycloakSession session,
                                           ValidadorRefreshDispositivo validador) {
        this.session = session;
        this.validador = validador;
    }

    @Override
    public void executeOnEvent(ClientPolicyContext context) throws ClientPolicyException {
        if (context.getEvent() != ClientPolicyEvent.TOKEN_REFRESH) {
            return;
        }
        TokenRefreshContext refreshContext = (TokenRefreshContext) context;
        String deviceToken = refreshContext.getParams().getFirst("device_token");
        if (deviceToken == null || deviceToken.isBlank()) {
            throw new ClientPolicyException(
                    "invalid_grant",
                    "DEVICE_TOKEN_MISSING: device_token e obrigatorio no refresh token.",
                    Response.Status.BAD_REQUEST);
        }

        String usuarioSub = resolverUsuarioSub();
        try {
            ResultadoValidacaoRefreshDispositivo resultado =
                    validador.validar(usuarioSub, deviceToken);
            if (!resultado.valido()) {
                throw new ClientPolicyException(
                        "invalid_grant",
                        resultado.codigo() + ": " + resultado.mensagem(),
                        Response.Status.BAD_REQUEST);
            }
        } catch (ClientPolicyException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientPolicyException(
                    "server_error",
                    "Falha ao validar o device token durante o refresh.",
                    Response.Status.INTERNAL_SERVER_ERROR,
                    e);
        }
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    private String resolverUsuarioSub() {
        UserSessionModel userSession = session.getContext().getUserSession();
        if (userSession != null && userSession.getUser() != null) {
            return userSession.getUser().getId();
        }
        UserModel user = session.getContext().getUser();
        return user == null ? "" : Objects.requireNonNullElse(user.getId(), "");
    }
}

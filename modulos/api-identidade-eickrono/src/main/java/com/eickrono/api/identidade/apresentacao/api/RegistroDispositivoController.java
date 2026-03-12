package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroRequest;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.apresentacao.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.ReenvioCodigoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistrarEventosOfflineRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RevogarTokenRequest;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.aplicacao.servico.OfflineDispositivoService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoService;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints responsáveis pelo registro e revogação de dispositivos móveis.
 */
@RestController
@RequestMapping("/identidade/dispositivos")
public class RegistroDispositivoController {

    private final RegistroDispositivoService registroDispositivoService;
    private final OfflineDispositivoService offlineDispositivoService;

    public RegistroDispositivoController(RegistroDispositivoService registroDispositivoService,
                                         OfflineDispositivoService offlineDispositivoService) {
        this.registroDispositivoService = registroDispositivoService;
        this.offlineDispositivoService = offlineDispositivoService;
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroDispositivoResponse> solicitarRegistro(@Valid @RequestBody RegistroDispositivoRequest request,
                                                                         @AuthenticationPrincipal Jwt jwt) {
        RegistroDispositivoResponse resposta = registroDispositivoService.solicitarRegistro(request, Optional.ofNullable(jwt));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resposta);
    }

    @PostMapping("/registro/{id}/confirmacao")
    public ResponseEntity<ConfirmacaoRegistroResponse> confirmarRegistro(@PathVariable("id") UUID id,
                                                                         @Valid @RequestBody ConfirmacaoRegistroRequest request,
                                                                         @AuthenticationPrincipal Jwt jwt) {
        ConfirmacaoRegistroResponse resposta = registroDispositivoService.confirmarRegistro(id, request, Optional.ofNullable(jwt));
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/offline/eventos")
    public ResponseEntity<Void> registrarEventosOffline(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestHeader("X-Device-Token") String tokenDispositivo,
                                                        @RequestBody RegistrarEventosOfflineRequest request) {
        offlineDispositivoService.registrarEventosOffline(
                extrairSub(jwt).orElseThrow(),
                tokenDispositivo,
                request);
        return ResponseEntity.accepted().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/offline/politica")
    public ResponseEntity<PoliticaOfflineDispositivoResponse> obterPoliticaOffline() {
        return ResponseEntity.ok(offlineDispositivoService.obterPolitica());
    }

    @PostMapping("/registro/{id}/reenviar")
    public ResponseEntity<Void> reenviarCodigos(@PathVariable("id") UUID id,
                                                @RequestBody(required = false) ReenvioCodigoRequest request) {
        registroDispositivoService.reenviarCodigos(id, request == null ? new ReenvioCodigoRequest() : request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/revogar")
    public ResponseEntity<Void> revogarToken(@AuthenticationPrincipal Jwt jwt,
                                             @RequestHeader("X-Device-Token") String tokenDispositivo,
                                             @RequestBody(required = false) RevogarTokenRequest request) {
        MotivoRevogacaoToken motivo = Optional.ofNullable(request)
                .map(RevogarTokenRequest::getMotivo)
                .flatMap(this::mapearMotivo)
                .orElse(MotivoRevogacaoToken.SOLICITACAO_CLIENTE);
        registroDispositivoService.revogarToken(
                extrairSub(jwt).orElseThrow(),
                tokenDispositivo,
                motivo);
        return ResponseEntity.noContent().build();
    }

    private Optional<String> extrairSub(Jwt jwt) {
        return Optional.ofNullable(jwt).map(Jwt::getSubject);
    }

    private Optional<MotivoRevogacaoToken> mapearMotivo(String valor) {
        if (!StringUtils.hasText(valor)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MotivoRevogacaoToken.valueOf(valor.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}

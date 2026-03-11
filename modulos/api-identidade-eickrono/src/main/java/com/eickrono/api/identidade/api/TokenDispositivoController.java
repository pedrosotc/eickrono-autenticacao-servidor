package com.eickrono.api.identidade.api;

import com.eickrono.api.identidade.dto.ValidacaoTokenDispositivoResponse;
import com.eickrono.api.identidade.servico.ResultadoValidacaoTokenDispositivo;
import com.eickrono.api.identidade.servico.TokenDispositivoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint dedicado para validacao explicita do token de dispositivo.
 */
@RestController
@RequestMapping("/identidade/dispositivos")
public class TokenDispositivoController {

    private final TokenDispositivoService tokenDispositivoService;

    public TokenDispositivoController(TokenDispositivoService tokenDispositivoService) {
        this.tokenDispositivoService = tokenDispositivoService;
    }

    @GetMapping("/token/validacao")
    public ResponseEntity<ValidacaoTokenDispositivoResponse> validarToken(@AuthenticationPrincipal Jwt jwt,
                                                                          @RequestHeader("X-Device-Token") String tokenDispositivo) {
        ResultadoValidacaoTokenDispositivo resultado =
                tokenDispositivoService.validarToken(jwt.getSubject(), tokenDispositivo);
        return ResponseEntity.ok(new ValidacaoTokenDispositivoResponse(
                resultado.valido(),
                resultado.codigo(),
                resultado.mensagem(),
                resultado.expiraEmOpt().orElse(null)));
    }
}

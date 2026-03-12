package com.eickrono.api.identidade.api;

import com.eickrono.api.identidade.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.dto.ValidacaoTokenDispositivoResponse;
import com.eickrono.api.identidade.servico.ResultadoValidacaoTokenDispositivo;
import com.eickrono.api.identidade.servico.TokenDispositivoService;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
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

    private static final String HEADER_DEVICE_TOKEN = "X-Device-Token";
    private static final String HEADER_USUARIO_SUB = "X-Usuario-Sub";
    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";

    private final TokenDispositivoService tokenDispositivoService;
    private final String segredoInternoEsperado;

    public TokenDispositivoController(TokenDispositivoService tokenDispositivoService,
                                      IntegracaoInternaProperties integracaoInternaProperties) {
        this.tokenDispositivoService = tokenDispositivoService;
        this.segredoInternoEsperado = Objects.requireNonNull(
                Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties e obrigatorio")
                        .getSegredo(),
                "integracao.interna.segredo e obrigatorio");
    }

    @GetMapping("/token/validacao")
    public ResponseEntity<ValidacaoTokenDispositivoResponse> validarToken(@AuthenticationPrincipal Jwt jwt,
                                                                          @RequestHeader(HEADER_DEVICE_TOKEN) String tokenDispositivo) {
        ResultadoValidacaoTokenDispositivo resultado =
                tokenDispositivoService.validarToken(jwt.getSubject(), tokenDispositivo);
        return ResponseEntity.ok(new ValidacaoTokenDispositivoResponse(
                resultado.valido(),
                resultado.codigo(),
                resultado.mensagem(),
                resultado.expiraEmOpt().orElse(null)));
    }

    @GetMapping("/token/validacao/interna")
    public ResponseEntity<ValidacaoTokenDispositivoResponse> validarTokenInternamente(
            @RequestHeader(HEADER_SEGREDO_INTERNO) String segredoInterno,
            @RequestHeader(HEADER_DEVICE_TOKEN) String tokenDispositivo,
            @RequestHeader(value = HEADER_USUARIO_SUB, required = false) String usuarioSub) {
        validarSegredo(segredoInterno);
        ResultadoValidacaoTokenDispositivo resultado = StringUtils.hasText(usuarioSub)
                ? tokenDispositivoService.validarToken(usuarioSub, tokenDispositivo)
                : tokenDispositivoService.validarTokenSemUsuario(tokenDispositivo);
        return ResponseEntity.ok(new ValidacaoTokenDispositivoResponse(
                resultado.valido(),
                resultado.codigo(),
                resultado.mensagem(),
                resultado.expiraEmOpt().orElse(null)));
    }

    private void validarSegredo(String segredoInformado) {
        if (!Objects.equals(segredoInternoEsperado, segredoInformado)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Segredo interno invalido");
        }
    }
}

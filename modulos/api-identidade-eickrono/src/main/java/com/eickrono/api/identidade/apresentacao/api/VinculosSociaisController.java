package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.servico.VinculoSocialService;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints para gerenciar vínculos sociais.
 */
@RestController
@RequestMapping("/identidade/vinculos-sociais")
public class VinculosSociaisController {

    private final VinculoSocialService vinculoSocialService;

    public VinculosSociaisController(final VinculoSocialService vinculoSocialService) {
        this.vinculoSocialService = Objects.requireNonNull(vinculoSocialService, "vinculoSocialService é obrigatório");
    }

    @GetMapping
    public ResponseEntity<VinculosSociaisDto> listar(@AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.listar(Objects.requireNonNull(jwt, "jwt é obrigatório")));
    }

    @PostMapping("/{provedor}/sincronizacao")
    public ResponseEntity<VinculosSociaisDto> sincronizar(@PathVariable("provedor") final String provedor,
                                                          @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.sincronizar(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                provedor));
    }

    @DeleteMapping("/{provedor}")
    public ResponseEntity<VinculosSociaisDto> remover(@PathVariable("provedor") final String provedor,
                                                      @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(vinculoSocialService.remover(
                Objects.requireNonNull(jwt, "jwt é obrigatório"),
                provedor));
    }
}

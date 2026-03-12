package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.apresentacao.dto.PerfilDto;
import com.eickrono.api.identidade.aplicacao.servico.AuditoriaService;
import com.eickrono.api.identidade.aplicacao.servico.PerfilService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de leitura do perfil do usuário autenticado.
 */
@RestController
@RequestMapping("/identidade")
public class PerfilController {

    private final PerfilService perfilService;
    private final AuditoriaService auditoriaService;

    public PerfilController(PerfilService perfilService, AuditoriaService auditoriaService) {
        this.perfilService = perfilService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/perfil")
    public ResponseEntity<PerfilDto> obterPerfil(@AuthenticationPrincipal Jwt jwt) {
        PerfilDto perfil = perfilService.buscarOuProvisionar(jwt);
        auditoriaService.registrarEvento("PERFIL_CONSULTADO", jwt.getSubject(), "Perfil consultado");
        return ResponseEntity.ok(perfil);
    }
}

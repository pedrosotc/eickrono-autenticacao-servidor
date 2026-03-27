package com.eickrono.api.identidade.apresentacao.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints para gerenciar vínculos sociais.
 */
@RestController
@RequestMapping("/identidade/vinculos-sociais")
public class VinculosSociaisController {

    @GetMapping
    public void listar() {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Endpoint desativado neste serviço. Consulte vínculos no servidor de domínio.");
    }

    @PostMapping
    public void criar() {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Endpoint desativado neste serviço. Gerencie vínculos no servidor de domínio.");
    }
}

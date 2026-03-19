package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.apresentacao.dto.sessao.CriarSessaoInternaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.sessao.SessaoInternaApiResposta;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/identidade/sessoes/interna")
public class SessaoInternaController {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";

    private final AutenticacaoSessaoInternaServico servico;
    private final String segredoInternoEsperado;

    public SessaoInternaController(final AutenticacaoSessaoInternaServico servico,
                                   final IntegracaoInternaProperties integracaoInternaProperties) {
        this.servico = Objects.requireNonNull(servico, "servico é obrigatório");
        this.segredoInternoEsperado = Objects.requireNonNull(
                Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties é obrigatório")
                        .getSegredo(),
                "integracao.interna.segredo é obrigatório");
    }

    @PostMapping
    public SessaoInternaApiResposta abrirSessao(@RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
                                                @Valid @RequestBody final CriarSessaoInternaApiRequest request) {
        validarSegredo(segredoInterno);
        SessaoInternaAutenticada sessao = servico.autenticar(request.login(), request.senha());
        return SessaoInternaApiResposta.de(sessao);
    }

    private void validarSegredo(final String segredoInformado) {
        if (!Objects.equals(segredoInternoEsperado, segredoInformado)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Segredo interno invalido");
        }
    }
}

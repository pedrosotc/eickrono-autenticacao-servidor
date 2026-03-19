package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.modelo.DesafioAtestacaoGerado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoAtestacaoAppConcluida;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.CriarDesafioAtestacaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.DesafioAtestacaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.ValidacaoAtestacaoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.atestacao.ValidarAtestacaoApiRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/identidade/atestacoes/interna")
public class AtestacaoAppInternaController {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String HEADER_CLIENT_IP = "X-Eickrono-Client-Ip";
    private static final String HEADER_CLIENT_USER_AGENT = "X-Eickrono-Client-User-Agent";

    private final AtestacaoAppServico servico;
    private final String segredoInternoEsperado;

    public AtestacaoAppInternaController(final AtestacaoAppServico servico,
                                         final IntegracaoInternaProperties integracaoInternaProperties) {
        this.servico = Objects.requireNonNull(servico, "servico é obrigatório");
        this.segredoInternoEsperado = Objects.requireNonNull(
                Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties é obrigatório")
                        .getSegredo(),
                "integracao.interna.segredo é obrigatório");
    }

    @PostMapping("/desafios")
    @ResponseStatus(HttpStatus.CREATED)
    public DesafioAtestacaoApiResposta criarDesafio(@RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
                                                    @RequestHeader(value = HEADER_CLIENT_IP, required = false) final String ipOriginal,
                                                    @RequestHeader(value = HEADER_CLIENT_USER_AGENT, required = false) final String userAgentOriginal,
                                                    @Valid @RequestBody final CriarDesafioAtestacaoApiRequest request) {
        validarSegredo(segredoInterno);
        DesafioAtestacaoGerado desafio = servico.gerarDesafio(
                request.operacao(),
                request.plataforma(),
                ipOriginal,
                userAgentOriginal
        );
        return DesafioAtestacaoApiResposta.de(desafio);
    }

    @PostMapping("/validacoes")
    public ValidacaoAtestacaoApiResposta validarAtestacao(@RequestHeader(HEADER_SEGREDO_INTERNO) final String segredoInterno,
                                                          @Valid @RequestBody final ValidarAtestacaoApiRequest request) {
        validarSegredo(segredoInterno);
        ValidacaoAtestacaoAppConcluida validacao = servico.validarComprovante(request.paraEntradaAplicacao());
        return ValidacaoAtestacaoApiResposta.de(validacao);
    }

    private void validarSegredo(final String segredoInformado) {
        if (!Objects.equals(segredoInternoEsperado, segredoInformado)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo interno invalido");
        }
    }
}

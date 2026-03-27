package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoCodigoRecuperacaoSenhaRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroPublicoRealizada;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfil;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.RecuperacaoSenhaIniciada;
import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.ClienteContextoPessoaPerfil;
import com.eickrono.api.identidade.aplicacao.servico.RecuperacaoSenhaService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CadastroApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CadastroApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmacaoCodigoRecuperacaoSenhaApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmacaoEmailCadastroApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmarCodigoRecuperacaoSenhaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.ConfirmarEmailCadastroApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.CriarSessaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DisponibilidadeUsuarioCadastroApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.IniciarRecuperacaoSenhaApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.RecuperacaoSenhaApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.RenovarSessaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.RedefinirSenhaRecuperacaoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.SessaoApiResposta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/publica")
public class FluxoPublicoController {

    private static final String ERRO_KEYCLOAK_CONTA_DESABILITADA = "Account disabled";
    private static final String ERRO_KEYCLOAK_CONTA_INCOMPLETA = "Account is not fully set up";
    private static final String ERRO_KEYCLOAK_CREDENCIAIS_INVALIDAS = "Invalid user credentials";
    private static final String STATUS_PENDENTE_EMAIL = "PENDENTE_EMAIL";
    private static final String STATUS_LIBERADO = "LIBERADO";

    private final CadastroContaInternaServico cadastroContaInternaServico;
    private final AtestacaoAppServico atestacaoAppServico;
    private final AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;
    private final ClienteContextoPessoaPerfil clienteContextoPessoaPerfil;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;

    public FluxoPublicoController(final CadastroContaInternaServico cadastroContaInternaServico,
                                  final AtestacaoAppServico atestacaoAppServico,
                                  final AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico,
                                  final ClienteContextoPessoaPerfil clienteContextoPessoaPerfil,
                                  final RecuperacaoSenhaService recuperacaoSenhaService,
                                  final RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService) {
        this.cadastroContaInternaServico = Objects.requireNonNull(
                cadastroContaInternaServico, "cadastroContaInternaServico é obrigatório");
        this.atestacaoAppServico = Objects.requireNonNull(atestacaoAppServico, "atestacaoAppServico é obrigatório");
        this.autenticacaoSessaoInternaServico = Objects.requireNonNull(
                autenticacaoSessaoInternaServico, "autenticacaoSessaoInternaServico é obrigatório");
        this.clienteContextoPessoaPerfil = Objects.requireNonNull(
                clienteContextoPessoaPerfil, "clienteContextoPessoaPerfil é obrigatório");
        this.recuperacaoSenhaService = Objects.requireNonNull(
                recuperacaoSenhaService, "recuperacaoSenhaService é obrigatório");
        this.registroDispositivoLoginSilenciosoService = Objects.requireNonNull(
                registroDispositivoLoginSilenciosoService, "registroDispositivoLoginSilenciosoService é obrigatório");
    }

    @PostMapping("/cadastros")
    @ResponseStatus(HttpStatus.CREATED)
    public CadastroApiResposta criarCadastro(@Valid @RequestBody final CadastroApiRequest requisicao,
                                             final HttpServletRequest servletRequest) {
        validarRegrasCadastro(requisicao);
        atestacaoAppServico.validarComprovante(requisicao.atestacao().paraEntrada());
        CadastroInternoRealizado cadastro = cadastroContaInternaServico.cadastrarPublico(
                requisicao.tipoPessoa(),
                requisicao.nomeCompleto(),
                requisicao.nomeFantasia(),
                requisicao.usuario(),
                requisicao.sexo(),
                requisicao.paisNascimento(),
                requisicao.dataNascimento(),
                requisicao.emailPrincipal(),
                requisicao.telefone(),
                requisicao.tipoValidacaoTelefone(),
                requisicao.senha(),
                "app-flutter-publico",
                extrairIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
        return new CadastroApiResposta(
                cadastro.cadastroId().toString(),
                "",
                STATUS_PENDENTE_EMAIL,
                cadastro.emailPrincipal(),
                Objects.requireNonNullElse(requisicao.telefone(), ""),
                cadastro.verificacaoEmailObrigatoria(),
                "VALIDAR_CONTATOS"
        );
    }

    @GetMapping("/cadastros/usuarios/disponibilidade")
    public DisponibilidadeUsuarioCadastroApiResposta consultarDisponibilidadeUsuario(
            @RequestParam final String usuario) {
        String usuarioNormalizado = Objects.requireNonNull(usuario, "usuario é obrigatório")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (usuarioNormalizado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usuario é obrigatório.");
        }
        return new DisponibilidadeUsuarioCadastroApiResposta(
                usuarioNormalizado,
                cadastroContaInternaServico.usuarioDisponivelPublico(usuarioNormalizado)
        );
    }

    @PostMapping("/cadastros/{cadastroId}/confirmacoes/email")
    public ConfirmacaoEmailCadastroApiResposta confirmarEmailCadastro(@PathVariable final String cadastroId,
                                                                     @Valid @RequestBody
                                                                     final ConfirmarEmailCadastroApiRequest requisicao) {
        ConfirmacaoEmailCadastroPublicoRealizada confirmacao = cadastroContaInternaServico.confirmarEmailPublico(
                parseCadastroId(cadastroId),
                requisicao.codigo()
        );
        return new ConfirmacaoEmailCadastroApiResposta(
                confirmacao.cadastroId().toString(),
                confirmacao.usuarioId(),
                confirmacao.statusUsuario(),
                confirmacao.emailPrincipal(),
                confirmacao.emailConfirmado(),
                confirmacao.podeAutenticar(),
                "LOGIN"
        );
    }

    @PostMapping("/cadastros/{cadastroId}/confirmacoes/email/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarConfirmacaoEmailCadastro(@PathVariable final String cadastroId) {
        cadastroContaInternaServico.reenviarCodigoEmail(parseCadastroId(cadastroId));
    }

    @DeleteMapping("/cadastros/{cadastroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCadastro(@PathVariable final String cadastroId) {
        cadastroContaInternaServico.cancelarCadastroPendentePublico(parseCadastroId(cadastroId));
    }

    @PostMapping("/sessoes")
    public SessaoApiResposta criarSessao(@Valid @RequestBody final CriarSessaoApiRequest requisicao) {
        atestacaoAppServico.validarComprovante(requisicao.atestacao().paraEntrada());
        String loginNormalizado = requisicao.login().trim().toLowerCase(Locale.ROOT);
        SessaoInternaAutenticada sessao;
        try {
            sessao = autenticacaoSessaoInternaServico.autenticar(
                    loginNormalizado,
                    requisicao.senha()
            );
        } catch (ResponseStatusException exception) {
            throw mapearErroLoginPublico(loginNormalizado, exception);
        }
        ContextoPessoaPerfil contexto = clienteContextoPessoaPerfil.buscarPorEmail(loginNormalizado)
                .orElseThrow(() -> new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "conta_nao_liberada",
                        "A conta ainda não está liberada para utilizar o aplicativo."
                ));
        String statusUsuario = Objects.requireNonNullElse(contexto.statusUsuario(), STATUS_LIBERADO);
        if (!STATUS_LIBERADO.equalsIgnoreCase(statusUsuario)) {
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "conta_nao_liberada",
                    "A conta ainda não está liberada para utilizar o aplicativo."
            );
        }
        DispositivoSessaoRegistrado dispositivoRegistrado = registroDispositivoLoginSilenciosoService.registrar(
                contexto,
                requisicao.dispositivo()
        );
        return new SessaoApiResposta(
                sessao.autenticado(),
                sessao.tipoToken(),
                sessao.accessToken(),
                sessao.refreshToken(),
                sessao.expiresIn(),
                dispositivoRegistrado.tokenDispositivo(),
                dispositivoRegistrado.tokenDispositivoExpiraEm(),
                statusUsuario,
                false,
                true,
                true
        );
    }

    @PostMapping("/sessoes/refresh")
    public SessaoApiResposta renovarSessao(@Valid @RequestBody final RenovarSessaoApiRequest requisicao) {
        SessaoInternaAutenticada sessao = autenticacaoSessaoInternaServico.renovar(
                requisicao.refreshToken(),
                requisicao.tokenDispositivo()
        );
        return new SessaoApiResposta(
                sessao.autenticado(),
                sessao.tipoToken(),
                sessao.accessToken(),
                sessao.refreshToken(),
                sessao.expiresIn(),
                null,
                null,
                STATUS_LIBERADO,
                false,
                true,
                true
        );
    }

    @PostMapping("/recuperacoes-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecuperacaoSenhaApiResposta iniciarRecuperacaoSenha(
            @Valid @RequestBody final IniciarRecuperacaoSenhaApiRequest requisicao) {
        RecuperacaoSenhaIniciada recuperacao = recuperacaoSenhaService.iniciar(requisicao.emailPrincipal());
        return new RecuperacaoSenhaApiResposta(
                recuperacao.fluxoId().toString(),
                "Se este e-mail estiver cadastrado, enviaremos um código de verificação."
        );
    }

    @PostMapping("/recuperacoes-senha/{fluxoId}/confirmacoes/email")
    public ConfirmacaoCodigoRecuperacaoSenhaApiResposta confirmarCodigoRecuperacaoSenha(
            @PathVariable final String fluxoId,
            @Valid @RequestBody final ConfirmarCodigoRecuperacaoSenhaApiRequest requisicao) {
        ConfirmacaoCodigoRecuperacaoSenhaRealizada confirmacao =
                recuperacaoSenhaService.confirmarCodigo(parseCadastroId(fluxoId), requisicao.codigo());
        return new ConfirmacaoCodigoRecuperacaoSenhaApiResposta(
                confirmacao.fluxoId().toString(),
                confirmacao.codigoConfirmado(),
                confirmacao.podeDefinirSenha()
        );
    }

    @PostMapping("/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarCodigoRecuperacaoSenha(@PathVariable final String fluxoId) {
        recuperacaoSenhaService.reenviarCodigo(parseCadastroId(fluxoId));
    }

    @PostMapping("/recuperacoes-senha/{fluxoId}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenhaRecuperacaoSenha(
            @PathVariable final String fluxoId,
            @Valid @RequestBody final RedefinirSenhaRecuperacaoApiRequest requisicao) {
        recuperacaoSenhaService.redefinirSenha(
                parseCadastroId(fluxoId),
                requisicao.senha(),
                requisicao.confirmacaoSenha()
        );
    }

    private static void validarRegrasCadastro(final CadastroApiRequest requisicao) {
        if (!Objects.equals(requisicao.senha(), requisicao.confirmacaoSenha())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A confirmação de senha não confere.");
        }
        if (requisicao.tipoPessoa() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipoPessoa é obrigatório.");
        }
        if (requisicao.tipoPessoa().name().equals("FISICA")) {
            validarFisica(requisicao.sexo(), requisicao.paisNascimento(), requisicao.dataNascimento());
            return;
        }
        if (requisicao.dataNascimento() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Pessoa jurídica não deve informar data de nascimento.");
        }
    }

    private static void validarFisica(final Object sexo, final String paisNascimento, final LocalDate dataNascimento) {
        if (sexo == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "sexo é obrigatório para pessoa física.");
        }
        if (paisNascimento == null || paisNascimento.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "paisNascimento é obrigatório para pessoa física."
            );
        }
        if (dataNascimento == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "dataNascimento é obrigatória para pessoa física."
            );
        }
    }

    private static UUID parseCadastroId(final String cadastroId) {
        try {
            return UUID.fromString(Objects.requireNonNull(cadastroId, "cadastroId é obrigatório"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cadastroId inválido.");
        }
    }

    private FluxoPublicoException mapearErroLoginPublico(final String loginNormalizado,
                                                         final ResponseStatusException exception) {
        String motivo = Objects.requireNonNullElse(exception.getReason(), "").trim();
        if (ERRO_KEYCLOAK_CONTA_DESABILITADA.equalsIgnoreCase(motivo)
                || ERRO_KEYCLOAK_CONTA_INCOMPLETA.equalsIgnoreCase(motivo)) {
            UUID cadastroPendenteId = cadastroContaInternaServico
                    .buscarCadastroPendenteEmailPublico(loginNormalizado)
                    .orElse(null);
            if (cadastroPendenteId != null) {
                return new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "conta_nao_liberada",
                        "A conta ainda não está liberada para utilizar o aplicativo.",
                        Map.of("cadastroId", cadastroPendenteId.toString())
                );
            }
            if (ERRO_KEYCLOAK_CONTA_INCOMPLETA.equalsIgnoreCase(motivo)) {
                return new FluxoPublicoException(
                        HttpStatus.FORBIDDEN,
                        "conta_nao_liberada",
                        "A conta ainda não está liberada para utilizar o aplicativo."
                );
            }
            return new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "conta_desabilitada",
                    "A conta está desabilitada para autenticação."
            );
        }
        if (ERRO_KEYCLOAK_CREDENCIAIS_INVALIDAS.equalsIgnoreCase(motivo)
                || "Credenciais invalidas.".equalsIgnoreCase(motivo)) {
            return new FluxoPublicoException(
                    HttpStatus.UNAUTHORIZED,
                    "credenciais_invalidas",
                    "Credenciais inválidas."
            );
        }
        return new FluxoPublicoException(
                HttpStatus.BAD_GATEWAY,
                "falha_autenticacao",
                "Não foi possível autenticar a sessão agora."
        );
    }

    private static String extrairIp(final HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return servletRequest.getRemoteAddr();
        }
        return forwardedFor.split(",")[0].trim();
    }
}

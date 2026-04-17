package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfil;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.DispositivoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.RegistroDispositivoRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistroDispositivoLoginSilenciosoService {

    private static final String HMAC_ALG = "HmacSHA256";

    private final RegistroDispositivoRepositorio registroDispositivoRepositorio;
    private final DispositivoIdentidadeRepositorio dispositivoIdentidadeRepositorio;
    private final DispositivoIdentidadeService dispositivoIdentidadeService;
    private final TokenDispositivoService tokenDispositivoService;
    private final DispositivoProperties dispositivoProperties;
    private final AuditoriaService auditoriaService;
    private final Clock clock;
    private final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;
    private final HexFormat hexFormat = HexFormat.of();

    @Autowired
    public RegistroDispositivoLoginSilenciosoService(
            final RegistroDispositivoRepositorio registroDispositivoRepositorio,
            final DispositivoIdentidadeRepositorio dispositivoIdentidadeRepositorio,
            final DispositivoIdentidadeService dispositivoIdentidadeService,
            final TokenDispositivoService tokenDispositivoService,
            final DispositivoProperties dispositivoProperties,
            final AuditoriaService auditoriaService,
            final Clock clock,
            final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService) {
        this.registroDispositivoRepositorio = Objects.requireNonNull(
                registroDispositivoRepositorio, "registroDispositivoRepositorio é obrigatório");
        this.dispositivoIdentidadeRepositorio = Objects.requireNonNull(
                dispositivoIdentidadeRepositorio, "dispositivoIdentidadeRepositorio é obrigatório");
        this.dispositivoIdentidadeService = Objects.requireNonNull(
                dispositivoIdentidadeService, "dispositivoIdentidadeService é obrigatório");
        this.tokenDispositivoService = Objects.requireNonNull(
                tokenDispositivoService, "tokenDispositivoService é obrigatório");
        this.dispositivoProperties = Objects.requireNonNull(
                dispositivoProperties, "dispositivoProperties é obrigatório");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService é obrigatório");
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");
        this.sincronizacaoModeloMultiappService = sincronizacaoModeloMultiappService;
    }

    public RegistroDispositivoLoginSilenciosoService(
            final RegistroDispositivoRepositorio registroDispositivoRepositorio,
            final DispositivoIdentidadeRepositorio dispositivoIdentidadeRepositorio,
            final DispositivoIdentidadeService dispositivoIdentidadeService,
            final TokenDispositivoService tokenDispositivoService,
            final DispositivoProperties dispositivoProperties,
            final AuditoriaService auditoriaService,
            final Clock clock) {
        this(
                registroDispositivoRepositorio,
                dispositivoIdentidadeRepositorio,
                dispositivoIdentidadeService,
                tokenDispositivoService,
                dispositivoProperties,
                auditoriaService,
                clock,
                null
        );
    }

    @Transactional
    public DispositivoSessaoRegistrado registrar(final ContextoPessoaPerfil contexto,
                                                 final DispositivoSessaoApiRequest dispositivo) {
        ContextoPessoaPerfil contextoObrigatorio = Objects.requireNonNull(contexto, "contexto é obrigatório");
        DispositivoSessaoApiRequest dispositivoObrigatorio = Objects.requireNonNull(
                dispositivo, "dispositivo é obrigatório");
        OffsetDateTime agora = OffsetDateTime.now(clock);
        String fingerprint = derivarFingerprint(dispositivoObrigatorio);
        String plataforma = normalizarObrigatorio(dispositivoObrigatorio.plataforma(), "plataforma").toUpperCase(Locale.ROOT);
        String versaoApp = normalizarOpcional(dispositivoObrigatorio.versaoApp());

        dispositivoIdentidadeRepositorio.findByUsuarioSubAndFingerprint(contextoObrigatorio.sub(), fingerprint)
                .ifPresent(this::validarEstadoDispositivoParaLogin);

        RegistroDispositivo registro = new RegistroDispositivo(
                UUID.randomUUID(),
                contextoObrigatorio.sub(),
                contextoObrigatorio.pessoaId(),
                contextoObrigatorio.emailPrincipal(),
                null,
                fingerprint,
                plataforma,
                versaoApp,
                null,
                StatusRegistroDispositivo.CONFIRMADO,
                agora,
                agora.plusHours(dispositivoProperties.getToken().getValidadeHoras())
        );
        registro.definirStatus(StatusRegistroDispositivo.CONFIRMADO, agora);
        registroDispositivoRepositorio.save(registro);
        sincronizarRegistroSeConfigurado(registro);

        DispositivoIdentidade dispositivoIdentidade = dispositivoIdentidadeService.garantirDispositivo(
                contextoObrigatorio.sub(),
                contextoObrigatorio.pessoaId(),
                registro
        );
        validarEstadoDispositivoParaLogin(dispositivoIdentidade);

        TokenDispositivoService.TokenEmitido tokenEmitido = tokenDispositivoService.emitirToken(
                registro,
                dispositivoIdentidade,
                contextoObrigatorio.sub()
        );

        auditoriaService.registrarEvento(
                "DISPOSITIVO_REGISTRO_SILENCIOSO_LOGIN",
                contextoObrigatorio.sub(),
                "Dispositivo atualizado silenciosamente durante o login"
        );

        return new DispositivoSessaoRegistrado(
                tokenEmitido.tokenClaro(),
                tokenEmitido.entidade().getExpiraEm()
        );
    }

    private void validarEstadoDispositivoParaLogin(final DispositivoIdentidade dispositivo) {
        if (dispositivo.getStatus() == StatusDispositivoIdentidade.ATIVO) {
            return;
        }
        throw new FluxoPublicoException(
                HttpStatus.LOCKED,
                "dispositivo_nao_liberado",
                "Este dispositivo não está liberado para uso com a conta."
        );
    }

    private String derivarFingerprint(final DispositivoSessaoApiRequest dispositivo) {
        String base = String.join("|",
                normalizarObrigatorio(dispositivo.plataforma(), "plataforma").toUpperCase(Locale.ROOT),
                normalizarOpcional(dispositivo.identificadorInstalacao()),
                normalizarOpcional(dispositivo.modelo()),
                normalizarOpcional(dispositivo.fabricante()),
                normalizarOpcional(dispositivo.sistemaOperacional()),
                normalizarOpcional(dispositivo.versaoSistema()),
                normalizarOpcional(dispositivo.versaoApp())
        );
        String segredo = dispositivoProperties.getToken().getSegredoHmac();
        if (!StringUtils.hasText(segredo)) {
            throw new IllegalStateException("identidade.dispositivo.token.segredo-hmac deve ser configurado");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] resultado = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
            return hexFormat.formatHex(resultado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao derivar fingerprint do dispositivo", ex);
        }
    }

    private String normalizarObrigatorio(final String valor, final String campo) {
        String normalizado = normalizarOpcional(valor);
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return normalizado;
    }

    private String normalizarOpcional(final String valor) {
        return valor == null ? "" : valor.trim();
    }

    private void sincronizarRegistroSeConfigurado(final RegistroDispositivo registro) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.sincronizarRegistroDispositivo(registro);
        }
    }
}

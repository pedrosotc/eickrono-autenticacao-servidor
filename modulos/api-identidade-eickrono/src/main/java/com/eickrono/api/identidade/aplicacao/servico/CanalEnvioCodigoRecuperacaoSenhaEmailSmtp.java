package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;
import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroEmailProperties;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class CanalEnvioCodigoRecuperacaoSenhaEmailSmtp implements CanalEnvioCodigoRecuperacaoSenhaEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoRecuperacaoSenhaEmailSmtp.class);
    private static final DateTimeFormatter FORMATADOR_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'");

    private final JavaMailSender javaMailSender;
    private final CadastroEmailProperties cadastroEmailProperties;

    public CanalEnvioCodigoRecuperacaoSenhaEmailSmtp(final JavaMailSender javaMailSender,
                                                     final CadastroEmailProperties cadastroEmailProperties) {
        this.javaMailSender = Objects.requireNonNull(javaMailSender, "javaMailSender é obrigatório");
        this.cadastroEmailProperties = Objects.requireNonNull(
                cadastroEmailProperties, "cadastroEmailProperties é obrigatório");
    }

    @Override
    public void enviar(final RecuperacaoSenha recuperacaoSenha, final String codigo) {
        RecuperacaoSenha recuperacao = Objects.requireNonNull(recuperacaoSenha, "recuperacaoSenha é obrigatória");
        String codigoConfirmacao = Objects.requireNonNull(codigo, "codigo é obrigatório").trim();
        if (codigoConfirmacao.isBlank()) {
            throw new IllegalArgumentException("codigo é obrigatório");
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(recuperacao.getEmailPrincipal());
        mensagem.setFrom(cadastroEmailProperties.getRemetente());
        if (cadastroEmailProperties.getResponderPara() != null
                && !cadastroEmailProperties.getResponderPara().isBlank()) {
            mensagem.setReplyTo(cadastroEmailProperties.getResponderPara());
        }
        mensagem.setSubject(cadastroEmailProperties.getAssuntoRecuperacaoSenha());
        mensagem.setText(criarCorpoMensagem(recuperacao, codigoConfirmacao));

        try {
            javaMailSender.send(mensagem);
            LOGGER.info(
                    "Código de recuperação de senha enviado por SMTP para {} (fluxoId={})",
                    recuperacao.getEmailPrincipal(),
                    recuperacao.getFluxoId()
            );
        } catch (MailException ex) {
            throw new IllegalStateException("Falha ao enviar o código de recuperação de senha por SMTP.", ex);
        }
    }

    private String criarCorpoMensagem(final RecuperacaoSenha recuperacaoSenha, final String codigo) {
        return """
                Olá,

                Você solicitou a recuperação de senha da sua conta no %s.

                Código de recuperação: %s
                Solicitação: %s
                Validade até: %s

                Se você não reconhece esta solicitação, ignore esta mensagem.
                """
                .formatted(
                        cadastroEmailProperties.getNomeAplicacao(),
                        codigo,
                        recuperacaoSenha.getFluxoId(),
                        FORMATADOR_DATA_HORA.format(recuperacaoSenha.getCodigoEmailExpiraEm())
                );
    }
}

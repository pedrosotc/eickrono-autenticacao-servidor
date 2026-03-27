package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.infraestrutura.configuracao.CadastroEmailProperties;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class CanalEnvioCodigoCadastroEmailSmtp implements CanalEnvioCodigoCadastroEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoCadastroEmailSmtp.class);
    private static final DateTimeFormatter FORMATADOR_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'");

    private final JavaMailSender javaMailSender;
    private final CadastroEmailProperties cadastroEmailProperties;

    public CanalEnvioCodigoCadastroEmailSmtp(final JavaMailSender javaMailSender,
                                             final CadastroEmailProperties cadastroEmailProperties) {
        this.javaMailSender = Objects.requireNonNull(javaMailSender, "javaMailSender é obrigatório");
        this.cadastroEmailProperties = Objects.requireNonNull(
                cadastroEmailProperties, "cadastroEmailProperties é obrigatório");
    }

    @Override
    public void enviar(final CadastroConta cadastroConta, final String codigo) {
        CadastroConta cadastro = Objects.requireNonNull(cadastroConta, "cadastroConta é obrigatório");
        String codigoConfirmacao = Objects.requireNonNull(codigo, "codigo é obrigatório").trim();
        if (codigoConfirmacao.isBlank()) {
            throw new IllegalArgumentException("codigo é obrigatório");
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(cadastro.getEmailPrincipal());
        mensagem.setFrom(cadastroEmailProperties.getRemetente());
        if (cadastroEmailProperties.getResponderPara() != null
                && !cadastroEmailProperties.getResponderPara().isBlank()) {
            mensagem.setReplyTo(cadastroEmailProperties.getResponderPara());
        }
        mensagem.setSubject(cadastroEmailProperties.getAssunto());
        mensagem.setText(criarCorpoMensagem(cadastro, codigoConfirmacao));

        try {
            javaMailSender.send(mensagem);
            LOGGER.info(
                    "Código de confirmação de cadastro enviado por SMTP para {} (cadastroId={}, sistema={})",
                    cadastro.getEmailPrincipal(),
                    cadastro.getCadastroId(),
                    cadastro.getSistemaSolicitante()
            );
        } catch (MailException ex) {
            throw new IllegalStateException("Falha ao enviar o código de confirmação do cadastro por SMTP.", ex);
        }
    }

    private String criarCorpoMensagem(final CadastroConta cadastroConta, final String codigo) {
        return """
                Olá,

                Você solicitou a confirmação de cadastro da sua conta no %s.

                Código de confirmação: %s
                Cadastro: %s
                Validade até: %s

                Se você não reconhece esta solicitação, ignore esta mensagem.
                """
                .formatted(
                        cadastroEmailProperties.getNomeAplicacao(),
                        codigo,
                        cadastroConta.getCadastroId(),
                        FORMATADOR_DATA_HORA.format(cadastroConta.getCodigoEmailExpiraEm())
                );
    }
}

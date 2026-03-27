package com.eickrono.api.identidade.infraestrutura.configuracao;

import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoCadastroEmail;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoCadastroEmailLog;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoCadastroEmailSmtp;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoRecuperacaoSenhaEmail;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoRecuperacaoSenhaEmailLog;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigoRecuperacaoSenhaEmailSmtp;
import com.eickrono.api.identidade.aplicacao.servico.CanalNotificacaoTentativaCadastroEmail;
import com.eickrono.api.identidade.aplicacao.servico.CanalNotificacaoTentativaCadastroEmailLog;
import com.eickrono.api.identidade.aplicacao.servico.CanalNotificacaoTentativaCadastroEmailSmtp;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class CadastroEmailConfiguracao {

    @Bean
    public CanalEnvioCodigoCadastroEmail canalEnvioCodigoCadastroEmail(
            final CadastroEmailProperties cadastroEmailProperties,
            final ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        Objects.requireNonNull(cadastroEmailProperties, "cadastroEmailProperties é obrigatório");
        String fornecedor = Objects.requireNonNullElse(cadastroEmailProperties.getFornecedor(), "log")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (fornecedor) {
            case "log" -> new CanalEnvioCodigoCadastroEmailLog();
            case "smtp" -> new CanalEnvioCodigoCadastroEmailSmtp(
                    Objects.requireNonNull(
                            javaMailSenderProvider.getIfAvailable(),
                            "Fornecedor SMTP configurado, mas nenhum JavaMailSender foi inicializado. "
                                    + "Revise spring.mail.host, spring.mail.port e credenciais."
                    ),
                    cadastroEmailProperties
            );
            default -> throw new IllegalStateException(
                    "Fornecedor de e-mail do cadastro inválido: " + cadastroEmailProperties.getFornecedor());
        };
    }

    @Bean
    public CanalEnvioCodigoRecuperacaoSenhaEmail canalEnvioCodigoRecuperacaoSenhaEmail(
            final CadastroEmailProperties cadastroEmailProperties,
            final ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        Objects.requireNonNull(cadastroEmailProperties, "cadastroEmailProperties é obrigatório");
        String fornecedor = Objects.requireNonNullElse(cadastroEmailProperties.getFornecedor(), "log")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (fornecedor) {
            case "log" -> new CanalEnvioCodigoRecuperacaoSenhaEmailLog();
            case "smtp" -> new CanalEnvioCodigoRecuperacaoSenhaEmailSmtp(
                    Objects.requireNonNull(
                            javaMailSenderProvider.getIfAvailable(),
                            "Fornecedor SMTP configurado, mas nenhum JavaMailSender foi inicializado. "
                                    + "Revise spring.mail.host, spring.mail.port e credenciais."
                    ),
                    cadastroEmailProperties
            );
            default -> throw new IllegalStateException(
                    "Fornecedor de e-mail do cadastro inválido: " + cadastroEmailProperties.getFornecedor());
        };
    }

    @Bean
    public CanalNotificacaoTentativaCadastroEmail canalNotificacaoTentativaCadastroEmail(
            final CadastroEmailProperties cadastroEmailProperties,
            final ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        Objects.requireNonNull(cadastroEmailProperties, "cadastroEmailProperties é obrigatório");
        String fornecedor = Objects.requireNonNullElse(cadastroEmailProperties.getFornecedor(), "log")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (fornecedor) {
            case "log" -> new CanalNotificacaoTentativaCadastroEmailLog();
            case "smtp" -> new CanalNotificacaoTentativaCadastroEmailSmtp(
                    Objects.requireNonNull(
                            javaMailSenderProvider.getIfAvailable(),
                            "Fornecedor SMTP configurado, mas nenhum JavaMailSender foi inicializado. "
                                    + "Revise spring.mail.host, spring.mail.port e credenciais."
                    ),
                    cadastroEmailProperties
            );
            default -> throw new IllegalStateException(
                    "Fornecedor de e-mail do cadastro inválido: " + cadastroEmailProperties.getFornecedor());
        };
    }
}

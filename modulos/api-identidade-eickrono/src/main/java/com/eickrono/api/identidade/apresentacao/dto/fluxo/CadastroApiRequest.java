package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro;
import com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CadastroApiRequest(
        @NotNull TipoPessoaCadastro tipoPessoa,
        @NotBlank String nomeCompleto,
        String nomeFantasia,
        @NotBlank String usuario,
        SexoPessoaCadastro sexo,
        String paisNascimento,
        LocalDate dataNascimento,
        @NotBlank String emailPrincipal,
        @NotBlank String telefone,
        @NotNull CanalValidacaoTelefoneCadastro tipoValidacaoTelefone,
        @NotBlank String senha,
        @NotBlank String confirmacaoSenha,
        @AssertTrue boolean aceitouTermos,
        @AssertTrue boolean aceitouPrivacidade,
        @NotNull PlataformaAtestacaoApp plataformaApp,
        @Valid @NotNull AtestacaoOperacaoApiRequest atestacao
) {
}

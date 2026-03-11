package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PessoaRepositorio;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisiona e sincroniza a identidade raiz (Pessoa) e a projeção legada de PerfilIdentidade.
 */
@Service
public class ProvisionamentoIdentidadeService {

    private static final String PROVEDOR_EMAIL = "EMAIL";

    private final PessoaRepositorio pessoaRepositorio;
    private final FormaAcessoRepositorio formaAcessoRepositorio;
    private final PerfilIdentidadeRepositorio perfilIdentidadeRepositorio;

    public ProvisionamentoIdentidadeService(PessoaRepositorio pessoaRepositorio,
                                            FormaAcessoRepositorio formaAcessoRepositorio,
                                            PerfilIdentidadeRepositorio perfilIdentidadeRepositorio) {
        this.pessoaRepositorio = pessoaRepositorio;
        this.formaAcessoRepositorio = formaAcessoRepositorio;
        this.perfilIdentidadeRepositorio = perfilIdentidadeRepositorio;
    }

    @Transactional
    public Pessoa provisionarOuAtualizar(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt é obrigatório");
        return provisionarOuAtualizar(
                jwt.getSubject(),
                extrairEmail(jwt),
                extrairNome(jwt),
                extrairColecao(jwt, "perfis"),
                extrairColecao(jwt, "papeis"),
                OffsetDateTime.now());
    }

    @Transactional
    public Pessoa provisionarOuAtualizar(String sub, String email, String nome, Set<String> perfis,
                                         Set<String> papeis, OffsetDateTime atualizadoEm) {
        String subNormalizado = obrigatorio(sub, "sub");
        String emailNormalizado = obrigatorio(email, "email").toLowerCase();
        String nomeNormalizado = obrigatorio(nome, "nome");
        OffsetDateTime instante = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");

        Pessoa pessoa = pessoaRepositorio.findBySub(subNormalizado)
                .map(existente -> {
                    existente.atualizar(emailNormalizado, nomeNormalizado, perfis, papeis, instante);
                    return existente;
                })
                .orElseGet(() -> new Pessoa(subNormalizado, emailNormalizado, nomeNormalizado, perfis, papeis, instante));

        Pessoa salva = salvarPessoa(pessoa);
        sincronizarFormaAcessoEmail(salva, emailNormalizado, instante);
        sincronizarPerfilLegado(salva);
        return salva;
    }

    @Transactional
    public FormaAcesso registrarFormaAcessoSocial(Pessoa pessoa, String provedor, String identificador,
                                                  OffsetDateTime vinculadoEm) {
        Pessoa pessoaObrigatoria = Objects.requireNonNull(pessoa, "pessoa é obrigatória");
        String provedorNormalizado = obrigatorio(provedor, "provedor").toUpperCase();
        String identificadorNormalizado = obrigatorio(identificador, "identificador");
        OffsetDateTime instante = Objects.requireNonNull(vinculadoEm, "vinculadoEm é obrigatório");

        Optional<FormaAcesso> conflito = formaAcessoRepositorio.findByTipoAndProvedorAndIdentificador(
                TipoFormaAcesso.SOCIAL, provedorNormalizado, identificadorNormalizado);
        if (conflito.isPresent()) {
            FormaAcesso forma = conflito.orElseThrow();
            if (!Objects.equals(forma.getPessoa().getId(), pessoaObrigatoria.getId())) {
                throw new IllegalStateException("Forma de acesso social já vinculada a outra pessoa");
            }
            return forma;
        }

        FormaAcesso forma = new FormaAcesso(
                pessoaObrigatoria,
                TipoFormaAcesso.SOCIAL,
                provedorNormalizado,
                identificadorNormalizado,
                false,
                instante,
                instante);
        return formaAcessoRepositorio.save(forma);
    }

    private void sincronizarFormaAcessoEmail(Pessoa pessoa, String email, OffsetDateTime atualizadoEm) {
        Optional<FormaAcesso> conflito = formaAcessoRepositorio.findByTipoAndProvedorAndIdentificador(
                TipoFormaAcesso.EMAIL_SENHA, PROVEDOR_EMAIL, email);
        if (conflito.isPresent() && !Objects.equals(conflito.orElseThrow().getPessoa().getId(), pessoa.getId())) {
            throw new IllegalStateException("E-mail já vinculado a outra pessoa");
        }

        FormaAcesso forma = formaAcessoRepositorio
                .findByPessoaAndTipoAndPrincipalTrue(pessoa, TipoFormaAcesso.EMAIL_SENHA)
                .orElseGet(() -> new FormaAcesso(
                        pessoa,
                        TipoFormaAcesso.EMAIL_SENHA,
                        PROVEDOR_EMAIL,
                        email,
                        true,
                        atualizadoEm,
                        atualizadoEm));

        forma.atualizarIdentificador(email, true, atualizadoEm);
        formaAcessoRepositorio.save(forma);
    }

    private Pessoa salvarPessoa(Pessoa pessoa) {
        return Objects.requireNonNull(
                pessoaRepositorio.save(Objects.requireNonNull(pessoa, "pessoa é obrigatória")),
                "pessoa salva é obrigatória");
    }

    private void sincronizarPerfilLegado(Pessoa pessoa) {
        PerfilIdentidade perfil = perfilIdentidadeRepositorio.findBySub(pessoa.getSub())
                .orElseGet(() -> new PerfilIdentidade(
                        pessoa.getSub(),
                        pessoa.getEmail(),
                        pessoa.getNome(),
                        pessoa.getPerfis(),
                        pessoa.getPapeis(),
                        pessoa.getAtualizadoEm()));
        perfil.atualizarPerfil(
                pessoa.getEmail(),
                pessoa.getNome(),
                pessoa.getPerfis(),
                pessoa.getPapeis(),
                pessoa.getAtualizadoEm());
        perfilIdentidadeRepositorio.save(perfil);
    }

    private String extrairEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("JWT não contém e-mail para provisionamento");
        }
        return email;
    }

    private String extrairNome(Jwt jwt) {
        String nome = jwt.getClaimAsString("name");
        if (nome != null && !nome.isBlank()) {
            return nome;
        }
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        return extrairEmail(jwt);
    }

    private Set<String> extrairColecao(Jwt jwt, String claim) {
        Object valor = jwt.getClaims().get(claim);
        if (valor instanceof Collection<?> colecao) {
            return colecao.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (valor instanceof String texto && !texto.isBlank()) {
            return Set.of(texto);
        }
        return Set.of();
    }

    private String obrigatorio(String valor, String nomeCampo) {
        String texto = Objects.requireNonNull(valor, nomeCampo + " é obrigatório").trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException(nomeCampo + " é obrigatório");
        }
        return texto;
    }
}

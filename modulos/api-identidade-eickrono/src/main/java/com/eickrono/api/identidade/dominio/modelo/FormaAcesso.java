package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Forma de acesso vinculada à pessoa, como e-mail/senha ou login social.
 */
@Entity
@Table(name = "pessoas_formas_acesso")
public class FormaAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFormaAcesso tipo;

    @Column(nullable = false)
    private String provedor;

    @Column(nullable = false)
    private String identificador;

    @Column(nullable = false)
    private boolean principal;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "verificado_em")
    private OffsetDateTime verificadoEm;

    protected FormaAcesso() {
        // Construtor protegido para JPA.
    }

    public FormaAcesso(Pessoa pessoa, TipoFormaAcesso tipo, String provedor, String identificador,
                       boolean principal, OffsetDateTime criadoEm, OffsetDateTime verificadoEm) {
        this.pessoa = Objects.requireNonNull(pessoa, "pessoa é obrigatória");
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.provedor = Objects.requireNonNull(provedor, "provedor é obrigatório");
        this.identificador = Objects.requireNonNull(identificador, "identificador é obrigatório");
        this.principal = principal;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.verificadoEm = verificadoEm;
    }

    public Long getId() {
        return id;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public TipoFormaAcesso getTipo() {
        return tipo;
    }

    public String getProvedor() {
        return provedor;
    }

    public String getIdentificador() {
        return identificador;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getVerificadoEm() {
        return verificadoEm;
    }

    public void atualizarIdentificador(String novoIdentificador, boolean novoPrincipal, OffsetDateTime novoVerificadoEm) {
        this.identificador = Objects.requireNonNull(novoIdentificador, "identificador é obrigatório");
        this.principal = novoPrincipal;
        this.verificadoEm = novoVerificadoEm;
    }
}

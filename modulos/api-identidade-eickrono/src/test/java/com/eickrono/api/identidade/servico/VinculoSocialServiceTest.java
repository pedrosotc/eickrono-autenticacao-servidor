package com.eickrono.api.identidade.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.VinculoSocialRepositorio;
import com.eickrono.api.identidade.dto.CriarVinculoSocialRequisicao;
import com.eickrono.api.identidade.dto.VinculoSocialDto;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class VinculoSocialServiceTest {

    @Mock
    private PerfilIdentidadeRepositorio perfilRepositorio;
    @Mock
    private ProvisionamentoIdentidadeService provisionamentoIdentidadeService;

    private VinculoSocialService vinculoSocialService;
    private final List<VinculoSocial> vinculosPersistidos = new ArrayList<>();
    private final List<AuditoriaEventoIdentidade> auditorias = new ArrayList<>();
    private long proximoIdVinculo = 42L;

    private PerfilIdentidadeRepositorio perfilRepositorio() {
        return Objects.requireNonNull(perfilRepositorio);
    }

    private VinculoSocialRepositorio vinculoRepositorio() {
        return (VinculoSocialRepositorio) Proxy.newProxyInstance(
                VinculoSocialRepositorio.class.getClassLoader(),
                new Class<?>[] {VinculoSocialRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByPerfil" -> vinculosPersistidos.stream()
                            .filter(vinculo -> vinculo.getPerfil().equals(Objects.requireNonNull(args)[0]))
                            .toList();
                    case "save" -> salvarVinculo((VinculoSocial) Objects.requireNonNull(args)[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "VinculoSocialRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio() {
        return (AuditoriaEventoIdentidadeRepositorio) Proxy.newProxyInstance(
                AuditoriaEventoIdentidadeRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaEventoIdentidadeRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        AuditoriaEventoIdentidade auditoria = (AuditoriaEventoIdentidade) Objects.requireNonNull(args)[0];
                        auditorias.add(auditoria);
                        yield auditoria;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AuditoriaEventoIdentidadeRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private void inicializarServico() {
        vinculosPersistidos.clear();
        auditorias.clear();
        proximoIdVinculo = 42L;

        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositorio());
        vinculoSocialService = new VinculoSocialService(
                perfilRepositorio(),
                vinculoRepositorio(),
                auditoriaService,
                Objects.requireNonNull(provisionamentoIdentidadeService));
    }

    @Test
    @DisplayName("listar vínculos sociais: deve converter vínculos persistidos em DTOs ordenados")
    void deveRetornarVinculosDoPerfil() throws Exception {
        inicializarServico();
        PerfilIdentidade perfil = criarPerfil();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);
        when(perfilRepositorio().findBySub("sub-123")).thenReturn(Optional.of(perfil));

        vinculosPersistidos.add(criarVinculo(perfil, 1L, "GOOGLE", "123"));
        vinculosPersistidos.add(criarVinculo(perfil, 2L, "GITHUB", "abc"));

        List<VinculoSocialDto> resultado = vinculoSocialService.listar(jwt);

        assertThat(resultado)
                .extracting(VinculoSocialDto::id, VinculoSocialDto::provedor, VinculoSocialDto::identificador)
                .containsExactly(tuple(1L, "GOOGLE", "123"), tuple(2L, "GITHUB", "abc"));
    }

    @Test
    @DisplayName("listar vínculos sociais: deve lançar exceção quando perfil do usuário não existir")
    void deveLancarQuandoPerfilNaoExistir() {
        inicializarServico();
        Jwt jwt = jwt("sub-inexistente");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(criarPessoa());
        when(perfilRepositorio().findBySub("sub-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vinculoSocialService.listar(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Perfil não encontrado");
        assertThat(vinculosPersistidos).isEmpty();
    }

    @Test
    @DisplayName("criar vínculo social: deve persistir vínculo e registrar auditoria")
    void devePersistirVinculo() {
        inicializarServico();
        PerfilIdentidade perfil = criarPerfil();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);
        when(perfilRepositorio().findBySub("sub-123")).thenReturn(Optional.of(perfil));

        VinculoSocialDto dto = vinculoSocialService.criar(jwt, new CriarVinculoSocialRequisicao("GOOGLE", "123456"));

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.provedor()).isEqualTo("GOOGLE");
        assertThat(dto.identificador()).isEqualTo("123456");
        assertThat(auditorias).hasSize(1);
        assertThat(auditorias.getFirst().getTipoEvento()).isEqualTo("VINCULO_SOCIAL_CRIADO");
        verify(provisionamentoIdentidadeService).registrarFormaAcessoSocial(
                pessoa,
                "GOOGLE",
                "123456",
                dto.vinculadoEm());
    }

    @Test
    @DisplayName("criar vínculo social: deve recusar criação caso o perfil não seja encontrado")
    void deveLancarQuandoPerfilNaoExiste() {
        inicializarServico();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(criarPessoa());
        when(perfilRepositorio().findBySub("sub-123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vinculoSocialService.criar(jwt, new CriarVinculoSocialRequisicao("GOOGLE", "123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Perfil não encontrado");
        assertThat(vinculosPersistidos).isEmpty();
    }

    private PerfilIdentidade criarPerfil() {
        return new PerfilIdentidade(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.parse("2024-05-01T12:00:00Z"));
    }

    private Pessoa criarPessoa() {
        return new Pessoa(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.parse("2024-05-01T12:00:00Z"));
    }

    private Jwt jwt(String sub) {
        return Jwt.withTokenValue("token")
                .subject(sub)
                .claim("email", "teste@eickrono.com")
                .claim("name", "Pessoa Teste")
                .header("alg", "none")
                .build();
    }

    private VinculoSocial criarVinculo(PerfilIdentidade perfil, Long id, String provedor, String identificador) throws Exception {
        VinculoSocial vinculo = new VinculoSocial(perfil, provedor, identificador, OffsetDateTime.parse("2024-05-02T15:00:00Z"));
        definirId(vinculo, id);
        return vinculo;
    }

    private VinculoSocial salvarVinculo(VinculoSocial vinculo) throws Exception {
        VinculoSocial salvo = Objects.requireNonNull(vinculo);
        if (salvo.getId() == null) {
            definirId(salvo, proximoIdVinculo++);
        }
        vinculosPersistidos.removeIf(existente -> Objects.equals(existente.getId(), salvo.getId()));
        vinculosPersistidos.add(salvo);
        return salvo;
    }

    private void definirId(VinculoSocial vinculo, Long id) throws Exception {
        Field field = VinculoSocial.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(vinculo, id);
    }
}

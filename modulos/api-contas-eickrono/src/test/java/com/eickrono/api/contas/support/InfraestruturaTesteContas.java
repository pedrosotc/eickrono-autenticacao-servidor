package com.eickrono.api.contas.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;
import org.testcontainers.containers.PostgreSQLContainer;

public final class InfraestruturaTesteContas {

    private static final String DEFAULT_POSTGRES_IMAGE = "postgres:15.5";
    private static final String DEFAULT_POSTGRES_DATABASE = "eickrono_contas_test";
    private static final String DEFAULT_POSTGRES_USERNAME = "test";
    private static final String DEFAULT_POSTGRES_PASSWORD = "test";
    private static PostgreSQLContainer<?> postgres;

    private InfraestruturaTesteContas() {
    }

    public static final class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(@NonNull ConfigurableApplicationContext context) {
            iniciarPostgres();
            TestPropertyValues.of(
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword(),
                    "spring.datasource.driver-class-name=" + postgres.getDriverClassName(),
                    "spring.flyway.enabled=true",
                    "spring.jpa.hibernate.ddl-auto=validate"
            ).applyTo(context.getEnvironment());
            context.addApplicationListener(new EncerramentoInfraestruturaListener());
        }
    }

    public static final class EncerramentoInfraestruturaListener implements ApplicationListener<ContextClosedEvent> {
        @Override
        public void onApplicationEvent(@NonNull ContextClosedEvent event) {
            encerrarInfraestrutura();
        }
    }

    public static void encerrarInfraestrutura() {
        if (postgres != null) {
            try {
                postgres.close();
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao encerrar container PostgreSQL de testes da API de contas", e);
            } finally {
                postgres = null;
            }
        }
    }

    private static void iniciarPostgres() {
        if (postgres == null) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(obterVariavelAmbiente(
                    "EICKRONO_TEST_POSTGRES_IMAGE",
                    DEFAULT_POSTGRES_IMAGE));
            container.withDatabaseName(obterVariavelAmbiente(
                    "EICKRONO_TEST_POSTGRES_DB_CONTAS",
                    obterVariavelAmbiente("POSTGRES_DB", DEFAULT_POSTGRES_DATABASE)));
            container.withUsername(obterVariavelAmbiente("POSTGRES_USER", DEFAULT_POSTGRES_USERNAME));
            container.withPassword(obterVariavelAmbiente("POSTGRES_PASSWORD", DEFAULT_POSTGRES_PASSWORD));
            postgres = container;
        }
        if (!postgres.isRunning()) {
            postgres.start();
        }
    }

    private static String obterVariavelAmbiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        return valor;
    }
}

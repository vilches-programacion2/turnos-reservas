package reservas.integracion.infrastructure.kafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import reservas.integracion.application.exception.IntegracionCatedraException;
import reservas.integracion.application.exception.IntegracionCatedraException.Error;
import reservas.integracion.application.usecases.PrepararIntegracionCatedraUseCaseImpl;
import reservas.integracion.domain.model.EstadoProvisionamiento;
import reservas.integracion.domain.model.IntegracionCatedra;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KafkaCatedraAdapterTest {
    private static final List<String> TOPICS = List.of("acciones-asignadas", "telefono-asignado");
    private Admin admin;
    private ClienteControlado adapter;

    @BeforeEach
    void preparar() {
        admin = mock(Admin.class);
        adapter = new ClienteControlado(propiedades(Duration.ofSeconds(10)), admin);
    }

    @Test
    void usaConfiguracionAsignadaYArrancaConsumidorDeAcciones() {
        metadataCorrecta();
        var integracion = integracion();
        adapter.preparar(integracion);
        assertThat(adapter.adminConfig).containsEntry("bootstrap.servers", "broker.test:9092")
                .containsEntry("security.protocol", "PLAINTEXT").containsEntry("retries", 2)
                .containsEntry("default.api.timeout.ms", 10000).doesNotContainValue(integracion.getRedisPassword());
        assertThat(adapter.productores().getConfigurationProperties())
                .containsEntry("acks", "all").containsEntry("enable.idempotence", true)
                .containsEntry("retries", 2).containsEntry("delivery.timeout.ms", 10000)
                .containsEntry("key.serializer", StringSerializer.class).containsEntry("value.serializer", StringSerializer.class);
        assertThat(adapter.consumidores().getConfigurationProperties())
                .containsEntry("group.id", "grupo-asignado").containsEntry("enable.auto.commit", false)
                .containsEntry("allow.auto.create.topics", false).containsEntry("auto.offset.reset", "earliest")
                .containsEntry("key.deserializer", StringDeserializer.class)
                .containsEntry("value.deserializer", StringDeserializer.class);
        verify(admin).describeTopics(TOPICS);
        verify(admin).close(Duration.ofSeconds(1));
        verifyNoMoreInteractions(admin);
        assertThat(adapter.topicAcciones).isEqualTo(TOPICS.get(0));
        verify(adapter.listener).start();
        adapter.cerrar();
        verify(adapter.listener).stop();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProvisionamiento.class, names = {"PENDING", "FAILED", "REVOKED"})
    void noAbreKafkaSiLaCuentaNoEstaProvisionada(EstadoProvisionamiento estado) {
        var integracion = integracion();
        integracion.setProvisioningStatus(estado);
        adapter.preparar(integracion);
        verifyNoInteractions(admin);
        assertThat(adapter.abiertos).hasValue(0);
        assertNoDisponible();
    }

    @Test
    void revocarLaIntegracionRetiraLasFactoriesDisponibles() {
        metadataCorrecta();
        var integracion = integracion();
        adapter.preparar(integracion);
        assertThat(adapter.productores()).isNotNull();
        integracion.setProvisioningStatus(EstadoProvisionamiento.REVOKED);
        adapter.preparar(integracion);
        assertNoDisponible();
        assertThat(adapter.abiertos).hasValue(1);
    }

    @Test
    void falloDeMetadataNoFiltraLaCausaNiDejaFactoriesHabilitadas() {
        var future = new KafkaFutureImpl<Map<String, TopicDescription>>();
        future.completeExceptionally(new IllegalStateException("secreto-de-prueba-no-publicar"));
        responder(future);
        assertThatThrownBy(() -> adapter.preparar(integracion()))
                .isInstanceOfSatisfying(IntegracionCatedraException.class, e -> {
                    assertThat(e.getError()).isEqualTo(Error.KAFKA_NO_DISPONIBLE);
                    assertThat(e.getCause()).isNull();
                    assertThat(e.getMessage()).doesNotContain("secreto-de-prueba");
                });
        assertNoDisponible();
        verify(admin).close(Duration.ofSeconds(1));
    }

    @Test
    void timeoutDeMetadataEsAcotadoYCierraElCliente() {
        adapter = new ClienteControlado(propiedades(Duration.ofMillis(30)), admin);
        responder(new KafkaFutureImpl<>());
        assertThatThrownBy(() -> adapter.preparar(integracion()))
                .isInstanceOfSatisfying(IntegracionCatedraException.class,
                        e -> assertThat(e.getError()).isEqualTo(Error.KAFKA_NO_DISPONIBLE));
        verify(admin).close(Duration.ofSeconds(1));
        assertNoDisponible();
    }

    @Test
    void ausenciaDeUnoDeLosTopicsNoSeConfundeConConexionLista() {
        var future = new KafkaFutureImpl<Map<String, TopicDescription>>();
        future.complete(Map.of(TOPICS.getFirst(), descripcion()));
        responder(future);
        assertThatThrownBy(() -> adapter.preparar(integracion()))
                .isInstanceOfSatisfying(IntegracionCatedraException.class,
                        e -> assertThat(e.getError()).isEqualTo(Error.KAFKA_NO_DISPONIBLE));
        assertNoDisponible();
    }

    @Test
    void falloRestNoIntentaPrepararKafka() {
        var preparaciones = new AtomicInteger();
        var useCase = new PrepararIntegracionCatedraUseCaseImpl(
                () -> { throw new IntegracionCatedraException(Error.NO_AUTENTICADO, 401); },
                config -> preparaciones.incrementAndGet());
        assertThatThrownBy(useCase::preparar).isInstanceOf(IntegracionCatedraException.class);
        assertThat(preparaciones).hasValue(0);
        verifyNoInteractions(admin);
    }

    @Test
    void rechazaUnTimeoutOperativoMenorQueElDeSolicitud() {
        assertThatThrownBy(() -> new CatedraKafkaProperties("PLAINTEXT", Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMillis(250), 2)).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertNoDisponible() {
        assertThatThrownBy(adapter::productores).isInstanceOf(IntegracionCatedraException.class);
        assertThatThrownBy(adapter::consumidores).isInstanceOf(IntegracionCatedraException.class);
    }

    private void metadataCorrecta() {
        var future = new KafkaFutureImpl<Map<String, TopicDescription>>();
        future.complete(Map.of(TOPICS.get(0), descripcion(), TOPICS.get(1), descripcion()));
        responder(future);
    }

    private TopicDescription descripcion() {
        var descripcion = mock(TopicDescription.class);
        var node = new Node(0, "broker.test", 9092);
        when(descripcion.partitions()).thenReturn(List.of(new TopicPartitionInfo(0, node, List.of(node), List.of(node))));
        return descripcion;
    }

    private void responder(KafkaFutureImpl<Map<String, TopicDescription>> future) {
        var resultado = mock(DescribeTopicsResult.class);
        when(resultado.allTopicNames()).thenReturn(future);
        when(admin.describeTopics(anyCollection())).thenReturn(resultado);
    }

    private CatedraKafkaProperties propiedades(Duration timeout) {
        return new CatedraKafkaProperties("PLAINTEXT", Duration.ofMillis(10), timeout, Duration.ofMillis(1), 2);
    }

    private IntegracionCatedra integracion() {
        return IntegracionCatedra.builder().provisioningStatus(EstadoProvisionamiento.PROVISIONED)
                .kafkaBootstrapServers("broker.test:9092").kafkaConsumerGroupId("grupo-asignado")
                .kafkaAppointmentActionsTopic(TOPICS.get(0)).kafkaAppointmentPhoneTopic(TOPICS.get(1))
                .redisPassword("secreto-redis-no-es-credencial-kafka").build();
    }

    private static class ClienteControlado extends KafkaCatedraAdapter {
        private final Admin admin;
        private final AtomicInteger abiertos = new AtomicInteger();
        private final KafkaMessageListenerContainer<String, String> listener = mock();
        private Map<String, Object> adminConfig;
        private String topicAcciones;

        ClienteControlado(CatedraKafkaProperties properties, Admin admin) {
            super(properties, mock(), JsonMapper.builder().build());
            this.admin = admin;
        }

        @Override
        protected KafkaMessageListenerContainer<String, String> crearConsumidor(ConsumerFactory<String, String> factory, String topic) {
            topicAcciones = topic;
            return listener;
        }

        @Override
        protected Admin abrirAdmin(Map<String, Object> configuracion) {
            abiertos.incrementAndGet();
            adminConfig = Map.copyOf(configuracion);
            return admin;
        }
    }
}

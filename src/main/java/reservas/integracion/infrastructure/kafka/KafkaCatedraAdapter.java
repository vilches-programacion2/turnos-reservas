package reservas.integracion.infrastructure.kafka;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Component;
import reservas.integracion.application.exception.IntegracionCatedraException;
import reservas.integracion.application.exception.IntegracionCatedraException.Error;
import reservas.integracion.domain.model.EstadoProvisionamiento;
import reservas.integracion.domain.model.IntegracionCatedra;
import reservas.integracion.domain.ports.out.PrepararKafkaCatedraPort;
import reservas.procesoreserva.domain.ports.out.PublicarTelefonoPort;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CatedraKafkaProperties.class)
public class KafkaCatedraAdapter implements PrepararKafkaCatedraPort, PublicarTelefonoPort {
    private final CatedraKafkaProperties properties;
    private final AcknowledgingMessageListener<String, String> acciones;
    private final JsonMapper json;
    private volatile Conexion conexion;

    @Override
    public synchronized void preparar(IntegracionCatedra integracion) {
        cerrar();
        if (integracion.getProvisioningStatus() != EstadoProvisionamiento.PROVISIONED) return;

        var comunes = new HashMap<String, Object>();
        comunes.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, integracion.getKafkaBootstrapServers());
        comunes.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, properties.securityProtocol());
        comunes.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, Math.toIntExact(properties.requestTimeout().toMillis()));
        comunes.put(CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG, properties.retryBackoff().toMillis());

        var adminConfig = new HashMap<>(comunes);
        adminConfig.put(AdminClientConfig.CLIENT_ID_CONFIG, "turnos-reservas-verificacion");
        adminConfig.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Math.toIntExact(properties.operationTimeout().toMillis()));
        adminConfig.put(AdminClientConfig.RETRIES_CONFIG, properties.retries());
        var topics = List.of(integracion.getKafkaAppointmentActionsTopic(), integracion.getKafkaAppointmentPhoneTopic());
        try (var recurso = new AdminAcotado(abrirAdmin(adminConfig))) {
            var metadata = recurso.admin().describeTopics(topics).allTopicNames()
                    .get(properties.operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!metadata.keySet().containsAll(topics) || metadata.values().stream().anyMatch(topic -> topic.partitions().isEmpty())) {
                throw new IntegracionCatedraException(Error.KAFKA_NO_DISPONIBLE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntegracionCatedraException(Error.KAFKA_NO_DISPONIBLE);
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            throw new IntegracionCatedraException(Error.KAFKA_NO_DISPONIBLE);
        }

        var producer = new HashMap<>(comunes);
        producer.put(ProducerConfig.ACKS_CONFIG, "all");
        producer.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producer.put(ProducerConfig.RETRIES_CONFIG, properties.retries());
        producer.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        producer.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Math.toIntExact(properties.operationTimeout().toMillis()));
        producer.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, properties.operationTimeout().toMillis());
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        var consumer = new HashMap<>(comunes);
        consumer.put(ConsumerConfig.GROUP_ID_CONFIG, integracion.getKafkaConsumerGroupId());
        consumer.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Math.toIntExact(properties.operationTimeout().toMillis()));
        consumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumer.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        consumer.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "classic");
        consumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        var productores = new DefaultKafkaProducerFactory<String, String>(producer);
        var consumidores = new DefaultKafkaConsumerFactory<String, String>(consumer);
        var listener = crearConsumidor(consumidores, integracion.getKafkaAppointmentActionsTopic());
        try {
            listener.start();
        } catch (RuntimeException e) {
            listener.stop();
            productores.destroy();
            throw new IntegracionCatedraException(Error.KAFKA_NO_DISPONIBLE);
        }
        var template = new KafkaTemplate<String, String>(productores);
        template.setProducerListener(null);
        conexion = new Conexion(productores, consumidores, listener, template,
                integracion.getGroupId(), integracion.getKafkaAppointmentPhoneTopic());
    }

    @Override
    public Resultado publicar(EnvioTelefono envio) {
        var actual = conexion;
        if (actual == null) return Resultado.NO_INICIADO;
        String mensaje = json.writeValueAsString(Map.of(
                "eventType", "AdditionalInformationSubmitted",
                "eventId", envio.eventId().toString(),
                "groupId", actual.groupId(),
                "reservationProcessId", envio.reservationProcessId(),
                "holdId", envio.holdId(),
                "requestEventId", envio.requestEventId(),
                "information", Map.of("phoneNumber", envio.phoneNumber()),
                "occurredAt", envio.occurredAt().toString(),
                "schemaVersion", 1));
        try {
            actual.template().send(actual.telefonoTopic(), envio.reservationProcessId(), mensaje)
                    .get(properties.operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return Resultado.PUBLICADO;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.INDETERMINADO;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            return Resultado.INDETERMINADO;
        }
    }

    protected KafkaMessageListenerContainer<String, String> crearConsumidor(ConsumerFactory<String, String> factory, String topic) {
        var props = new ContainerProperties(topic);
        props.setMessageListener(acciones);
        props.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        props.setSyncCommits(true);
        props.setSyncCommitTimeout(properties.operationTimeout());
        var contenedor = new KafkaMessageListenerContainer<>(factory, props);
        var errores = new CommonContainerStoppingErrorHandler() {
            @Override
            public boolean isAckAfterHandle() {
                return false;
            }
        };
        contenedor.setCommonErrorHandler(errores);
        return contenedor;
    }

    public ProducerFactory<String, String> productores() {
        var actual = conexion;
        if (actual == null) throw new IntegracionCatedraException(Error.KAFKA_NO_DISPONIBLE);
        return actual.productores();
    }

    public ConsumerFactory<String, String> consumidores() {
        var actual = conexion;
        if (actual == null) throw new IntegracionCatedraException(Error.KAFKA_NO_DISPONIBLE);
        return actual.consumidores();
    }

    protected Admin abrirAdmin(Map<String, Object> configuracion) {
        return Admin.create(configuracion);
    }

    @PreDestroy
    public synchronized void cerrar() {
        var anterior = conexion;
        conexion = null;
        if (anterior != null) {
            anterior.listener().stop();
            anterior.productores().destroy();
        }
    }

    private record Conexion(DefaultKafkaProducerFactory<String, String> productores,
            DefaultKafkaConsumerFactory<String, String> consumidores, KafkaMessageListenerContainer<String, String> listener,
            KafkaTemplate<String, String> template, String groupId, String telefonoTopic) {
    }

    private record AdminAcotado(Admin admin) implements AutoCloseable {
        @Override
        public void close() {
            admin.close(Duration.ofSeconds(1));
        }
    }
}

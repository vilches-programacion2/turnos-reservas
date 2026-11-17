package reservas.integracion.infrastructure.client.mapper;

import org.springframework.stereotype.Component;
import reservas.integracion.application.exception.IntegracionCatedraException;
import reservas.integracion.application.exception.IntegracionCatedraException.Error;
import reservas.integracion.domain.model.EstadoProvisionamiento;
import reservas.integracion.domain.model.IntegracionCatedra;
import reservas.integracion.infrastructure.client.dto.IntegracionCatedraResponse;

@Component
public class IntegracionCatedraMapper {
    public IntegracionCatedra toDomain(IntegracionCatedraResponse response) {
        if (response == null || vacio(response.getGroupId())) {
            throw new IntegracionCatedraException(Error.RESPUESTA_INVALIDA);
        }
        EstadoProvisionamiento estado;
        try {
            estado = EstadoProvisionamiento.valueOf(response.getProvisioningStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IntegracionCatedraException(Error.RESPUESTA_INVALIDA);
        }
        if (estado == EstadoProvisionamiento.PROVISIONED
                && (vacio(response.getRedisHost()) || response.getRedisPort() == null
                || response.getRedisPort() < 1 || response.getRedisPort() > 65535
                || vacio(response.getRedisUsername()) || vacio(response.getRedisPassword())
                || vacio(response.getRedisReadNamespace()) || vacio(response.getRedisWriteNamespace())
                || vacio(response.getKafkaBootstrapServers()) || vacio(response.getKafkaConsumerGroupId())
                || vacio(response.getKafkaCatalogTopic()) || vacio(response.getKafkaAppointmentActionsTopic())
                || vacio(response.getKafkaAppointmentPhoneTopic()))) {
            throw new IntegracionCatedraException(Error.RESPUESTA_INVALIDA);
        }
        return IntegracionCatedra.builder()
                .groupId(response.getGroupId())
                .redisHost(response.getRedisHost())
                .redisPort(response.getRedisPort())
                .redisUsername(response.getRedisUsername())
                .redisPassword(response.getRedisPassword())
                .redisReadNamespace(response.getRedisReadNamespace())
                .redisWriteNamespace(response.getRedisWriteNamespace())
                .kafkaBootstrapServers(response.getKafkaBootstrapServers())
                .kafkaConsumerGroupId(response.getKafkaConsumerGroupId())
                .kafkaCatalogTopic(response.getKafkaCatalogTopic())
                .kafkaAppointmentActionsTopic(response.getKafkaAppointmentActionsTopic())
                .kafkaAppointmentPhoneTopic(response.getKafkaAppointmentPhoneTopic())
                .provisioningStatus(estado)
                .build();
    }

    private boolean vacio(String value) {
        return value == null || value.isBlank();
    }
}

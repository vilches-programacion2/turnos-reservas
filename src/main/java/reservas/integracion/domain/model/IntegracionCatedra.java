package reservas.integracion.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegracionCatedra {
    private String groupId;
    private String redisHost;
    private Integer redisPort;
    private String redisUsername;
    private String redisPassword;
    private String redisReadNamespace;
    private String redisWriteNamespace;
    private String kafkaBootstrapServers;
    private String kafkaConsumerGroupId;
    private String kafkaCatalogTopic;
    private String kafkaAppointmentActionsTopic;
    private String kafkaAppointmentPhoneTopic;
    private EstadoProvisionamiento provisioningStatus;
}

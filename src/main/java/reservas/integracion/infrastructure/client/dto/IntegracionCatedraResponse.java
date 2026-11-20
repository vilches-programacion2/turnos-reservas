package reservas.integracion.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegracionCatedraResponse {
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
    private String provisioningStatus;
}

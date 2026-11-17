package reservas.shared.infrastructure.catedra;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatedraProperties.class)
public class CatedraConfiguration {
    @Bean(destroyMethod = "close")
    HttpClient catedraHttpClient(CatedraProperties properties) {
        properties.validar();
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    RestClient catedraRestClient(CatedraProperties properties, HttpClient catedraHttpClient) {
        var factory = new JdkClientHttpRequestFactory(catedraHttpClient);
        factory.setReadTimeout(properties.getReadTimeout());
        var builder = RestClient.builder().requestFactory(factory);
        if (properties.isEnabled()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        return builder.build();
    }
}

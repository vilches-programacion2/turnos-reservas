package reservas.catalogo.infrastructure.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogoProperties.class)
public class CatalogoConfiguration {
    @Bean(destroyMethod = "close")
    HttpClient catalogoHttpClient(CatalogoProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    RestClient catalogoRestClient(CatalogoProperties properties, HttpClient catalogoHttpClient) {
        var factory = new JdkClientHttpRequestFactory(catalogoHttpClient);
        factory.setReadTimeout(properties.readTimeout());
        var builder = RestClient.builder().requestFactory(factory);
        if (properties.configurado()) {
            builder.baseUrl(properties.baseUrl());
        }
        return builder.build();
    }
}

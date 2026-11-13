package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({SubscriptionProperties.class, PaddleProperties.class, EarlyEndProperties.class})
public class SubscriptionConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** Version of the Paddle API the client is written for; pinned, so a new account default changes nothing. */
    static final String PADDLE_VERSION = "1";

    /** HTTP client for the Paddle API; short timeouts, so a slow provider does not block request threads for long. */
    @Bean
    @Conditional(OperatingModeCondition.Portal.class)
    public RestClient paddleRestClient(PaddleProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader("Paddle-Version", PADDLE_VERSION)
                .requestFactory(requestFactory)
                .build();
    }
}

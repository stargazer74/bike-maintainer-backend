package de.chriswohlbrecht.maintenance.cucumber.support;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

@Lazy
@Component
public class ApiClient {

    private final RestTestClient restTestClient;

    public ApiClient(@LocalServerPort int port) {
        this.restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    public EntityExchangeResult<byte[]> get(String path) {
        return restTestClient.get().uri(path).exchange().returnResult(byte[].class);
    }

    public EntityExchangeResult<byte[]> post(String path, String jsonBody) {
        return restTestClient.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange()
                .returnResult(byte[].class);
    }

    public EntityExchangeResult<byte[]> put(String path, String jsonBody) {
        return restTestClient.put().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange()
                .returnResult(byte[].class);
    }

    public EntityExchangeResult<byte[]> delete(String path) {
        return restTestClient.delete().uri(path).exchange().returnResult(byte[].class);
    }
}

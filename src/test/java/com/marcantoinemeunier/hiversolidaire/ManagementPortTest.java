package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class ManagementPortTest {

    @Value("${local.management.port}")
    int managementPort;

    @Value("${local.server.port}")
    int serverPort;

    private HttpStatusCode statusOf(int port, String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path)
                .exchange((request, response) -> response.getStatusCode());
    }

    @Test
    void health_is_served_on_management_port() {
        assertThat(managementPort).isNotEqualTo(serverPort);
        assertThat(statusOf(managementPort, "/actuator/health").value()).isEqualTo(200);
    }

    @Test
    void other_actuator_endpoints_are_not_exposed_on_management_port() {
        assertThat(statusOf(managementPort, "/actuator/env").value()).isIn(401, 403, 404);
        assertThat(statusOf(managementPort, "/actuator/info").value()).isIn(401, 403, 404);
    }

    @Test
    void health_is_not_served_on_public_port() {
        assertThat(statusOf(serverPort, "/actuator/health").value()).isIn(401, 403, 404);
    }
}

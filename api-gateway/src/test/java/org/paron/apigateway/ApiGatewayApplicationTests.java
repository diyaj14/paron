package org.paron.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldExposeTokenPingRoute() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertNotNull(routes);
        assertTrue(routes.stream().anyMatch(route -> route.getId().equals("ping-token")),
                "Expected a gateway route for /api/v1/tokens/ping");
    }
}

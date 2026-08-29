package org.paron.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();

        HttpMethod method = exchange.getRequest().getMethod();
        String methodName = method != null ? method.name() : "?";
        String path = exchange.getRequest().getPath().value();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            long duration = System.currentTimeMillis() - start;
            log.info("{} {} -> {} ({} ms)", methodName, path,
                    status != null ? status.value() : "?",
                    duration);
        }));
    }
}

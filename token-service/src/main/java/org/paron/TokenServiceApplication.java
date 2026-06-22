package org.paron;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.paron.model.MarkUsedRequest;
import org.paron.model.TokenRequest;
import org.paron.model.TokenResponse;
import org.paron.model.ValidateTokenRequest;
import org.paron.repo.TokenRepository;
import org.paron.security.JwtUtil;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
public class TokenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenServiceApplication.class, args);
    }

}


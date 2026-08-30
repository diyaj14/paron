package org.paron.controller;

/*Token controller gets requests that relate to tokenservices from outside world,namely genreating token
for a user and getting request from merchant to check if token is valid
 */
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.paron.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.paron.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("api/v1/tokens")
@Slf4j
public class TokenController {

    @Autowired TokenService tokenService;

    /*Generate token for user*/
    @PostMapping("/gettoken")
    public ResponseEntity<TokenResponse> giveToken(@Valid @RequestBody TokenRequest tokenRequest){
        log.info("Token generation request recieved for user with id {}",tokenRequest.getUserId());
        TokenResponse tokenResponse=tokenService.generateToken(tokenRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenResponse);
    }

    /*Validate token before transaction*/
    @GetMapping("/validateToken")
    public ResponseEntity<ValidateTokenResponse> validate(@Valid @RequestBody ValidateTokenRequest request){
        ValidateTokenResponse validate =tokenService.validate(request);
        return ResponseEntity.ok(validate);
    }

    /*change token status from active to used, called by sync-service after payment*/
    @PostMapping("/mark-used")
    public ResponseEntity<String> markTokenAsUsed(
            @Valid @RequestBody MarkUsedRequest request) {

        tokenService.markAsUsed(request);
        return ResponseEntity.ok("Token marked as USED successfully");
    }

    /* return user's offline transcation history with latest first without
    actual jwt value - return all tokens - actived,used,expired
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<TokenResponse>> getTokenHistory(@PathVariable String userId){
        List<TokenResponse> response=tokenService.getTokenHistory(userId);
        return ResponseEntity.ok(response);
    }

    /*Read-only spend state — the authoritative "how much spent?" evidence
    the dispute arbiter consumes to rule on conflicting receipts.*/
    @GetMapping("/state")
    public ResponseEntity<TokenSpendState> spendState(@RequestParam String token) {
        return ResponseEntity.ok(tokenService.getSpendState(token));
    }


    /*Health check*/
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("token-service is running");
    }




}

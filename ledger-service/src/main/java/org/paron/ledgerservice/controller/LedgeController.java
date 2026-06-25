package org.paron.ledgerservice.controller;


import org.paron.ledgerservice.service.LedgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class LedgeController {

    @Autowired LedgeService ledgeService;

    @PostMapping("/reserve")
    public void reserveFund(){
        ledgeService.reserveFund();
    }

    @PostMapping("/release")
    public void releaseFund(){
        ledgeService.releaseFund();
    }

}

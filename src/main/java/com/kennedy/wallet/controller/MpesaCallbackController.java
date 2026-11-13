package com.kennedy.wallet.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kennedy.wallet.config.MpesaProperties;
import com.kennedy.wallet.service.DepositLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@RestController @RequestMapping("/api/mpesa")
public class MpesaCallbackController {
    private final MpesaProperties properties;
    private final DepositLifecycleService deposits;
    public MpesaCallbackController(MpesaProperties properties,DepositLifecycleService deposits){this.properties=properties;this.deposits=deposits;}
    @PostMapping(value="/callback/{secretToken}",consumes="application/json")
    public CallbackAck callback(@PathVariable String secretToken,@RequestBody JsonNode payload,HttpServletRequest request){
        String configured=properties.getCallbackToken();
        if(configured.isBlank()||!MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),secretToken.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String allow=properties.getAllowedCallbackIps();
        if(allow!=null&&!allow.isBlank()&&Arrays.stream(allow.split(",")).map(String::trim).noneMatch(request.getRemoteAddr()::equals))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String checkoutId=payload.path("Body").path("stkCallback").path("CheckoutRequestID").asText("");
        if(!checkoutId.isBlank())deposits.receiveCallback(checkoutId,payload);
        return new CallbackAck(0,"Accepted");
    }
    public record CallbackAck(int ResultCode,String ResultDesc){ }
}

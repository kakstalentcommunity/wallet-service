package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.AccountQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountQueryService queries;
    public AccountController(AccountQueryService queries) { this.queries = queries; }
    @GetMapping("/me") public AccountQueryService.AccountBalance myBalance(Authentication authentication) {
        return queries.myBalance(UUID.fromString(authentication.getName()));
    }
}

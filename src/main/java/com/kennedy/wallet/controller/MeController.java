package com.kennedy.wallet.controller;
import org.springframework.web.bind.annotation.*;import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/me") public class MeController {@GetMapping public Object me(Authentication authentication){return java.util.Map.of("userId",authentication.getName());}}

package com.kennedy.wallet.controller;
import com.kennedy.wallet.service.AuthService;import com.kennedy.wallet.domain.User;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;import jakarta.servlet.http.HttpServletRequest;
@RestController @RequestMapping("/api/auth") public class AuthController {private final AuthService auth;public AuthController(AuthService a){auth=a;}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) public Object register(@Valid @RequestBody Credentials c){User u=auth.register(c.phone(),c.password());return new UserView(u.getId().toString(),u.getPhone());}
 @PostMapping("/login") public AuthService.TokenPair login(@Valid @RequestBody Credentials c,HttpServletRequest req){return auth.login(c.phone(),c.password());}
 @PostMapping("/refresh") public AuthService.TokenPair refresh(@Valid @RequestBody RefreshRequest r){return auth.refresh(r.refreshToken());}
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@Valid @RequestBody RefreshRequest r){auth.logout(r.refreshToken());}
 public record Credentials(@NotBlank String phone,@NotBlank @Size(min=8,max=72) String password){} public record RefreshRequest(@NotBlank String refreshToken){} public record UserView(String id,String phone){}
}

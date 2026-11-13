package com.kennedy.wallet.service;
import com.kennedy.wallet.config.WalletProperties; import com.kennedy.wallet.domain.User; import io.jsonwebtoken.Jwts; import io.jsonwebtoken.security.Keys; import org.springframework.stereotype.Service; import java.nio.charset.StandardCharsets; import java.time.Instant; import java.util.Date;
@Service public class JwtService {
 private final WalletProperties props; public JwtService(WalletProperties p){props=p;}
 public String issue(User u){Instant now=Instant.now();return Jwts.builder().issuer(props.getJwt().getIssuer()).audience().add(props.getJwt().getAudience()).and().subject(u.getId().toString()).id(java.util.UUID.randomUUID().toString()).claim("role",u.getRole().name()).issuedAt(Date.from(now)).expiration(Date.from(now.plus(props.getJwt().getAccessTtl()))).signWith(key()).compact();}
 public io.jsonwebtoken.Claims parse(String token){return Jwts.parser().verifyWith(key()).requireIssuer(props.getJwt().getIssuer()).requireAudience(props.getJwt().getAudience()).build().parseSignedClaims(token).getPayload();}
 private javax.crypto.SecretKey key(){return Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));}
}

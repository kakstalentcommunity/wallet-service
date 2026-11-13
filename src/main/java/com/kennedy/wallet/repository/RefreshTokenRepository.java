package com.kennedy.wallet.repository;
import com.kennedy.wallet.domain.RefreshToken; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID>{Optional<RefreshToken> findByTokenHash(String tokenHash);}

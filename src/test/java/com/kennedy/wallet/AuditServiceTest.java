package com.kennedy.wallet;

import com.kennedy.wallet.repository.AuditLogRepository;
import com.kennedy.wallet.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    @Mock AuditLogRepository repository;
    @Test void rejectsSensitiveAuditFields(){
        var service=new AuditService(repository);
        assertThrows(IllegalArgumentException.class,()->service.record(null,"LOGIN_FAILED",Map.of("refreshToken","secret")));
    }
}

package com.kennedy.wallet;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.UserRepository;
import com.kennedy.wallet.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {
    @Mock NotificationService notifications;@Mock UserRepository users;
    @Test void retriesNotificationFailuresWithoutPropagatingThem(){
        UUID userId=UUID.randomUUID();User user=new User("+254712345678","hash");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException()).doThrow(new IllegalStateException()).doNothing().when(notifications).send(anyString(),anyString());
        var listener=new NotificationEventListener(notifications,users);
        listener.afterPaymentCommit(new PaymentCompletedEvent(userId,"WLT-REFERENCE",100,TransactionType.TRANSFER,true));
        verify(notifications,times(3)).send(eq("+254712345678"),contains("WLT-REFERENCE"));
    }
}

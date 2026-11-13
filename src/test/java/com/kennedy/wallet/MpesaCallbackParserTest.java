package com.kennedy.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kennedy.wallet.service.MpesaCallbackParser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MpesaCallbackParserTest {
    @Test void extractsCheckoutResultAmountAndPhone() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"ws_CO_1","ResultCode":0,"CallbackMetadata":{"Item":[{"Name":"Amount","Value":12},{"Name":"PhoneNumber","Value":254712345678}]}}}}
                """);
        var result=MpesaCallbackParser.parse(json);
        assertNotNull(result);
        assertEquals("ws_CO_1",result.checkoutRequestId());
        assertEquals(0,result.resultCode());
        assertEquals(new BigDecimal("12"),result.amountKes());
        assertEquals("254712345678",result.phone());
    }
    @Test void rejectsCallbackWithoutCheckoutIdentifier() throws Exception {
        var json=new ObjectMapper().readTree("{\"Body\":{\"stkCallback\":{\"ResultCode\":0}}}");
        assertNull(MpesaCallbackParser.parse(json));
    }
}

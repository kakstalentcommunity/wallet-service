package com.kennedy.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public final class MpesaCallbackParser {
    private MpesaCallbackParser() { }
    public static CallbackData parse(JsonNode raw) {
        JsonNode callback = raw.path("Body").path("stkCallback");
        String checkoutId = text(callback.path("CheckoutRequestID"));
        JsonNode result = callback.path("ResultCode");
        if (checkoutId == null || !result.canConvertToInt()) return null;
        BigDecimal amount = null; String phone = null;
        JsonNode items = callback.path("CallbackMetadata").path("Item");
        if (items.isArray()) for (JsonNode item : items) {
            String name = text(item.path("Name"));
            if ("Amount".equals(name)) try { amount = new BigDecimal(text(item.path("Value"))); } catch (RuntimeException ignored) { return null; }
            if ("PhoneNumber".equals(name)) phone = text(item.path("Value"));
        }
        return new CallbackData(checkoutId, result.intValue(), amount, phone);
    }
    private static String text(JsonNode node) { return node.isMissingNode() || node.isNull() ? null : node.asText(); }
    public record CallbackData(String checkoutRequestId, int resultCode, BigDecimal amountKes, String phone) { }
}

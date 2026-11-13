package com.kennedy.wallet.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kennedy.wallet.config.MpesaProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.function.Supplier;

@Component
public class MpesaClient {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private final MpesaProperties properties;
    private final RestClient client;
    private volatile CachedToken cachedToken;

    public MpesaClient(MpesaProperties properties) {
        this.properties = properties;
        if (properties.getConnectTimeoutMillis() < 1 || properties.getReadTimeoutMillis() < 1)
            throw new IllegalStateException("M-Pesa timeouts must be positive");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        this.client = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    public StkResponse startStkPush(String phone, long amountMinor, String accountReference) {
        requireConfigured();
        String timestamp = TIMESTAMP.format(Instant.now());
        String password = Base64.getEncoder().encodeToString((properties.getShortcode() + properties.getPasskey() + timestamp).getBytes(StandardCharsets.UTF_8));
        StkRequest request = new StkRequest(properties.getShortcode(), password, timestamp, "CustomerPayBillOnline",
                Math.toIntExact(amountMinor / 100), phone.substring(1), properties.getShortcode(), phone.substring(1),
                properties.getCallbackBaseUrl() + "/api/mpesa/callback/" + properties.getCallbackToken(),
                accountReference, "Wallet deposit");
        // STK push is deliberately not retried: a timeout can happen after Daraja accepts the request.
        return client.post().uri("/mpesa/stkpush/v1/processrequest").contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken())).body(request).retrieve().body(StkResponse.class);
    }

    public StkQueryResponse queryStkStatus(String checkoutRequestId) {
        requireConfigured();
        String timestamp = TIMESTAMP.format(Instant.now());
        String password = Base64.getEncoder().encodeToString((properties.getShortcode() + properties.getPasskey() + timestamp).getBytes(StandardCharsets.UTF_8));
        StkQueryRequest request = new StkQueryRequest(properties.getShortcode(), password, timestamp, checkoutRequestId);
        return retry(() -> client.post().uri("/mpesa/stkpushquery/v1/query").contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken())).body(request).retrieve().body(StkQueryResponse.class));
    }

    private synchronized String accessToken() {
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plusSeconds(5))) return cachedToken.value();
        requireConfigured();
        TokenResponse token = retry(() -> client.get().uri("/oauth/v1/generate?grant_type=client_credentials")
                .headers(headers -> headers.setBasicAuth(properties.getConsumerKey(), properties.getConsumerSecret()))
                .retrieve().body(TokenResponse.class));
        if (token == null || token.accessToken() == null || token.expiresIn() == null)
            throw new IllegalStateException("M-Pesa OAuth response was invalid");
        long cacheSeconds = Math.max(1, Long.parseLong(token.expiresIn()) - 60);
        cachedToken = new CachedToken(token.accessToken(), Instant.now().plusSeconds(cacheSeconds));
        return cachedToken.value();
    }

    private <T> T retry(Supplier<T> request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try { return request.get(); }
            catch (RestClientResponseException ex) {
                if (ex.getStatusCode().is4xxClientError()) throw new IllegalStateException("M-Pesa request was rejected");
                last = ex;
            } catch (RuntimeException ex) { last = ex; }
            if (attempt < 3) try { Thread.sleep(100L * attempt); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("M-Pesa request interrupted"); }
        }
        throw new IllegalStateException("M-Pesa service is temporarily unavailable", last);
    }
    private void requireConfigured() {
        if (properties.getConsumerKey().isBlank() || properties.getConsumerSecret().isBlank() || properties.getShortcode().isBlank()
                || properties.getPasskey().isBlank() || properties.getCallbackToken().isBlank() || properties.getCallbackBaseUrl().isBlank())
            throw new IllegalStateException("M-Pesa deposit configuration is incomplete");
    }

    private record CachedToken(String value, Instant expiresAt) { }
    public record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") String expiresIn) { }
    public record StkRequest(String BusinessShortCode, String Password, String Timestamp, String TransactionType, int Amount,
                             String PartyA, String PartyB, String PhoneNumber, String CallBackURL, String AccountReference, String TransactionDesc) { }
    public record StkResponse(String MerchantRequestID, String CheckoutRequestID, String ResponseCode, String ResponseDescription, String CustomerMessage) { }
    public record StkQueryRequest(String BusinessShortCode, String Password, String Timestamp, String CheckoutRequestID) { }
    public record StkQueryResponse(String ResponseCode, String ResponseDescription, String MerchantRequestID,
                                   String CheckoutRequestID, String ResultCode, String ResultDesc) { }
}

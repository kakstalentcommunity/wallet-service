package com.kennedy.wallet.service;

import com.kennedy.wallet.config.NotificationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component @Profile("prod") public class AfricasTalkingNotificationService implements NotificationService {
    private final NotificationProperties properties;private final RestClient client;
    public AfricasTalkingNotificationService(NotificationProperties properties){this.properties=properties;this.client=RestClient.builder().baseUrl(properties.getBaseUrl()).build();}
    @Override public void send(String phone,String message){
        if(properties.getApiKey().isBlank()||properties.getUsername().isBlank())throw new IllegalStateException("Africa's Talking configuration is incomplete");
        String body="username="+encode(properties.getUsername())+"&to="+encode(phone)+"&message="+encode(message);
        if(!properties.getSenderId().isBlank())body+="&from="+encode(properties.getSenderId());
        client.post().uri("/version1/messaging").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("apiKey",properties.getApiKey()).header("Accept","application/json").body(body).retrieve().toBodilessEntity();
    }
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}

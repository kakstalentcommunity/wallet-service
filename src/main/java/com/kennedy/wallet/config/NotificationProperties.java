package com.kennedy.wallet.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("wallet.notifications.africas-talking") public class NotificationProperties {
 private String username="",apiKey="",senderId="",baseUrl="https://api.sandbox.africastalking.com";
 public String getUsername(){return username;}public void setUsername(String x){username=x;}public String getApiKey(){return apiKey;}public void setApiKey(String x){apiKey=x;}public String getSenderId(){return senderId;}public void setSenderId(String x){senderId=x;}public String getBaseUrl(){return baseUrl;}public void setBaseUrl(String x){baseUrl=x;}
}

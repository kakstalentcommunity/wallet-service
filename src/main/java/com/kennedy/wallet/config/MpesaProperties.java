package com.kennedy.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wallet.mpesa")
public class MpesaProperties {
    private String baseUrl = "https://sandbox.safaricom.co.ke";
    private String consumerKey = "", consumerSecret = "", shortcode = "", passkey = "";
    private String callbackBaseUrl = "", callbackToken = "";
    private String allowedCallbackIps = "";
    private int connectTimeoutMillis = 3000, readTimeoutMillis = 10000;
    public String getBaseUrl(){return baseUrl;} public void setBaseUrl(String x){baseUrl=x;}
    public String getConsumerKey(){return consumerKey;} public void setConsumerKey(String x){consumerKey=x;}
    public String getConsumerSecret(){return consumerSecret;} public void setConsumerSecret(String x){consumerSecret=x;}
    public String getShortcode(){return shortcode;} public void setShortcode(String x){shortcode=x;}
    public String getPasskey(){return passkey;} public void setPasskey(String x){passkey=x;}
    public String getCallbackBaseUrl(){return callbackBaseUrl;} public void setCallbackBaseUrl(String x){callbackBaseUrl=x;}
    public String getCallbackToken(){return callbackToken;} public void setCallbackToken(String x){callbackToken=x;}
    public String getAllowedCallbackIps(){return allowedCallbackIps;} public void setAllowedCallbackIps(String x){allowedCallbackIps=x;}
    public int getConnectTimeoutMillis(){return connectTimeoutMillis;} public void setConnectTimeoutMillis(int x){connectTimeoutMillis=x;}
    public int getReadTimeoutMillis(){return readTimeoutMillis;} public void setReadTimeoutMillis(int x){readTimeoutMillis=x;}
}

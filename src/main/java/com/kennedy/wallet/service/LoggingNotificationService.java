package com.kennedy.wallet.service;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.context.annotation.Profile;import org.springframework.stereotype.Component;
@Component @Profile("!prod") public class LoggingNotificationService implements NotificationService {
 private static final Logger log=LoggerFactory.getLogger(LoggingNotificationService.class);
 public void send(String phone,String message){log.info("SMS notification queued for phone {}",mask(phone));}
 private String mask(String phone){return phone!=null&&phone.length()>9?phone.substring(0,5)+"****"+phone.substring(phone.length()-4):"****";}
}

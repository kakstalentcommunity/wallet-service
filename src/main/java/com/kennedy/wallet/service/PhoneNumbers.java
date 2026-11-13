package com.kennedy.wallet.service;
import java.util.regex.Pattern;
public final class PhoneNumbers {
 private static final Pattern VALID=Pattern.compile("\\+254[17]\\d{8}"); private PhoneNumbers(){}
 public static String normalize(String input){if(input==null)throw new IllegalArgumentException("Invalid Kenyan phone number");String s=input.replaceAll("[\\s()-]","");if(s.matches("0[17]\\d{8}"))s="+254"+s.substring(1);else if(s.matches("254[17]\\d{8}"))s="+"+s;if(!VALID.matcher(s).matches())throw new IllegalArgumentException("Invalid Kenyan phone number");return s;}
}

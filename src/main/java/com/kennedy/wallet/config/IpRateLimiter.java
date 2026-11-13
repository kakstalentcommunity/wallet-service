package com.kennedy.wallet.config;

/** Replaceable boundary for client IP request throttling. */
public interface IpRateLimiter {
    boolean allow(String clientIp);
}

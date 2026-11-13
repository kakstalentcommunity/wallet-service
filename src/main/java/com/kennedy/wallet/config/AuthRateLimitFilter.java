package com.kennedy.wallet.config;
import jakarta.servlet.*;import jakarta.servlet.http.*;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;import java.io.IOException;
@Component @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 100) public class AuthRateLimitFilter extends OncePerRequestFilter {
 private final IpRateLimiter limiter; public AuthRateLimitFilter(IpRateLimiter limiter){this.limiter=limiter;}
 protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith("/api/auth/");}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{if(!limiter.allow(req.getRemoteAddr())){res.setStatus(429);res.setContentType("application/problem+json");res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too many requests\",\"status\":429,\"code\":\"RATE_LIMITED\"}");return;}chain.doFilter(req,res);}
}

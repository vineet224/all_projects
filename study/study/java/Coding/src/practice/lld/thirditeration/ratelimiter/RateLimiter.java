package practice.lld.thirditeration.ratelimiter;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * functional requirements
 * - implement a configurable rate limiter where the window and limits per window are configurable
 * -
 * - discussion with interviewer that we can implement sliding window for simple implementation, token bucket as it allows burst handling and leaky bucket if don't want to allow the bursts and only to allow the requests at fixed rate
 * - we have to apply the rate limiting on the Ip of the user
 */

class RateLimitConfiguration{
    double ratepersec;
    int windowsizeinsec;
    int tokenlimitperwindow;

    public RateLimitConfiguration( int windowsizeinsec, int tokenlimitperwindow){
        this.windowsizeinsec = windowsizeinsec;
        this.tokenlimitperwindow = tokenlimitperwindow;
        this.ratepersec = ((double)tokenlimitperwindow/(double)windowsizeinsec);
    }
}

class Token{
    long lastupdatedepoch;
    AtomicInteger availableTokens;
    public Token(long lastupdatedepoch, AtomicInteger availableTokens){
        this.lastupdatedepoch = lastupdatedepoch;
        this.availableTokens = availableTokens;
    }
}

public class RateLimiter {

    private final ConcurrentHashMap<String,Token> tokenBucket = new ConcurrentHashMap<String,Token>();
    private final RateLimitConfiguration rateLimitConfig;

    public RateLimiter(RateLimitConfiguration rateLimitConfig){
        this.rateLimitConfig = rateLimitConfig;
    }

    public synchronized boolean applyRateLimit(String userIp){
        long currentTime = System.currentTimeMillis();
        tokenBucket.putIfAbsent(userIp, new Token(currentTime, new AtomicInteger(0)));
        Token userToken =  tokenBucket.get(userIp);
        long lastupdatetime =   userToken.lastupdatedepoch;
        int currentTokens = userToken.availableTokens.decrementAndGet();
        if(currentTokens<0){
            userToken.availableTokens.incrementAndGet();
            System.out.println("user encounter rate limit");
            return false;
        }
        long currentTimeMillis = System.currentTimeMillis();
        double diff = (double) currentTimeMillis - lastupdatetime;
        double newTokens = (rateLimitConfig.ratepersec*diff)/1000;
        int maxToken = (int)newTokens;
        maxToken = Math.min(rateLimitConfig.tokenlimitperwindow, maxToken);
        return userToken.availableTokens.compareAndSet(currentTokens, maxToken);
    }

}

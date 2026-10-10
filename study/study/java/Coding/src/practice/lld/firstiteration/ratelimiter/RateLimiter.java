package practice.lld.firstiteration.ratelimiter;

import java.util.HashMap;

/***
 * functional requirements
 * - the user should not be able to make many simultaneous requests it would be configurable
 * - the identification of same user can be based on the IP or on the userId , i think it should be based on the IP only
 * - there are different rate limits of different api's that are public facing the read api or some api might have other rate limitation some get otp or make order might have some other limitation
 * -
 * non functional requirements
 * - user should not be blocked for entire day for rate limiting
 * - i can't think of the non-functional requirements here in the rate limiting context,
 * - the only thing is that it should support the calls upto 1 million request per second for the rate limit check
 *
 * entity:
 * - configuredLimit{timeWindow: 30 sec, allowedTime: 100}
 * - configuredLimit2{timeWindow: 15 sec, allowedTime: 100}
 * - configuredLimit3{timewindow: 10 sec, allowedTime: 10}
 *
 * User{
 *     userId:
 *
 * } // i don't think this is of any user here
 *
 * IpUser{
 *     Ip: String,
 *     userId:
 * } // either user try to access by Ip or by same userId they are considered same we have to check both
 *
 * RateLimitStrategies
 *
 * FxiedWindow: her
 *
 */

class RateLimitConfig{
    int windowSec;
    int allowedCall;

    public RateLimitConfig(int windowSec, int allowedTime){
        this.allowedCall = allowedTime;
        this.windowSec = windowSec;
    }
}

class IpUser{
    String ip;
    String userId;

    public IpUser(String ip, String userId){this.userId=userId;this.ip=ip;}
}


interface RateLimitStrategy{
    boolean applyRateLimit(IpUser user, RateLimitConfig config);
}

class FixedWindowStrategy implements RateLimitStrategy{
    @Override
    public boolean applyRateLimit(IpUser user, RateLimitConfig config){
        return false;
    }
}

class TokenBucketStrategy implements RateLimitStrategy{
    @Override
    public boolean applyRateLimit(IpUser user, RateLimitConfig config) {
        return false;
    }
}

class TokenCount{
    int token;
    long lastUpdatedEpoch;
    public TokenCount(int token, long lastUpdatedEpoch){this.token=token;this.lastUpdatedEpoch=lastUpdatedEpoch;}
}



public class RateLimiter {

    HashMap<String, TokenCount> userTokens;
    HashMap<String, IpUser> iptoUser;
    HashMap<String, IpUser> idToUser;
    RateLimitConfig lightRateLimitConfig;
    RateLimitConfig heavyRateLimitConfig;

    public RateLimiter(RateLimitConfig lightRateLimitConfig, RateLimitConfig heavyRateLimitConfig){
        this.heavyRateLimitConfig = heavyRateLimitConfig;
        this.lightRateLimitConfig = lightRateLimitConfig;
        this.idToUser = new HashMap<>();
        this.iptoUser = new HashMap<>();
        this.userTokens = new HashMap<>();
    }


    public void addUser(IpUser user){
        if(idToUser.containsKey(user.userId)){
            System.out.println(" the user already present");
            return;
        }
        idToUser.put(user.userId, user);
        iptoUser.put(user.ip, user);
        userTokens.put(user.userId, new TokenCount(lightRateLimitConfig.allowedCall, System.currentTimeMillis()));
    }

    public boolean checkUserBasedOnIp(String ip){
        IpUser user = iptoUser.get(ip);
        return checkUserBasedOnId(user.userId);
    }

    public boolean checkUserBasedOnId(String userId){

        userTokens.putIfAbsent(userId, new TokenCount(lightRateLimitConfig.allowedCall, System.currentTimeMillis()));

        long currentTimeEpoch = System.currentTimeMillis();
        TokenCount userToken = userTokens.get(userId);

        long diff = currentTimeEpoch - userToken.lastUpdatedEpoch;

        double rateToIncreaseTokenPerMilliSec = (double)lightRateLimitConfig.allowedCall/(double)lightRateLimitConfig.windowSec*(double)1000;

        int maxToken = lightRateLimitConfig.allowedCall;

        double tokenToBeRenewed = (double)(diff) * rateToIncreaseTokenPerMilliSec;

        int newToken = Math.max(maxToken, (int)(tokenToBeRenewed) + userToken.token );

        userToken.token = newToken;

        if(userToken.token <0) return false;

        userToken.token--;
        return true;
    }

}

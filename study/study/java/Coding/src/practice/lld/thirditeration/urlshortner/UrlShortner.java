package practice.lld.thirditeration.urlshortner;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * functional user should be able to create a short url for it's submitted long url
 * user should be able to provide alias as well if he wants
 * user should be able to call the short url and get redirected to the actual url
 *
 * nonfunctional requirements
 * there must be no same short url created for two  different long urls
 */

class UniqueSingleStringWorker{

    long saltcurrent;
    long saltmax;


    public synchronized String generateBase62uniqueshort(String url,String userId){

        if(saltcurrent == saltmax){

        }

        RandomGenerator rg = new SecureRandom();
         return "";
    }

    public synchronized void updateNewSaltRangeFromGlobal(){

    }
}

public class UrlShortner {

    ConcurrentHashMap<String, String> shortVsLongUrls = new ConcurrentHashMap<>();
    ConcurrentHashMap<String,String> userLongUrlvsShortUrls = new ConcurrentHashMap<>();

    UniqueSingleStringWorker worker = new UniqueSingleStringWorker();

//    public String generateShortUrl(String longurl,String userId){
//        String userLongUrlKey = longurl + "_" + userId;
//        if(userLongUrlvsShortUrls.contains(userLongUrlKey)){
//            throw new IllegalArgumentException("userLongUrl already exists");
//        }
//        String shortUrl = worker.generateBase62uniqueshort(longurl,userId);
//        userLongUrlvsShortUrls.putIfAbsent(userLongUrlKey, shortUrl);
//        shortVsLongUrls.put(shortUrl,longurl);
//    }
//
//    public String generateShortUrl(String longurl, String userId, String alias){
//         // we don't call the worker here we check if the alias short url already exist or not
//        String userLongUrlKey = longurl + "_" + userId;
//        if(userLongUrlvsShortUrls.contains(userLongUrlKey)){
//            throw new IllegalArgumentException("userLongUrl already exists");
//        }
//        String shortUrl = worker.generateBase62uniqueshort(longurl,userId);
//        userLongUrlvsShortUrls.putIfAbsent(userLongUrlKey, shortUrl);
//        shortVsLongUrls.putIfAbsent(alias,longurl);
//    }

}

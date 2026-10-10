package practice.lld.thirditeration.urlshortner.chatgpt.UrlShortner;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// ---------- Entities ----------

record UserUrlKey(String userId, String longUrl) {}

record ShortUrl(
        String code,
        String longUrl,
        String userId,
        Instant createdAt
) {}

// ---------- ID Generator ----------

interface ShortCodeGenerator {
    String generate();
}

class Base62IdGenerator implements ShortCodeGenerator {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final AtomicLong counter = new AtomicLong(1);

    @Override
    public String generate() {
        long id = counter.getAndIncrement();

        if (id <= 0) {
            throw new IllegalStateException("ID space exhausted");
        }

        StringBuilder sb = new StringBuilder();

        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % 62)));
            id /= 62;
        }

        return sb.reverse().toString();
    }
}

// ---------- URL Shortener Service ----------

class UrlShortenerService {

    private final Map<String, ShortUrl> byCode =
            new ConcurrentHashMap<>();

    private final Map<UserUrlKey, String> byUserAndUrl =
            new ConcurrentHashMap<>();

    private final ShortCodeGenerator generator;

    // Protects creation across both indexes.
    private final Object creationLock = new Object();

    UrlShortenerService(ShortCodeGenerator generator) {
        this.generator = generator;
    }

    public String shorten(String userId, String longUrl) {
        return create(userId, longUrl, null);
    }

    public String shortenWithAlias(
            String userId,
            String longUrl,
            String alias
    ) {
        return create(userId, longUrl, alias);
    }

    private String create(
            String userId,
            String longUrl,
            String alias
    ) {
        validateUserId(userId);
        validateUrl(longUrl);

        if (alias != null) {
            validateAlias(alias);
        }

        UserUrlKey userKey = new UserUrlKey(userId, longUrl);

        synchronized (creationLock) {

            String existingCode = byUserAndUrl.get(userKey);

            if (existingCode != null) {
                // Same user + URL always returns the original code.
                // A new alias request cannot rename an existing link.
                if (alias != null && !alias.equals(existingCode)) {
                    throw new IllegalArgumentException(
                            "URL already shortened as: " + existingCode
                    );
                }
                return existingCode;
            }

            String code;

            if (alias != null) {
                if (byCode.containsKey(alias)) {
                    throw new IllegalArgumentException("Alias already taken");
                }
                code = alias;
            } else {
                // Generated IDs can collide with custom aliases.
                do {
                    code = generator.generate();
                } while (byCode.containsKey(code));
            }

            ShortUrl entry = new ShortUrl(
                    code, longUrl, userId, Instant.now()
            );

            byCode.put(code, entry);
            byUserAndUrl.put(userKey, code);

            return code;
        }
    }

    public String resolve(String code) {
        ShortUrl entry = byCode.get(code);

        if (entry == null) {
            throw new IllegalArgumentException("Short URL not found");
        }

        return entry.longUrl();
    }

    public ShortUrl getDetails(String code) {
        ShortUrl entry = byCode.get(code);

        if (entry == null) {
            throw new IllegalArgumentException("Short URL not found");
        }

        return entry;
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Invalid user ID");
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Invalid URL");
        }

        try {
            URI uri = URI.create(url);

            String scheme = uri.getScheme();

            if (scheme == null ||
                    !(scheme.equalsIgnoreCase("http") ||
                      scheme.equalsIgnoreCase("https")) ||
                    uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid HTTP URL");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    private void validateAlias(String alias) {
        if (!alias.matches("[a-zA-Z0-9_-]{3,30}")) {
            throw new IllegalArgumentException(
                    "Alias must contain 3-30 letters, digits, _ or -"
            );
        }
    }
}

// ---------- Demo / Tests ----------

public class UrlShortener {

    public static void main(String[] args) throws Exception {

        UrlShortenerService service =
                new UrlShortenerService(new Base62IdGenerator());

        // 1. Basic shortening
        String code1 = service.shorten(
                "user1", "https://example.com/products/123"
        );

        System.out.println("Generated code: " + code1);
        System.out.println("Resolved URL: " + service.resolve(code1));

        // 2. Idempotency: same user + same URL
        String code2 = service.shorten(
                "user1", "https://example.com/products/123"
        );

        assert code1.equals(code2);

        // 3. Custom alias
        String alias = service.shortenWithAlias(
                "user2",
                "https://github.com",
                "my-github"
        );

        assert alias.equals("my-github");
        assert service.resolve("my-github").equals("https://github.com");

        // 4. Alias conflict
        try {
            service.shortenWithAlias(
                    "user3", "https://google.com", "my-github"
            );
            throw new AssertionError("Expected alias conflict");
        } catch (IllegalArgumentException expected) {
            System.out.println("Alias conflict handled");
        }

        // 5. Concurrent creation: same user and same URL
        int threads = 10;
        var executor = java.util.concurrent.Executors
                .newFixedThreadPool(threads);

        try {
            var futures = new java.util.ArrayList<
                    java.util.concurrent.Future<String>>();

            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() ->
                        service.shorten(
                                "user4",
                                "https://example.com/concurrent"
                        )
                ));
            }

            String expectedCode = futures.get(0).get();

            for (var future : futures) {
                assert expectedCode.equals(future.get());
            }

            System.out.println(
                    "Concurrent creation returned same code: " + expectedCode
            );

        } finally {
            executor.shutdown();
        }

        System.out.println("All tests passed");
    }
}

package com.netflexity.anypoint.mcp.license;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LicenseService {

    private final LicenseProperties licenseProperties;

    private Map<String, LicenseTier> keyTiers = new ConcurrentHashMap<>();
    private Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LicenseService(LicenseProperties licenseProperties) {
        this.licenseProperties = licenseProperties;
    }

    @PostConstruct
    public void init() {
        for (Map.Entry<String, String> entry : licenseProperties.getKeys().entrySet()) {
            String apiKey = entry.getKey();
            LicenseTier tier;
            try {
                tier = LicenseTier.valueOf(entry.getValue().toUpperCase());
            } catch (IllegalArgumentException e) {
                tier = LicenseTier.FREE;
            }
            keyTiers.put(apiKey, tier);
            buckets.put(apiKey, buildBucket(tier));
        }
    }

    private Bucket buildBucket(LicenseTier tier) {
        Bandwidth limit = switch (tier) {
            case FREE -> Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1)));
            case PRO -> Bandwidth.classic(600, Refill.intervally(600, Duration.ofMinutes(1)));
            case ENTERPRISE -> Bandwidth.classic(10000, Refill.intervally(10000, Duration.ofMinutes(1)));
        };
        return Bucket.builder().addLimit(limit).build();
    }

    public LicenseTier getTier(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return LicenseTier.FREE;
        }
        return keyTiers.getOrDefault(apiKey, LicenseTier.FREE);
    }

    public void requirePro(String apiKey) {
        if (getTier(apiKey) == LicenseTier.FREE) {
            throw new AccessDeniedException("Pro license required");
        }
    }

    public void requireEnterprise(String apiKey) {
        if (getTier(apiKey) != LicenseTier.ENTERPRISE) {
            throw new AccessDeniedException("Enterprise license required");
        }
    }

    public void checkRateLimit(String apiKey) {
        // For null/blank keys use a shared FREE bucket keyed by "__free__"
        String bucketKey = (apiKey == null || apiKey.isBlank()) ? "__free__" : apiKey;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> buildBucket(getTier(apiKey)));

        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Rate limit exceeded");
        }
    }

    public boolean isProKey(String apiKey) {
        return getTier(apiKey) != LicenseTier.FREE;
    }
}

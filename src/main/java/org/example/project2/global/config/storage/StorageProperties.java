package org.example.project2.global.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String bucket,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String publicBaseUrl
) {
    public boolean isUploadConfigured() {
        return hasText(bucket) && hasText(endpoint) && hasText(region)
                && hasText(accessKey) && hasText(secretKey);
    }

    public boolean hasPublicBaseUrl() {
        return hasText(publicBaseUrl);
    }

    public String publicUrl(String key) {
        return publicBaseUrl.replaceAll("/$", "") + "/" + bucket + "/" + key;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

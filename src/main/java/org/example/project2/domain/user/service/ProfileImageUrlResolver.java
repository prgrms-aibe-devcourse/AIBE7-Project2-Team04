package org.example.project2.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.user.entity.User;
import org.example.project2.global.config.storage.StorageProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImageUrlResolver {
    private final StorageProperties storageProperties;

    public String resolve(User user) {
        if (user.getProfileImageKey() != null && !user.getProfileImageKey().isBlank()
                && storageProperties.hasPublicBaseUrl()) {
            return storageProperties.publicUrl(user.getProfileImageKey());
        }
        return user.getProfileImageUrl();
    }
}

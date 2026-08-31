package org.example.project2.domain.user.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.global.storage.FileStore;
import org.example.project2.global.storage.UploadFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserProfileImageService {
    private final FileStore fileStore;
    private final UserRepository userRepository;

    @Transactional
    public String upload(UUID userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        UploadFile uploadFile = fileStore.storeFile(file);
        user.updateProfileImageKey(uploadFile.storedName());
        return uploadFile.storedName();
    }
}

package org.example.project2.global.storage;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.global.config.storage.StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3FileStore implements FileStore {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    @Override
    public UploadFile storeFile(MultipartFile file) {
        if (!storageProperties.isUploadConfigured()) {
            throw new IllegalStateException("파일 저장소가 설정되지 않았습니다.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("프로필 이미지를 선택해주세요.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("프로필 사진은 최대 5MB까지 업로드할 수 있습니다.");
        }

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "image");
        String safeName = StringUtils.cleanPath(originalName);
        String extension = StringUtils.getFilenameExtension(safeName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("JPG 또는 PNG 이미지만 업로드할 수 있습니다.");
        }

        String key = "profiles/" + UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(key)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return new UploadFile(originalName, key);
        } catch (IOException exception) {
            throw new IllegalStateException("프로필 이미지 업로드에 실패했습니다.", exception);
        }
    }
}

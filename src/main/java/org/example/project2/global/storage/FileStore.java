package org.example.project2.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStore {
    UploadFile storeFile(MultipartFile file);
}

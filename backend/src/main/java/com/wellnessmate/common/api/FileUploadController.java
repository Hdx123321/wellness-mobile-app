package com.wellnessmate.common.api;

import com.wellnessmate.common.domain.UploadedFile;
import com.wellnessmate.common.repository.UploadedFileRepository;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {
  private final UploadedFileRepository files;

  public FileUploadController(UploadedFileRepository files) { this.files = files; }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> upload(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
    String contentType = file.getContentType();
    boolean isVideo = contentType != null && contentType.startsWith("video/");
    long maxSize = isVideo ? 100L * 1024 * 1024 : 10L * 1024 * 1024;
    String typeLabel = isVideo ? "100 MB" : "10 MB";
    if (file.getSize() > maxSize)
      throw new IllegalArgumentException("File must be under " + typeLabel);
    Long userId = Long.parseLong(jwt.getSubject());
    String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
    UploadedFile saved = files.save(new UploadedFile(userId, contentType,
        filename, file.getBytes()));
    return Map.of("id", saved.getId(), "url", "/api/files/" + saved.getId());
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> download(@PathVariable Long id) {
    UploadedFile file = files.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("File not found"));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, file.getContentType())
        .body(file.getData());
  }
}

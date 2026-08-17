package in.craves.userchef.web;

import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.BlobDocumentStorageService.StoredDocumentBytes;
import in.craves.userchef.service.ChefDocumentReviewService;
import in.craves.userchef.service.ChefDocumentReviewService.DocumentMetadata;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backoffice/chef-reviews")
public class BackofficeChefDocumentController {
    private final ChefDocumentReviewService service;

    public BackofficeChefDocumentController(ChefDocumentReviewService service) {
        this.service = service;
    }

    @GetMapping("/{applicationId}/documents")
    public List<DocumentMetadata> list(
        @AuthenticationPrincipal CurrentUser admin,
        @PathVariable UUID applicationId
    ) {
        return service.list(admin, applicationId);
    }

    @GetMapping("/{applicationId}/documents/{documentId}/content")
    public ResponseEntity<byte[]> download(
        @AuthenticationPrincipal CurrentUser admin,
        @PathVariable UUID applicationId,
        @PathVariable UUID documentId
    ) {
        StoredDocumentBytes document = service.download(admin, applicationId, documentId);
        ContentDisposition disposition = ContentDisposition.inline()
            .filename(document.originalFileName())
            .build();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(MediaType.parseMediaType(document.contentType()))
            .contentLength(document.bytes().length)
            .body(document.bytes());
    }
}

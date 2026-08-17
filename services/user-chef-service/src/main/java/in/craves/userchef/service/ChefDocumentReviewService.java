package in.craves.userchef.service;

import in.craves.userchef.exception.ApiException;
import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.BlobDocumentStorageService.StoredDocumentBytes;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChefDocumentReviewService {
    private final JdbcTemplate jdbcTemplate;
    private final BlobDocumentStorageService storageService;

    public ChefDocumentReviewService(JdbcTemplate jdbcTemplate, BlobDocumentStorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
    }

    public List<DocumentMetadata> list(CurrentUser admin, UUID applicationId) {
        requireAdmin(admin);
        return jdbcTemplate.query(
            """
                SELECT id, document_type, original_file_name, content_type, file_size_bytes, status
                FROM chef_kyc_document
                WHERE application_id = ?
                ORDER BY document_type
                """,
            (resultSet, rowNumber) -> new DocumentMetadata(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("document_type"),
                resultSet.getString("original_file_name"),
                resultSet.getString("content_type"),
                resultSet.getLong("file_size_bytes"),
                resultSet.getString("status")
            ),
            applicationId
        );
    }

    public StoredDocumentBytes download(
        CurrentUser admin,
        UUID applicationId,
        UUID documentId
    ) {
        requireAdmin(admin);
        DocumentLocation location = jdbcTemplate.query(
            """
                SELECT blob_container, blob_name, original_file_name, content_type, file_size_bytes
                FROM chef_kyc_document
                WHERE id = ? AND application_id = ?
                """,
            (resultSet, rowNumber) -> new DocumentLocation(
                resultSet.getString("blob_container"),
                resultSet.getString("blob_name"),
                resultSet.getString("original_file_name"),
                resultSet.getString("content_type"),
                resultSet.getLong("file_size_bytes")
            ),
            documentId,
            applicationId
        ).stream().findFirst().orElseThrow(() ->
            ApiException.notFound("CHEF_DOCUMENT_NOT_FOUND", "Chef proof document was not found")
        );
        return storageService.downloadKycDocument(
            location.container(),
            location.blobName(),
            location.originalFileName(),
            location.contentType(),
            location.fileSizeBytes()
        );
    }

    private static void requireAdmin(CurrentUser user) {
        if (user == null || !user.hasAnyRole("PLATFORM_ADMIN", "CHEF_ADMIN", "COMPLIANCE_ADMIN")) {
            throw ApiException.forbidden(
                "CHEF_DOCUMENT_REVIEW_ROLE_REQUIRED",
                "Chef document review access is required"
            );
        }
    }

    public record DocumentMetadata(
        UUID id,
        String documentType,
        String originalFileName,
        String contentType,
        long fileSizeBytes,
        String status
    ) {
    }

    private record DocumentLocation(
        String container,
        String blobName,
        String originalFileName,
        String contentType,
        long fileSizeBytes
    ) {
    }
}

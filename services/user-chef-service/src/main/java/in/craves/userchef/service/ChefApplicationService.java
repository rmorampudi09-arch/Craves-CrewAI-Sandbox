package in.craves.userchef.service;

import in.craves.userchef.exception.ApiException;
import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.BlobDocumentStorageService.StoredDocument;
import in.craves.userchef.web.ApiDtos.AdminDecisionRequest;
import in.craves.userchef.web.ApiDtos.ChefApplicationRequest;
import in.craves.userchef.web.ApiDtos.ChefApplicationResponse;
import in.craves.userchef.web.ApiDtos.ChefApplicationStatus;
import in.craves.userchef.web.ApiDtos.KycDocumentResponse;
import in.craves.userchef.web.ApiDtos.KycDocumentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChefApplicationService {
    private static final Set<KycDocumentType> REQUIRED_APPLICATION_DOCUMENTS = Set.of(
        KycDocumentType.APPLICANT_PHOTO,
        KycDocumentType.GOVERNMENT_ID_FRONT,
        KycDocumentType.GOVERNMENT_ID_BACK,
        KycDocumentType.TAX_ID_CARD
    );

    private final JdbcTemplate jdbcTemplate;
    private final BlobDocumentStorageService storageService;
    private final AuthInternalClient authInternalClient;
    private final NotificationInternalClient notificationInternalClient;

    public ChefApplicationService(
        JdbcTemplate jdbcTemplate,
        BlobDocumentStorageService storageService,
        AuthInternalClient authInternalClient,
        NotificationInternalClient notificationInternalClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
        this.authInternalClient = authInternalClient;
        this.notificationInternalClient = notificationInternalClient;
    }

    public ChefApplicationResponse getMyApplication(CurrentUser user) {
        List<ChefApplicationResponse> rows = findApplications("WHERE identity_id = ?", user.identityId());
        if (rows.isEmpty()) {
            return new ChefApplicationResponse(null, user.identityId(), user.phoneNumber(), null, null, null, null, null, null, null, null, null, null, null, ChefApplicationStatus.NOT_SUBMITTED, null, null, null, null, List.of());
        }
        return rows.getFirst();
    }

    @Transactional
    public ChefApplicationResponse submitApplication(CurrentUser user, ChefApplicationRequest request) {
        List<String> statuses = jdbcTemplate.query(
            "SELECT status FROM chef_application WHERE identity_id = ?",
            (rs, rowNum) -> rs.getString("status"),
            user.identityId()
        );
        if (!statuses.isEmpty() && "APPROVED".equals(statuses.getFirst())) {
            throw ApiException.conflict("CHEF_ALREADY_APPROVED", "Approved chef applications cannot be resubmitted");
        }

        if (statuses.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO chef_application (id, identity_id, phone_number, email, first_name, last_name, address_line1, address_line2, landmark, city, state, postal_code, latitude, longitude, status, submitted_at, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', now(), now(), now())",
                UUID.randomUUID(), user.identityId(), user.phoneNumber(), request.email(), request.firstName(),
                request.lastName(), request.addressLine1(), blankToNull(request.addressLine2()),
                blankToNull(request.landmark()), request.city(), request.state(), blankToNull(request.postalCode()),
                request.latitude(), request.longitude()
            );
        } else {
            jdbcTemplate.update(
                "UPDATE chef_application SET phone_number = ?, email = ?, first_name = ?, last_name = ?, address_line1 = ?, address_line2 = ?, landmark = ?, city = ?, state = ?, postal_code = ?, latitude = ?, longitude = ?, status = 'PENDING', rejection_reason = NULL, reviewed_at = NULL, reviewed_by_identity_id = NULL, submitted_at = now(), updated_at = now() WHERE identity_id = ?",
                user.phoneNumber(), request.email(), request.firstName(), request.lastName(), request.addressLine1(),
                blankToNull(request.addressLine2()), blankToNull(request.landmark()), request.city(), request.state(),
                blankToNull(request.postalCode()), request.latitude(), request.longitude(), user.identityId()
            );
        }
        return getMyApplication(user);
    }

    @Transactional
    public KycDocumentResponse uploadDocument(CurrentUser user, KycDocumentType documentType, MultipartFile file) {
        ChefApplicationResponse application = getExistingApplication(user.identityId());
        if (application.status() == ChefApplicationStatus.APPROVED) {
            throw ApiException.conflict("CHEF_ALREADY_APPROVED", "Documents cannot be changed after approval");
        }
        if (!REQUIRED_APPLICATION_DOCUMENTS.contains(documentType)) {
            throw ApiException.badRequest("CHEF_DOCUMENT_TYPE_NOT_ALLOWED", "Use one of the current Chef application evidence types");
        }

        StoredDocument stored = storageService.uploadKycDocument(user.identityId(), documentType, file);
        List<UUID> existing = jdbcTemplate.query(
            "SELECT id FROM chef_kyc_document WHERE application_id = ? AND document_type = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class), application.id(), documentType.name()
        );

        UUID documentId = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        if (existing.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO chef_kyc_document (id, application_id, identity_id, document_type, original_file_name, blob_container, blob_name, content_type, file_size_bytes, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED', now(), now())",
                documentId, application.id(), user.identityId(), documentType.name(), stored.originalFileName(),
                stored.container(), stored.blobName(), stored.contentType(), stored.fileSizeBytes()
            );
        } else {
            jdbcTemplate.update(
                "UPDATE chef_kyc_document SET original_file_name = ?, blob_container = ?, blob_name = ?, content_type = ?, file_size_bytes = ?, status = 'UPLOADED', updated_at = now() WHERE id = ?",
                stored.originalFileName(), stored.container(), stored.blobName(), stored.contentType(),
                stored.fileSizeBytes(), documentId
            );
        }
        return getDocument(documentId);
    }

    public List<KycDocumentResponse> listMyApplicationEvidence(CurrentUser user) {
        ChefApplicationResponse application = getExistingApplication(user.identityId());
        return listApplicationEvidence(application.id());
    }

    public List<KycDocumentResponse> listApplicationEvidenceForAdmin(CurrentUser admin, UUID applicationId) {
        requireReviewAccess(admin);
        getApplicationForAdmin(admin, applicationId);
        return listApplicationEvidence(applicationId);
    }

    public List<ChefApplicationResponse> listApplications(CurrentUser admin, ChefApplicationStatus status) {
        requireReviewAccess(admin);
        if (status == null || status == ChefApplicationStatus.NOT_SUBMITTED) {
            return findApplications("", new Object[]{});
        }
        return findApplications("WHERE status = ?", status.name());
    }

    public ChefApplicationResponse getApplicationForAdmin(CurrentUser admin, UUID applicationId) {
        requireReviewAccess(admin);
        List<ChefApplicationResponse> rows = findApplications("WHERE id = ?", applicationId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("CHEF_APPLICATION_NOT_FOUND", "Chef application was not found");
        }
        return rows.getFirst();
    }

    @Transactional
    public ChefApplicationResponse approve(CurrentUser admin, UUID applicationId) {
        requireDecisionAccess(admin);
        ChefApplicationResponse application = getApplicationForAdmin(admin, applicationId);
        if (application.status() != ChefApplicationStatus.PENDING) {
            throw ApiException.conflict("CHEF_APPLICATION_NOT_PENDING", "Only pending chef applications can be approved");
        }
        requireCompleteApplicationDocuments(applicationId);
        updateDecision(applicationId, admin.identityId(), "APPROVED", null);
        authInternalClient.grantChefRole(application.identityId(), applicationId);
        ChefApplicationResponse approved = getApplicationForAdmin(admin, applicationId);
        notificationInternalClient.chefApproved(approved);
        return approved;
    }

    @Transactional
    public ChefApplicationResponse reject(CurrentUser admin, UUID applicationId, AdminDecisionRequest request) {
        requireDecisionAccess(admin);
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw ApiException.badRequest("REJECTION_REASON_REQUIRED", "Rejection reason is required");
        }
        ChefApplicationResponse application = getApplicationForAdmin(admin, applicationId);
        if (application.status() != ChefApplicationStatus.PENDING) {
            throw ApiException.conflict("CHEF_APPLICATION_NOT_PENDING", "Only pending chef applications can be rejected");
        }
        updateDecision(applicationId, admin.identityId(), "REJECTED", request.reason());
        ChefApplicationResponse rejected = getApplicationForAdmin(admin, applicationId);
        notificationInternalClient.chefRejected(rejected);
        return rejected;
    }

    private void requireCompleteApplicationDocuments(UUID applicationId) {
        Set<KycDocumentType> uploaded = listApplicationEvidence(applicationId).stream()
            .map(KycDocumentResponse::documentType)
            .collect(java.util.stream.Collectors.toSet());
        List<KycDocumentType> missing = REQUIRED_APPLICATION_DOCUMENTS.stream()
            .filter(type -> !uploaded.contains(type))
            .sorted()
            .toList();
        if (!missing.isEmpty()) {
            throw ApiException.conflict(
                "CHEF_REQUIRED_DOCUMENTS_MISSING",
                "Required Chef application evidence is incomplete: " + missing.stream()
                    .map(ChefApplicationService::documentLabel)
                    .toList()
            );
        }
    }

    private static String documentLabel(KycDocumentType type) {
        return switch (type) {
            case APPLICANT_PHOTO -> "Applicant photo";
            case GOVERNMENT_ID_FRONT -> "Government ID front";
            case GOVERNMENT_ID_BACK -> "Government ID back";
            case TAX_ID_CARD -> "Tax ID card";
            case AADHAAR_CARD -> "Legacy government ID";
            case PAN_CARD -> "Legacy tax ID";
        };
    }

    private void updateDecision(UUID applicationId, UUID adminIdentityId, String decision, String reason) {
        int updated = jdbcTemplate.update(
            "UPDATE chef_application SET status = ?, rejection_reason = ?, reviewed_at = now(), reviewed_by_identity_id = ?, updated_at = now() WHERE id = ?",
            decision, blankToNull(reason), adminIdentityId, applicationId
        );
        if (updated == 0) {
            throw ApiException.notFound("CHEF_APPLICATION_NOT_FOUND", "Chef application was not found");
        }
        jdbcTemplate.update(
            "INSERT INTO admin_chef_decision_audit (id, application_id, admin_identity_id, decision, reason, created_at) VALUES (?, ?, ?, ?, ?, now())",
            UUID.randomUUID(), applicationId, adminIdentityId, decision, blankToNull(reason)
        );
    }

    private ChefApplicationResponse getExistingApplication(UUID identityId) {
        List<ChefApplicationResponse> rows = findApplications("WHERE identity_id = ?", identityId);
        if (rows.isEmpty()) {
            throw ApiException.badRequest("CHEF_APPLICATION_REQUIRED", "Submit chef application before uploading documents");
        }
        return rows.getFirst();
    }

    private KycDocumentResponse getDocument(UUID documentId) {
        return jdbcTemplate.query("SELECT * FROM chef_kyc_document WHERE id = ?", this::mapDocument, documentId).getFirst();
    }

    private List<ChefApplicationResponse> findApplications(String whereClause, Object... args) {
        String sql = "SELECT * FROM chef_application " + whereClause + " ORDER BY submitted_at DESC";
        return jdbcTemplate.query(sql, this::mapApplication, args);
    }

    private ChefApplicationResponse mapApplication(ResultSet rs, int rowNum) throws SQLException {
        UUID applicationId = rs.getObject("id", UUID.class);
        return new ChefApplicationResponse(
            applicationId, rs.getObject("identity_id", UUID.class), rs.getString("phone_number"),
            rs.getString("email"), rs.getString("first_name"), rs.getString("last_name"),
            rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("landmark"),
            rs.getString("city"), rs.getString("state"), rs.getString("postal_code"),
            rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
            ChefApplicationStatus.valueOf(rs.getString("status")), rs.getString("rejection_reason"),
            instant(rs, "submitted_at"), instant(rs, "reviewed_at"),
            rs.getObject("reviewed_by_identity_id", UUID.class), listLegacyDocuments(applicationId)
        );
    }

    private List<KycDocumentResponse> listLegacyDocuments(UUID applicationId) {
        return jdbcTemplate.query(
            "SELECT * FROM chef_kyc_document WHERE application_id = ? AND document_type IN ('AADHAAR_CARD', 'PAN_CARD') ORDER BY document_type",
            this::mapDocument, applicationId
        );
    }

    private List<KycDocumentResponse> listApplicationEvidence(UUID applicationId) {
        return jdbcTemplate.query(
            "SELECT * FROM chef_kyc_document WHERE application_id = ? AND document_type IN ('APPLICANT_PHOTO', 'GOVERNMENT_ID_FRONT', 'GOVERNMENT_ID_BACK', 'TAX_ID_CARD') ORDER BY document_type",
            this::mapDocument, applicationId
        );
    }

    private KycDocumentResponse mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new KycDocumentResponse(
            rs.getObject("id", UUID.class), KycDocumentType.valueOf(rs.getString("document_type")),
            rs.getString("original_file_name"), rs.getString("blob_container"), rs.getString("blob_name"),
            rs.getString("content_type"), rs.getLong("file_size_bytes"), rs.getString("status"),
            instant(rs, "created_at"), instant(rs, "updated_at")
        );
    }

    private static void requireReviewAccess(CurrentUser user) {
        if (user == null || !user.hasAnyRole(
            "PLATFORM_ADMIN", "CHEF_ADMIN", "COMPLIANCE_ADMIN", "AUDIT_ADMIN"
        )) {
            throw ApiException.forbidden("CHEF_REVIEW_ROLE_REQUIRED", "Chef review access is required");
        }
    }

    private static void requireDecisionAccess(CurrentUser user) {
        if (user == null || !user.hasAnyRole("PLATFORM_ADMIN", "CHEF_ADMIN")) {
            throw ApiException.forbidden("CHEF_DECISION_ROLE_REQUIRED", "Chef decision access is required");
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

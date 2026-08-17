# Admin Chef Review

Routes:

```text
/admin/chef-reviews
/admin/chef-reviews/{applicationId}
```

Backend operations:

```text
GET  /api/v1/backoffice/chef-reviews
GET  /api/v1/backoffice/chef-reviews/{applicationId}
GET  /api/v1/backoffice/chef-reviews/{applicationId}/documents/{documentId}/content
POST /api/v1/backoffice/chef-reviews/{applicationId}/approve
POST /api/v1/backoffice/chef-reviews/{applicationId}/reject
```

The new proof-content endpoint streams only PDF/JPEG/PNG files to an authenticated ADMIN. It validates the application/document relationship, private container, blob path, content type and size. No SAS URL or blob name reaches the browser.

Approvals require an explicit browser confirmation. Rejections require a reason. User-Chef Service owns role grant, status transition, notifications and durable decision audit.

Later run `azure-pipelines-admin-chef-review-ci.yml`, deploy User-Chef Service first, configure APIM, then test with pending and non-admin identities.

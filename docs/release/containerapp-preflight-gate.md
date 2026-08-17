# Container Apps read-only preflight

Reads the selected Azure Container Apps and verifies that each has a current image, `Running` status, an active and provisioned latest revision, and at least one replica.

The check intentionally does not depend on `healthState`, which has already returned `None` for valid revisions in this environment. It performs Azure read operations only and never updates a revision, image, secret, scale rule, ingress setting, or environment variable.

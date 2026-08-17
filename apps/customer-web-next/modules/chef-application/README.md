# Craves Chef Application Web Module

## Scope

Provides chef application status, submission and proof-file upload through the existing User/Chef Service contract.

## Routes

```text
/chef/application
GET|POST /api/chef/application
POST /api/chef/application/proof-files
```

## Backend contract

```text
GET  /api/v1/chef/application
POST /api/v1/chef/application
POST /api/v1/chef/application/proof-files
```

## Safety

- All browser mutations require a same-origin request.
- The Craves token remains in an HTTP-only cookie.
- Only Aadhaar card and PAN card proof types already supported by the backend are accepted.
- File types are limited to PDF, JPEG and PNG, with a 10 MB client/BFF ceiling.
- Blob container/name, reviewer identity and registered phone are removed from browser responses.
- Approval, rejection, compliance and FSSAI rules are not implemented in the frontend.

## Pipelines

```text
azure-pipelines-chef-web-application-ci.yml
azure-pipelines-chef-application-apim.yml
```

The APIM pipeline requires `confirmConfigureChefApplication=true` and uses `Craves-Dev-Service-Connection` later through `AZURE_SERVICE_CONNECTION`.

ALTER TABLE chef_kyc_document
    DROP CONSTRAINT IF EXISTS ck_chef_kyc_document_type;

ALTER TABLE chef_kyc_document
    ADD CONSTRAINT ck_chef_kyc_document_type
    CHECK (
        document_type IN (
            'APPLICANT_PHOTO',
            'GOVERNMENT_ID_FRONT',
            'GOVERNMENT_ID_BACK',
            'TAX_ID_CARD',
            'AADHAAR_CARD',
            'PAN_CARD'
        )
    );

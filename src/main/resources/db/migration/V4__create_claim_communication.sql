CREATE TABLE claim_communication (
    id           UUID PRIMARY KEY,
    claim_id     UUID         NOT NULL REFERENCES claim (id),
    kind         VARCHAR(32)  NOT NULL,
    body         TEXT         NOT NULL,
    author_type  VARCHAR(16)  NOT NULL,
    staff_id     UUID         REFERENCES staff (id),
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_comm_kind CHECK (kind IN (
        'INFORMATION_REQUEST', 'INFORMATION_RESPONSE', 'NOTE')),
    CONSTRAINT chk_comm_author CHECK (author_type IN ('CLAIMANT', 'STAFF')),
    CONSTRAINT chk_comm_staff CHECK (
        (author_type = 'STAFF' AND staff_id IS NOT NULL)
        OR (author_type = 'CLAIMANT' AND staff_id IS NULL)),
    CONSTRAINT chk_comm_body CHECK (length(trim(body)) > 0)
);

CREATE INDEX idx_comm_claim_created ON claim_communication (claim_id, created_at);

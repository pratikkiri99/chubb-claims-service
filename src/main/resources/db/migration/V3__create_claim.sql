CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claim (
    id                   UUID PRIMARY KEY,
    claim_number         VARCHAR(32)   NOT NULL,
    policy_id            UUID          NOT NULL REFERENCES policy (id),
    market               VARCHAR(8)    NOT NULL,
    coverage_type        VARCHAR(16)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    claimant_name        VARCHAR(255)  NOT NULL,
    claimant_email       VARCHAR(255)  NOT NULL,
    claimant_phone       VARCHAR(32)   NOT NULL,
    incident_date        DATE          NOT NULL,
    incident_location    VARCHAR(512)  NOT NULL,
    incident_description TEXT          NOT NULL,
    claimed_amount       NUMERIC(19,2) NOT NULL,
    reserve_amount       NUMERIC(19,2) NOT NULL,
    settlement_amount    NUMERIC(19,2),
    rejection_reason     TEXT,
    assigned_staff_id    UUID          REFERENCES staff (id),
    assigned_at          TIMESTAMPTZ,
    decided_at           TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_claim_number UNIQUE (claim_number),
    CONSTRAINT chk_claim_market CHECK (market IN ('AU', 'HK', 'SG', 'JP', 'KR', 'TW')),
    CONSTRAINT chk_claim_coverage CHECK (coverage_type IN ('MOTOR', 'PROPERTY')),
    CONSTRAINT chk_claim_status CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'PENDING_INFORMATION', 'SETTLED', 'REJECTED')),
    CONSTRAINT chk_claimed_amount CHECK (claimed_amount > 0),
    CONSTRAINT chk_reserve_amount CHECK (reserve_amount >= 0),
    CONSTRAINT chk_settlement_positive CHECK (settlement_amount IS NULL OR settlement_amount > 0),
    CONSTRAINT chk_open_unassigned CHECK (
        (status = 'OPEN' AND assigned_staff_id IS NULL AND assigned_at IS NULL)
        OR status <> 'OPEN'),
    CONSTRAINT chk_non_open_assigned CHECK (
        status = 'OPEN'
        OR (assigned_staff_id IS NOT NULL AND assigned_at IS NOT NULL)),
    CONSTRAINT chk_settled CHECK (
        (status = 'SETTLED'
            AND settlement_amount IS NOT NULL
            AND decided_at IS NOT NULL
            AND rejection_reason IS NULL)
        OR status <> 'SETTLED'),
    CONSTRAINT chk_rejected CHECK (
        (status = 'REJECTED'
            AND rejection_reason IS NOT NULL
            AND length(trim(rejection_reason)) > 0
            AND decided_at IS NOT NULL
            AND settlement_amount IS NULL)
        OR status <> 'REJECTED'),
    CONSTRAINT chk_undecided CHECK (
        status IN ('SETTLED', 'REJECTED')
        OR (decided_at IS NULL AND settlement_amount IS NULL AND rejection_reason IS NULL))
);

CREATE INDEX idx_claim_queue
    ON claim (market, created_at DESC)
    WHERE status = 'OPEN' AND assigned_staff_id IS NULL;

CREATE INDEX idx_claim_assignee_status ON claim (assigned_staff_id, status);
CREATE INDEX idx_claim_exposure ON claim (market, coverage_type, status);

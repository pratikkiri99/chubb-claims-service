CREATE TABLE policy (
    id              UUID PRIMARY KEY,
    policy_number   VARCHAR(32)  NOT NULL,
    market          VARCHAR(8)   NOT NULL,
    coverage_type   VARCHAR(16)  NOT NULL,
    holder_name     VARCHAR(255) NOT NULL,
    sum_insured     NUMERIC(19,2) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_policy_number_market UNIQUE (policy_number, market),
    CONSTRAINT chk_policy_market CHECK (market IN ('AU', 'HK', 'SG', 'JP', 'KR', 'TW')),
    CONSTRAINT chk_policy_coverage CHECK (coverage_type IN ('MOTOR', 'PROPERTY')),
    CONSTRAINT chk_policy_status CHECK (status IN ('ACTIVE', 'LAPSED', 'CANCELLED')),
    CONSTRAINT chk_policy_sum_insured CHECK (sum_insured > 0)
);

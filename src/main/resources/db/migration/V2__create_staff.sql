CREATE TABLE staff (
    id          UUID PRIMARY KEY,
    full_name   VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    market      VARCHAR(8)   NOT NULL,
    team        VARCHAR(64)  NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_staff_email UNIQUE (email),
    CONSTRAINT chk_staff_market CHECK (market IN ('AU', 'HK', 'SG', 'JP', 'KR', 'TW')),
    CONSTRAINT chk_staff_role CHECK (role IN ('OFFICER', 'MANAGER'))
);

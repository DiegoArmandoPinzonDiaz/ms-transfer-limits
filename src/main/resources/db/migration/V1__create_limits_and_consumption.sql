CREATE TABLE channel_limits (
    account_id          VARCHAR(64)   NOT NULL,
    channel             VARCHAR(16)   NOT NULL CHECK (channel IN ('MOBILE', 'WEB', 'BRANCH')),
    daily_limit         NUMERIC(19,2) NOT NULL CHECK (daily_limit > 0),
    per_operation_limit NUMERIC(19,2) NOT NULL CHECK (per_operation_limit > 0),
    PRIMARY KEY (account_id, channel),
    CHECK (per_operation_limit <= daily_limit)
);

-- Consumo por cuenta, canal y fecha de negocio (America/Mexico_City).
CREATE TABLE daily_consumption (
    account_id    VARCHAR(64)   NOT NULL,
    channel       VARCHAR(16)   NOT NULL,
    business_date DATE          NOT NULL,
    consumed      NUMERIC(19,2) NOT NULL CHECK (consumed >= 0),
    PRIMARY KEY (account_id, channel, business_date),
    FOREIGN KEY (account_id, channel) REFERENCES channel_limits (account_id, channel)
);

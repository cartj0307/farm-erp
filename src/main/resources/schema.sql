-- Farm ERP database schema (SQLite)
-- Money is whole cents (INTEGER). Dates are YYYY-MM-DD text.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS farm (
    farm_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    location     TEXT,
    total_acres  REAL    NOT NULL CHECK (total_acres > 0)
);

CREATE TABLE IF NOT EXISTS field (
    field_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    farm_id   INTEGER NOT NULL REFERENCES farm (farm_id) ON DELETE CASCADE,
    name      TEXT    NOT NULL,
    acres     REAL    NOT NULL CHECK (acres > 0),
    notes     TEXT,
    UNIQUE (farm_id, name)
);

CREATE TABLE IF NOT EXISTS soil_sample (
    sample_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    field_id            INTEGER NOT NULL REFERENCES field (field_id) ON DELETE CASCADE,
    sample_date         TEXT    NOT NULL,
    nitrogen_ppm        REAL    CHECK (nitrogen_ppm >= 0),
    phosphorus_ppm      REAL    CHECK (phosphorus_ppm >= 0),
    potassium_ppm       REAL    CHECK (potassium_ppm >= 0),
    ph                  REAL    CHECK (ph BETWEEN 0 AND 14),
    organic_matter_pct  REAL    CHECK (organic_matter_pct BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS application (
    application_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    field_id          INTEGER NOT NULL REFERENCES field (field_id) ON DELETE CASCADE,
    application_date  TEXT    NOT NULL,
    application_type  TEXT    NOT NULL
                      CHECK (application_type IN ('WATER', 'FERTILIZER', 'LIME', 'CHEMICAL')),
    product           TEXT,
    amount            REAL    NOT NULL CHECK (amount > 0),
    unit              TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS input_cost (
    cost_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    field_id      INTEGER NOT NULL REFERENCES field (field_id) ON DELETE CASCADE,
    crop_year     INTEGER NOT NULL,
    cost_date     TEXT    NOT NULL,
    category      TEXT    NOT NULL
                  CHECK (category IN ('SEED', 'FERTILIZER', 'CHEMICAL', 'FUEL',
                                      'LABOR', 'EQUIPMENT', 'WATER', 'OTHER')),
    description   TEXT,
    amount_cents  INTEGER NOT NULL CHECK (amount_cents > 0)
);

CREATE TABLE IF NOT EXISTS yield_record (
    yield_id              INTEGER PRIMARY KEY AUTOINCREMENT,
    field_id              INTEGER NOT NULL REFERENCES field (field_id) ON DELETE CASCADE,
    crop_year             INTEGER NOT NULL,
    crop                  TEXT    NOT NULL,
    harvest_date          TEXT    NOT NULL,
    total_yield           REAL    NOT NULL CHECK (total_yield >= 0),
    yield_unit            TEXT    NOT NULL CHECK (yield_unit IN ('BUSHELS', 'TONS')),
    price_cents_per_unit  INTEGER NOT NULL CHECK (price_cents_per_unit >= 0),
    UNIQUE (field_id, crop_year, crop)
);

CREATE TABLE IF NOT EXISTS cash_entry (
    cash_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    farm_id        INTEGER NOT NULL REFERENCES farm (farm_id) ON DELETE CASCADE,
    entry_date     TEXT    NOT NULL,
    balance_cents  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS loan (
    loan_id                INTEGER PRIMARY KEY AUTOINCREMENT,
    farm_id                INTEGER NOT NULL REFERENCES farm (farm_id) ON DELETE CASCADE,
    lender                 TEXT    NOT NULL,
    principal_cents        INTEGER NOT NULL CHECK (principal_cents > 0),
    annual_rate            REAL    NOT NULL CHECK (annual_rate >= 0),
    start_date             TEXT    NOT NULL,
    term_months            INTEGER NOT NULL CHECK (term_months > 0),
    monthly_payment_cents  INTEGER NOT NULL CHECK (monthly_payment_cents >= 0),
    first_payment_date     TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS bill (
    bill_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    farm_id       INTEGER NOT NULL REFERENCES farm (farm_id) ON DELETE CASCADE,
    payee         TEXT    NOT NULL,
    amount_cents  INTEGER NOT NULL CHECK (amount_cents > 0),
    due_date      TEXT    NOT NULL,
    paid          INTEGER NOT NULL DEFAULT 0 CHECK (paid IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_field_farm          ON field (farm_id);
CREATE INDEX IF NOT EXISTS idx_sample_field_date   ON soil_sample (field_id, sample_date);
CREATE INDEX IF NOT EXISTS idx_application_field   ON application (field_id, application_date);
CREATE INDEX IF NOT EXISTS idx_cost_field_year     ON input_cost (field_id, crop_year);
CREATE INDEX IF NOT EXISTS idx_yield_field_crop    ON yield_record (field_id, crop);
CREATE INDEX IF NOT EXISTS idx_cash_farm_date      ON cash_entry (farm_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_loan_farm           ON loan (farm_id);
CREATE INDEX IF NOT EXISTS idx_bill_farm_paid_due  ON bill (farm_id, paid, due_date);

CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================================
-- Subscription plans (platform-defined tiers). capability_mask is the ceiling
-- of Capability bits an organization on this plan can ever exercise, no
-- matter what its roles grant. stripe_price_id is wired up once billing
-- (Stripe) lands; nullable/unused until then.
-- ============================================================================
CREATE TABLE subscription_plans (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(50)  NOT NULL UNIQUE,
    name              VARCHAR(100) NOT NULL,
    capability_mask   BIGINT NOT NULL DEFAULT 0,
    max_users         INT,              -- NULL = unlimited
    max_storage_bytes BIGINT,           -- NULL = unlimited
    max_documents     INT,              -- NULL = unlimited
    stripe_price_id   VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- Organizations are the tenant boundary. Every other table hangs off org_id.
-- One reserved, non-billable row (slug = '_platform') holds VOR's own
-- operator accounts so org_id can stay NOT NULL everywhere.
-- ============================================================================
CREATE TABLE organizations (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    slug                    VARCHAR(100) NOT NULL UNIQUE,
    plan_id                 BIGINT NOT NULL REFERENCES subscription_plans(id),
    subscription_status     VARCHAR(20) NOT NULL DEFAULT 'TRIALING'
                                CHECK (subscription_status IN ('TRIALING','ACTIVE','PAST_DUE','CANCELED','SUSPENDED')),
    trial_ends_at           TIMESTAMP WITH TIME ZONE,
    stripe_customer_id      VARCHAR(255) UNIQUE,
    stripe_subscription_id  VARCHAR(255) UNIQUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orgs_stripe_customer ON organizations(stripe_customer_id);

-- ============================================================================
-- Departments are per-organization and self-nesting (org charts nest).
-- ============================================================================
CREATE TABLE departments (
    id                     BIGSERIAL PRIMARY KEY,
    org_id                 BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    parent_department_id   BIGINT REFERENCES departments(id) ON DELETE CASCADE,
    name                   VARCHAR(255) NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_departments_org ON departments(org_id);
CREATE INDEX idx_departments_parent ON departments(parent_department_id);
-- Postgres treats NULL as distinct in a plain multi-column UNIQUE, so top-level
-- and nested name-uniqueness need separate partial indexes.
CREATE UNIQUE INDEX uq_departments_top_level ON departments(org_id, name) WHERE parent_department_id IS NULL;
CREATE UNIQUE INDEX uq_departments_nested ON departments(org_id, parent_department_id, name) WHERE parent_department_id IS NOT NULL;

-- ============================================================================
-- Roles are per-organization and configurable: a company names its own roles,
-- gives each a seniority level, and picks which fixed platform Capability
-- bits (see security.Capability) that role gets. hierarchy_level seeds with
-- gaps (10/20/30/40) so an org can insert intermediate levels later.
-- ============================================================================
CREATE TABLE roles (
    id                BIGSERIAL PRIMARY KEY,
    org_id            BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    hierarchy_level   INT NOT NULL,
    capability_mask   BIGINT NOT NULL DEFAULT 0,
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, name)
);
CREATE INDEX idx_roles_org ON roles(org_id);

-- ============================================================================
-- Users. Login identifier is email (globally unique) since username becomes
-- an org-scoped display name once orgs exist. role_id is required and
-- ON DELETE RESTRICT: a role can't be deleted while anyone still holds it.
-- ============================================================================
CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    org_id                BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    department_id         BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    role_id               BIGINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    username              VARCHAR(255) NOT NULL,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    totp_secret           VARCHAR(255),
    is_platform_operator  BOOLEAN NOT NULL DEFAULT FALSE,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, username)
);
CREATE INDEX idx_users_org ON users(org_id);
CREATE INDEX idx_users_department ON users(department_id);

CREATE TABLE sessions (
    id              BIGSERIAL PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    jwt_id          VARCHAR(255) NOT NULL,
    issued_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address      INET,
    user_agent      TEXT
);
CREATE INDEX idx_sessions_org ON sessions(org_id);

-- ============================================================================
-- Documents / chunks. Visibility is no longer a fixed 6-bit mask: a document
-- has a minimum_role_level (0 = everyone in scope) and an optional
-- department_id (NULL = org-wide). chunks denormalizes org_id/department_id/
-- minimum_role_level from its parent document at ingestion time, same
-- pattern the original schema used, so the RAG hot-path query never needs an
-- extra join to enforce visibility.
-- ============================================================================
CREATE TABLE documents (
    id                  BIGSERIAL PRIMARY KEY,
    org_id              BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    department_id       BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    title               VARCHAR(512) NOT NULL,
    file_hash           VARCHAR(64) NOT NULL,
    file_uri            VARCHAR(1024) NOT NULL,
    file_size           BIGINT DEFAULT 0,
    uploaded_by         BIGINT NOT NULL REFERENCES users(id),
    uploaded_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','SYNCED','ERROR')),
    processed_at        TIMESTAMP WITH TIME ZONE,
    error_message       TEXT,
    minimum_role_level  INT NOT NULL DEFAULT 0,
    metadata_json       JSONB,
    UNIQUE (org_id, file_hash)
);
CREATE INDEX idx_documents_org_status ON documents(org_id, status);
CREATE INDEX idx_documents_uploaded_at ON documents(uploaded_at DESC);

CREATE TABLE chunks (
    id                  BIGSERIAL PRIMARY KEY,
    doc_id              BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    org_id              BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    department_id       BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    chunk_index         INT NOT NULL,
    content             TEXT NOT NULL,
    minimum_role_level  INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_chunks_doc ON chunks(doc_id);
CREATE INDEX idx_chunks_org_level ON chunks(org_id, minimum_role_level);
CREATE INDEX idx_chunks_department ON chunks(department_id);

CREATE TABLE embeddings (
    id              BIGSERIAL PRIMARY KEY,
    chunk_id        BIGINT NOT NULL UNIQUE REFERENCES chunks(id) ON DELETE CASCADE,
    embedding       vector(1024) NOT NULL
);

CREATE INDEX embeddings_hnsw_idx ON embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ============================================================================
-- Chat threads / history. org_id is denormalized (not just derivable through
-- a join) so an executive dashboard's "all org chat activity" query and the
-- VIEW_ALL_ORG_CHATS capability can scan directly without joining to users.
-- ============================================================================
CREATE TABLE threads (
    id              BIGSERIAL PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_threads_org ON threads(org_id, updated_at DESC);
CREATE INDEX idx_threads_user ON threads(user_id, created_at DESC);

CREATE TABLE chat_history (
    id              BIGSERIAL PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    thread_id       BIGINT NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query           TEXT NOT NULL,
    response        TEXT NOT NULL,
    source_ids      JSONB,
    tokens_used     INT DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_chat_history_org ON chat_history(org_id, created_at DESC);
CREATE INDEX idx_chat_thread ON chat_history(thread_id, created_at);

-- ============================================================================
-- Config: platform_config is true infra-level defaults (platform-operator
-- only). org_settings is per-tenant key/value overrides.
-- ============================================================================
CREATE TABLE platform_config (
    key             VARCHAR(255) PRIMARY KEY,
    value           TEXT NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
INSERT INTO platform_config (key, value) VALUES
    ('temperature', '0.7'),
    ('top_p', '0.9'),
    ('frequency_penalty', '0.0');

CREATE TABLE org_settings (
    org_id      BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    key         VARCHAR(255) NOT NULL,
    value       TEXT NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (org_id, key)
);

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    org_id          BIGINT REFERENCES organizations(id) ON DELETE SET NULL,
    user_id         BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(255) NOT NULL,
    details         JSONB,
    ip_address      INET,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_audit_org ON audit_log(org_id, created_at DESC);
CREATE INDEX idx_audit_user ON audit_log(user_id, created_at DESC);

-- ============================================================================
-- Seed data: subscription tiers and the reserved internal platform org/role
-- that VOR's own operator accounts live in. Capability bits mirror
-- security.Capability: UPLOAD_DOCUMENTS=1, MANAGE_USERS=2,
-- MANAGE_DEPARTMENTS=4, MANAGE_BILLING=8, VIEW_ALL_ORG_CHATS=16,
-- MANAGE_ROLES=32, INVITE_USERS=64, VIEW_ANALYTICS=128 (255 = all).
-- ============================================================================
INSERT INTO subscription_plans (code, name, capability_mask, max_users, max_storage_bytes, max_documents) VALUES
    ('trial',      'Trial',      73,  5,   1073741824,   20),
    ('starter',    'Starter',    109, 25,  10737418240,  200),
    ('business',   'Business',   127, 200, 107374182400, 2000),
    ('enterprise', 'Enterprise', 255, NULL, NULL,        NULL);

INSERT INTO organizations (name, slug, plan_id, subscription_status)
    SELECT 'VOR Concierge Platform', '_platform', id, 'ACTIVE' FROM subscription_plans WHERE code = 'enterprise';

INSERT INTO roles (org_id, name, hierarchy_level, capability_mask, is_default)
    SELECT id, 'Platform Operator', 999, 255, TRUE FROM organizations WHERE slug = '_platform';

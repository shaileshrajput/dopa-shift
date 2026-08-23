-- DopaShift Initial Schema
-- Flyway Migration V1: All core tables, indexes, constraints, and foreign keys

-- ============================================================================
-- USERS
-- ============================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    preferred_locale VARCHAR(5) NOT NULL DEFAULT 'en',
    accent_color VARCHAR(7),
    profile_photo_url TEXT,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    reminder_aggressiveness JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- GOALS
-- ============================================================================
CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, name)
);
CREATE INDEX idx_goals_user_id ON goals(user_id);

-- ============================================================================
-- KEYWORDS
-- ============================================================================
CREATE TABLE keywords (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    keyword VARCHAR(50) NOT NULL,
    UNIQUE(goal_id, keyword)
);
CREATE INDEX idx_keywords_goal_id ON keywords(goal_id);

-- ============================================================================
-- GOAL CHECKLIST ITEMS
-- ============================================================================
CREATE TABLE goal_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    text VARCHAR(500) NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_goal_checklist_goal_id ON goal_checklist_items(goal_id);
CREATE INDEX idx_goal_checklist_user_id ON goal_checklist_items(user_id);

-- ============================================================================
-- DAILY TODO ITEMS
-- ============================================================================
CREATE TABLE daily_todo_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    due_date_time TIMESTAMPTZ,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    day_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_daily_todo_user_date ON daily_todo_items(user_id, day_date);

-- ============================================================================
-- HABIT TRACKS
-- ============================================================================
CREATE TABLE habit_tracks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    start_date DATE NOT NULL,
    current_day INT NOT NULL DEFAULT 1,
    is_finished BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_habit_tracks_goal ON habit_tracks(goal_id);
CREATE INDEX idx_habit_tracks_user ON habit_tracks(user_id);

-- ============================================================================
-- HABIT CHECKPOINTS
-- ============================================================================
CREATE TABLE habit_checkpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    habit_track_id UUID NOT NULL REFERENCES habit_tracks(id) ON DELETE CASCADE,
    day_number INT NOT NULL CHECK (day_number BETWEEN 1 AND 30),
    description VARCHAR(200) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMPTZ,
    UNIQUE(habit_track_id, day_number)
);
CREATE INDEX idx_checkpoints_track ON habit_checkpoints(habit_track_id);

-- ============================================================================
-- REMINDERS
-- ============================================================================
CREATE TABLE reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL,
    entity_id UUID NOT NULL,
    scheduled_time TIME NOT NULL,
    scheduled_date DATE,
    recurrence_type VARCHAR(20),
    recurrence_weekdays INT[],
    recurrence_interval_days INT,
    condition_type VARCHAR(20),
    escalation_interval_minutes INT NOT NULL DEFAULT 15,
    max_escalations INT NOT NULL DEFAULT 3,
    current_escalation_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_fired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reminders_user ON reminders(user_id);
CREATE INDEX idx_reminders_entity ON reminders(entity_id);

-- ============================================================================
-- CHANGE LOG (Sync Engine)
-- ============================================================================
CREATE TABLE change_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id UUID NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    field VARCHAR(100) NOT NULL,
    value JSONB,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    device_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id)
);
CREATE INDEX idx_change_log_user_ts ON change_log(user_id, timestamp);
CREATE INDEX idx_change_log_entity ON change_log(entity_id);

-- ============================================================================
-- CONFLICT HISTORY
-- ============================================================================
CREATE TABLE conflict_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_log_id UUID NOT NULL REFERENCES change_log(id),
    superseded_value JSONB,
    superseded_device_id VARCHAR(100),
    superseded_timestamp TIMESTAMPTZ,
    resolution_reason VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- AUDIT LOG
-- ============================================================================
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    before_state JSONB,
    after_state JSONB,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    previous_hash VARCHAR(128),
    entry_hash VARCHAR(128) NOT NULL
);
CREATE INDEX idx_audit_correlation ON audit_log(correlation_id);
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);

-- ============================================================================
-- EFFICIENCY SCORES
-- ============================================================================
CREATE TABLE efficiency_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    score_date DATE NOT NULL,
    productive_seconds BIGINT NOT NULL,
    total_tracked_seconds BIGINT NOT NULL,
    score_percent INT,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, score_date)
);
CREATE INDEX idx_efficiency_user_date ON efficiency_scores(user_id, score_date);

-- ============================================================================
-- INTERCEPTION RULES
-- ============================================================================
CREATE TABLE interception_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_id UUID REFERENCES goals(id),
    app_package_name VARCHAR(256),
    site_domain VARCHAR(256),
    daily_allowance_minutes INT NOT NULL CHECK (daily_allowance_minutes BETWEEN 1 AND 480),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_interception_user ON interception_rules(user_id);

-- ============================================================================
-- VIDEO CACHE
-- ============================================================================
CREATE TABLE video_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keyword_set_hash VARCHAR(64) NOT NULL,
    video_url TEXT NOT NULL,
    video_title TEXT,
    source VARCHAR(10) NOT NULL,
    goal_id UUID REFERENCES goals(id),
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_video_cache_hash ON video_cache(keyword_set_hash);
CREATE INDEX idx_video_cache_expires ON video_cache(expires_at);

-- ============================================================================
-- LLM CONFIGURATIONS
-- ============================================================================
CREATE TABLE llm_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    provider_type VARCHAR(20) NOT NULL,
    api_key_encrypted BYTEA NOT NULL,
    api_key_iv BYTEA NOT NULL,
    is_validated BOOLEAN NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- JOB QUEUE
-- ============================================================================
CREATE TABLE job_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority INT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    scheduled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_job_queue_status ON job_queue(status, scheduled_at) WHERE status = 'PENDING';

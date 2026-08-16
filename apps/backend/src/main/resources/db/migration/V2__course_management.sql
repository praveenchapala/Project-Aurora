-- ==========================================
-- Project Aurora
-- Version: V2
-- Description: Course Management Schema
-- ==========================================

CREATE TABLE courses (

    id BIGSERIAL PRIMARY KEY,

    title VARCHAR(200) NOT NULL,

    description TEXT,

    instructor_id BIGINT NOT NULL,

    is_published BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_courses_instructor
        FOREIGN KEY (instructor_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_courses_instructor_id
    ON courses(instructor_id);

CREATE INDEX idx_courses_published
    ON courses(is_published);
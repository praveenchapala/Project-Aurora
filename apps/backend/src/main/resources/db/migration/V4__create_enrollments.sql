CREATE TABLE enrollments (
    id BIGSERIAL PRIMARY KEY,

    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,

    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_enrollments_student
        FOREIGN KEY (student_id)
        REFERENCES users(id),

    CONSTRAINT fk_enrollments_course
        FOREIGN KEY (course_id)
        REFERENCES courses(id),

    CONSTRAINT uq_enrollments_student_course
        UNIQUE (student_id, course_id)
);

CREATE INDEX idx_enrollments_course_id
    ON enrollments(course_id);
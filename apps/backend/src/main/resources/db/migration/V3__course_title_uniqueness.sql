-- Enforce case-insensitive course title uniqueness per instructor.
-- Leading and trailing whitespace is ignored.

CREATE UNIQUE INDEX uq_courses_instructor_title_normalized
ON courses (instructor_id, LOWER(BTRIM(title)));
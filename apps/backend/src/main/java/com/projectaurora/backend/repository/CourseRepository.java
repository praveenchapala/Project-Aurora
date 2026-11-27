
package com.projectaurora.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.projectaurora.backend.entity.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // Find all published courses with pagination
    Page<Course> findByPublishedTrue(Pageable pageable);

    // Find all courses created by a specific instructor
    Page<Course> findByInstructor_Id(
            Long instructorId,
            Pageable pageable
    );

    // Check whether an instructor already has a course with this title
    boolean existsByTitleIgnoreCaseAndInstructor_Id(
            String title,
            Long instructorId
    );

    // Check for duplicate titles while excluding the current course
    boolean existsByTitleIgnoreCaseAndInstructor_IdAndIdNot(
            String title,
            Long instructorId,
            Long courseId
    );
}

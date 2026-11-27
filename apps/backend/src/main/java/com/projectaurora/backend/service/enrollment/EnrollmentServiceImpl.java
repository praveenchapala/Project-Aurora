
package com.projectaurora.backend.service.enrollment;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectaurora.backend.dto.enrollment.EnrollmentResponse;
import com.projectaurora.backend.entity.Course;
import com.projectaurora.backend.entity.Enrollment;
import com.projectaurora.backend.entity.EnrollmentStatus;
import com.projectaurora.backend.entity.User;
import com.projectaurora.backend.exception.CourseNotPublishedException;
import com.projectaurora.backend.exception.DuplicateEnrollmentException;
import com.projectaurora.backend.exception.ResourceNotFoundException;
import com.projectaurora.backend.repository.CourseRepository;
import com.projectaurora.backend.repository.EnrollmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    /**
     * Enroll a student in a published course.
     */
    @Override
    @Transactional
    public EnrollmentResponse enroll(Long courseId, User student) {

        // Validate authenticated student
        if (student == null || student.getId() == null) {
            throw new IllegalArgumentException(
                    "Authenticated student is required"
            );
        }

        // Find course
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course not found with id: " + courseId
                ));

        // Enrollment is allowed only for published courses
        if (!Boolean.TRUE.equals(course.getPublished())) {
            throw new CourseNotPublishedException(
                    "Enrollment is allowed only for published courses"
            );
        }

        // Prevent duplicate enrollment
        boolean alreadyEnrolled =
                enrollmentRepository.existsByStudentIdAndCourseId(
                        student.getId(),
                        courseId
                );

        if (alreadyEnrolled) {
            throw new DuplicateEnrollmentException(
                    "You are already enrolled in this course"
            );
        }

        // Create enrollment
        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .course(course)
                .enrolledAt(LocalDateTime.now())
                .status(EnrollmentStatus.ACTIVE)
                .build();

        try {
            Enrollment savedEnrollment =
                    enrollmentRepository.saveAndFlush(enrollment);

            return mapToResponse(savedEnrollment);

        } catch (DataIntegrityViolationException exception) {

            // Handle concurrent duplicate enrollment attempts
            if (isDuplicateEnrollmentConstraint(exception)) {
                throw new DuplicateEnrollmentException(
                        "You are already enrolled in this course"
                );
            }

            // Re-throw unrelated database errors
            throw exception;
        }
    }

    /**
     * Retrieve all enrollments belonging to a student.
     */
    @Override
    public List<EnrollmentResponse> getMyEnrollments(Long studentId) {

        return enrollmentRepository.findAllByStudentId(studentId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Check whether a student is enrolled in a course.
     */
    @Override
    public boolean isEnrolled(Long courseId, Long studentId) {

        // Verify that the course exists
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException(
                    "Course not found with id: " + courseId
            );
        }

        return enrollmentRepository.existsByStudentIdAndCourseId(
                studentId,
                courseId
        );
    }

    /**
     * Convert Enrollment entity into EnrollmentResponse DTO.
     */
    private EnrollmentResponse mapToResponse(Enrollment enrollment) {

        Course course = enrollment.getCourse();
        User instructor = course.getInstructor();

        String instructorName = null;

        if (instructor != null) {
            instructorName = (
                    instructor.getFirstName() + " " +
                    instructor.getLastName()
            ).trim();
        }

        return EnrollmentResponse.builder()
                .id(enrollment.getId())
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .instructorName(instructorName)
                .enrolledAt(enrollment.getEnrolledAt())
                .status(enrollment.getStatus())
                .build();
    }

    /**
     * Identify whether a database constraint violation was caused
     * by the enrollment unique constraint.
     */
    private boolean isDuplicateEnrollmentConstraint(
            DataIntegrityViolationException exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof
                    org.hibernate.exception.ConstraintViolationException) {

                org.hibernate.exception.ConstraintViolationException
                        hibernateException =
                        (org.hibernate.exception.ConstraintViolationException)
                                cause;

                if ("uq_enrollments_student_course".equals(
                        hibernateException.getConstraintName())) {
                    return true;
                }
            }

            String message = cause.getMessage();

            if (message != null &&
                    message.contains("uq_enrollments_student_course")) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}

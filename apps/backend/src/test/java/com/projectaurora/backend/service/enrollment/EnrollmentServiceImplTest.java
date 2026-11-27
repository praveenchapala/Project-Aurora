
package com.projectaurora.backend.service.enrollment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private EnrollmentServiceImpl enrollmentService;

    private User student;
    private User instructor;
    private Course course;

    @BeforeEach
    void setUp() {

        student = User.builder()
                .id(3L)
                .firstName("Praveen")
                .lastName("Chapala")
                .email("student@example.com")
                .passwordHash("password")
                .build();

        instructor = User.builder()
                .id(1L)
                .firstName("Course")
                .lastName("Instructor")
                .email("instructor@example.com")
                .passwordHash("password")
                .build();

        course = Course.builder()
                .id(101L)
                .title("Java Programming")
                .description("Learn Java")
                .instructor(instructor)
                .published(true)
                .build();
    }

    @Test
    void enroll_ShouldCreateEnrollmentSuccessfully() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        when(enrollmentRepository
                .existsByStudentIdAndCourseId(3L, 101L))
                .thenReturn(false);

        when(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
                .thenAnswer(invocation -> {
                    Enrollment enrollment = invocation.getArgument(0);
                    enrollment.setId(501L);
                    return enrollment;
                });

        EnrollmentResponse response =
                enrollmentService.enroll(101L, student);

        assertEquals(501L, response.getId());
        assertEquals(101L, response.getCourseId());
        assertEquals("Java Programming", response.getCourseTitle());
        assertEquals("Course Instructor", response.getInstructorName());
        assertNotNull(response.getEnrolledAt());
        assertEquals(EnrollmentStatus.ACTIVE, response.getStatus());

        verify(enrollmentRepository)
                .saveAndFlush(any(Enrollment.class));
    }

    @Test
    void enroll_ShouldThrowWhenCourseDoesNotExist() {

        when(courseRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> enrollmentService.enroll(999L, student)
        );

        verify(enrollmentRepository, never())
                .saveAndFlush(any(Enrollment.class));
    }

    @Test
    void enroll_ShouldRejectUnpublishedCourse() {

        course.setPublished(false);

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        assertThrows(
                CourseNotPublishedException.class,
                () -> enrollmentService.enroll(101L, student)
        );

        verify(enrollmentRepository, never())
                .saveAndFlush(any(Enrollment.class));
    }

    @Test
    void enroll_ShouldRejectExistingEnrollment() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        when(enrollmentRepository
                .existsByStudentIdAndCourseId(3L, 101L))
                .thenReturn(true);

        assertThrows(
                DuplicateEnrollmentException.class,
                () -> enrollmentService.enroll(101L, student)
        );

        verify(enrollmentRepository, never())
                .saveAndFlush(any(Enrollment.class));
    }

    @Test
    void enroll_ShouldHandleDatabaseDuplicateConstraint() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        when(enrollmentRepository
                .existsByStudentIdAndCourseId(3L, 101L))
                .thenReturn(false);

        ConstraintViolationException constraintViolation =
                new ConstraintViolationException(
                        "Duplicate enrollment",
                        (SQLException) null,
                        "uq_enrollments_student_course"
                );

        when(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Database constraint violation",
                        constraintViolation
                ));

        assertThrows(
                DuplicateEnrollmentException.class,
                () -> enrollmentService.enroll(101L, student)
        );
    }

    @Test
    void enroll_ShouldRethrowUnrelatedDatabaseError() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        when(enrollmentRepository
                .existsByStudentIdAndCourseId(3L, 101L))
                .thenReturn(false);

        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException(
                        "Unrelated database error",
                        new RuntimeException("Another constraint failed")
                );

        when(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
                .thenThrow(databaseException);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> enrollmentService.enroll(101L, student)
        );

        assertSame(databaseException, thrown);
    }

    @Test
    void getMyEnrollments_ShouldReturnOnlyStudentsEnrollments() {

        Enrollment enrollment = Enrollment.builder()
                .id(501L)
                .student(student)
                .course(course)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        when(enrollmentRepository.findAllByStudentId(3L))
                .thenReturn(List.of(enrollment));

        List<EnrollmentResponse> responses =
                enrollmentService.getMyEnrollments(3L);

        assertEquals(1, responses.size());
        assertEquals(501L, responses.get(0).getId());
        assertEquals("Java Programming", responses.get(0).getCourseTitle());
        assertEquals(
                "Course Instructor",
                responses.get(0).getInstructorName()
        );

        verify(enrollmentRepository)
                .findAllByStudentId(3L);
    }

    @Test
    void isEnrolled_ShouldReturnRepositoryResult() {

        // The service checks course existence before enrollment.
        when(courseRepository.existsById(101L))
                .thenReturn(true);

        when(enrollmentRepository
                .existsByStudentIdAndCourseId(3L, 101L))
                .thenReturn(true);

        assertTrue(enrollmentService.isEnrolled(101L, 3L));

        verify(courseRepository)
                .existsById(101L);

        verify(enrollmentRepository)
                .existsByStudentIdAndCourseId(3L, 101L);
    }

    @Test
    void isEnrolled_ShouldThrowWhenCourseDoesNotExist() {

        when(courseRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> enrollmentService.isEnrolled(999L, 3L)
        );

        verify(enrollmentRepository, never())
                .existsByStudentIdAndCourseId(anyLong(), anyLong());
    }
}

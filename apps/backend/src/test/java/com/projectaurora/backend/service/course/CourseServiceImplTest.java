
package com.projectaurora.backend.service.course;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import com.projectaurora.backend.dto.common.PageResponse;
import com.projectaurora.backend.dto.course.CourseCreateRequest;
import com.projectaurora.backend.dto.course.CourseResponse;
import com.projectaurora.backend.dto.course.CourseUpdateRequest;

import com.projectaurora.backend.entity.Course;
import com.projectaurora.backend.entity.User;

import com.projectaurora.backend.exception.DuplicateCourseException;
import com.projectaurora.backend.exception.ResourceNotFoundException;

import com.projectaurora.backend.repository.CourseRepository;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseServiceImpl courseService;

    private User instructor;
    private User anotherInstructor;
    private Course course;

    @BeforeEach
    void setUp() {

        instructor = User.builder()
                .id(1L)
                .firstName("Praveen")
                .lastName("Chapala")
                .email("instructor@example.com")
                .passwordHash("password")
                .build();

        anotherInstructor = User.builder()
                .id(2L)
                .firstName("Another")
                .lastName("Instructor")
                .email("another@example.com")
                .passwordHash("password")
                .build();

        course = Course.builder()
                .id(101L)
                .title("Java Programming")
                .description("Learn Java from basics")
                .instructor(instructor)
                .published(false)
                .build();
    }

    @Test
    void createCourse_ShouldCreateDraftSuccessfully() {

        CourseCreateRequest request = new CourseCreateRequest();
        request.setTitle(" Java Programming ");
        request.setDescription(" Learn Java ");

        when(courseRepository
                .existsByTitleIgnoreCaseAndInstructor_Id(
                        "Java Programming", 1L))
                .thenReturn(false);

        when(courseRepository.saveAndFlush(any(Course.class)))
                .thenAnswer(invocation -> {
                    Course saved = invocation.getArgument(0);
                    saved.setId(101L);
                    return saved;
                });

        CourseResponse response =
                courseService.createCourse(request, instructor);

        assertEquals(101L, response.getId());
        assertEquals("Java Programming", response.getTitle());
        assertEquals("Learn Java", response.getDescription());
        assertEquals(1L, response.getInstructorId());
        assertEquals("Praveen Chapala", response.getInstructorName());
        assertFalse(response.getPublished());

        verify(courseRepository).saveAndFlush(any(Course.class));
    }

    @Test
    void createCourse_ShouldRejectDuplicateTitle() {

        CourseCreateRequest request = new CourseCreateRequest();
        request.setTitle("Java Programming");

        when(courseRepository
                .existsByTitleIgnoreCaseAndInstructor_Id(
                        "Java Programming", 1L))
                .thenReturn(true);

        assertThrows(
                DuplicateCourseException.class,
                () -> courseService.createCourse(request, instructor)
        );

        verify(courseRepository, never()).saveAndFlush(any(Course.class));
    }

    @Test
    void createCourse_ShouldHandleDatabaseDuplicateConstraint() {

        CourseCreateRequest request = new CourseCreateRequest();
        request.setTitle("Java Programming");

        when(courseRepository
                .existsByTitleIgnoreCaseAndInstructor_Id(
                        "Java Programming", 1L))
                .thenReturn(false);

        ConstraintViolationException constraintViolation =
                new ConstraintViolationException(
                        "Duplicate course title",
                        (SQLException) null,
                        "uq_courses_instructor_title_normalized"
                );

        when(courseRepository.saveAndFlush(any(Course.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Database constraint violation",
                        constraintViolation
                ));

        assertThrows(
                DuplicateCourseException.class,
                () -> courseService.createCourse(request, instructor)
        );

        verify(courseRepository).saveAndFlush(any(Course.class));
    }

    @Test
    void createCourse_ShouldRethrowUnrelatedDatabaseError() {

        CourseCreateRequest request = new CourseCreateRequest();
        request.setTitle("Java Programming");

        when(courseRepository
                .existsByTitleIgnoreCaseAndInstructor_Id(
                        "Java Programming", 1L))
                .thenReturn(false);

        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException(
                        "Unrelated database constraint violation",
                        new RuntimeException("Some other database error")
                );

        when(courseRepository.saveAndFlush(any(Course.class)))
                .thenThrow(databaseException);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> courseService.createCourse(request, instructor)
        );

        assertSame(databaseException, thrown);
    }

    @Test
    void getCourses_ShouldReturnPublishedCoursesForNonAdmin() {

        Pageable pageable = PageRequest.of(0, 10);

        Course publishedCourse = Course.builder()
                .id(102L)
                .title("Spring Boot")
                .description("Spring Boot fundamentals")
                .instructor(instructor)
                .published(true)
                .build();

        Page<Course> coursePage =
                new PageImpl<>(java.util.List.of(publishedCourse));

        when(courseRepository.findByPublishedTrue(any(Pageable.class)))
                .thenReturn(coursePage);

        PageResponse<CourseResponse> response =
                courseService.getCourses(pageable, false);

        assertEquals(1, response.getTotalElements());
        assertEquals("Spring Boot", response.getContent().get(0).getTitle());

        verify(courseRepository).findByPublishedTrue(any(Pageable.class));
        verify(courseRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getCourses_ShouldReturnAllCoursesForAdmin() {

        Pageable pageable = PageRequest.of(0, 10);

        when(courseRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(course)));

        PageResponse<CourseResponse> response =
                courseService.getCourses(pageable, true);

        assertEquals(1, response.getTotalElements());

        verify(courseRepository).findAll(any(Pageable.class));
        verify(courseRepository, never())
                .findByPublishedTrue(any(Pageable.class));
    }

    @Test
    void getCourseById_ShouldAllowInstructorToViewOwnDraft() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        CourseResponse response =
                courseService.getCourseById(
                        101L,
                        1L,
                        true,
                        false
                );

        assertEquals(101L, response.getId());
        assertEquals("Java Programming", response.getTitle());
    }

    @Test
    void getCourseById_ShouldRejectStudentAccessToDraft() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        assertThrows(
                AccessDeniedException.class,
                () -> courseService.getCourseById(
                        101L,
                        3L,
                        false,
                        false
                )
        );
    }

    @Test
    void updateCourse_ShouldRejectAnotherInstructorsCourse() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        CourseUpdateRequest request = new CourseUpdateRequest();
        request.setTitle("Updated Java");
        request.setDescription("Updated description");

        assertThrows(
                AccessDeniedException.class,
                () -> courseService.updateCourse(
                        101L,
                        request,
                        2L,
                        true,
                        false
                )
        );

        verify(courseRepository, never())
                .saveAndFlush(any(Course.class));
    }

    @Test
    void updateCourse_ShouldAllowAdminToUpdateAnyCourse() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        when(courseRepository
                .existsByTitleIgnoreCaseAndInstructor_IdAndIdNot(
                        "Updated Java",
                        1L,
                        101L))
                .thenReturn(false);

        when(courseRepository.saveAndFlush(any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseUpdateRequest request = new CourseUpdateRequest();
        request.setTitle("Updated Java");
        request.setDescription("Updated description");

        CourseResponse response =
                courseService.updateCourse(
                        101L,
                        request,
                        99L,
                        false,
                        true
                );

        assertEquals("Updated Java", response.getTitle());
        assertEquals("Updated description", response.getDescription());

        verify(courseRepository).saveAndFlush(any(Course.class));
    }

    @Test
    void publishCourse_ShouldPublishDraftSuccessfully() {

        when(courseRepository.findById(101L))
                .thenReturn(Optional.of(course));

        when(courseRepository.save(any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponse response =
                courseService.publishCourse(
                        101L,
                        1L,
                        true,
                        false
                );

        assertTrue(response.getPublished());

        verify(courseRepository).save(course);
    }

    @Test
    void getCourseById_ShouldThrowExceptionWhenCourseDoesNotExist() {

        when(courseRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> courseService.getCourseById(
                        999L,
                        1L,
                        true,
                        false
                )
        );
    }

    @Test
    void getCourses_ShouldLimitPageSizeTo50() {

        Pageable requestedPageable = PageRequest.of(0, 100);

        when(courseRepository.findByPublishedTrue(any(Pageable.class)))
                .thenReturn(Page.empty());

        courseService.getCourses(requestedPageable, false);

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);

        verify(courseRepository).findByPublishedTrue(captor.capture());

        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    void getCourses_ShouldRejectUnsupportedSortProperty() {

        Pageable pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Direction.ASC, "passwordHash")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> courseService.getCourses(pageable, false)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(courseRepository);
    }

    @Test
    void getCourses_ShouldAcceptSupportedSortProperty() {

        Pageable pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        when(courseRepository.findByPublishedTrue(any(Pageable.class)))
                .thenReturn(Page.empty());

        courseService.getCourses(pageable, false);

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);

        verify(courseRepository).findByPublishedTrue(captor.capture());

        assertEquals(
                "createdAt",
                captor.getValue().getSort().getOrderFor("createdAt").getProperty()
        );

        assertEquals(
                Sort.Direction.DESC,
                captor.getValue().getSort().getOrderFor("createdAt").getDirection()
        );
    }
}

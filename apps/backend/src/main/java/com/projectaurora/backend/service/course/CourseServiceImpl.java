
package com.projectaurora.backend.service.course;

import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseServiceImpl implements CourseService {

    private static final int MAX_PAGE_SIZE = 50;

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "createdAt",
            "updatedAt",
            "title"
    );

    private static final String DUPLICATE_TITLE_CONSTRAINT =
            "uq_courses_instructor_title_normalized";

    private final CourseRepository courseRepository;

    @Override
    public CourseResponse createCourse(
            CourseCreateRequest request,
            User instructor) {

        String title = request.getTitle().trim();

        if (courseRepository.existsByTitleIgnoreCaseAndInstructor_Id(
                title, instructor.getId())) {

            throw new DuplicateCourseException(
                    "You already have a course with this title."
            );
        }

        Course course = Course.builder()
                .title(title)
                .description(normalizeDescription(request.getDescription()))
                .instructor(instructor)
                .published(false)
                .build();

        Course savedCourse = saveCourse(course);

        return mapToResponse(savedCourse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> getCourses(
            Pageable pageable,
            boolean isAdmin) {

        Pageable safePageable = normalizePageable(pageable);

        Page<Course> courses = isAdmin
                ? courseRepository.findAll(safePageable)
                : courseRepository.findByPublishedTrue(safePageable);

        Page<CourseResponse> courseResponses =
                courses.map(this::mapToResponse);

        return PageResponse.from(courseResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> getMyCourses(
            Long instructorId,
            Pageable pageable) {

        Pageable safePageable = normalizePageable(pageable);

        Page<CourseResponse> courseResponses = courseRepository
                .findByInstructor_Id(instructorId, safePageable)
                .map(this::mapToResponse);

        return PageResponse.from(courseResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse getCourseById(
            Long courseId,
            Long currentUserId,
            boolean isInstructor,
            boolean isAdmin) {

        Course course = findCourse(courseId);

        boolean isOwner = course.getInstructor()
                .getId()
                .equals(currentUserId);

        boolean canView = Boolean.TRUE.equals(course.getPublished())
                || isAdmin
                || (isInstructor && isOwner);

        if (!canView) {
            throw new AccessDeniedException(
                    "You are not authorized to view this course."
            );
        }

        return mapToResponse(course);
    }

    @Override
    public CourseResponse updateCourse(
            Long courseId,
            CourseUpdateRequest request,
            Long currentUserId,
            boolean isInstructor,
            boolean isAdmin) {

        Course course = findCourse(courseId);

        verifyOwnership(course, currentUserId, isInstructor, isAdmin);

        String title = request.getTitle().trim();

        boolean duplicateExists =
                courseRepository.existsByTitleIgnoreCaseAndInstructor_IdAndIdNot(
                        title,
                        course.getInstructor().getId(),
                        courseId
                );

        if (duplicateExists) {
            throw new DuplicateCourseException(
                    "You already have another course with this title."
            );
        }

        course.setTitle(title);
        course.setDescription(
                normalizeDescription(request.getDescription())
        );

        return mapToResponse(saveCourse(course));
    }

    @Override
    public CourseResponse publishCourse(
            Long courseId,
            Long currentUserId,
            boolean isInstructor,
            boolean isAdmin) {

        Course course = findCourse(courseId);

        verifyOwnership(course, currentUserId, isInstructor, isAdmin);

        if (!Boolean.TRUE.equals(course.getPublished())) {
            course.setPublished(true);
            course = courseRepository.save(course);
        }

        return mapToResponse(course);
    }

    /**
     * Saves and flushes a course immediately so database constraint
     * violations can be handled within this method.
     */
    private Course saveCourse(Course course) {

        try {
            return courseRepository.saveAndFlush(course);

        } catch (DataIntegrityViolationException exception) {

            if (isDuplicateCourseTitleConstraint(exception)) {
                throw new DuplicateCourseException(
                        "A course with this title already exists for this instructor."
                );
            }

            throw exception;
        }
    }

    /**
     * Checks the exception cause chain for the exact PostgreSQL
     * unique-index constraint.
     */
    private boolean isDuplicateCourseTitleConstraint(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof ConstraintViolationException violation) {

                return DUPLICATE_TITLE_CONSTRAINT.equals(
                        violation.getConstraintName()
                );
            }

            current = current.getCause();
        }

        return false;
    }

    private Course findCourse(Long courseId) {

        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course not found with ID: " + courseId
                ));
    }

    private void verifyOwnership(
            Course course,
            Long currentUserId,
            boolean isInstructor,
            boolean isAdmin) {

        boolean isOwner = course.getInstructor()
                .getId()
                .equals(currentUserId);

        if (!isAdmin && !(isInstructor && isOwner)) {
            throw new AccessDeniedException(
                    "You are not authorized to manage this course."
            );
        }
    }

    /**
     * Validates pagination and sorting parameters.
     *
     * Maximum page size is 50.
     * Supported sort properties are createdAt, updatedAt and title.
     */
    private Pageable normalizePageable(Pageable pageable) {

        if (pageable.getPageNumber() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number cannot be negative."
            );
        }

        if (pageable.getPageSize() < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be greater than zero."
            );
        }

        pageable.getSort().forEach(order -> {

            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty()
                );
            }
        });

        int page = pageable.getPageNumber();
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        return PageRequest.of(page, size, pageable.getSort());
    }

    private String normalizeDescription(String description) {

        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }

    private CourseResponse mapToResponse(Course course) {

        User instructor = course.getInstructor();

        String instructorName =
                instructor.getFirstName() + " " + instructor.getLastName();

        return CourseResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .instructorId(instructor.getId())
                .instructorName(instructorName.trim())
                .published(course.getPublished())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}

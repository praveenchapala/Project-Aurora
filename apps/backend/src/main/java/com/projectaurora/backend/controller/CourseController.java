
package com.projectaurora.backend.controller;

import com.projectaurora.backend.dto.common.ApiResponse;
import com.projectaurora.backend.dto.common.PageResponse;
import com.projectaurora.backend.dto.course.CourseCreateRequest;
import com.projectaurora.backend.dto.course.CourseResponse;
import com.projectaurora.backend.dto.course.CourseUpdateRequest;
import com.projectaurora.backend.entity.User;
import com.projectaurora.backend.security.UserPrincipal;
import com.projectaurora.backend.service.course.CourseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    // 1. Create a course (INSTRUCTOR only)
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CourseCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireInstructor(principal);

        User instructor = principal.getUser();

        CourseResponse course =
                courseService.createCourse(request, instructor);

        ApiResponse<CourseResponse> response =
                ApiResponse.<CourseResponse>builder()
                        .success(true)
                        .message("Course created successfully.")
                        .data(course)
                        .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Browse courses
    // ADMIN sees all courses; other authenticated users see published courses.
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> getCourses(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {

        boolean isAdmin = hasRole(principal, "ROLE_ADMIN");

        PageResponse<CourseResponse> courses =
                courseService.getCourses(pageable, isAdmin);

        ApiResponse<PageResponse<CourseResponse>> response =
                ApiResponse.<PageResponse<CourseResponse>>builder()
                        .success(true)
                        .message("Courses retrieved successfully.")
                        .data(courses)
                        .build();

        return ResponseEntity.ok(response);
    }

    // 3. Get instructor's own courses (INSTRUCTOR only)
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> getMyCourses(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {

        requireInstructor(principal);

        Long instructorId = principal.getUser().getId();

        PageResponse<CourseResponse> courses =
                courseService.getMyCourses(instructorId, pageable);

        ApiResponse<PageResponse<CourseResponse>> response =
                ApiResponse.<PageResponse<CourseResponse>>builder()
                        .success(true)
                        .message("Your courses retrieved successfully.")
                        .data(courses)
                        .build();

        return ResponseEntity.ok(response);
    }

    // 4. Get course by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourseById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long currentUserId = principal.getUser().getId();

        boolean isInstructor = hasRole(principal, "ROLE_INSTRUCTOR");
        boolean isAdmin = hasRole(principal, "ROLE_ADMIN");

        CourseResponse course =
                courseService.getCourseById(
                        id,
                        currentUserId,
                        isInstructor,
                        isAdmin
                );

        ApiResponse<CourseResponse> response =
                ApiResponse.<CourseResponse>builder()
                        .success(true)
                        .message("Course retrieved successfully.")
                        .data(course)
                        .build();

        return ResponseEntity.ok(response);
    }

    // 5. Update course (OWNER INSTRUCTOR or ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CourseUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireInstructorOrAdmin(principal);

        Long currentUserId = principal.getUser().getId();

        boolean isInstructor = hasRole(principal, "ROLE_INSTRUCTOR");
        boolean isAdmin = hasRole(principal, "ROLE_ADMIN");

        CourseResponse course =
                courseService.updateCourse(
                        id,
                        request,
                        currentUserId,
                        isInstructor,
                        isAdmin
                );

        ApiResponse<CourseResponse> response =
                ApiResponse.<CourseResponse>builder()
                        .success(true)
                        .message("Course updated successfully.")
                        .data(course)
                        .build();

        return ResponseEntity.ok(response);
    }

    // 6. Publish course (OWNER INSTRUCTOR or ADMIN)
    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<CourseResponse>> publishCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireInstructorOrAdmin(principal);

        Long currentUserId = principal.getUser().getId();

        boolean isInstructor = hasRole(principal, "ROLE_INSTRUCTOR");
        boolean isAdmin = hasRole(principal, "ROLE_ADMIN");

        CourseResponse course =
                courseService.publishCourse(
                        id,
                        currentUserId,
                        isInstructor,
                        isAdmin
                );

        ApiResponse<CourseResponse> response =
                ApiResponse.<CourseResponse>builder()
                        .success(true)
                        .message("Course published successfully.")
                        .data(course)
                        .build();

        return ResponseEntity.ok(response);
    }

    // Authorization helpers

    private boolean hasRole(
            UserPrincipal principal,
            String role) {

        return principal.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private void requireInstructor(UserPrincipal principal) {

        if (!hasRole(principal, "ROLE_INSTRUCTOR")) {
            throw new AccessDeniedException(
                    "Only instructors can perform this operation."
            );
        }
    }

    private void requireInstructorOrAdmin(UserPrincipal principal) {

        if (!hasRole(principal, "ROLE_INSTRUCTOR")
                && !hasRole(principal, "ROLE_ADMIN")) {

            throw new AccessDeniedException(
                    "Only instructors and administrators can perform this operation."
            );
        }
    }
}

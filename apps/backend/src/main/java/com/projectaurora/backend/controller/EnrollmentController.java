
package com.projectaurora.backend.controller;

import java.util.List;

import com.projectaurora.backend.dto.common.ApiResponse;
import com.projectaurora.backend.dto.enrollment.EnrollmentResponse;
import com.projectaurora.backend.security.UserPrincipal;
import com.projectaurora.backend.service.enrollment.EnrollmentService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    // 1. Enroll authenticated student in a course
    @PostMapping("/courses/{courseId}/enroll")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireStudent(principal);

        EnrollmentResponse enrollment =
                enrollmentService.enroll(
                        courseId,
                        principal.getUser()
                );

        ApiResponse<EnrollmentResponse> response =
                ApiResponse.<EnrollmentResponse>builder()
                        .success(true)
                        .message("Enrolled in course successfully.")
                        .data(enrollment)
                        .build();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 2. Get authenticated student's enrollments
    @GetMapping("/enrollments/me")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getMyEnrollments(
            @AuthenticationPrincipal UserPrincipal principal) {

        requireStudent(principal);

        Long studentId = principal.getUser().getId();

        List<EnrollmentResponse> enrollments =
                enrollmentService.getMyEnrollments(studentId);

        ApiResponse<List<EnrollmentResponse>> response =
                ApiResponse.<List<EnrollmentResponse>>builder()
                        .success(true)
                        .message("Your enrollments retrieved successfully.")
                        .data(enrollments)
                        .build();

        return ResponseEntity.ok(response);
    }

    // 3. Check whether authenticated student is enrolled
    @GetMapping("/courses/{courseId}/enrollment")
    public ResponseEntity<ApiResponse<Boolean>> checkEnrollment(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireStudent(principal);

        Long studentId = principal.getUser().getId();

        boolean enrolled =
                enrollmentService.isEnrolled(
                        courseId,
                        studentId
                );

        ApiResponse<Boolean> response =
                ApiResponse.<Boolean>builder()
                        .success(true)
                        .message("Enrollment status retrieved successfully.")
                        .data(enrolled)
                        .build();

        return ResponseEntity.ok(response);
    }

    // Authorization helper
    private void requireStudent(UserPrincipal principal) {

        boolean isStudent = principal.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_STUDENT"::equals);

        if (!isStudent) {
            throw new AccessDeniedException(
                    "Only students can perform this operation."
            );
        }
    }
}

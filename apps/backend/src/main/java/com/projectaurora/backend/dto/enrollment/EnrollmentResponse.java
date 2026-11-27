package com.projectaurora.backend.dto.enrollment;

import java.time.LocalDateTime;

import com.projectaurora.backend.entity.EnrollmentStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnrollmentResponse {

    private Long id;

    private Long courseId;

    private String courseTitle;

    private String instructorName;

    private LocalDateTime enrolledAt;

    private EnrollmentStatus status;
}

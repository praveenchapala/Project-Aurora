package com.projectaurora.backend.dto.course;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CourseResponse {

    private Long id;
    private String title;
    private String description;
    private Long instructorId;
    private String instructorName;
    private Boolean published;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
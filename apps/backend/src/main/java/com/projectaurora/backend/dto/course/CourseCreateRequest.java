package com.projectaurora.backend.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourseCreateRequest {

    @NotBlank(message = "Course title is required")
    @Size(max = 200, message = "Course title cannot exceed 200 characters")
    private String title;

    @Size(max = 10000, message = "Course description cannot exceed 10000 characters")
    private String description;
}

package com.projectaurora.backend.service.course;

import org.springframework.data.domain.Pageable;

import com.projectaurora.backend.dto.common.PageResponse;
import com.projectaurora.backend.dto.course.CourseCreateRequest;
import com.projectaurora.backend.dto.course.CourseResponse;
import com.projectaurora.backend.dto.course.CourseUpdateRequest;
import com.projectaurora.backend.entity.User;

public interface CourseService {

    CourseResponse createCourse(
            CourseCreateRequest request,
            User instructor
    );

    PageResponse<CourseResponse> getCourses(
            Pageable pageable,
            boolean isAdmin
    );

    PageResponse<CourseResponse> getMyCourses(
            Long instructorId,
            Pageable pageable
    );

    CourseResponse getCourseById(
            Long courseId,
            Long currentUserId,
            boolean isInstructor,
            boolean isAdmin
    );

    CourseResponse updateCourse(
            Long courseId,
            CourseUpdateRequest request,
            Long currentUserId,
            boolean isInstructor,
            boolean isAdmin
    );

    CourseResponse publishCourse(
            Long courseId,
            Long currentUserId,
            boolean isInstructor,
            boolean isAdmin
    );
}

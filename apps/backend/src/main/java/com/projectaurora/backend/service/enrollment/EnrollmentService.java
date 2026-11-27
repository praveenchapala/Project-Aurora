package com.projectaurora.backend.service.enrollment;

import java.util.List;

import com.projectaurora.backend.dto.enrollment.EnrollmentResponse;
import com.projectaurora.backend.entity.User;

public interface EnrollmentService {

    EnrollmentResponse enroll(Long courseId, User student);

    List<EnrollmentResponse> getMyEnrollments(Long studentId);

    boolean isEnrolled(Long courseId, Long studentId);
}

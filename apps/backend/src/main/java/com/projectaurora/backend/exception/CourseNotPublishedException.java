
package com.projectaurora.backend.exception;

public class CourseNotPublishedException extends IllegalArgumentException {

    public CourseNotPublishedException(String message) {
        super(message);
    }
}

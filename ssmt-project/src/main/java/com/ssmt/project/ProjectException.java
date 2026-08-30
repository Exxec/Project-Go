package com.ssmt.project;

import com.ssmt.core.exception.SsmtException;

/**
 * Reports localization-project read, validation, and build failures.
 */
public class ProjectException extends SsmtException {
    private static final long serialVersionUID = 1L;

    public ProjectException(String message) {
        super(message);
    }

    public ProjectException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.ssmt.scanner;

import com.ssmt.core.exception.SsmtException;
import java.util.List;

/**
 * Indicates that the scanned dependency graph contains one or more cycles.
 */
public final class CyclicDependencyException extends SsmtException {

    private static final long serialVersionUID = 1L;
    private final String[] unresolvedModIds;

    public CyclicDependencyException(List<String> unresolvedModIds) {
        super("Circular mod dependency detected among: " + String.join(", ", unresolvedModIds));
        this.unresolvedModIds = unresolvedModIds.toArray(String[]::new);
    }

    public List<String> unresolvedModIds() {
        return List.of(unresolvedModIds.clone());
    }
}

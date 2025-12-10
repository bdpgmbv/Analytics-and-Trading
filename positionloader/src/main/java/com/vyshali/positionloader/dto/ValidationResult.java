package com.vyshali.positionloader.dto;

/*
 * 12/10/2025 - 12:50 PM
 * @author Vyshali Prabananth Lal
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of position validation with errors and warnings.
 */
public class ValidationResult {

    private final Integer accountId;
    private final List<Issue> errors = new ArrayList<>();
    private final List<Issue> warnings = new ArrayList<>();

    public ValidationResult(Integer accountId) {
        this.accountId = accountId;
    }

    public void addError(String code, String message) {
        errors.add(new Issue(code, message));
    }

    public void addWarning(String code, String message) {
        warnings.add(new Issue(code, message));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public int errorCount() {
        return errors.size();
    }

    public int warningCount() {
        return warnings.size();
    }

    public List<Issue> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<Issue> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public String errorSummary() {
        return errors.stream().map(Issue::code).collect(Collectors.joining(", "));
    }

    public boolean hasZeroPriceErrors() {
        return errors.stream().anyMatch(e -> e.code().contains("ZERO_PRICE"));
    }

    public Integer getAccountId() {
        return accountId;
    }

    /**
     * Single validation issue.
     */
    public record Issue(String code, String message) {
    }
}
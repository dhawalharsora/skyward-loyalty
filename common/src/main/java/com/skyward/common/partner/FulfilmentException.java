package com.skyward.common.partner;

/**
 * Signals that a partner fulfilment did not succeed.
 *
 * <p>The {@code indeterminate} flag is what makes safe compensation possible:
 * <ul>
 *   <li><b>definite</b> ({@code indeterminate=false}) — the partner explicitly rejected; the reward was
 *       NOT issued, so it is safe to compensate (release the hold).</li>
 *   <li><b>indeterminate</b> ({@code indeterminate=true}) — timeout, open circuit, or connection error;
 *       the outcome is unknown, so we must NOT compensate blindly (the partner may have fulfilled). The
 *       saga is left in-flight for recovery to resolve via an idempotent re-fulfil.</li>
 * </ul>
 */
public class FulfilmentException extends RuntimeException {

    private final boolean indeterminate;

    public FulfilmentException(String message, Throwable cause, boolean indeterminate) {
        super(message, cause);
        this.indeterminate = indeterminate;
    }

    /** Conservative default: unknown causes are treated as indeterminate. */
    public FulfilmentException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public boolean indeterminate() {
        return indeterminate;
    }
}

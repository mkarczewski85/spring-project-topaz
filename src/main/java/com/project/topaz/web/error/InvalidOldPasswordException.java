package com.project.topaz.web.error;

public final class InvalidOldPasswordException extends RuntimeException {

    private static final long serialVersionUID = 5861310537366287163L;

    public InvalidOldPasswordException() {
        super();
    }

    public InvalidOldPasswordException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidOldPasswordException(String message) {
        super(message);
    }

    public InvalidOldPasswordException(Throwable cause) {
        super(cause);
    }

}

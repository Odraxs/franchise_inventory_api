package co.com.bancolombia.model.exception;

import lombok.Getter;

@Getter
public class TechnicalException extends DomainException {

    public enum ErrorCode {
        DATABASE_UNAVAILABLE("The database is unavailable, try again later");

        private final String message;

        ErrorCode(String message) {
            this.message = message;
        }
    }

    private final ErrorCode errorCode;

    public TechnicalException(ErrorCode errorCode) {
        super(errorCode.name(), errorCode.message);
        this.errorCode = errorCode;
    }

    public TechnicalException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), errorCode.message, cause);
        this.errorCode = errorCode;
    }
}

package co.com.bancolombia.model.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    public enum ErrorCode {
        INVALID_REQUEST("The request is malformed or has a field of the wrong type"),
        INVALID_FRANCHISE_NAME("The franchise name must not be blank and must have at most 100 characters"),
        DUPLICATE_FRANCHISE_NAME("A franchise with this name already exists"),
        FRANCHISE_NOT_FOUND("The franchise does not exist"),
        INVALID_BRANCH_NAME("The branch name must not be blank and must have at most 100 characters"),
        DUPLICATE_BRANCH_NAME("A branch with this name already exists in the franchise");

        private final String message;

        ErrorCode(String message) {
            this.message = message;
        }
    }

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message, cause);
        this.errorCode = errorCode;
    }
}

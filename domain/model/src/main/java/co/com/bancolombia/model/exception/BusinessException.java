package co.com.bancolombia.model.exception;

import lombok.Getter;

@Getter
public class BusinessException extends DomainException {

    public enum ErrorCode {
        INVALID_REQUEST("The request is malformed or has a field of the wrong type"),
        INVALID_FRANCHISE_NAME("The franchise name must not be blank and must have at most 100 characters"),
        DUPLICATE_FRANCHISE_NAME("A franchise with this name already exists"),
        FRANCHISE_NOT_FOUND("The franchise does not exist"),
        INVALID_BRANCH_NAME("The branch name must not be blank and must have at most 100 characters"),
        DUPLICATE_BRANCH_NAME("A branch with this name already exists in the franchise"),
        BRANCH_NOT_FOUND("The branch does not exist in the franchise"),
        INVALID_PRODUCT_NAME("The product name must not be blank and must have at most 100 characters"),
        INVALID_PRODUCT_UPDATE("Only the name changes here; update the stock through the stock endpoint"),
        INVALID_STOCK("The stock must be an integer from 0 to 2147483647"),
        DUPLICATE_PRODUCT_NAME("A product with this name already exists in the branch"),
        PRODUCT_NOT_FOUND("The product does not exist in the branch");

        private final String message;

        ErrorCode(String message) {
            this.message = message;
        }
    }

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.name(), errorCode.message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), errorCode.message, cause);
        this.errorCode = errorCode;
    }
}

package com.devfuel.common.api;

public record ErrorResponse(boolean success, ErrorBody error) {

    public static ErrorResponse of(ErrorBody error) {
        return new ErrorResponse(false, error);
    }
}

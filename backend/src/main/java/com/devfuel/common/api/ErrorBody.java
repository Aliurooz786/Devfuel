package com.devfuel.common.api;

import com.devfuel.common.exception.ErrorCode;

public record ErrorBody(ErrorCode code, String message) {
}

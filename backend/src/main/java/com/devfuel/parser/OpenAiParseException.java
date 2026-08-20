package com.devfuel.parser;

public class OpenAiParseException extends RuntimeException {

    public OpenAiParseException(String message) {
        super(message);
    }

    public OpenAiParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

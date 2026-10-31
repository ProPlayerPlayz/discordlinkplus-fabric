package com.mcdiscordbot.fabric;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Do not print exception messages, request URLs, response bodies, or webhook tokens. */
final class ChatFailure {
    private ChatFailure() {}
    static String describe(Throwable error) {
        int depth = 0;
        while ((error instanceof ExecutionException || error instanceof CompletionException) && error.getCause() != null && depth++ < 16) error = error.getCause();
        if (error instanceof ErrorResponseException response)
            return "Discord " + response.getErrorCode() + " (" + response.getErrorResponse().name() + ")";
        if (error instanceof TimeoutException) return "Discord request timed out";
        return error.getClass().getSimpleName();
    }
}

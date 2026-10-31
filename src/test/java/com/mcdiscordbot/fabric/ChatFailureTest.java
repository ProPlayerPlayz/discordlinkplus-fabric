package com.mcdiscordbot.fabric;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class ChatFailureTest {
    @Test void unwrapsNestedAsyncErrorsWithoutPrintingPrivateExceptionMessages() {
        var error = new ExecutionException(new CompletionException(new IllegalArgumentException("private-token-and-message")));
        assertEquals("IllegalArgumentException", ChatFailure.describe(error));
        assertEquals("Discord request timed out", ChatFailure.describe(new ExecutionException(new TimeoutException("private-url"))));
    }

    @Test void keepsDiscordErrorCodeButOmitsRequestUrlAndResponseContent() {
        var raw = new okhttp3.Response.Builder()
            .request(new okhttp3.Request.Builder().url("https://discord.com/api/webhooks/123/private-webhook-token").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(400).message("Bad Request")
            .body(okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"), "{\"code\":50035,\"message\":\"private-message-text\"}"))
            .build();
        try (var response = new Response(raw, 0L, Set.of())) {
            var error = ErrorResponseException.create(ErrorResponse.INVALID_FORM_BODY, response);
            assertEquals("Discord 50035 (INVALID_FORM_BODY)", ChatFailure.describe(new ExecutionException(error)));
        }
    }
}

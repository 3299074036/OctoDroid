package com.gh4a;

import com.meisolsson.githubsdk.model.ClientErrorResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ApiRequestExceptionTest {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static ApiRequestException ex(int code, String jsonBody) {
        ResponseBody body = jsonBody == null
                ? ResponseBody.create(JSON, "")
                : ResponseBody.create(JSON, jsonBody);
        return new ApiRequestException(Response.error(code, body));
    }

    @Test
    public void getStatus_returnsHttpCode() {
        assertEquals(404, ex(404, "{\"message\":\"Not Found\"}").getStatus());
        assertEquals(422, ex(422, "{\"message\":\"x\"}").getStatus());
    }

    @Test
    public void getMessage_messageOnly() {
        assertEquals("Not Found (404)", ex(404, "{\"message\":\"Not Found\"}").getMessage());
    }

    @Test
    public void getMessage_messageAndFieldErrors() {
        String json = "{\"message\":\"Validation Failed\",\"errors\":["
                + "{\"resource\":\"Issue\",\"field\":\"title\",\"code\":\"missing_field\"},"
                + "{\"resource\":\"Issue\",\"field\":\"body\",\"code\":\"invalid\"}]}";
        assertEquals("Validation Failed (422) "
                        + "[Value for required field title is missing, "
                        + "Value for field body is invalid]",
                ex(422, json).getMessage());
    }

    @Test
    public void getMessage_allFieldErrorReasons() {
        String json = "{\"message\":\"Validation Failed\",\"errors\":["
                + "{\"resource\":\"Repo\",\"field\":\"name\",\"code\":\"missing\"},"
                + "{\"resource\":\"Repo\",\"field\":\"name\",\"code\":\"already_exists\"},"
                + "{\"resource\":\"Repo\",\"field\":\"desc\",\"code\":\"too_large\"},"
                + "{\"resource\":\"Repo\",\"field\":\"x\",\"code\":\"custom\",\"message\":\"custom msg\"}]}";
        // "missing" is the JSON code for MissingResource.
        assertEquals("Validation Failed (422) "
                        + "[Resource Repo does not exist, "
                        + "A resource of type 'Repo' with the same value in field name already exists, "
                        + "The field desc was too large, custom msg]",
                ex(422, json).getMessage());
    }

    @Test
    public void getMessage_unmappedReasonsSkipped() {
        // NotAvailable / Unprocessable have no format branch and are skipped.
        String json = "{\"message\":\"Validation Failed\",\"errors\":["
                + "{\"resource\":\"R\",\"field\":\"f\",\"code\":\"not_available\"},"
                + "{\"resource\":\"R\",\"field\":\"f\",\"code\":\"unprocessable\"}]}";
        assertEquals("Validation Failed (422)", ex(422, json).getMessage());
    }

    @Test
    public void getMessage_nullReasonSkipped() {
        // T-MOD-01: a field error without "code" makes Moshi's generated
        // adapter throw NullPointerException ("Null reason") inside the
        // constructor, which only catches IOException | JsonDataException —
        // the NPE escapes instead of degrading to a null response.
        String json = "{\"message\":\"Validation Failed\",\"errors\":["
                + "{\"resource\":\"R\",\"field\":\"f\"}]}";
        try {
            ex(422, json);
            org.junit.Assert.fail("expected NullPointerException from constructor");
        } catch (NullPointerException expected) {
            assertEquals("Null reason", expected.getMessage());
        }
    }

    @Test
    public void getMessage_customReasonNullMessage_skipped() {
        String json = "{\"message\":\"Validation Failed\",\"errors\":["
                + "{\"resource\":\"R\",\"field\":\"f\",\"code\":\"custom\"}]}";
        assertEquals("Validation Failed (422)", ex(422, json).getMessage());
    }

    @Test
    public void getMessage_emptyErrorsList_messageOnly() {
        assertEquals("Bad (400)", ex(400, "{\"message\":\"Bad\",\"errors\":[]}").getMessage());
    }

    @Test
    public void getMessage_noMessageNoErrors_httpStatus() {
        assertEquals("HTTP status 500", ex(500, "{}").getMessage());
    }

    @Test
    public void getMessage_emptyMessage_httpStatus() {
        assertEquals("HTTP status 403", ex(403, "{\"message\":\"\"}").getMessage());
    }

    @Test
    public void getMessage_nullMessageWithErrors_httpStatus() {
        // Message absent, errors present but message empty: falls to HTTP status.
        String json = "{\"errors\":[{\"resource\":\"R\",\"field\":\"f\",\"code\":\"invalid\"}]}";
        assertEquals("HTTP status 422", ex(422, json).getMessage());
    }

    @Test
    public void getResponse_parsedResponseAvailable() {
        ApiRequestException e = ex(404, "{\"message\":\"Not Found\"}");
        ClientErrorResponse response = e.getResponse();
        assertEquals("Not Found", response.message());
    }

    @Test
    public void constructor_invalidJsonBody_responseNullMessageNull() {
        ApiRequestException e = ex(500, "this is not json {{{");
        assertNull(e.getResponse());
        // getMessage() with null mResponse delegates to super.getMessage(),
        // which is null for a no-message RuntimeException.
        assertNull(e.getMessage());
        assertEquals(500, e.getStatus());
    }

    @Test
    public void constructor_wrongTypedMessage_responseNull() {
        // #225: a wrong-typed error body raises JsonDataException (a
        // RuntimeException, not IOException) inside the constructor; it must
        // be swallowed so the exception still carries the status code.
        // NOTE: Moshi coerces a JSON number into a String, so message-as-a
        // number parses fine; an array value is what actually breaks it.
        ApiRequestException e = ex(400, "{\"message\":[\"x\"]}");
        assertNull(e.getResponse());
        assertEquals(400, e.getStatus());
        assertNull(e.getMessage());
    }

    @Test
    public void constructor_wrongTypedErrors_responseNull() {
        ApiRequestException e = ex(422, "{\"message\":\"x\",\"errors\":\"nope\"}");
        assertNull(e.getResponse());
        assertEquals(422, e.getStatus());
    }

    @Test
    public void constructor_emptyBody_responseNull() {
        ApiRequestException e = ex(502, "");
        assertNull(e.getResponse());
        assertEquals(502, e.getStatus());
    }

    @Test
    public void getMessage_unknownCode_parseFails_messageNull() {
        // An unknown "code" does NOT reach formatFieldError's null-reason
        // guard (line 81): the SDK's Moshi adapter wraps the Reason
        // adapter in .nonNull(), so an unknown enum value throws
        // JsonDataException, the constructor degrades to a null response,
        // and getMessage() falls back to RuntimeException's null message.
        // (A missing "code" instead throws NPE "Null reason" — T-MOD-01.)
        String json = "{\"message\":\"Validation Failed\",\"errors\":["
                + "{\"resource\":\"R\",\"field\":\"f\",\"code\":\"some_future_code\"}]}";
        ApiRequestException e = ex(422, json);
        assertNull(e.getResponse());
        assertNull(e.getMessage());
    }

    @Test
    public void getMessage_isStableAcrossCalls() {
        ApiRequestException e = ex(404, "{\"message\":\"Not Found\"}");
        assertEquals(e.getMessage(), e.getMessage());
        assertTrue(e instanceof RuntimeException);
    }
}

package com.booking.engine.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/v1/test");
    }

    @Test
    void handlesEntityNotFound() {
        EntityNotFoundException exception = new EntityNotFoundException("Barber", UUID.fromString("11111111-1111-1111-1111-111111111111"));

        ErrorResponse response = handler.handleEntityNotFound(exception, request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.message()).contains("Barber not found");
        assertThat(response.path()).isEqualTo("/api/v1/test");
    }

    @Test
    void handlesValidationErrorsAndKeepsFirstDuplicateFieldMessage() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "payload");
        bindingResult.addError(new FieldError("payload", "name", "Name is required"));
        bindingResult.addError(new FieldError("payload", "name", "Replacement should be ignored"));
        bindingResult.addError(new FieldError("payload", "email", "Email is invalid"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleValidatedMethod", Object.class);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ValidationErrorResponse response = handler.handleValidationErrors(exception, request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Validation Error");
        assertThat(response.message()).isEqualTo("Request validation failed");
        assertThat(response.fieldErrors()).containsEntry("name", "Name is required");
        assertThat(response.fieldErrors()).containsEntry("email", "Email is invalid");
        assertThat(response.path()).isEqualTo("/api/v1/test");
    }

    @Test
    void handlesConstraintViolations() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = Mockito.mock(ConstraintViolation.class);
        Path propertyPath = Mockito.mock(Path.class);
        Mockito.when(propertyPath.toString()).thenReturn("bookingDate");
        Mockito.when(violation.getPropertyPath()).thenReturn(propertyPath);
        Mockito.when(violation.getMessage()).thenReturn("must be today or in the future");

        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ErrorResponse response = handler.handleConstraintViolation(exception, request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Validation Error");
        assertThat(response.message()).contains("bookingDate: must be today or in the future");
    }

    @Test
    void handlesBookingAndPaymentValidationErrors() {
        ErrorResponse bookingResponse = handler.handleBookingValidation(
                new BookingValidationException("Slot is not available"), request).getBody();
        ErrorResponse paymentResponse = handler.handlePaymentProcessing(
                new PaymentProcessingException("Payment capture failed"), request).getBody();

        assertThat(bookingResponse).isNotNull();
        assertThat(bookingResponse.error()).isEqualTo("Bad Request");
        assertThat(bookingResponse.message()).isEqualTo("Slot is not available");

        assertThat(paymentResponse).isNotNull();
        assertThat(paymentResponse.error()).isEqualTo("Bad Request");
        assertThat(paymentResponse.message()).isEqualTo("Payment capture failed");
    }

    @Test
    void handlesIllegalStateAndIllegalArgument() {
        ErrorResponse stateResponse = handler.handleIllegalState(new IllegalStateException("State issue"), request).getBody();
        ErrorResponse argumentResponse = handler.handleIllegalArgument(new IllegalArgumentException("Argument issue"), request).getBody();

        assertThat(stateResponse).isNotNull();
        assertThat(stateResponse.message()).isEqualTo("State issue");
        assertThat(argumentResponse).isNotNull();
        assertThat(argumentResponse.message()).isEqualTo("Argument issue");
    }

    @Test
    void handlesDataIntegrityViolationWithFriendlyConflictMessages() {
        DataIntegrityViolationException displayOrderConflict = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException("duplicate key value violates unique constraint on display_order"));

        DataIntegrityViolationException genericConflict = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException("some other conflict"));

        ErrorResponse displayOrderResponse = handler.handleDataIntegrityViolation(displayOrderConflict, request).getBody();
        ErrorResponse genericResponse = handler.handleDataIntegrityViolation(genericConflict, request).getBody();

        assertThat(displayOrderResponse).isNotNull();
        assertThat(displayOrderResponse.status()).isEqualTo(409);
        assertThat(displayOrderResponse.error()).isEqualTo("Conflict");
        assertThat(displayOrderResponse.message()).contains("Display order is already in use");

        assertThat(genericResponse).isNotNull();
        assertThat(genericResponse.message()).isEqualTo("Request conflicts with existing data constraints");
    }

    @Test
    void handlesAuthenticationAccessDeniedAndUnexpectedExceptions() {
        ErrorResponse authResponse = handler.handleAuthentication(
                new BadCredentialsException("Invalid credentials"), request).getBody();
        ErrorResponse accessDeniedResponse = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden area"), request).getBody();
        ErrorResponse genericResponse = handler.handleGenericException(
                new Exception("Unexpected boom"), request).getBody();

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.status()).isEqualTo(401);
        assertThat(authResponse.error()).isEqualTo("Unauthorized");
        assertThat(authResponse.message()).isEqualTo("Invalid credentials");

        assertThat(accessDeniedResponse).isNotNull();
        assertThat(accessDeniedResponse.status()).isEqualTo(403);
        assertThat(accessDeniedResponse.error()).isEqualTo("Forbidden");
        assertThat(accessDeniedResponse.message()).isEqualTo("Forbidden area");

        assertThat(genericResponse).isNotNull();
        assertThat(genericResponse.status()).isEqualTo(500);
        assertThat(genericResponse.error()).isEqualTo("Internal Server Error");
        assertThat(genericResponse.message()).isEqualTo("An unexpected error occurred");
    }
    private void sampleValidatedMethod(Object ignored) {
    }
}

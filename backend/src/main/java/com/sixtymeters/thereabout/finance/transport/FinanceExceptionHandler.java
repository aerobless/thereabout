package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.generated.model.GenFinanceError;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = FinanceController.class)
public class FinanceExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<GenFinanceError> application(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .body(
            new GenFinanceError().code(error.getStatusCode().toString()).detail(error.getReason()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<GenFinanceError> invalid(Exception error) {
    String detail =
        error instanceof MethodArgumentNotValidException validation
            ? validation.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Invalid input")
            : "Invalid request values";
    return ResponseEntity.badRequest()
        .body(new GenFinanceError().code("INVALID_INPUT").detail(detail));
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<GenFinanceError> stale(Exception error) {
    return ResponseEntity.status(409)
        .body(
            new GenFinanceError()
                .code("VERSION_CONFLICT")
                .detail("Record changed; reload before saving"));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<GenFinanceError> unavailable(Exception error) {
    return ResponseEntity.status(503)
        .body(
            new GenFinanceError()
                .code("UNAVAILABLE")
                .detail("Operation unavailable; no changes were committed"));
  }
}

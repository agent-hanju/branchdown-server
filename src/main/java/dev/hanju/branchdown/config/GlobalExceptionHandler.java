package dev.hanju.branchdown.config;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NoSuchElementException.class)
  public ProblemDetail handleNoSuchElementException(NoSuchElementException e) {
    log.warn("NoSuchElementException: {}", e.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    problem.setTitle("Resource Not Found");
    return problem;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgumentException(IllegalArgumentException e) {
    log.warn("IllegalArgumentException: {}", e.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setTitle("Invalid Argument");
    return problem;
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalStateException(IllegalStateException e) {
    log.error("IllegalStateException: {}", e.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    problem.setTitle("Invalid State");
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleException(Exception e) {
    log.error("Unexpected exception", e);
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
    problem.setTitle("Internal Server Error");
    return problem;
  }
}

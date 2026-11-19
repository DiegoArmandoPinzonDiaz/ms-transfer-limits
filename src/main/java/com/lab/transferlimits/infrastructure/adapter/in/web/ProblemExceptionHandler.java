package com.lab.transferlimits.infrastructure.adapter.in.web;

import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.domain.InvalidRequestException;
import com.lab.transferlimits.domain.RejectionReason;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos los errores salen como RFC 9457 (application/problem+json). El detail solo lleva mensajes
 * controlados: nunca stack traces, SQL ni nombres de clases (el detalle tecnico va solo al log).
 */
@RestControllerAdvice
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemExceptionHandler.class);
  private static final String TYPE_BASE = "https://transferlimits.lab.example/problems/";

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalidRequest(InvalidRequestException ex) {
    return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Peticion invalida", ex.getMessage());
  }

  @ExceptionHandler(LimitsNotFoundException.class)
  ProblemDetail limitsNotFound(LimitsNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND, "limits-not-found", "Limites no encontrados", ex.getMessage());
  }

  @ExceptionHandler(TransferRejectedException.class)
  ProblemDetail transferRejected(TransferRejectedException ex) {
    Rejected rejected = ex.rejected();
    ProblemDetail problem =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "transfer-rejected",
            "Transferencia rechazada",
            ex.getMessage());
    problem.setProperty("reasonCode", rejected.reason().name());
    problem.setProperty("requestedAmount", ex.requestedAmount().value());
    if (rejected.reason() == RejectionReason.PER_OPERATION_LIMIT_EXCEEDED) {
      problem.setProperty("perOperationLimit", rejected.perOperationLimit().value());
    } else {
      problem.setProperty("availableAmount", rejected.availableToday().value());
    }
    return problem;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception ex) {
    LOG.error("Error inesperado procesando la peticion", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal-error",
        "Error interno",
        "Ocurrio un error inesperado.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField())
            .distinct()
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest()
        .body(
            problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Peticion invalida",
                "Campos obligatorios o invalidos: " + fields + "."));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.badRequest()
        .body(
            problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Peticion invalida",
                "El cuerpo de la peticion no es valido."));
  }

  private static ProblemDetail problem(
      HttpStatus status, String slug, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_BASE + slug));
    problem.setTitle(title);
    return withTraceId(problem);
  }

  /** Agrega traceId (extension RFC 9457) para que soporte rastree el error sin exponer detalles. */
  private static ProblemDetail withTraceId(ProblemDetail problem) {
    SpanContext context = Span.current().getSpanContext();
    if (context.isValid()) {
      problem.setProperty("traceId", context.getTraceId());
    }
    return problem;
  }

  /** Errores estandar de Spring MVC (405, 415, ...) tambien llevan traceId. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      withTraceId(problem);
    }
    return response;
  }
}

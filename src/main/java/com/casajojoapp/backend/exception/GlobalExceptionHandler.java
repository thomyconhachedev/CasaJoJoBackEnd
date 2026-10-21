package com.casajojoapp.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Convierte las excepciones en respuestas JSON {"error": "..."} con el codigo HTTP que corresponde,
 * sin exponer trazas ni detalles internos de la base de datos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, String>> reglaDeNegocio(BusinessRuleException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(ResourceNotFoundException e) {
        return error(404, e.getMessage());
    }

    @ExceptionHandler(SincronizacionException.class)
    public ResponseEntity<Map<String, String>> sincronizacion(SincronizacionException e) {
        log.warn("Falló la sincronización del menú: {}", e.getMessage());
        return error(502, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(400, detalle.isEmpty() ? "La petición no es válida." : detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return error(400, "El cuerpo de la petición no es un JSON válido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return error(400, "El parámetro '" + e.getName() + "' no tiene un formato válido.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> baseDeDatos(DataAccessException e) {
        log.error("Error de acceso a la base de datos", e);
        return error(500, "Ocurrió un error al acceder a los datos. Intenta nuevamente.");
    }

    private ResponseEntity<Map<String, String>> error(int status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("error", mensaje));
    }
}

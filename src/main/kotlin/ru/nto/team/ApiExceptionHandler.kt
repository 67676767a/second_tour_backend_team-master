package ru.nto.team

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class ApiException(val status: Int, message: String) : RuntimeException(message)

class ValidationException(val errors: Map<String, String>) : RuntimeException("Validation failed")

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(error: ApiException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(error.status).body(mapOf("error" to (error.message ?: "Error")))

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(error: ValidationException): ResponseEntity<Map<String, Map<String, String>>> =
        ResponseEntity.badRequest().body(mapOf("errors" to error.errors))
}

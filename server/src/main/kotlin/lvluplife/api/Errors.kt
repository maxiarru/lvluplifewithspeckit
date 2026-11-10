package lvluplife.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import java.util.concurrent.CancellationException

enum class ApiError(val status: HttpStatusCode, val safeMessage: String) {
    INVALID_REQUEST(HttpStatusCode.BadRequest, "La solicitud no tiene el formato requerido."),
    AUTH_REQUIRED(HttpStatusCode.Unauthorized, "Se requiere una sesión válida."),
    FORBIDDEN(HttpStatusCode.Forbidden, "Acceso denegado."),
    NOT_FOUND(HttpStatusCode.NotFound, "No se encontró la ruta solicitada."),
    METHOD_NOT_ALLOWED(HttpStatusCode.MethodNotAllowed, "Método no permitido."),
    REQUEST_TOO_LARGE(HttpStatusCode.PayloadTooLarge, "La solicitud es demasiado grande."),
    USERNAME_UNAVAILABLE(HttpStatusCode.Conflict, "Ese nombre no está disponible."),
    OPERATION_ID_REUSED(HttpStatusCode.Conflict, "La operación ya tiene otro contenido."),
    RECORD_STALE(HttpStatusCode.Conflict, "El registro cambió. Actualizá los datos."),
    RECORD_DELETED(HttpStatusCode.Conflict, "El registro fue eliminado."),
    CYCLE_CLOSED(HttpStatusCode.Conflict, "El ciclo de la operación ya está cerrado."),
    INVALID_INPUT(HttpStatusCode.UnprocessableEntity, "Revisá los datos ingresados."),
    DATE_NOT_ALLOWED(HttpStatusCode.UnprocessableEntity, "La fecha no está permitida."),
    EDIT_WINDOW_EXPIRED(HttpStatusCode.UnprocessableEntity, "Finalizó el plazo para corregir el registro."),
    DAILY_DURATION_EXCEEDED(HttpStatusCode.UnprocessableEntity, "La duración supera el saldo de esa fecha."),
    TEMPORARILY_UNAVAILABLE(HttpStatusCode.ServiceUnavailable, "No se pudo completar la solicitud. Intentá nuevamente."),
}

class ApiException(val error: ApiError) : RuntimeException(error.name)

internal suspend fun ApplicationCall.respondApiError(error: ApiError) {
    val body = apiJson.createObjectNode().apply {
        putObject("error").put("code", error.name).put("message", error.safeMessage)
    }
    respondText(apiJson.writeValueAsString(body), ContentType.Application.Json, error.status)
}

internal fun Application.installApiErrors() {
    install(StatusPages) {
        exception<Exception> { call, failure ->
            when (failure) {
                is CancellationException -> throw failure
                is ApiException -> call.respondApiError(failure.error)
                is SecurityException -> call.respondApiError(ApiError.FORBIDDEN)
                else -> call.respondApiError(ApiError.TEMPORARILY_UNAVAILABLE)
            }
            // Do not log/return exception messages, payloads, tokens or original causes.
        }
        status(HttpStatusCode.NotFound) { call, _ -> call.respondApiError(ApiError.NOT_FOUND) }
        status(HttpStatusCode.MethodNotAllowed) { call, _ -> call.respondApiError(ApiError.METHOD_NOT_ALLOWED) }
    }
}

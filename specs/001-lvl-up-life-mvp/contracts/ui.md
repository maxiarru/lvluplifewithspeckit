# Contrato de interfaz Android

Fuente: [spec](../spec.md), [API](api.md). Describe comportamiento requerido para el diseño; no componentes implementados.

| Flujo | Entradas y resultados |
|---|---|
| Acceso/alta | Google mediante Credential Manager; usuario nuevo elige nombre válido y disponible. Cancelación vuelve al acceso sin crear perfil. Una identidad existente recupera personaje único. |
| Principal | Skin actual, nivel general sin barra de XP general independiente, seis niveles/XP de atributos y acceso a carga. El progreso confirmado viene del servidor. |
| Carga | Actividad guardada, fecha admitida y horas/minutos o realización de comida. Conservar borrador ante validación o fallo conocido; sin nueva carga offline. Doble toque comparte el envío; una nueva carga intencional es otro envío. |
| Actividades propias | Nombre, categoría libre, principal y secundario opcional compatible; sin CON. Guardar y reutilizar, sin administración adicional fuera de spec. |
| Historial | Día, semana lunes–domingo o mes; conservar registros de ciclos anteriores. Editar/eliminar solo con las restricciones autoritativas; el indicador local de ventana es orientativo y no concede permiso. |
| Clases | Catálogo de las 26 clases y tres etapas visible desde comienzo; bloqueo según nivel general 1/15/35. Recursos visuales coherentes con tabla de spec. |
| Reinicio | Explicar que reinicia progresión y conserva cuenta, nombre, actividades propias e historial. Confirmación explícita o cancelación sin cambios. Confirmación pendiente no implica otro reinicio al reintentar. |

## Estados y sesión

- `Loading`: consulta en curso; `Confirmed`: respuesta vigente con revisión y fecha; `Empty`: consulta exitosa sin resultados. Error nunca equivale a vacío.
- `Stale`: datos anteriores de la misma cuenta con aviso de desactualización y opción de reintento. No presentar como actual un saldo de otra fecha o una proyección de un recibo antiguo.
- `Pending`: envío con resultado desconocido; no anunciar éxito/fallo definitivo ni XP confirmada. Mostrar seguimiento y permitir recuperar/reintentar el mismo envío, con sesión de su cuenta original.
- `Rejected`: motivo seguro de rechazo; borrador conservado donde corresponda. Corregir inicia un comando nuevo sujeto a validaciones, sin modificar identidad del anterior.
- Al cerrar app se recuperan pendientes desde Room; al reconectar con sesión válida se consulta su resultado. Al cambiar cuenta se limpian UI, navegación y datos en memoria anteriores. Ningún dato ni pendiente de A es visible para B.
- Sesión vencida suspende recuperación; Firebase puede refrescar sesión. Si no se obtiene una válida, solicitar autenticación. Cancelación no reasigna pendientes. Acceso denegado no se reintenta automáticamente.
- Conflicto de registro muestra su estado actual o eliminación. Una intención renovada usa la versión actual solo si aún corresponde, sin sobrescribir silenciosamente otro cambio.

## Calidad del flujo

Contraste ≥4,5:1 y significado esencial no comunicado solo por color. Textos de error comprensibles sin detalles de infraestructura. Diseñar la carga frecuente para ≤3 interacciones y mediana objetivo ≤15 s según definición de interacción del PRD; comprobar el flujo concreto antes de cerrarlo, incluyendo horas/minutos. Esto no acredita métricas medidas ni agrega encuestas a la app. Pruebas Compose cubren accesibilidad, sesión, vacío/error/desactualizado y recuperación; las reglas de XP se prueban en dominio, no se replican en UI.

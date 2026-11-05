# Contrato HTTP v1

Diseño para cliente Android y servicio autoritativo; no es una API implementada. Referencias: [modelo](../data-model.md), [spec](../spec.md).

## Convenciones y confianza

Base `/v1`; JSON UTF-8; HTTPS TLS ≥1.2. Todas las rutas requieren `Authorization: Bearer <Firebase ID token>`. UID derivado del token verificado, nunca de parámetros de propietario. No hay rutas públicas para datos de cuentas ni Firestore directo. Instantes RFC3339 UTC, fechas ISO de calendario de negocio UTC−3, versiones enteras monotónicas. Campos desconocidos se rechazan; en creación/edición cualquier `xp` del cliente se rechaza, no se ignora. Los límites de transporte deben proteger el servicio sin introducir límites funcionales de registros; cuerpos desproporcionados reciben 413 sin efectos.

## Comandos

`POST /v1/commands` recibe un sobre:

```json
{
  "operationId": "UUID",
  "type": "CREATE_RECORD",
  "cycleId": "ciclo-vigente-observado",
  "payload": {
    "activityId": "strength",
    "activityDate": "2026-11-05",
    "hours": 0,
    "minutes": 30
  }
}
```

`operationId` se persiste antes del primer envío, es estable entre reintentos y dobles toques, y pertenece al UID original. Un nuevo envío intencional crea UUID distinto incluso si su contenido coincide. Hash canónico incluye tipo, ciclo y payload normalizado, excluye token. No se cambia contenido de un UUID al corregir datos; un comando corregido es nuevo. Campos por variante:

| `type` | Campos | Resultado confirmado |
|---|---|---|
| `COMPLETE_PROFILE` | `payload.username`; sin ciclo | Reserva nombre único case-insensitive y crea cuenta/personaje/ciclo solo si faltan; identidad ya completada conserva su único perfil. |
| `CREATE_CUSTOM_ACTIVITY` | `payload.name`, `category`, `primary`, `secondary?`; sin ciclo | ID de definición propia reutilizable. |
| `CREATE_RECORD` | `cycleId`; `activityId`, `activityDate`, `hours` y `minutes` para duración; comida sin duración | ID, versión 1, creación autoritativa y revisión resultante. |
| `EDIT_RECORD` | `cycleId`; `recordId`, `expectedVersion`, valores resultantes permitidos | Mismo ID/creación, versión incrementada y revisión resultante. |
| `DELETE_RECORD` | `cycleId`; `recordId`, `expectedVersion` | ID eliminado, versión nueva y revisión resultante; no resurrección en reintento. |
| `RESET_PROGRESSION` | `cycleId`; `payload.confirmed: true` | Ciclos cerrado y nuevo, proyección inicial y revisión resultante. No existe reset implícito sin confirmación UI. |

Para edición de comida solo `activityDate`; no conversión de modalidad. Para duración los valores resultantes incluyen actividad, fecha y componentes de duración. El servidor resuelve definición y atributos desde catálogo o actividad del UID. No acepta nivel, clase, consumos, propietario ni instantes de creación del cliente. Duración conserva componentes enteros hasta validarlos conforme a RF-47.

## Resultados de comandos

Resultado definitivo, HTTP 200 para confirmado y 409/422 para rechazo funcional persistido:

```json
{
  "operationId": "UUID",
  "outcome": "CONFIRMED",
  "result": {"recordId": "id", "version": 1, "cycleId": "id", "revision": 12},
  "validatedAt": "2026-11-05T15:00:00Z"
}
```

`REJECTED` contiene `error.code`, mensaje seguro y detalles propios necesarios. Todo resultado definitivo se reconoce desde el recibo por UUID y mismo hash antes de reevaluar tiempo/versión/ciclo. Si el mismo UUID contiene otro comando, 409 `OPERATION_ID_REUSED`, sin alterar recibo original. Un recibo no se usa como proyección actual: consultar `/progress` después de resolverlo. Puede incluir una proyección etiquetada con su revisión de aplicación, nunca presentarla como vigente si es antigua.

Errores de transporte, 401, 403 y fallos transitorios no son recibos funcionales automáticos. 401 requiere sesión válida; 403 no se reintenta automáticamente. Si se perdió la respuesta o el servidor no puede garantizar que no hubo commit, el cliente mantiene resultado desconocido. Ningún timeout implica por sí solo que no se guardó.

## Consultas

| Ruta | Respuesta y semántica |
|---|---|
| `GET /v1/me` | Estado de alta y perfil propio; `profileRequired: true` si identidad válida aún no completó alta. Sin crear perfil por autenticar o cancelar el ingreso. |
| `GET /v1/catalog` | 11 actividades, tabla completa de 26 clases: Novato solo con Etapa I y las otras 25 con etapas I/II/III, con 76 IDs de recursos únicos. No hay entradas para Novato II o III. Catálogo solo de lectura. Entrega incremental: US2 implementa datos de actividades/clases/etapas; US5 completa las referencias visuales. Solo la entrega final exige los 76 IDs resolubles. |
| `GET /v1/custom-activities` | Definiciones propias, orden estable por creación e ID; respuesta completa o error, no resultado parcial presentado como completo. |
| `GET /v1/progress` | Replay vigente del ciclo, seis atributos, nivel general/contador, clase, etapa y desbloqueos; `revision`, `computedAt`, `businessDate`. Incluye penalizaciones de fechas cerradas sin exigir nuevos registros. |
| `GET /v1/history?period=day\|week\|month&date=YYYY-MM-DD` | Período que contiene fecha: día calendario, lunes–domingo o mes calendario, UTC−3. Registros vigentes de todos los ciclos ordenados por fecha, creación e ID. Devuelve `periodStart`, `periodEndExclusive`, `revision`, `items`, `complete: true`. El servidor pagina internamente bajo revisión estable; no muestra un período incompleto como completo. `items: []` solo por consulta exitosa realmente vacía. |
| `GET /v1/operations/{operationId}` | Recibo propio definitivo si existe. Si no, HTTP 200 con `outcome: UNKNOWN`; no prueba de rechazo ni de ausencia de envío en vuelo. Se puede reenviar el mismo sobre. No revela recibos de otros UID. |

No hay endpoint de historial público, asignación manual de XP, cambio de reglas o borrado de cuenta. Otro dispositivo con sesión válida de la misma cuenta consulta los datos ya guardados mediante las rutas de perfil, actividades, progreso e historial, sin necesitar UUID de operaciones. La recuperación de una operación corresponde a la instalación que conserva su sobre pendiente; no hay descubrimiento, importación ni sincronización de pendientes entre dispositivos. Consultar un recibo siempre requiere autenticación y autorización por UID; conocer su UUID no concede acceso.

## Errores y conflictos

| HTTP / código | Efecto y acción del cliente |
|---|---|
| 400 `INVALID_REQUEST` | Esquema inválido/campo prohibido; no aplicar cambio. Conservar entrada y explicar. |
| 401 `AUTH_REQUIRED` | Suspender recuperación, refrescar sesión si Firebase puede y, si no, pedir autenticación; mantener UID original del pendiente. |
| 403 `FORBIDDEN` | No revelar existencia ni datos ajenos; sin reintento automático. |
| 409 `USERNAME_UNAVAILABLE` | Nombre no asignado; solicitar otro mediante un nuevo comando. |
| 409 `RECORD_STALE` / `RECORD_DELETED` | Sin cambios; devolver estado vigente propio o informar eliminación. Nuevo intento intencional requiere nueva versión, UUID y validaciones. |
| 409 `CYCLE_CLOSED` | Sin traslado al ciclo nuevo; devolver ciclo actual propio. |
| 422 `INVALID_INPUT` / `DATE_NOT_ALLOWED` / `EDIT_WINDOW_EXPIRED` / `DAILY_DURATION_EXCEEDED` | Rechazo funcional con motivo seguro, sin cambio de registro ni progresión. Corregir y enviar nuevo comando cuando corresponda. |
| 503 `TEMPORARILY_UNAVAILABLE` | Dependencia o contención impide completar; no afirmar rechazo si pudo haber commit. Conservar pendiente y consultar recibo antes de reenviar. |

Una respuesta inválida o ilegible de un envío se trata como resultado desconocido, no como confirmación. En consultas fallidas mostrar error/reintento, conservando datos previos de la cuenta activa solo con indicación de desactualizados. No exponer excepciones internas, tokens, datos ajenos o rutas privadas en errores.

## Reglas transaccionales observables

- Dos creaciones distintas con 1 XP de margen comparten ese margen; no exceden el tope.
- Dos duraciones que compiten por una hora restante no pueden consumir dos horas.
- Dos correcciones desde una versión no pueden ambas confirmarse.
- Registro y reinicio compiten por revisión de cuenta: registro primero queda en ciclo anterior; reinicio primero rechaza creación dirigida al ciclo cerrado.
- Cada intento aún no confirmado revalida límites temporales en servidor. Recibo confirmado se devuelve aun después de vencer la ventana o cambiar ciclo.
- Lectura y escritura de datos ajenos se rechazan aunque los identificadores sean conocidos. La suite prueba tanto API como denegación Firestore directa.

Trazabilidad: RF-01–57, especialmente AC-89–90, 103–104, 110–131. Los escenarios numéricos de progresión permanecen en spec y fixtures del dominio, sin duplicar fórmulas aquí.

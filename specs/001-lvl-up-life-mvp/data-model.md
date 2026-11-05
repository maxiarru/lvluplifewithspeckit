# Modelo de datos: Lvl Up Life

Fuente: [spec.md](spec.md). Diseño lógico y persistente; no son migraciones implementadas. Tiempos como instantes UTC y fechas de actividad ISO `YYYY-MM-DD` calculadas con offset fijo `-03:00`; no zona horaria del dispositivo.

## Documentos Firestore

Todas las rutas privadas se resuelven a partir del UID verificado. El cliente no accede directamente a Firestore.

| Entidad / ruta | Campos principales | Relaciones e invariantes |
|---|---|---|
| Cuenta `users/{uid}` | `username`, `usernameKey`, `createdAt`, `currentCycleId`, `revision`, `schemaVersion` | UID Firebase único; nombre `[A-Za-z0-9_]{1,15}`, clave ASCII lowercase; revisión crece con toda mutación y publicación de progreso. |
| Reserva `usernames/{usernameKey}` | `uid`, `createdAt` | Solo servidor; no enumerable por cliente. Alta reserva nombre y crea cuenta/ciclo/proyección en una transacción. Cuenta ya creada no genera otro personaje. |
| Ciclo `users/{uid}/cycles/{cycleId}` | `startedAt`, `closedAt?`, `rulesVersion` | Uno actual; comienza por alta o reinicio. Los anteriores se conservan y no admiten correcciones. |
| Actividad personalizada `users/{uid}/activities/{activityId}` | `name`, `category`, `primary`, `secondary?`, `createdAt` | Nombre trim 1–50, categoría no vacía, principal y secundario pertenecientes a STR/DEX/INT/WIT/MEN y diferentes entre sí; secundario opcional, sin CON. Solo crear/reutilizar, sin CRUD adicional fuera de spec. |
| Registro `users/{uid}/records/{recordId}` | `cycleId`, `activityId`, `activityDefinition`, `activityDate`, `durationMinutes?`, `createdAt`, `updatedAt`, `version`, `deletedAt?` | Definición resuelta por servidor conserva modalidad y atributos aplicados. Identidad y creación no cambian al editar. Borrado lógico excluido del historial de registros vigentes y del replay; tombstone permite informar conflicto de eliminación. |
| Proyección `users/{uid}/projections/current` | `cycleId`, `revision`, `asOfDate`, `computedAt`, `generalLevel`, `generalRemainder`, `attributes`, `classId`, `stage` | Derivada, jamás editable por cliente; seis atributos con nivel, XP y estado de inactividad. Guardada atómicamente con cambio. Una consulta reconstruye desde registros, no desde XP previa. |
| Recibo `users/{uid}/operations/{operationId}` | `commandType`, `requestHash`, `cycleId?`, `outcome`, `result`, `validatedAt`, `appliedRevision?` | Inmutable; confirmado o rechazado funcional definitivo; UID implícito en ruta. Idempotencia por cuenta y UUID. Sin TTL en MVP. No almacena tokens ni detalles de otras cuentas. |

El catálogo inicial de 11 actividades y la tabla de 26 clases: Novato con una etapa y las otras 25 con tres (76 imágenes) son datos versionados del dominio y recursos del producto, no editables por el usuario. No se inventan tasas ni asignaciones nuevas. `rulesVersion` identifica las reglas aprobadas; cambiar fórmulas exige actualizar especificaciones, no una configuración remota arbitraria.

## Validaciones y progresión

- RF-10/47: horas y minutos enteros no negativos, minutos 0–59 y total >0. Validar componentes antes de convertir para impedir normalizar minutos 60 o fracciones silenciosamente. Suma de duración vigente por fecha/ciclo ≤1.440, también concurrente. Comidas no consumen minutos.
- RF-13–15/54: hoy, ayer y anteayer al validar en servidor; corrección solo ciclo actual y tiempo transcurrido <300 s. Editar excluye valor previo y valida origen/destino; comida solo fecha, duración solo otra duración. No cambia `createdAt`.
- RF-17–30: dominio aplica fórmulas y redondeos enteros de spec, topes independientes y niveles 1–50; nivel 50 deja XP cero. General usa contador de subidas, nunca XP general enviada por cliente.
- RF-31–40/44: replay por fecha, creación e ID ordinal; actividades antes de penalizar cada fecha cerrada. Solo XP efectiva positiva interrumpe inactividad; seis fechas de gracia y luego hasta 5 XP sin perder nivel. No penalizar fecha abierta ni fechas anteriores al inicio del ciclo. Clase Novato prioritaria y solo Etapa I; equilibrio sin redondeo y etapas por nivel general para las otras 25 clases.
- Un registro cargado retroactivamente pertenece al ciclo del envío validado; tras reinicio no hereda consumos, sueño, comidas o inactividad previos. Historial anterior permanece consultable.

Los saldos diarios, comidas puntuables y sueño pendiente se calculan durante el replay; no se persiste un documento mutable por cada registro de XP ni una segunda contabilidad susceptible de divergir. Se pueden devolver contribuciones del resultado de cálculo, sin almacenarlas como fuente de verdad.

## Protocolo de escritura y lectura consistente

1. Verificar token y esquema; derivar UID. Consultar recibo por UUID: resultado existente se devuelve si el hash coincide; colisión con otro comando es conflicto.
2. Leer raíz con revisión R, consultar documentos necesarios y volver a leer raíz. Si la revisión difiere, repetir. Toda mutación, incluidos actividades propias, alta y reinicio, debe respetar la revisión raíz; no hay escrituras por fuera del servicio.
3. Construir estado candidato y replay con el dominio puro usando tiempo explícito. No ejecutar efectos secundarios en ese cálculo.
4. Transacción final lee recibo y raíz y todas las precondiciones necesarias antes de escribir. Si R cambió, reintentar lectura y replay externos. Si ya hay recibo, devolverlo. Obtener hora de validación del intento; si cambió fecha de negocio respecto del cálculo, reconstruir antes de aceptar. Verificar versión del registro y ciclo vigente.
5. Fijar creación autoritativa del candidato en el intento aceptado; si cambia el orden usado en el replay externo, recalcular en memoria con el snapshot estable antes de escribir. Commit atómico del registro o ciclo afectado, proyección con R+1, raíz R+1 y recibo. Un rechazo funcional definitivo puede guardar solo recibo y revisión, sin modificar registros ni progreso. Un fallo transitorio no fija resultado funcional. La carrera por nombre incluye reserva en la misma transacción de alta.
6. Lectura de progreso usa igual snapshot/replay y barrera final; historial reúne todas las páginas de un período bajo una revisión estable antes de devolver un resultado completo. Cambios concurrentes obligan a recomenzar, no a mezclar versiones. Límites técnicos producen error recuperable, nunca un vacío falso.

Recálculo no vuelve a escribir todos los registros y el tamaño transaccional es constante respecto al historial. Las lecturas sí crecen: no se promete tiempo constante. Todos los accesos de prueba, importaciones y fixtures deben mantener este protocolo; herramientas que escriben directamente solo en emuladores aislados antes de iniciar pruebas.

## Índices previstos

- Registros por `cycleId`, `activityDate`, `createdAt` y ID ordinal para replay; filtrar tombstones en cálculo.
- Historial por `activityDate`, `createdAt` e ID, incluyendo ciclos anteriores y excluyendo eliminados; declarar índices compuestos en `firebase/firestore.indexes.json` al implementar.
- UUID y nombre normalizado usan lookup por ID. No crear índice público de usuarios.
- Validar índices con el emulador y con un entorno de integración real antes de desplegar; emulación no demuestra todas las cuotas ni comportamiento de producción.

## Estados locales y transiciones

Room `PendingOperation`: clave `(uid, operationId)`, tipo, sobre canónico y hash, ciclo original, fecha local de creación para diagnóstico no autoritativo, estado y último error seguro. Nunca tokens. Persistir antes de enviar, de modo que cierre entre persistencia y envío se recupera con el mismo UUID. `Draft` conserva entradas inválidas/no enviadas por UID sin enviarlas offline.

`Prepared → Sending → Confirmed | Rejected | Unknown`; `Unknown → Resolving → Confirmed | Rejected | Unknown`. Ausencia de recibo permite reenviar el mismo sobre, no declarar rechazo. Estados intermedios sobreviven a muerte del proceso como recuperables. Una sesión inválida suspende recuperación sin cambiar propietario. La recuperación se ejecuta solo desde la instalación que conserva el pendiente, con sesión válida de la cuenta original; no se sincronizan ni importan pendientes o borradores entre dispositivos. Otro dispositivo autenticado consulta los datos ya guardados de esa cuenta sin necesitar el UUID del pendiente. Las validaciones de revisión, versión y ciclo siguen aplicándose entre dispositivos.

Tras resultado definitivo se puede remover el sobre local, conservando resultado de interfaz; el recibo remoto permite reconocer reintentos antiguos. Si el usuario corrige una entrada rechazada, crea un nuevo comando intencional con nuevo UUID; nunca cambia el contenido del UUID anterior. Los pendientes de A no se exponen a B. Desactivar backup/transferencia de la base de pendientes y borrar estados de UI/memoria en logout; no borrar pendientes de A para simular resolución.

## Reinicio

Confirmación explícita produce un comando con UUID y ciclo esperado. Commit cierra ciclo actual, crea nuevo ciclo, apunta raíz al nuevo y publica proyección inicial junto al recibo. El resto de colecciones se conserva. Dos reinicios dirigidos al mismo ciclo no crean dos nuevos ciclos: primero confirma; segundo con UUID distinto encuentra ciclo cerrado y se rechaza; mismo UUID devuelve recibo. Registro concurrente se ordena por commit de la revisión raíz conforme a RF-50.

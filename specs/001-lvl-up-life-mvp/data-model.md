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

### Definición resuelta y modelo de registro de T029

ActivityDefinition conserva ID, nombre, categoría, modalidad, atributos y
experienceRule (DURATION/SLEEP/MEAL). Esta última etiqueta identifica la regla
canónica, no almacena tasas ni XP elegida por el cliente. Solo los IDs básicos
sleep y meal pueden usar sus excepciones; MEN personalizado usa DURATION.
La definición guardada en ActivityRecord es una instantánea inmutable del
resolver del servidor. activityId deriva de ella para impedir divergencias.
Los IDs de actividad/registro son segmentos privados de documento válidos,
sin rutas, controles, patrón reservado __.*__ ni más de1500 bytes UTF-8.

El nombre de una definición ya resuelta está recortado y tiene1–50 puntos de
código Unicode. La categoría solo debe contener texto no vacío: no se agrega
un máximo ni recorte obligatorio. El catálogo básico expone una lista que
no permite mutaciones. Un registro por duración exige1–1440 minutos enteros
normalizados; comida exige duración ausente. El presupuesto compartido de la
fecha/ciclo y componentes de entrada se validan aparte en T035.

create fija versión1 y updatedAt=createdAt. correct conserva ID/ciclo/createdAt,
incrementa versión y permite cambiar entre actividades por duración; comida
solo cambia de fecha. markDeleted conserva datos y añade tombstone/version,
sin borrado físico. Un tombstone no admite nuevas correcciones ni otro borrado.
restore valida versión positiva, modalidad y cronología del estado almacenado.
Las operaciones del modelo no autorizan ni escriben: ventana de edición,
cuenta/ciclo vigentes, recibos y transacciones se validan en sus servicios.

### Proyección derivada de T030

Progression es una instantánea inmutable: deriva UID/cycleId del ciclo abierto,
requiere revisión positiva y computedAt no anterior a su inicio, y calcula la
fecha de negocio mediante BusinessTime (UTC−3 fijo, sin reloj implícito).
Conserva exactamente seis InactivityBalance y copia defensivamente su mapa.
No expone constructor/copy públicos ni métodos para editar XP. La fábrica
derived recibe resultados confiables del motor del servidor, no campos de
comandos del cliente; initial reutiliza InitialCharacter y el inicio del ciclo.

AttributeBalance normaliza nivel1–50/XP: por debajo de50 el saldo es menor
que su umbral; en50 es0. GeneralBalance contiene nivel y subidas restantes,
sin XP general independiente: contador menor al requisito y0 al llegar a50.
InactivityBalance exige conteo de fechas no negativo, sin máximo nuevo.
ProgressionThresholds centraliza los umbrales usados tanto por estas invariantes
como por las futuras transiciones de T032, evitando fórmulas duplicadas.
El nivel/contador general debe corresponder a la suma de subidas de atributos;
en50 se admite el excedente descartado. classId es no vacío/sin controles y
stage pertenece a1–3, con únicamente1 para novato.

Estas validaciones no ejecutan XP, subidas, inactividad ni clasificación.
La asignación completa de clase/etapa se implementa en T034 y la reconstrucción
en T036; codecs, publicación transaccional y endpoints se conectan después.

### Clasificación y catálogo de T034

ClassCatalog centraliza las 26 clases con ID estable, nombre, atributos exactos
para clases puras/pares, cantidad equilibrada y etapas. Novato usa initialOnly,
sin condición por cantidad, y solo etapa1. Las otras25 tienen etapas1/2/3,
con mínimos generales1/15/35. Las colecciones y sus condiciones son inmutables.
Los IDs son slugs ASCII en español (por ejemplo maestro-de-armas y
heroe-integral); no se usan para inferir nombres de archivos de imágenes.

ClassRules exige los seis niveles1–50, prioriza todos en1 como Novato y compara
nivel*5 >= máximo*4 para seleccionar los equilibrados sin redondeo. Una clase
pura o par depende del conjunto de atributos; para3–6 solo importa la cantidad.
La apariencia refleja la etapa disponible en el nivel general actual1–50,
incluso después de una corrección que lo reduzca. Nombres desconocidos se
rechazan. ClassAppearance expone el ID canónico para la futura proyección;
la persistencia, consultas y vinculación de imágenes se implementan después.

### Reconstrucción pura de T036

ReplayEngine.rebuild recibe ciclo abierto, registros con definiciones resueltas e
instante autoritativo. Parte siempre de InitialCharacter y filtra otros ciclos y
tombstones; no toma como entrada una proyección anterior. Exige versión de reglas
soportada, identidades únicas por snapshot y cronología coherente de registros
vigentes. Ordena por fecha de actividad, creación e ID ordinal, sin usar updatedAt
para decidir prioridades de aporte.

Cada fecha reinicia presupuesto de XP, minutos de sueño pendientes, comidas y
duración. Cada aporte pasa por ExperienceRules y LevelRules; al terminar sus
actividades se aplica InactivityRules por atributo, incluidos días sin registros.
Solo las fechas cerradas pueden descontar y ninguna anterior al inicio del ciclo
cuenta para inactividad. Una carga retroactiva del nuevo ciclo puede aportar XP
antes de esa fecha sin heredar contadores del ciclo anterior. No se reaplica la
ventana de admisión de hoy/ayer/anteayer al historial ya guardado.

La salida contiene fecha de negocio, seis saldos con inactividad, saldo general,
clase/etapa, IDs procesados y XP efectiva por registro (incluidos aportes cero).
durationByDate es disperso: contiene solo fechas con minutos consumidos. Todas
las colecciones de salida, incluidos los mapas por registro, son inmutables y
no comparten almacenamiento mutable con las entradas. La reconstrucción también
rechaza sumas de duración superiores a 1440 en una fecha del ciclo actual.
El cálculo no ejecuta IO ni confirma revisiones; su publicación atómica y uso
en comandos/consultas se conectan en las tareas de servidor siguientes.

### Validación de registros de T035

RecordValidation recibe el instante autoritativo explícito; acepta únicamente la
fecha de negocio UTC−3 actual y sus dos anteriores. La diferencia de fechas se
calcula sin desplazar la fecha solicitada, incluso para valores extremos.
Esta ventana admite registros nuevos/correcciones; no limita el replay histórico.

Horas y minutos BigDecimal se validan antes de convertir: enteros no negativos,
minutos 0–59 y total 1–1440. Representaciones como 1.00 son enteras; fracciones
no se truncan. La suma usa solo duración vigente de la fecha/ciclo solicitados,
excluye tombstones y permite excluir el ID original al reemplazarlo. Comidas
no consumen minutos y sueño comparte el presupuesto con las demás duraciones.
El margen se resta y se rechaza al agotarlo, sin acumular sumas que desborden.
La función no modifica registros ni consulta el reloj. T038 debe integrar estos
chequeos con la revisión del snapshot y revalidación temporal antes del commit:
este cálculo puro no acredita por sí solo la exclusión concurrente en Firestore.

### Cálculo de aportes de T031

ExperienceRules contiene las fórmulas canónicas y un mapa diario inmutable:
STR/DEX/INT/WIT48, MEN30, CON28. La duración ordinaria usa bloques enteros
de5 minutos por registro; para dos atributos asigna floor(total/3) al secundario
y el resto al principal, antes de aplicar márgenes independientes, sin redistribuir.
Comidas: delta del total de7 XP por cada una de las primeras4; las demás aportan0.
Sueño:3 XP por bloque completo de60 minutos, con sobrante0–59 ligado a fecha/ciclo.
La saturación diaria o nivel50 produce aporte0 y consume los bloques completos;
no conserva XP pendiente ni traslada sobrantes a otra fecha o ciclo.

El motor proporciona presupuestos diarios ya derivados del ciclo/fecha y los
atributos en nivel50; un presupuesto negativo o superior al tope se rechaza como
contexto inválido. Los aportes devueltos no modifican entradas y sus mapas no se
pueden editar. Un aporte0 es un resultado válido, no una razón para descartar el
registro; T039 persiste los registros válidos independientemente de su aporte.
La elegibilidad de fecha, duración conjunta, concurrencia y almacenamiento no
se deciden dentro de estas funciones puras.

### Transiciones de nivel de T032

LevelRules aplica aportes confiables de XP y consume únicamente los umbrales
necesarios, reutilizando ProgressionThresholds. Puede normalizar un agregado
que cruce varias transiciones; los sobrantes se conservan mientras el nivel
resultante sea menor que50 y se descartan al llegar a50, manteniendo XP0.
Reporta la diferencia real entre niveles inicial/final como levelsGained.
Su operación sobre los seis atributos mantiene los no afectados y devuelve
un mapa inmutable más la suma de subidas reales, no la cantidad de XP.

El nivel general consume esas subidas por sus bandas canónicas y conserva
el contador restante hasta el máximo50, donde queda0. No agrega una reserva
de XP general. Entradas negativas se rechazan incluso al máximo. Las sumas
temporales usan Long y los bucles quedan limitados por los niveles, evitando
desbordamientos con agregados grandes. No se mutan los balances de entrada.
Topes diarios, modalidad y elegibilidad temporal se resuelven antes de aplicar
estos aportes; los agregados de las pruebas no habilitan XP manual ni registros
que superen los topes. Una corrección se reconstruye con replay, no con subidas
negativas aplicadas sobre el estado vigente.

### Inactividad por fecha de T033

InactivityRules recibe el balance después de aplicar XP y el aporte efectivo
total de esa fecha antes de penalizar. Un aporte positivo reinicia el contador,
incluso en la fecha abierta, sin volver a sumar XP. Un aporte0 en la fecha
actual no cuenta un día ni aplica descuento. Al cerrar una fecha inactiva se
incrementa el contador; las primeras6 no descuentan y desde la séptima se
restan hasta5 XP, conservando siempre el nivel y evitando saldo negativo.
Nivel50 conserva0 XP; la fecha que llegó a50 puede haber recibido XP positiva
antes de terminar con saldo0 y sigue siendo activa.

Los datos de entrada y salida son inmutables; XP efectiva negativa o fecha
futura son contexto inválido. El contador usa incremento comprobado sin
desbordar. La selección de fechas del ciclo, su orden y su procesamiento una
sola vez pertenecen al replay de T036: no se debe aplicar de nuevo el cierre
histórico sobre una proyección previamente penalizada.

### Codificación de reservas de nombre

`usernameKey` sigue siendo el nombre ASCII en minúsculas. Si coincide con
`__.*__`, el ID físico de su reserva lleva el prefijo `~`; los demás IDs usan
la clave directamente. Firestore reserva ese patrón de IDs, pero RF-04 permite
esos nombres. `~` no pertenece al alfabeto de usuario, por lo que la codificación
no introduce colisiones ni modifica nombres visibles. Las reservas guardan la
clave original y solo el servidor resuelve su ruta. Véanse los
[límites de IDs de Firestore](https://firebase.google.com/docs/firestore/quotas).

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

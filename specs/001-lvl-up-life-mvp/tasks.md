# Tasks: Lvl Up Life MVP

**Input**: [plan](plan.md), [spec](spec.md), [research](research.md), [modelo](data-model.md), [API](contracts/api.md), [UI](contracts/ui.md), [quickstart](quickstart.md) y constitución v1.0.0.
**Organización**: seis historias; las prioridades ordenan entrega, no reducen el MVP.
**Pruebas**: obligatorias por constitución IV, VI y VII y por el spec. Cada tarea de prueba crítica debe ejecutarse y fallar por comportamiento ausente antes de implementar su regla; registrar rojo/verde en `specs/001-lvl-up-life-mvp/validation.md`. No basta con escribir pruebas antes y ejecutarlas después.
**Formato**: IDs únicos y estables; el orden de ejecución es el listado por fase, no el orden numérico (T085–T087 se incorporan en Foundational); [P] indica trabajo en archivos distintos que comparte los prerrequisitos indicados. Rutas relativas a la raíz; paquetes Kotlin de dominio y servidor bajo `lvluplife`. No implementar funcionalidades excluidas, desplegar ni crear recursos remotos.


## Phase 1: Setup

Objetivo: crear los tres módulos y un arnés ejecutable. Completar antes de Phase 2.

- [X] T001 Crear `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` y wrapper `gradlew.bat` según versiones de plan/research; fijar dependencias, lockfiles y KSP compatible, comprobar KGP efectivo y compilación mínima sin cambiar Kotlin 2.4.20 silenciosamente. Finalizar con ` .\gradlew.bat --version` (Gradle/JVM) y ` .\gradlew.bat help --info` (configuración mínima); añadir tarea reportBuildVersions en `build.gradle.kts` que muestre AGP/KGP/Compose/KSP efectivos y ejecutarla. Registrar versiones, ausencia de rangos dinámicos y lockfiles; compilación de fuentes se acredita en la tarea siguiente.

- [X] T002 Configurar `domain/build.gradle.kts`, `server/build.gradle.kts` y `app/build.gradle.kts`: dominio JVM sin Android/Firebase, servidor Ktor JVM 17, Android Compose minSdk 29; habilitar tests y source set integrationTest del servidor. Finalizar con `.\gradlew.bat :domain:compileKotlin :server:compileKotlin :app:assembleDebug :server:integrationTestClasses` y `.\gradlew.bat :server:tasks --all`; salida exitosa y tarea integrationTest disponible, informes en validation.md.

- [X] T003 [P] Configurar `firebase/firebase.json`, `firebase/firestore.rules` deny-all, `firebase/firestore.indexes.json` y versión fija de CLI en `package.json`; emuladores Auth 9099 y Firestore 8080, proyecto demo-lvluplife. Finalizar con arranque Auth/Firestore comprobado mediante `firebase emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore "node firebase/verify-emulators.mjs"`; crear ese verificador para Auth disponible y lectura/escritura Firestore desde SDK cliente autenticado/anonimizado ambas denegadas (no Admin). Guardar salida y puertos, sin tokens.

- [X] T004 [P] Crear `app/src/main/AndroidManifest.xml`, `app/src/debug/AndroidManifest.xml` y configuración `app/src/debug/res/xml/network_security_config.xml`: HTTP/emuladores solo debug, release HTTPS TLS ≥1.2; excluir base local de backup y transferencia en `app/src/main/res/xml/data_extraction_rules.xml` y `backup_rules.xml`. Finalizar con assembleDebug/assembleRelease y prueba `app/src/test/kotlin/lvluplife/VariantSecurityTest.kt` sobre manifests/recursos fusionados: debug permite HTTP local, release lo deniega y no configura emuladores; XML legacy y API31+ excluyen base/sidecars de pendientes en cloud-backup y device-transfer. No afirmar TLS del servicio desplegado con esta inspección.

- [X] T005 Crear `server/src/integrationTest/kotlin/lvluplife/fixtures/TestEnvironment.kt` y `domain/src/test/kotlin/lvluplife/fixtures/ScenarioFixtures.kt` con cuentas sintéticas, reloj y fallos controlables; ninguna ruta de fixtures ni reloj manipulable en producción. Finalizar ejecutando `domain/src/test/kotlin/lvluplife/fixtures/ClockFixtureTest.kt` y `server/src/integrationTest/kotlin/lvluplife/fixtures/FaultFixtureTest.kt`: avance de reloj reproducible y fallos antes/después del punto de commit simulado. Inspeccionar JAR release/source sets: fixtures solo test/integrationTest y ninguna ruta/configuración pública activa controles de prueba.


## Phase 2: Foundational

Objetivo: infraestructura común verificable, sin fórmulas de progresión anticipadas. Bloquea todas las historias. Evaluar integridad antes de implementar, sin modificar sus marcas automáticamente.

- [x] T006 Revisar las 36 entradas de `specs/001-lvl-up-life-mvp/checklists/integridad.md` y registrar evidencia o bloqueos en `specs/001-lvl-up-life-mvp/validation.md`; no usar casillas pendientes como aprobación ni modificar sus marcas sin autorización. Completada anticipadamente el 2026-11-05 por solicitud explícita del usuario: 36/36 ítems aprobados con justificación en validation.md; sin acreditar código ni pruebas ejecutadas.

- [X] T007 [P] Escribir y ejecutar pruebas de autenticación, esquema estricto, ausencia de secretos, UID ajeno y errores HTTP de `contracts/api.md` en `server/src/test/kotlin/lvluplife/api/TrustBoundaryTest.kt`; comprobar fallo antes de los adaptadores.

- [X] T008 [P] Escribir y ejecutar pruebas de persistencia antes de envío, cierre de proceso, filtros por UID y sesiones A/B en `app/src/androidTest/kotlin/lvluplife/data/PendingStoreTest.kt`; incluir recibo ausente que mantiene Unknown.

- [X] T009 Crear contratos y tipos compartidos en `domain/src/main/kotlin/lvluplife/model/Identity.kt` y `server/src/main/kotlin/lvluplife/application/Ports.kt`: UID, revisión, UUID, reloj explícito, puertos persistencia; sin dependencias externas en dominio. Finalizar con `domain/src/test/kotlin/lvluplife/model/IdentityTest.kt` verde para UID/UUID/revisión y fake que implemente Ports en `server/src/test/kotlin/lvluplife/application/PortsTest.kt`; ejecutar :domain:test y :server:test. Inspeccionar :domain:dependencies --configuration runtimeClasspath: solo stdlib/plataforma, sin Android/Compose/Firebase/Ktor ni dependencia de app/server.

- [X] T010 Implementar autenticación/autorización en `server/src/main/kotlin/lvluplife/api/Authentication.kt`: verificar token Firebase, revocación/deshabilitación y derivar UID; hacer verdes los casos de identidad/aislamiento de TrustBoundaryTest.
- [X] T011 Implementar rutas /v1, esquema estricto y errores HTTP en `server/src/main/kotlin/lvluplife/api/Server.kt` y `Errors.kt`: rechazar campos desconocidos y no filtrar secretos; hacer verdes los casos restantes de TrustBoundaryTest.

- [X] T012 Implementar adaptadores y configuración ADC/emuladores en `server/src/main/kotlin/lvluplife/persistence/FirestoreAccess.kt` y `server/src/main/kotlin/lvluplife/api/ServerConfig.kt`; IAM mínimo documentado en `server/README.md`, sin claves versionadas ni tokens en logs. Finalizar con `server/src/integrationTest/kotlin/lvluplife/persistence/FirestoreAccessTest.kt` verde contra emuladores: lectura/escritura por UID, reconexión y error seguro. Documentar variables/puertos, ADC de producción, identidad y permisos IAM estrictamente necesarios con motivo; registrar configuración local y resultado. IAM remoto no probado se declara explícitamente, sin crear recursos.

- [X] T013 Implementar `app/src/main/kotlin/lvluplife/data/PendingDatabase.kt` y `PendingDao.kt`: clave «(uid, operationId)», «Nunca tokens», sobre/hash/tipo/ciclo/estado/error seguro, `Draft` por UID; «Persistir antes de enviar» y eliminar solo tras resultado definitivo. Hacer verdes pruebas Room.

- [X] T014 Implementar cliente HTTP en `app/src/main/kotlin/lvluplife/data/ApiClient.kt`; probar serialización, errores, timeout y respuesta ilegible con transporte simulado en `app/src/test/kotlin/lvluplife/data/ApiClientTest.kt`; 401 suspende y 403 no reintenta automáticamente.
- [X] T015 Escribir y ejecutar primero `app/src/test/kotlin/lvluplife/application/OperationCoordinatorTest.kt`, observar fallo y luego implementar `app/src/main/kotlin/lvluplife/application/OperationCoordinator.kt`: Prepared/Sending/Unknown/Resolving/Confirmed/Rejected, UUID estable, ausencia no es rechazo, corrección crea UUID nuevo, persistencia antes del envío y recuperación con UID original; sin nuevas cargas offline ni importación entre dispositivos. Cerrar con transiciones y fallos antes/después de envío verdes.

- [X] T016 Implementar sesión y estados UI comunes en `app/src/main/kotlin/lvluplife/application/SessionController.kt` y `app/src/main/kotlin/lvluplife/ui/LoadState.kt`: Loading/Confirmed/Stale/Empty/Error/Pending; limpiar memoria/navegación en logout, suspender pendientes con sesión inválida y reanudar solo con UID original. Antes de implementar, escribir/ejecutar `app/src/test/kotlin/lvluplife/application/SessionControllerTest.kt` y `app/src/androidTest/kotlin/lvluplife/ui/SessionNavigationTest.kt`; finalizar verdes: logout borra memoria y back stack, B no ve caché/pendientes A, sesión vencida suspende, B no envía A, volver A reanuda sin cambiar UUID y conserva estados Stale/Error/Empty correctos.


- [X] T085 Escribir y ejecutar pruebas de infraestructura común en `server/src/integrationTest/kotlin/lvluplife/persistence/OperationInfrastructureTest.kt`: recibos por UID/UUID, hash canónico, mismo UUID/contenido recupera resultado, contenido diferente se rechaza, rechazo funcional inmutable y fallo transitorio sin recibo definitivo. Cubrir commit atómico de escritura sintética/recibo/revisión, raíz inicialmente ausente, precondiciones concurrentes y fallo antes/después de commit; sin depender de Account, Cycle ni replay. Observar fallo antes de T086–T087.

- [X] T086 Implementar recibos/hash/idempotencia en `server/src/main/kotlin/lvluplife/persistence/OperationStore.kt`: recibo inmutable confirmado o rechazado funcional definitivo, clave por UID/UUID y sin TTL en MVP. Hacer verdes los casos de recibos de T085; no fijar resultado ante fallo transitorio. Esta infraestructura queda disponible antes del alta US1.

- [X] T087 Implementar la base transaccional en `server/src/main/kotlin/lvluplife/persistence/RevisionTransaction.kt`: leer recibo/raíz/precondiciones antes de escribir y confirmar atómicamente el cambio, recibo y revisión; admitir creación inicial de raíz y conflictos concurrentes sin depender de modelos de US1 ni del motor US2. Hacer verdes los casos atómicos de T085. US1 añade reserva de nombre/cuenta/ciclo y US2 extiende la base con snapshot/replay y validaciones de registros.

## Phase 3: US1 — Cuenta y personaje propios (P1)

Objetivo: acceso Google, alta única y privacidad. Prueba independiente: cuentas nuevas/existentes, Martin/martin concurrentes, cancelación, denegación y acceso desde segundo dispositivo sin cargar actividades. RF-01–05, 52, 57; AC-01–05, 67–68, 70, 72, 90, 94–95, 119–120, 130–131.

- [X] T017 [P] [US1] Escribir y ejecutar pruebas contractuales y de alta concurrente en `server/src/integrationTest/kotlin/lvluplife/account/AccountContractTest.kt`: /me, COMPLETE_PROFILE, nombre inválido y UID ajeno; no crear perfil por autenticar o cancelar.

- [X] T018 [P] [US1] Escribir y ejecutar pruebas de acceso/cancelación, cuenta existente y limpieza al cambiar sesión en `app/src/androidTest/kotlin/lvluplife/ui/AccessFlowTest.kt`; simular proveedor en tests sin acreditar Google real.

- [X] T019 [US1] Crear cuenta y ciclo inicial en `domain/src/main/kotlin/lvluplife/model/Account.kt` y `Cycle.kt`: «UID Firebase único», «nombre `[A-Za-z0-9_]{1,15}`, clave ASCII lowercase», «Uno actual; comienza por alta o reinicio»; personaje Novato y seis atributos nivel 1/0 XP; closedAt opcional y rulesVersion según modelo.

- [X] T020 [US1] Implementar alta, reserva única y proyección inicial atómicas en `server/src/main/kotlin/lvluplife/application/CompleteProfile.kt` y `server/src/main/kotlin/lvluplife/persistence/AccountStore.kt`: conservar escritura visible del nombre, un personaje por UID; reutilizar OperationStore y la base RevisionTransaction de T086–T087 para recibo inmutable, hash y revisión según modelo; añadir las precondiciones y la reserva de nombre del alta, sin duplicar infraestructura.

- [X] T021 [US1] Implementar GET /me y COMPLETE_PROFILE en `server/src/main/kotlin/lvluplife/api/AccountRoutes.kt`; cuenta existente recupera perfil, sin duplicar alta; hacer verdes contratos y carrera por nombre.

- [X] T022 [US1] Implementar Credential Manager/Google ID y Firebase Auth en `app/src/main/kotlin/lvluplife/data/GoogleSignIn.kt`; sesión cancelada no escribe, sesión vencida suspende recuperación y denegación informa sin exponer datos.

- [X] T023 [US1] Implementar acceso, nombre y navegación autenticada en `app/src/main/kotlin/lvluplife/ui/AccessScreen.kt`, `ProfileScreen.kt` y `AppNavigation.kt`; manejar nombre ocupado y reintento con nuevo comando.

- [X] T024 [US1] Ejecutar pruebas US1 y comprobar lectura/escritura API ajena y Firestore directo rechazados en `server/src/integrationTest/kotlin/lvluplife/account/IsolationTest.kt`; registrar resultados y prueba de segunda instalación con misma identidad en `specs/001-lvl-up-life-mvp/validation.md`.


## Phase 4: US2 — Registrar y reconocer progreso (P1)

Objetivo: catálogo, carga, motor canónico y recuperación de creaciones. Prueba independiente: cuenta preparada, 30 min STR=6 XP, dual=4/2, sueño 30+30=3 MEN, quinta comida 0 XP, topes y cierre/reapertura. RF-06, 10–13, 17–40, 44–45, 47, 49, 51–56.

- [X] T025 [P] [US2] Escribir y ejecutar fixtures numéricos de XP/reparto/comida/sueño/topes en `domain/src/test/kotlin/lvluplife/progression/ExperienceTest.kt`: AC-25–35, 77–81, 89, 100–101; fallo observado antes de ExperienceRules.

- [X] T026 [P] [US2] Escribir y ejecutar pruebas de niveles, sobrantes, inactividad, clase y etapas en `domain/src/test/kotlin/lvluplife/progression/ProgressionTest.kt`: AC-36–62, 82–85, 91–92, 106–107; fallo observado antes de reglas.

- [X] T027 [P] [US2] Escribir y ejecutar pruebas de replay/orden estable y validación temporal en `domain/src/test/kotlin/lvluplife/progression/ReplayTest.kt`: AC-16–18, 85, 93, 96, 98, 102, 104–105, 124; incluir fecha abierta/cerrada, UTC−3 y ciclo explícito.

- [X] T028 [P] [US2] Escribir y ejecutar contratos /catalog, /progress, CREATE_RECORD y /operations/{operationId} en `server/src/integrationTest/kotlin/lvluplife/record/CreateRecordContractTest.kt`: XP del cliente rechazada, payload/ciclo/fecha/duración, respuesta perdida, concurrencia y recibo antiguo. Para /catalog comprobar las 11 actividades, los datos de las 26 clases y sus etapas válidas (76 combinaciones), sin exigir archivos de imagen ni referencias visuales resolubles antes de US5.

- [X] T029 [US2] Crear catálogo y registros en `domain/src/main/kotlin/lvluplife/model/ActivityCatalog.kt` y `ActivityRecord.kt`: 11 actividades exactas; «Identidad y creación no cambian al editar», durationMinutes requerido para duración y ausente para comida, definition resuelta por servidor, version y deletedAt opcional; verificar fixtures de catálogo y modalidades.
- [X] T030 [US2] Crear proyección en `domain/src/main/kotlin/lvluplife/model/Progression.kt`: «Derivada, jamás editable por cliente», seis atributos con nivel/XP/inactividad, revisión/fecha/ciclo, clase/etapa y contador general; comprobar construcción e invariantes en `domain/src/test/kotlin/lvluplife/model/ProgressionModelTest.kt`.

- [X] T031 [US2] Implementar `domain/src/main/kotlin/lvluplife/progression/ExperienceRules.kt`: fórmulas exactas de spec, bloques enteros, acumulación de sueño por fecha/ciclo y topes STR/DEX/INT/WIT 48, MEN 30, CON 28; guardar registros con 0 XP. Ejecutar pruebas rojas correspondientes hasta verde.

- [X] T032 [US2] Implementar `domain/src/main/kotlin/lvluplife/progression/LevelRules.kt`: niveles 1–50, sobrantes, XP cero al 50 y contador general sin XP general; cerrar con casos de niveles de ProgressionTest ejecutados previamente en rojo y ahora verdes.
- [X] T033 [US2] Implementar `domain/src/main/kotlin/lvluplife/progression/InactivityRules.kt`: seis fechas de gracia y luego hasta 5 XP sin perder nivel ni saldo negativo, solo fechas cerradas y XP efectiva positiva interrumpe inactividad; cerrar con casos de inactividad de ProgressionTest previamente rojos y ahora verdes.

- [X] T034 [US2] Implementar `domain/src/main/kotlin/lvluplife/progression/ClassRules.kt` y `ClassCatalog.kt`: tabla exacta de 26 clases, equilibrio 80 % sin redondeo, prioridad Novato con solo Etapa I y etapas I/II/III por 1/15/35 para las otras 25 clases. Hacer verdes pruebas correspondientes; recursos completos se entregan en US5.

- [X] T035 [US2] Implementar `domain/src/main/kotlin/lvluplife/progression/RecordValidation.kt`: «horas y minutos enteros no negativos, minutos 0–59 y total >0», «Suma de duración vigente por fecha/ciclo ≤1.440, también concurrente. Comidas no consumen minutos»; solo hoy/ayer/anteayer con tiempo explícito. Hacer verdes casos de validación de ReplayTest previamente rojos.
- [X] T036 [US2] Implementar `domain/src/main/kotlin/lvluplife/progression/ReplayEngine.kt`: «replay por fecha, creación e ID ordinal», tiempo/ciclo explícitos, actividades antes de penalizar fechas cerradas; hacer verdes ReplayTest y regresión completa del dominio tras validar registros.

- [X] T037 [US2] Integrar OperationStore de T086 con los comandos de registros: asociar ciclo y resultado de progresión, recuperar recibo antes de reevaluar tiempos y reutilizar hash/idempotencia sin una segunda implementación. Cerrar con casos de recibos de CreateRecordContractTest verdes y regresión de OperationInfrastructureTest.
- [X] T038 [US2] Extender la base de T087 en `server/src/main/kotlin/lvluplife/persistence/RevisionTransaction.kt` para registros: snapshot raíz/registros/raíz, replay externo y commit corto que revisa recibo/revisión/ciclo/tiempo; toda mutación incrementa revisión, revalidar medianoche y orden createdAt final. Cerrar con concurrencia, revisión cambiada y cruce de medianoche de RecordRevisionTest verdes; los contratos HTTP originales de CreateRecordContractTest se cierran al conectar el handler en T039.

- [X] T039 [US2] Implementar creación en `server/src/main/kotlin/lvluplife/application/CreateRecord.kt` y `server/src/main/kotlin/lvluplife/api/RecordRoutes.kt`: registro/proyección/raíz/recibo atómicos, definición autoritativa, ciclo/tiempo vigentes y fallo transitorio sin rechazo definitivo; hacer verdes contratos CREATE_RECORD.
- [X] T040 [US2] Implementar consultas en `server/src/main/kotlin/lvluplife/application/ReadProgress.kt`, `server/src/main/kotlin/lvluplife/api/ProgressRoutes.kt` y `OperationRoutes.kt`, e implementar `server/src/main/kotlin/lvluplife/api/CatalogRoutes.kt`: /catalog devuelve las 11 actividades y los datos de las 26 clases con sus etapas válidas, sin exigir imágenes antes de US5; replay también sin registros nuevos, recibo propio autenticado, ausencia UNKNOWN y proyección antigua no reemplaza vigente; hacer verdes contratos /catalog (datos), /progress y /operations. Las referencias visuales resolubles se completan y validan en T064–T070.

- [X] T041 [P] [US2] Escribir y ejecutar pruebas de doble toque, pendientes tras cierre y borrador conservado en `app/src/androidTest/kotlin/lvluplife/ui/RecordFlowTest.kt`: RF-49, 51–56 y AC-112–113, 118–129; 401/403, UNKNOWN y recibo antiguo no reemplaza estado vigente.

- [X] T042 [US2] Implementar formulario/envío en `app/src/main/kotlin/lvluplife/ui/RecordScreen.kt` y `app/src/main/kotlin/lvluplife/data/RecordRepository.kt`: fecha/duración/comida, sobre antes de enviar, doble toque reutiliza UUID, nueva intención crea otro, borrador/pendiente ante fallo y sin cargas nuevas offline; hacer verdes casos de carga de RecordFlowTest.
- [X] T043 [US2] Implementar principal y progreso en `app/src/main/kotlin/lvluplife/ui/HomeScreen.kt` y `app/src/main/kotlin/lvluplife/data/ProgressRepository.kt`: XP confirmada, skin actual, seis atributos y nivel general sin barra general, Loading/Error/Stale y recibo antiguo sin regresión de estado; hacer verdes casos de presentación de RecordFlowTest.

- [X] T044 [US2] Verificar pérdida de respuesta antes/después del commit, reintentos concurrentes y segundo dispositivo sin importar pendiente en `server/src/integrationTest/kotlin/lvluplife/record/CreateRecoveryTest.kt`; ejecutar tests dominio/servidor/Android US2 y registrar evidencia en `specs/001-lvl-up-life-mvp/validation.md`.


## Phase 5: US3 — Actividades personales (P2)

Objetivo: crear y reutilizar definiciones propias. Prueba independiente: cuenta preparada, principal STR sin secundario válido; rechazar CON, repetidos e incompatibles; registrar definición recuperada. RF-07–09; AC-07–11, 69, 73–76.

- [X] T045 [P] [US3] Escribir y ejecutar pruebas de dominio de definiciones en `domain/src/test/kotlin/lvluplife/activity/CustomActivityTest.kt`: nombre/categoría, CON, repetidos y compatibilidad; comprobar fallo antes del modelo/validación.
- [X] T046 [P] [US3] Escribir y ejecutar contratos CREATE_CUSTOM_ACTIVITY/GET /custom-activities en `server/src/integrationTest/kotlin/lvluplife/activity/ActivityContractTest.kt`; incluir cuenta ajena y fallos/reintentos; comprobar fallo antes del servicio.

- [X] T047 [P] [US3] Escribir y ejecutar prueba de crear/recuperar/reutilizar y conservación de entradas en `app/src/androidTest/kotlin/lvluplife/ui/CustomActivityFlowTest.kt`.

- [X] T048 [US3] Crear `domain/src/main/kotlin/lvluplife/model/CustomActivity.kt` y validaciones: «Nombre trim 1–50, categoría no vacía, principal y secundario pertenecientes a STR/DEX/INT/WIT/MEN y diferentes entre sí; sin CON», secondary opcional; aplicar matriz de compatibilidad exacta del spec. Hacer verdes pruebas dominio.

- [X] T049 [US3] Implementar persistencia por UID, revisión y recibo mediante infraestructura US2 en `server/src/main/kotlin/lvluplife/persistence/CustomActivityStore.kt` y `server/src/main/kotlin/lvluplife/application/CreateCustomActivity.kt`; conservar createdAt y solo crear/reutilizar, sin CRUD adicional.

- [X] T050 [US3] Implementar rutas y resolución de actividad propia al registrar en `server/src/main/kotlin/lvluplife/api/ActivityRoutes.kt` y `server/src/main/kotlin/lvluplife/application/ActivityResolver.kt`: orden estable creación/ID, respuesta completa o error, nunca definition ni atributos del cliente como autoridad.

- [X] T051 [US3] Implementar formulario y selector reutilizable en `app/src/main/kotlin/lvluplife/ui/CustomActivityScreen.kt` y `ActivityPicker.kt`, repositorio `app/src/main/kotlin/lvluplife/data/ActivityRepository.kt`; usar coordinador de operaciones y borradores por UID.

- [X] T052 [US3] Ejecutar suites US3 y registro reutilizado con el motor US2; registrar resultados de validación, aislamiento y recuperación en `specs/001-lvl-up-life-mvp/validation.md`.


## Phase 6: US4 — Historial y correcciones (P2)

Objetivo: historial completo por período y correcciones recientes consistentes. Prueba independiente: fixtures de fechas/ciclos, admitir a 299 s y rechazar a 300 s, recalcular al editar/eliminar, conflictos y fallos de consulta. RF-14–16, 43–44, 46–49, 53–56.

- [X] T053 [P] [US4] Escribir y ejecutar pruebas de corrección/replay en `domain/src/test/kotlin/lvluplife/record/CorrectionTest.kt`: AC-19–24, 85–86, 97, 103, 108–109; conservar ID/creación, comida solo fecha y duración solo otra duración, ciclo actual y ventana exclusiva 300 s.

- [X] T054 [P] [US4] Escribir y ejecutar contratos GET /history, EDIT_RECORD y DELETE_RECORD en `server/src/integrationTest/kotlin/lvluplife/history/HistoryContractTest.kt`: períodos UTC−3, páginas bajo revisión estable, expectedVersion, tombstone y AC-110–111, 116–117, 123–125.

- [X] T055 [P] [US4] Escribir y ejecutar pruebas UI de historial vacío/error/Stale, conflictos y recuperación tras cierre en `app/src/androidTest/kotlin/lvluplife/ui/HistoryFlowTest.kt`; no informar éxito ni fallo definitivo con resultado desconocido.

- [ ] T056 [US4] Implementar reglas de corrección en `domain/src/main/kotlin/lvluplife/record/CorrectionRules.kt`: «corrección solo ciclo actual y tiempo transcurrido <300 s», excluir valor previo al validar fecha/duración destino, no convertir modalidades ni cambiar createdAt; hacer verdes pruebas antes de conectar persistencia.

- [ ] T057 [US4] Implementar consulta en `server/src/main/kotlin/lvluplife/application/ReadHistory.kt`: día/semana lunes–domingo/mes UTC−3, todos los ciclos, excluir eliminados y orden estable; devolver período completo bajo revisión estable o error; hacer verdes casos de consulta de HistoryContractTest.
- [ ] T058 [US4] Implementar edición/eliminación en `server/src/main/kotlin/lvluplife/application/CorrectRecord.kt`: expectedVersion/ciclo/ventana, borrado lógico con deletedAt, «tombstone permite informar conflicto de eliminación», sin recreación; integrar replay/recibo en transacción común y hacer verdes casos de comandos de HistoryContractTest.

- [ ] T059 [US4] Implementar ruta de historial en `server/src/main/kotlin/lvluplife/api/HistoryRoutes.kt` e índices `firebase/firestore.indexes.json`: paginación interna estable y complete:true solo con todas las páginas; verificar consultas de HistoryContractTest y configuración de índices.
- [ ] T060 [US4] Implementar rutas de corrección en `server/src/main/kotlin/lvluplife/api/CorrectionRoutes.kt`: EDIT_RECORD/DELETE_RECORD integrados con transacción común, recibo original antes de reevaluar tiempos, recálculo atómico y conflictos seguros; hacer verdes contratos HTTP de correcciones.

- [ ] T061 [US4] Implementar historial/períodos en `app/src/main/kotlin/lvluplife/ui/HistoryScreen.kt` y `app/src/main/kotlin/lvluplife/data/HistoryRepository.kt`: día/semana/mes, todos los ciclos, Empty solo tras éxito y error/Stale/reintento; hacer verdes casos de consulta de HistoryFlowTest.
- [ ] T062 [US4] Implementar edición/eliminación en `app/src/main/kotlin/lvluplife/ui/EditRecordScreen.kt` y `app/src/main/kotlin/lvluplife/data/CorrectionRepository.kt`: ventana orientativa, conflictos muestran estado vigente, nuevo UUID al corregir, recuperación solo instalación original; hacer verdes casos de corrección/pendientes de HistoryFlowTest.

- [ ] T063 [US4] Ejecutar carreras editar/editar, editar/eliminar, temporalidad 299/300 s y medianoche con fallo antes/después de commit en `server/src/integrationTest/kotlin/lvluplife/history/CorrectionRecoveryTest.kt`; hacer verdes suites y registrar evidencia en `specs/001-lvl-up-life-mvp/validation.md`.


## Phase 7: US5 — Clases y etapas visuales (P3)

Objetivo: explorar todas las clases y etapas, con apariencia actual coherente. Prueba independiente: personajes preparados en niveles 14/15/34/35, catálogo completo y desbloqueos sin cargar actividades. RF-36–40; AC-51–62, 91–92.

- [ ] T064 [P] [US5] Escribir y ejecutar prueba contractual de catálogo visual completo en `server/src/test/kotlin/lvluplife/api/ClassCatalogContractTest.kt`: 26 clases, Novato solo con Etapa I y las otras 25 con tres etapas (76 referencias únicas), ausencia de Novato II/III y referencias consistentes con ClassRules; sin nuevas fórmulas en UI.

- [ ] T065 [P] [US5] Escribir y ejecutar pruebas Compose de catálogo, apariencia y desbloqueos 14/15/34/35 para las 25 clases distintas de Novato y Novato solo con Etapa I desbloqueada, sin II/III, en todos esos niveles del catálogo en `app/src/androidTest/kotlin/lvluplife/ui/ClassCatalogFlowTest.kt`.

- [ ] T066 [US5] Preparar lote visual 1: Novato, Guerrero, Explorador, Guardián, Sabio, Monje, Místico, Maestro de Armas, Paladín, Guerrero Arcano, Luchador Monje, Guerrero Místico, Ágil Guardián. Entregar únicamente Etapa I de Novato y etapas I/II/III de las otras 12 clases del lote (37 recursos) en `app/src/main/res/drawable/`, mapeo en `app/src/main/kotlin/lvluplife/ui/ClassAppearance.kt` y origen/licencias en `app/src/main/assets/visual-assets.md`. Incluir inventario clase/etapa/archivo/ID/licencia y hoja de contacto; aceptar solo 37 referencias resolubles sin faltantes, placeholders ni recortes, etapas distinguibles y estilo coherente al tamaño de Home/catálogo. Revisar visualmente hoja y pantallas; lote 2 completa inventario de 76 sin duplicados.
- [ ] T067 [US5] Preparar lote visual 2: Artífice, Ninja, Explorador de Sueños, Guardián de la Sabiduría, Monje Guardián, Druida, Ilusionista, Oráculo, Monje Espiritual, Aventurero Versátil, Maestro del Equilibrio, Héroe Ascendente, Héroe Integral. Entregar etapas I/II/III de cada clase (39 recursos) en `app/src/main/res/drawable/`, mapeo en `app/src/main/kotlin/lvluplife/ui/ClassAppearance.kt` y origen/licencias en `app/src/main/assets/visual-assets.md`. Incluir inventario clase/etapa/archivo/ID/licencia y hoja de contacto; aceptar solo 39 referencias resolubles sin faltantes, placeholders ni recortes, etapas distinguibles y estilo coherente al tamaño de Home/catálogo. Revisar visualmente hoja y pantallas; lote 2 completa inventario de 76 sin duplicados.

- [ ] T068 [US5] Completar respuesta de catálogo visual en `server/src/main/kotlin/lvluplife/api/CatalogRoutes.kt`: extender la ruta de datos creada en T040 sin duplicarla; 26 clases, Novato solo con Etapa I y las otras 25 con tres etapas; completar las referencias visuales con 76 IDs coherentes con recursos y sin entradas Novato II/III; hacer verde ClassCatalogContractTest, sin calcular fórmulas duplicadas.
- [ ] T069 [US5] Implementar Compose en `app/src/main/kotlin/lvluplife/ui/ClassCatalogScreen.kt`: todas las clases desde inicio, Novato solo con Etapa I desbloqueada y sin entradas II/III, desbloqueos 1/15/35 para las otras 25 clases, sin selector de objetivo; hacer verde ClassCatalogFlowTest en 14/15/34/35.

- [ ] T070 [US5] Integrar apariencia final del personaje en `app/src/main/kotlin/lvluplife/ui/HomeScreen.kt`; ejecutar pruebas catálogo/UI y registrar completitud de recursos y contraste en `specs/001-lvl-up-life-mvp/validation.md`.


## Phase 8: US6 — Reiniciar sin perder historial (P3)

Objetivo: reinicio confirmado y recuperable, con datos conservados. Prueba independiente: fixtures con historial/actividades propias, cancelar y confirmar, carrera con registro y respuesta perdida. RF-41–44, 46, 49–55; AC-63–66, 86–88, 99, 102, 114–120.

- [ ] T071 [P] [US6] Escribir y ejecutar pruebas de nuevo ciclo en `domain/src/test/kotlin/lvluplife/progression/ResetTest.kt`: consumos/sueño/comidas/inactividad nuevos, carga retroactiva y estado inicial; no borrar cuenta, nombre, actividades ni historial.

- [ ] T072 [P] [US6] Escribir y ejecutar contratos RESET_PROGRESSION en `server/src/integrationTest/kotlin/lvluplife/reset/ResetContractTest.kt`: confirmed:true obligatorio, ciclo esperado, doble reinicio y carrera con CREATE_RECORD según AC-114–115.

- [ ] T073 [P] [US6] Escribir y ejecutar pruebas de confirmación/cancelación, Pending y cierre/reapertura en `app/src/androidTest/kotlin/lvluplife/ui/ResetFlowTest.kt`; mismo pendiente no produce otro ciclo.

- [ ] T074 [US6] Implementar transición en `domain/src/main/kotlin/lvluplife/progression/ResetRules.kt`: ciclo anterior cerrado, inicial Novato nivel 1/0 XP y sin penalización anterior al inicio; hacer verdes pruebas antes de implementar servicio.

- [ ] T075 [US6] Implementar `server/src/main/kotlin/lvluplife/application/ResetProgression.kt` y `server/src/main/kotlin/lvluplife/api/ResetRoutes.kt`: commit cierra ciclo/crea nuevo/cambia puntero/proyección/recibo; conservar colecciones, versión y reglas aprobadas, UUID repetido devuelve recibo y otro UUID con ciclo cerrado se rechaza.

- [ ] T076 [US6] Implementar diálogo y acción en `app/src/main/kotlin/lvluplife/ui/ResetScreen.kt` y `app/src/main/kotlin/lvluplife/data/ResetRepository.kt`: explicar datos preservados, cancelación sin cambio, persistir sobre confirmado antes de enviar, sin reenviar al ciclo nuevo automáticamente.

- [ ] T077 [US6] Ejecutar fallos antes/después de commit y recuperación de reinicio solo desde instalación que conserva el pendiente en `server/src/integrationTest/kotlin/lvluplife/reset/ResetRecoveryTest.kt`; verificar otro dispositivo consulta estado confirmado sin importar pendientes y registrar evidencia en `specs/001-lvl-up-life-mvp/validation.md`.


## Phase 9: Polish y calidad transversal

Objetivo: cerrar evidencia y calidad del MVP completo; las pruebas críticas ya deben estar verdes en cada historia. Estudios y muestras futuras conservan carácter orientativo del PRD.

- [ ] T078 [P] Completar regresión de aislamiento de todos los comandos/consultas, tokens en logs/errores y restricciones release en `server/src/integrationTest/kotlin/lvluplife/security/FullIsolationTest.kt` y `app/src/test/kotlin/lvluplife/ReleaseConfigTest.kt`; cubrir RNF-07–08 sin reemplazar pruebas tempranas.

- [ ] T079 [P] Verificar contraste ≥4,5:1 y significado no dependiente solo de color en `app/src/androidTest/kotlin/lvluplife/ui/AccessibilityTest.kt`; corregir recursos en `app/src/main/kotlin/lvluplife/ui/theme/Theme.kt` y flujo frecuente ≤3 interacciones según definición del PRD. Revisar acceso, nombre, principal, carga de comida/duración/sueño, actividad propia, historial, edición/eliminación, clases y confirmación de reinicio; cubrir estados aplicables Loading/Confirmed/Empty/Error/Stale/Pending/Rejected y diálogos. Medir pares texto/control-fondo en capturas y colores efectivos con razón WCAG (Lmax+0,05)/(Lmin+0,05), incluir estados deshabilitado/seleccionado y evidencias. Desde principal contar cada toque/selección/gesto; tipeo continuo por campo cuenta uno y cambiar campo aparte, según PRD. Tres flujos guardados comida/entrenamiento/sueño deben cumplir ≤3; documentar secuencia y colores/capturas, corregir fallos; estudio mediana 15 s sigue orientativo.

- [ ] T080 Definir y ejecutar matriz representativa Android 10/API29 y versiones soportadas por el SDK fijado, tamaños de pantalla y cierre/reapertura en `specs/001-lvl-up-life-mvp/validation.md`; registrar dispositivos/versiones efectivamente probados y límites de evidencia para SC-009–010. Matriz mínima técnica: API29, API31 y API37 (si SDK37 del plan puede instalarse; incompatibilidad se registra como bloqueo técnico, sin sustituir silenciosamente), cada una en teléfono 360×640 dp y pantalla 800×1280 dp, portrait y landscape. Aprobar cada combinación al completar acceso/carga/progreso/historial/corrección/clases/reinicio, errores y reapertura sin crash, pérdida confirmada ni controles inaccesibles. Registrar imagen/dispositivo/API/tamaño/resultado; combinaciones no ejecutadas quedan NO DISPONIBLE, sin declarar compatibilidad verificada ni marcar completada esta validación.

- [ ] T081 Preparar arnés orientativo de rendimiento/idempotencia en `server/src/integrationTest/kotlin/lvluplife/quality/PerformanceScenarios.kt`: volúmenes 0/1.000/10.000, ciclo completo, contención y arranque frío; documentar resultados disponibles sin declarar p95/99,5 % acreditados por emulación ni imponer topes funcionales nuevos. Entregar invocación reproducible :server:integrationTest --tests "*PerformanceScenarios*" con parámetros de volumen/perfil documentados: lectura de progreso/historial y CREATE/EDIT/DELETE, secuencial y concurrente por cuenta; inyectar fallo poscommit y reintento del mismo UUID. Emitir JSON/CSV con semilla, entorno, versión, calentamiento, n, p50/p95, errores, lecturas/reintentos y duración; arranque frío separado, 0/1.000/10.000 como perfiles. Cerrar arnés con smoke pequeño ejecutado y comprobación de esquema de reporte/consistencia/idempotencia; muestras amplias y metas p95 siguen orientativas, no condición de cierre del arnés.

- [ ] T082 Validar build, unitarios dominio/servidor/Android, integración y connectedDebugAndroidTest según `specs/001-lvl-up-life-mvp/quickstart.md`; comprobar Google real solo con proyecto/dispositivo ya configurados, registrar fallos o verificaciones no disponibles en `specs/001-lvl-up-life-mvp/validation.md`, sin crear infraestructura ni desplegar. Resultado en tres grupos APROBADO/FALLIDO/NO DISPONIBLE con comando, entorno, salida/informe y motivo. Para cerrar: assembleDebug, :domain:test, :server:test, testDebugUnitTest, :server:integrationTest y connectedDebugAndroidTest pertinentes deben ejecutarse y aprobar, sin fallos críticos abiertos. Sin dispositivo no cerrar tarea: registrar bloqueo. Google real sin configuración queda NO DISPONIBLE, no equivale a prueba aprobada ni obliga crear infraestructura; declarar esa limitación de integración explícitamente.

- [ ] T083 Actualizar y comprobar comandos reales de `specs/001-lvl-up-life-mvp/quickstart.md` desde un checkout/configuración de prueba: prerrequisitos, puertos, arranque y comandos de tests/build reproducibles; enlazar resultados y señalar pasos externos no disponibles sin presentarlos como ejecutados.
- [ ] T084 Completar matriz RF-01–57, RNF-01–13 y AC-01–131 en `specs/001-lvl-up-life-mvp/validation.md`: cada fila enlaza prueba/evidencia, resultado y limitaciones; distinguir TDD observado, ejecutado y estudios orientativos pendientes, sin telemetría ni encuestas añadidas.


## Dependencias y orden de ejecución

```text
Setup → Foundational → US1 → US2 ┬→ US3
                                ├→ US4
                                ├→ US5
                                └→ US6
US1 + US2 + US3 + US4 + US5 + US6 → Polish
```

Foundational entrega recibos/hash/idempotencia y la base de commit atómico con revisión (T085–T087), sin modelos de cuenta ni replay. US1 introduce cuenta/ciclo y reutiliza esa base. US2 añade el motor y extiende el protocolo para registros/replay; T037 integra recibos ya existentes y T038 extiende transacciones, sin duplicar infraestructura. US3–US6 pueden probarse con cuentas, registros y actividades sintéticas: sus pruebas no dependen de ejecutar las pantallas de otras historias. US4 puede probar ciclos anteriores con fixtures antes de implementar US6. US6 puede probar preservación con actividades preparadas antes de US3. Los cambios en rutas compartidas se integran secuencialmente. Los dos lotes visuales se ejecutan en orden porque comparten mapeo e inventario; la respuesta de catálogo precede su pantalla. Validación de registros precede replay; recibos preceden protocolo transaccional; comandos preceden rutas y sus pantallas.

T085–T087 se ejecutan en Foundational antes de T017–T024 aunque sus IDs sean mayores: se conservan los IDs existentes para mantener trazabilidad. T028 prueba los datos del catálogo y T040 implementa su ruta antes del cierre US2. T064–T070 completan y validan referencias/imágenes en US5; las suites de US2 no exigen estos archivos.

Dentro de cada fase, seguir el orden listado salvo los grupos [P]: las pruebas [P] al inicio comparten solo fases anteriores. Una implementación nunca se ejecuta en paralelo con su prueba roja pendiente. US2 tiene una segunda tarea de prueba Android después de la infraestructura de carga del servidor; debe fallar antes de implementar sus pantallas. Todas las tareas de cierre dependen de implementación y pruebas de su historia.

## Ejemplos de ejecución paralela por historia

| Historia | Tareas simultáneas permitidas tras sus prerrequisitos |
|---|---|
| US1 | AccountContractTest.kt y AccessFlowTest.kt antes de implementar alta/UI |
| US2 | ExperienceTest.kt, ProgressionTest.kt, ReplayTest.kt y CreateRecordContractTest.kt antes del motor/servicio |
| US3 | CustomActivityTest.kt, ActivityContractTest.kt y CustomActivityFlowTest.kt |
| US4 | CorrectionTest.kt, HistoryContractTest.kt y HistoryFlowTest.kt |
| US5 | ClassCatalogContractTest.kt y ClassCatalogFlowTest.kt |
| US6 | ResetTest.kt, ResetContractTest.kt y ResetFlowTest.kt |

Estas oportunidades describen el trabajo posible; no autorizan omitir las barreras de TDD ni editar simultáneamente el mismo archivo.

## Estrategia de implementación

1. Completar setup y foundation; resolver bloqueos documentales antes de implementar la parte afectada.
2. Entregar US1 como primer incremento verificable de identidad y privacidad. US1 sola no completa el MVP aprobado.
3. Añadir US2 para disponer del recorrido principal de valor: acceso → actividad → progreso confirmado.
4. Incorporar US3/US4 (P2) y US5/US6 (P3), validando cada historia con fixtures independientes y regresión de las anteriores.
5. Cerrar las seis historias y calidad transversal para completar el MVP. No crear tareas para notificaciones, redes sociales, XP manual, sincronización de pendientes ni nuevas cargas offline.

## Trazabilidad de cobertura

| Área | Fase responsable |
|---|---|
| RF-01–05; identidad, autorización y cancelación | Foundation, US1 |
| RF-06, 10–13, 17–40, 44–45, 47; progresión canónica | US2; catálogo visual completo US5 |
| RF-07–09; definiciones propias | US3 |
| RF-14–16, 43, 46, 48; historial/correcciones | US4; preservación US6 |
| RF-41–42, 50; reinicio y carrera de ciclo | US6 |
| RF-49, 51–57; recuperación, errores, sesión, tiempo e identidad | Foundation y pruebas por operación en US1–US6 |
| RNF-01–13, SC-001–013 | Pruebas por historia y calidad transversal; métricas/estudios orientativos sin bloquear por ausencia de muestras futuras |

Los AC numéricos se toman íntegros del spec, no se reescriben fórmulas en estas tareas. La matriz final debe asignar cada AC-01–131 a su prueba/evidencia concreta y reportar los no verificados.

## Correspondencia de tareas revisadas

IDs anteriores corresponden a la versión de 69 tareas; usar únicamente los IDs nuevos para ejecución. La tabla conserva la correspondencia histórica; T037–T038 ahora integran/extienden infraestructura adelantada a Foundational. Se añaden T085–T087 sin renumerar los IDs existentes.

| ID anterior | IDs nuevos | Ajuste |
|---|---|---|
| T001 | T001 | Criterio de finalización precisado |
| T002 | T002 | Criterio de finalización precisado |
| T003 | T003 | Criterio de finalización precisado |
| T004 | T004 | Criterio de finalización precisado |
| T005 | T005 | Criterio de finalización precisado |
| T009 | T009 | Criterio de finalización precisado |
| T010 | T010, T011 | Dividida en dos |
| T011 | T012 | Criterio de finalización precisado |
| T013 | T014, T015 | Dividida en dos |
| T014 | T016 | Criterio de finalización precisado |
| T027 | T029, T030 | Dividida en dos |
| T029 | T032, T033 | Dividida en dos |
| T031 | T035, T036 | Dividida en dos |
| T032 | T037, T038 | Dividida en dos |
| T033 | T039, T040 | Dividida en dos |
| T035 | T042, T043 | Dividida en dos |
| T037 | T045, T046 | Dividida en dos |
| T048 | T057, T058 | Dividida en dos |
| T049 | T059, T060 | Dividida en dos |
| T050 | T061, T062 | Dividida en dos |
| T054 | T066, T067 | Dividida en dos |
| T055 | T068, T069 | Dividida en dos |
| T065 | T079 | Criterio de finalización precisado |
| T066 | T080 | Criterio de finalización precisado |
| T067 | T081 | Criterio de finalización precisado |
| T068 | T082 | Criterio de finalización precisado |
| T069 | T083, T084 | Dividida en dos |

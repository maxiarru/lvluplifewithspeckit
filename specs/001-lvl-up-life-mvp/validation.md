# Evidencia de validación: Lvl Up Life MVP

## Revisión documental de integridad — 2026-11-05

Evaluación solicitada por el usuario, realizada por Codex sobre los documentos actuales. Alcance: calidad de requisitos de CHK001–CHK036; no implementación, compilación, recursos visuales entregados ni pruebas ejecutadas.

Fuentes: [especificación](spec.md), [constitución v1.0.0](../../.specify/memory/constitution.md), [modelo de datos](data-model.md), [contrato API](contracts/api.md), [plan](plan.md) y [tareas](tasks.md). Las referencias RF/AC/SC y los títulos de sección de la tabla identifican la evidencia específica en estas fuentes. Las obligaciones constitucionales forman parte del criterio; los diseños complementan su aplicación sin sustituir requisitos ausentes.

Resultado: **36 aprobados, 0 pendientes, 0 bloqueados**. La aprobación acredita que el comportamiento esperado está documentado y es evaluable. No acredita que la aplicación lo cumpla. Las etiquetas Gap/Ambiguity del checklist eran preguntas de revisión, no defectos confirmados.

| Ítem | Resultado | Evidencia documental | Justificación |
|---|---|---|---|
| CHK001 | APROBADO | RF-17–18, RF-20–21; Reglas de XP; AC-25–35, AC-78–81 | Se definen bloques de 5 minutos, reparto 2/3–1/3, sueño por fecha y comidas, con sobrantes y topes independientes. |
| CHK002 | APROBADO | RF-23–30; AC-36–44, AC-82 | Umbral 30 × N por atributo, contador de subidas generales, sobrantes y máximo 50 explícitos. |
| CHK003 | APROBADO | RF-41–43; Regla de recálculo; AC-65–66, AC-87–88, AC-99, AC-102 | Se enumeran XP, contador, consumos, duración, comidas, sueño e inactividad reiniciados y datos conservados. |
| CHK004 | APROBADO | SC-012; Constitución VI; modelo: Protocolo de escritura y lectura consistente | Registro, progreso, revisión y recibo se confirman atómicamente; fallos no permiten efectos parciales ni duplicados. |
| CHK005 | APROBADO | US1; SC-007; Constitución III; API: Convenciones y confianza | La autorización por identidad verificada cubre lectura/modificación de todos los datos propios; no basta ocultarlos en UI. |
| CHK006 | APROBADO | RF-48–49, RF-52–53, RF-56–57; AC-110–113, AC-128–131 | Se distinguen inválido, denegado, conflicto, fallo conocido y resultado desconocido; cada uno tiene acción de usuario. |
| CHK007 | APROBADO | Reglas de XP; RF-20–21; AC-26, AC-28, AC-79, AC-89 | Se redondea principal hacia arriba y secundario recibe resto antes de aplicar límites independientes. |
| CHK008 | APROBADO | Reglas de XP; Inactividad por fecha y atributo; RF-25, RF-35; AC-100–101 | XP calculada precede topes; efectiva positiva determina actividad; progreso dentro del nivel es distinto y queda cero al 50. |
| CHK009 | APROBADO | Reglas de duración y fechas; Regla de recálculo; RF-54; AC-103, AC-123–125 | Servidor UTC−3 determina fechas y ventana exclusiva <300 s; solo se penalizan fechas cerradas. |
| CHK010 | APROBADO | RF-49–51, RF-55; AC-117–118, AC-126–127; Entidad Operación pendiente | Misma operación conserva identidad/cuenta/ciclo; dos intenciones iguales son distintas; recuperación solo en instalación con pendiente. |
| CHK011 | APROBADO | RF-48; Edge Cases; AC-110–111 | Segunda edición/eliminación desde estado anterior se rechaza, informa estado vigente/eliminado y no recrea registros. |
| CHK012 | APROBADO | Regla de recálculo; AC-93 | Orden fecha/creación/ID ordinal; ediciones conservan ID y creación; eliminados excluidos. |
| CHK013 | APROBADO | RF-31–35, RF-44; AC-85, AC-102 | Replay retroactivo revierte penalizaciones indebidas sin contar inactividad anterior al comienzo del ciclo. |
| CHK014 | APROBADO | RF-43, RF-46, RF-50; AC-86, AC-114–115 | Historial conservado; ciclos anteriores no editables; commit ordena registro/reinicio y no traslada pendientes al nuevo ciclo. |
| CHK015 | APROBADO | RF-49, RF-51; Edge Cases; Assumptions; Fuera de alcance | Comprobar/reintentar una operación enviada no habilita cargas nuevas offline ni importación de pendientes. |
| CHK016 | APROBADO | RF-17, RF-19; Constitución II y VII; API: Convenciones y confianza | Cálculo único autoritativo y rechazo explícito de XP enviada por cliente, incluso en edición. |
| CHK017 | APROBADO | AC-25–44, AC-78–82, AC-89 | Fixtures con resultados numéricos: 10 min dual=2/0, 30+30 min sueño=3, nivel 49→50 con XP cero y topes parciales. |
| CHK018 | APROBADO | AC-110–117; RF-48–50 | Conflicto no altera datos; pendiente no acredita XP ni éxito; reintento recupera resultado sin repetir efectos. |
| CHK019 | APROBADO | SC-006, SC-007, SC-012 | Resultados medibles: cero duplicados, 100 % de accesos cruzados rechazados y 100 % de registros confirmados conservados. |
| CHK020 | APROBADO | Success Criteria: nota posterior a SC-013; Constitución: Flujo de desarrollo y calidad | Muestras y estudios futuros orientativos no eximen reglas funcionales, seguridad, TDD ni pruebas automatizadas. |
| CHK021 | APROBADO | US1–US4 y sus Acceptance Scenarios | Recorridos de acceso/alta, registro/progreso, actividad propia e historial/correcciones tienen escenarios independientes. |
| CHK022 | APROBADO | AC-89–90, AC-104 | Se fijan resultados concurrentes exactos para 1 XP restante, una hora disponible y nombre equivalente en mayúsculas/minúsculas. |
| CHK023 | APROBADO | RF-49; AC-116–117 | Edición, eliminación y reinicio quedan explícitamente incluidos en consulta/reintento sin efectos duplicados. |
| CHK024 | APROBADO | RF-51; AC-118; Entidad Operación pendiente | Seguimiento sobrevive cierre/reapertura y conserva cuenta/identidad hasta poder consultar resultado. |
| CHK025 | APROBADO | RF-52, RF-57; AC-119–120, AC-130–131 | Cancelación vuelve a acceso sin cambios; sesión vencida suspende; denegación no reintenta ni reasigna pendientes. |
| CHK026 | APROBADO | RF-53; AC-121–122 | Error/reintento y datos previos desactualizados se diferencian de vacío confirmado por consulta exitosa. |
| CHK027 | APROBADO | RF-47; Reglas de duración y fechas; AC-96–105 | Componentes enteros, minutos 0–59, total positivo, saldo 1.440 y liberación/consumo origen-destino explícitos. |
| CHK028 | APROBADO | RF-31–35, RF-44; AC-83–84, AC-106–107 | Seis fechas sin descuento; séptima y posteriores hasta 5 XP con piso cero, sin perder nivel, aun sin nuevas cargas. |
| CHK029 | APROBADO | RF-24–25, RF-36–40; Tabla de Clases; AC-58, AC-61–62, AC-82, AC-91–92 | Nivel 50/XP cero; Novato prioritario con solo I; 80 % sin redondeo y límites visuales 14/15 y 34/35 definidos. |
| CHK030 | APROBADO | RF-54; AC-123–125 | Se valida al aplicar según servidor, no al enviar; recibo ya confirmado conserva resultado tras vencer ventana o cambiar fecha. |
| CHK031 | APROBADO | Success Criteria: confidencialidad RNF-07; Constitución III; API: Errores y conflictos | Comunicaciones protegidas y prohibición expresa de secretos/tokens/datos ajenos en logs, errores y diagnósticos. |
| CHK032 | APROBADO | RF-52; AC-120; Constitución III | Logout/cambio de cuenta oculta datos consultados y pendientes de A; B no recibe operaciones ni cambia su propietario. |
| CHK033 | APROBADO | SC-006, SC-012; Constitución VI; RF-44, RF-49 | Se separan conservación de confirmados, no duplicación de registros/XP y equivalencia del progreso reconstruido. |
| CHK034 | APROBADO | Assumptions; RF-49, RF-56; AC-129; API: 503 | Identidad, persistencia y reloj autoritativo son dependencias explícitas; fallo no implica éxito, conserva entrada o pendiente según certeza. |
| CHK035 | APROBADO | Assumptions; Fuera de alcance; RF-19–20, RF-47; Constitución VII | Se confía en actividad declarada sin GPS/fotos; identidad, entradas, fechas y límites siguen validados por servidor. |
| CHK036 | APROBADO | Clarifications; RF-48–50; AC-110–117; Constitución VIII y Governance | Decisiones enlazadas por RF/AC; contradicciones deben explicitarse y resolverse con responsable antes de implementar, sin inventar requisitos. |

## Límites y seguimiento

- T006 queda completada por esta evaluación documental anticipada; no acredita Setup ni las demás tareas de Foundational.
- Las 76 imágenes se producirán e integrarán en T066–T067 y se validarán con el catálogo final de US5. Su ausencia actual no incumple un checklist de calidad de requisitos.
- No se ejecutaron Gradle, tests de dominio, integración, Android ni mediciones. No se verificaron Firebase/Google reales, dispositivos, accesibilidad efectiva ni recursos visuales.
- T084 permanece pendiente: esta tabla cubre CHK001–CHK036 y no sustituye la matriz futura RF-01–57, RNF-01–13 y AC-01–131 con evidencia de ejecución.
- Las tareas de implementación, incluido T082 y su validación de builds/tests, conservan sus estados pendientes. Esta revisión no autoriza despliegues ni crea infraestructura remota.
- El checklist requirements.md mantiene su ciclo separado; no se modificó durante esta evaluación.

## Intento de implementación — 2026-11-05

Estado: bloqueado en T001, sin completar Setup ni avanzar a tareas dependientes.

- Prerrequisitos: `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` terminó con código 0 y seleccionó `specs/001-lvl-up-life-mvp`.
- Gate de checklists: integridad 36/36 y requirements 16/16, sin pendientes. No se modificaron sus marcas.
- No existe `.specify/extensions.yml`; no hay hooks pre/post que ejecutar en este intento.
- Se revisaron plan, tareas, modelo, investigación, contratos, quickstart y constitución. El repositorio no contiene todavía módulos ni wrapper. Se añadió `.gitignore` para Kotlin/Android, herramientas locales y credenciales; el wrapper JAR queda expresamente permitido.
- `java -version` falló: Java no está en PATH. El runtime de `C:\Program Files\Android\Android Studio\jbr` falla por ausencia de `lib\jvm.cfg`. El runtime de Android Studio2 sí responde, pero es OpenJDK 25.0.3; no acredita el JDK 17 exigido.
- Caché local: distribuciones Gradle 8.0 y 8.7; ninguna 9.5.0. `.gradle/jdks` no contiene un JDK. SDK local: plataformas android-33 y android-34, sin android-37.
- `Invoke-WebRequest https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256 -TimeoutSec 20` falló por host desconocido en sandbox. Repetido con ejecución ampliada, terminó por timeout. `Test-NetConnection services.gradle.org -Port 443 -InformationLevel Quiet` devolvió False y un aviso de fallo de resolución de nombre. La ejecución ampliada fue admitida; no hubo rechazo de aprobación automática.
- No se ejecutaron `gradlew --version`, `help --info`, `reportBuildVersions`, builds ni tests: el wrapper y las herramientas requeridas no están disponibles. No hay lockfiles ni versiones efectivas verificadas. No se sustituyeron las versiones del plan.
- T001 permanece sin marcar. Las demás tareas conservan su estado; se detiene la ejecución conforme a la regla de fallo de tarea secuencial de speckit-implement. No hay evidencia roja/verde de implementación en este intento.

Para reanudar T001: proporcionar acceso de descarga a Gradle y repositorios de dependencias, JDK 17 y SDK API 37 (o instalaciones locales accesibles). Después generar y verificar wrapper/configuración, resolver pins/KSP y lockfiles y ejecutar todos los comandos de cierre de T001 antes de continuar T002. No se requiere crear infraestructura remota para resolver este bloqueo.

## Implementación — 2026-11-10: T001

- Checklists: integridad 36/36 y requirements 16/16, sin cambios de marcas. No existe `.specify/extensions.yml` (sin hooks previos).
- Se descargaron Gradle 9.5.0 y Temurin JDK 17.0.21+9 en `.tooling/` (ignorado). SHA-256 de distribución Gradle verificado: `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`, fijado también en el wrapper generado por Gradle.
- `gradlew.bat --version`: APROBADO, Gradle 9.5.0/JVM 17.0.21. El Kotlin 2.3.20 de esa salida es el Kotlin interno de Gradle, no KGP.
- `gradlew.bat help --info --write-locks --no-daemon`: APROBADO, configuración raíz mínima en 1m57s. Generó `buildscript-gradle.lockfile` y `settings-gradle.lockfile`.
- `gradlew.bat reportBuildVersions --no-daemon`: APROBADO, AGP=9.3.1, KGP=2.4.20, Compose compiler=2.4.20, KSP=2.3.10, Gradle=9.5.0, JVM=17.0.21. Versiones obtenidas de artefactos resueltos, no solo del catálogo.
- Catálogo sin versiones dinámicas; configuraciones de proyecto rechazan dependencias dinámicas/changing. El procesador Room y la compilación Android se verificarán con los módulos, no se acreditan aquí.
- Evidencia local: `.tooling/t001-help.log` y `.tooling/t001-versions.log`. Ejecuciones con JAVA_HOME apuntando a `.tooling/jdk-17.0.21+9` y GRADLE_USER_HOME a `.tooling/gradle-user-home`.
- El sandbox impide DNS/conexión al daemon; ejecución ampliada admitida permitió descargas y configuración. Ningún rechazo automático de aprobación.

## Implementación — 2026-11-10: T002

- `gradlew.bat :domain:compileKotlin :server:compileKotlin :app:assembleDebug :server:integrationTestClasses --write-locks --no-daemon`: APROBADO en 2m47s, 39 tareas ejecutadas. Evidencia local `.tooling/t002-build.log`.
- Dominio JVM puro, servidor Ktor/JVM17, Android Compose minSdk29/targetSdk37. Aplicación y servicio solo contienen un esqueleto de arranque; ninguna historia funcional acreditada.
- AGP instaló Build Tools 36.0.0 y SDK Platform 37.0 revisión 2 usando las licencias ya presentes en el SDK local. No se sustituyó API37 por API34.
- `gradlew.bat :server:tasks --all --no-daemon`: APROBADO en 55s, integrationTest e integrationTestClasses disponibles; `.tooling/t002-tasks.log`. integrationTestClasses es NO-SOURCE por ahora: no acredita pruebas ejecutadas.
- Lockfiles generados para domain/server/app. Pins adicionales: Ktor3.3.0, Compose BOM2025.10.00, Activity1.11.0, JUnit4.13.2; se conservan las versiones obligatorias del plan.
- Primer intento falló porque Gradle prohíbe combinar dependency locking y failOnDynamicVersions. Se retiró failOnDynamicVersions/failOnChangingVersions; siguen los pins explícitos y lockfiles. Esta corrección reemplaza la afirmación de rechazo automático de versiones dinámicas en la entrada T001.
- APK de arranque: `app/build/outputs/apk/debug/app-debug.apk`. Sin instalación ni tests de dispositivo todavía.

## Implementación — 2026-11-10: T003

- Configurados proyecto demo-lvluplife, Auth127.0.0.1:9099 y Firestore127.0.0.1:8080; reglas deny-all e índices inicialmente vacíos. CLI13.35.1/SDK cliente11.10.0 fijados en package.json y package-lock.json; paquete privado sin publicación.
- `npm.cmd install --no-fund --no-audit`: APROBADO (676 paquetes). Node instalado25.1.0/npm11.6.2; advertencia de engine de superstatic (18/20/22), no impidió esta verificación. No acredita compatibilidad con Node LTS en otros entornos.
- Con JAVA_HOME del JDK17 local y su bin en PATH: `npm.cmd run verify:emulators`: APROBADO; expande el comando emulators:exec especificado por T003. Evidencia `.tooling/t003-emulators.log`.
- SDK web cliente, sin Admin: lecturas/escrituras sin autenticar denegadas, autenticación anónima sintética exitosa y lecturas/escrituras autenticadas también denegadas. 4 comprobaciones PASS y Auth disponible. El script solo admite el proyecto demo y hosts locales fijados; no imprime tokens.
- Primer intento consultó un endpoint Auth con método GET no soportado (405); se corrigió la sonda para acreditar disponibilidad por autenticación real en el emulador y se repitió completa con código0.
- Emuladores cerrados por emulators:exec al terminar. No se crearon recursos remotos ni se acreditó Google real, IAM o persistencia en producción.

## Implementación — 2026-11-10: T004

- `gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest --tests lvluplife.VariantSecurityTest --write-locks --no-daemon`: APROBADO en 3m, 89 tareas (68 ejecutadas). Evidencia `.tooling/t004-security.log` y `app/build/test-results/testDebugUnitTest/TEST-lvluplife.VariantSecurityTest.xml`: 3 pruebas, 0 fallos/errores/omitidas.
- Pruebas sobre manifests fusionados y XML de packaged_res de ambas variantes: release deniega cleartext sin excepciones; debug solo habilita localhost/127.0.0.1/10.0.2.2, base denegada. No se configura Auth/Firestore de producción en este esqueleto. TLS del servicio desplegado no probado; restricciones TLS del cliente HTTP se cerrarán al implementar T014.
- Backup deshabilitado y recursos legacy/API31+ excluyen toda la carpeta database (base y sidecars), tanto cloud-backup como device-transfer. Prueba verifica referencias efectivas y exclusiones de ambas variantes.
- Durante el trabajo aparecieron 76 imágenes aportadas en drawable con nombres no válidos para Android. Primer build falló en packageDebugResources; se normalizaron mayúsculas/espacios/tildes a nombres Android, sin modificar contenido ni sobrescribir archivos. Se comprobó ausencia de colisiones y destino dentro de drawable. Mapeo original/nuevo local: `.tooling/t004-resource-renames.json`.
- Las imágenes compilan, pero NO se acredita todavía correspondencia semántica clase/etapa ni calidad visual US5 (T064–T070 siguen pendientes). En particular, los nombres originales incluyen orcaculo y Guerrero monje III; su asignación final debe revisarse con el inventario US5.

## Implementación — 2026-11-10: T005

- `gradlew.bat :domain:test --tests lvluplife.fixtures.ClockFixtureTest :server:integrationTest --tests lvluplife.fixtures.FaultFixtureTest :server:jar :domain:jar --write-locks --no-daemon`: APROBADO en50s. Reloj: 2 pruebas; fallos: 3 pruebas; sin fallos/errores/omitidas. Log `.tooling/t005-fixtures.log`, XML en domain/build/test-results/test y server/build/test-results/integrationTest.
- Reloj avanza reproduciblemente por299/300s, zona explícita conserva instante y rechaza retroceso. Cuentas A/B sintéticas distintas.
- Simulador de límite de commit: fallo anterior no persiste; posterior conserva valor aunque pierda respuesta; caso normal conserva aislamiento entre fixtures. No sustituye las pruebas posteriores de transacciones Firestore.
- Inspección de entradas ZIP de domain.jar y server.jar: ninguna referencia a fixtures/MutableClock/FaultPoint/TestEnvironment. rg en los tres source sets main no encontró estos controles. Solo existen en test/integrationTest, sin rutas ni configuración pública de reloj/fallos.
- Setup T001–T005 completado. T006 ya estaba aprobada documentalmente; ninguna regla crítica de progresión implementada todavía.
- Commit local previo: 8daa992, bloque verificado T001–T004. Sin push.

## TDD — 2026-11-10: T007 ROJO observado

- `gradlew.bat :server:test --tests lvluplife.api.TrustBoundaryTest --write-locks --no-daemon`: ejecutó 7 pruebas, las 7 FALLARON por comportamiento ausente (404 frente a401/400/403/413/503 esperados). Compilación exitosa: no se confunde error de compilación con prueba roja. Log `.tooling/t007-red.log`, XML server/build/test-results/test/TEST-lvluplife.api.TrustBoundaryTest.xml.
- Cubre autenticación requerida, proveedor simulado inválido/vencido/revocado/deshabilitado, campos prohibidos/desconocidos/XP, propietario ajeno, JSON malformado, cuerpo excesivo y errores seguros sin secretos. Verificación Firebase real y revocación del adaptador todavía pendientes de T010.
- serverModule es solo un punto de composición sin rutas/adaptadores; permitió ejecutar el rojo antes de implementarlos. T007 acredita escritura y ejecución roja, no seguridad funcional verde; T010–T011 deben hacerla pasar antes de cerrar el bloque Foundational.
- T009 pide ejecutar todas las pruebas servidor antes de T010–T011, pero estas pruebas deben seguir rojas hasta esos adaptadores. Se ejecutarán primero los tests específicos de tipos/puertos; T009 no se considerará cerrada hasta lograr la regresión completa tras T011. Se conserva el orden de implementación y no se omiten pruebas.

## Corte de revisión por tarea — 2026-11-10

- Por indicación del usuario, a partir de este punto se detiene el avance al finalizar cada T y se espera su decisión sobre hacer commit antes de continuar. No hay commits posteriores a ba839bc (T005).
- T007 tiene rojo observado y permanece pendiente de revisión/commit del usuario. T008 y T009 se habían empezado antes de esa indicación y sus cambios quedan sin commit.
- T008: build de instrumentación llegó a connectedDebugAndroidTest en el AVD API30, sin informe confirmado. Se detuvo el daemon Gradle exclusivo del proyecto para respetar el corte; no se acredita ejecución roja de los casos ni cierre de proceso y T008 sigue sin marcar.
- T009: comando enfocado `:domain:test :server:test --tests lvluplife.application.PortsTest --write-locks --no-daemon` APROBADO en1m47s. Tipos/puertos implementados en el working tree, sin cierre de tarea: faltan inspección de dependencias y suite completa verde tras T010–T011. Log `.tooling/t009-focused.log`.
- No existe .specify/extensions.yml al revisar el cierre de esta ejecución parcial; sin hooks posteriores registrados. MVP y Foundational NO completados.

## TDD — 2026-11-10: T008 ROJO observado al reanudar

- Prerrequisitos y checklists nuevamente PASS (integridad36/36, requirements16/16), sin modificar sus marcas. Sin extensions.yml.
- Tras el corte anterior, el informe UTP indicaba aborto externo y cero tests: no se contó como rojo. Se instalaron los APK ya compilados mediante adb install -r -t y se ejecutó instrumentación directamente en Pixel_6a_API_30/API30, emulator-5554.
- `adb shell am instrument -w -r -e class lvluplife.data.PendingStoreTest lvluplife.android.test/androidx.test.runner.AndroidJUnitRunner`: 5 pruebas ejecutadas, 5 fallos por IllegalStateException: Pending persistence is not implemented. Evidencia `.tooling/t008-direct-red.log`.
- Se ejecutaron individualmente PendingStoreProcessStages#persistBeforeProcessDeath y #recoverAfterProcessDeath con `adb shell am force-stop lvluplife.android` entre ambas: 1 prueba/1 fallo por etapa, misma ausencia de implementación. Logs `.tooling/t008-process-before-red.log` y `.tooling/t008-process-after-red.log`. No se acredita aún recuperación efectiva de datos tras matar el proceso.
- Casos: persistencia previa al transporte, reapertura, clave(uid,operationId), borradores y sesiones A/B, UNKNOWN ante recibo ausente, conservación del sobre y eliminación solo definitiva. La fábrica de producción contiene únicamente un contrato/stub fallante; Room no implementado hasta T013.
- scripts/test-pending-process.ps1 ejecutará ambas etapas con force-stop y exigirá OK de cada etapa al cerrar T013. Las pruebas de estado no sustituyen los tests futuros del coordinador de envíos de T015.
- T008 marcada por escritura y ejecución roja de sus pruebas, no por persistencia implementada ni pruebas verdes. El usuario autorizó en este turno los commits pendientes por separado; no se hizo push.
- `gradlew.bat :app:assembleDebugAndroidTest --write-locks --no-daemon`: APROBADO en40s, 50 tareas al día; lockfile de instrumentación actualizado. Log `.tooling/t008-test-build.log`.

## Implementación — 2026-11-10: T009

- Tipos UID/UUID/revisión y puertos de reloj, identidad y persistencia implementados. IdentityTest: 3 pruebas verdes; PortsTest: 2 verdes, fake sin Firebase con separación por UID. Los 2 tests de reloj anteriores permanecen verdes.
- Ejecución enfocada previa APROBADA: `:domain:test :server:test --tests lvluplife.application.PortsTest --write-locks --no-daemon` (1m47s).
- `gradlew.bat :domain:dependencies --configuration runtimeClasspath :domain:test :server:test --continue --write-locks --no-daemon`: ejecutado. Reporte de dominio: únicamente kotlin-stdlib2.4.20 y annotations13.0, sin Android/Compose/Firebase/Ktor ni app/server. Log `.tooling/t009-regression.log`.
- Resultado global de la ejecución: FALLIDO por las mismas 7 pruebas rojas de TrustBoundaryTest creadas en T007; PortsTest pasó (2/2), dominio sin fallos. No hay fallos nuevos de tipos/puertos. Esta ejecución NO acredita suite completa del servidor verde.
- Aclaración de la interpretación anterior: T009 exige IdentityTest/PortsTest verdes, ejecutar las suites e inspeccionar dependencias. Esos criterios de su alcance están satisfechos; las pruebas rojas de adaptadores de T007 se cierran expresamente en T010–T011. T009 se marca completada por tipos/puertos, sin declarar completo Foundational ni modificar las exigencias de seguridad pendientes.
- Commits de reanudación: fb662e8 (regla de trabajo), c6d9df8 (T007), 333bce4 (T008). El usuario autorizó estos commits y el avance posterior en este turno; sin push.

## Implementación — 2026-11-10: T010

- Agregado Firebase Admin Java9.11.0 (pin y lockfile), FirebaseIdentityVerifier e interceptación de todas las rutas /v1. UID derivado exclusivamente del resultado verificado y guardado en atributos de llamada; parámetros/headers de propietario no lo sustituyen. Token ausente/malformado/ambiguo/duplicado se rechaza antes del proveedor.
- El adaptador de producción llama FirebaseAuth.verifyIdToken(token,true), solicita comprobación de revocación y devuelve UID validado. Tokens inválidos/vencidos/revocados, usuario deshabilitado/eliminado y tenant incorrecto se rechazan. Fallo de certificados/configuración/dependencia produce indisponibilidad; no se conserva causa privada ni se imprimen tokens. Se respeta CancellationException y el IO bloqueante se deriva a Dispatchers.IO.
- `gradlew.bat :server:test --tests lvluplife.api.AuthenticationTest --tests *TrustBoundaryTest.everyQueryRequiresIdentity --tests *TrustBoundaryTest.invalidExpiredRevokedAndDisabledTokensAreDenied --tests *TrustBoundaryTest.dependencyFailuresAndDenialsNeverExposePrivateDetails --write-locks --no-daemon`: APROBADO en4m8s, 11 pruebas (8 nuevas y 3 de T007 antes rojas), sin fallos/errores/omitidas. Log `.tooling/t010-authentication.log` y XML en server/build/test-results/test.
- `gradlew.bat :domain:test :server:test --continue --write-locks --no-daemon`: regresión ejecutada, dominio verde; servidor17 pruebas,13 verdes y4 fallos esperados de esquema/tamaño/campos de cliente que siguen devolviendo404 y pertenecen a T011. No se declara suite global verde. Log `.tooling/t010-regression.log`.
- Pruebas usan proveedor sustituido y excepciones reales tipadas del SDK. Acreditan flag checkRevoked, mapeo de errores, derivación de identidad y respuestas HTTP seguras; NO acreditan Google real, firma contra certificados de producción ni IAM. La configuración FirebaseApp/ADC/emuladores se completa en T012; sin FirebaseApp configurada, las solicitudes con token fallan de forma cerrada con503.
- Fuentes oficiales consultadas: https://firebase.google.com/docs/auth/admin/verify-id-tokens y https://firebase.google.com/docs/auth/admin/manage-sessions; versión https://firebase.google.com/support/release-notes/admin/java. La comprobación básica de token no sustituye checkRevoked=true.
- Commits pendientes autorizados ya realizados: fb662e8 (regla), c6d9df8 (T007), 333bce4 (T008), 6d644cb (T009). T010 queda implementada/verificada, SIN COMMIT, para la revisión por tarea solicitada antes de avanzar a T011. Sin push.

## Implementación — 2026-11-10: T011

- Commit T010 autorizado y creado: 9c31483 (feat(auth): verificar identidad y revocación con Firebase). Se continuó inmediatamente con T011, conforme a la indicación del usuario. Sin push.
- Rutas /v1 para comandos, perfil, catálogo, progreso, actividades propias, historial y recibos. ApiHandlers recibe únicamente UID verificado y solicitudes tipadas; los servicios aún no implementados responden503, sin fingir perfiles, datos vacíos, confirmaciones ni escrituras.
- RequestSchema valida las seis variantes, campos requeridos/permitidos, tipos sin coerción, UUID, fechas ISO, presencia de ciclo y forma de duración. Rechaza propietario/XP/nivel/clase/creación enviados por cliente, campos desconocidos en sobre/payload, claves duplicadas, JSON adicional, tipos erróneos y confirmación de reinicio ausente/falsa. Reglas funcionales, definición de actividad y recibos siguen a cargo de las tareas posteriores.
- JSON UTF-8, cuerpo limitado a65536 bytes tanto con Content-Length como en lectura por chunks. Ese límite es de transporte por comando, no de número de registros ni historial. Consultas rechazan parámetros ajenos/duplicados y validan período/fecha/UUID según ruta.
- Errors.kt centraliza mensajes seguros y estados400/401/403/404/405/409/413/422/503. No devuelve causas, mensajes de excepciones ni datos recibidos. Authentication reutiliza estas respuestas; cancelación conserva su semántica. Las excepciones funcionales se mapean sin afirmar resultados persistidos; los recibos definitivos siguen pendientes de T085–T087 y de cada comando.
- Dependencias directas fijadas: Jackson databind2.18.3 (misma versión ya resuelta por Firebase Admin) y Ktor StatusPages3.3.0. Lockfile del servidor actualizado, sin cambiar Kotlin/AGP/Gradle.
- `gradlew.bat :domain:test :server:test --write-locks --no-daemon`: APROBADO en50s. Servidor24/24 verdes (Authentication8, RequestSchema7, TrustBoundary7, Ports2), sin fallos/errores/omitidas. Dominio5/5 verde, tarea test al día por fuentes no modificadas. Log `.tooling/t011-tests.log`; XML en server/build/test-results/test y domain/build/test-results/test.
- Se cerró el rojo observado de T007: las cuatro pruebas restantes de esquema/tamaño/campos pasan. Casos adicionales cubren las variantes, chunks sin Content-Length, consultas estrictas, UUID/formato ambiguo, HTTP409/422/503 y excepciones privadas. No se acredita aquí persistencia, Firestore/Admin real, Google real, IAM, TLS desplegado ni pruebas Android verdes.
- Fuentes de implementación: https://ktor.io/docs/server-status-pages.html y https://ktor.io/docs/server-requests.html. Revisión final: sin extensions.yml, por lo tanto sin hooks posteriores registrados. T011 marcada; sin commit todavía, esperando la revisión por tarea. Tras su commit autorizado se continuará directamente con T012, sin otra confirmación para comenzar.

## Implementación — 2026-11-10: T012

- T011 autorizada y commiteada individualmente en 3ea530e; se continuó inmediatamente con T012. Sin push.
- ServerConfig exige GOOGLE_CLOUD_PROJECT, valida modo/puerto/coincidencia con GCLOUD_PROJECT y falla sin reflejar valores privados. Producción es el modo por defecto, usa ADC y rechaza proyectos demo y cualquier variable de emulación, incluso vacía. Emulación exige LVLUP_ENV=emulator, demo-lvluplife y ambos hosts loopback con puertos8080/9099.
- ServerResources inicializa Firebase Admin y Firestore, inyecta el verificador en el arranque real de Ktor y cierra recursos al detenerse. No usa claves privadas versionadas. Producción obtiene ADC del entorno; modo demo usa credenciales vacías para FirebaseApp y credenciales de emulación administradas por el SDK Firestore. No se agregaron endpoints de fixtures ni controles de reloj/fallos.
- FirestoreAccess implementa IO bajo users/{uid}, raíz o pares colección/documento relativos; bloquea segmentos de escape/barras/controles/IDs excesivos. Espera acotada de10s, cancelación del future y error PersistenceUnavailable sin causa o rutas privadas. Su write es una primitiva; los escritores funcionales seguirán el protocolo transaccional de T085–T087, sin handlers de mutación activos en este incremento.
- Primer intento contra emuladores: 2 fallos de IO. Diagnóstico sin mensajes/tokens identificó INTERNAL/HTTP2 y alerta TLS (prefijo1503010002): NoCredentials seleccionaba un camino heredado de GrpcFirestoreRpc3.47.0 que ignoraba emulatorHost y tomaba el host por defecto. No se acreditó persistencia en esos intentos. Se retiró NoCredentials, se fijaron host y emulatorHost a127.0.0.1:8080 y se normalizó localhost a esa IP; las pruebas comprueban ambos destinos ANTES de cualquier RPC. La sonda temporal se eliminó.
- Comparación de diagnóstico: el SDK cliente de T003 volvió a denegar las cuatro lecturas/escrituras bajo el mismo emulador. Una ejecución de diagnóstico no llegó a tests por lockfile antiguo y otra por política de scripts de PowerShell; se usó Node como alternativa sin cambiar esa política. No se confundieron esos fallos con resultados de pruebas funcionales.
- Comando final, con JAVA_HOME/JDK17 y GRADLE_USER_HOME locales, LVLUP_ENV=emulator, GOOGLE_CLOUD_PROJECT=demo-lvluplife, FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 y FIRESTORE_EMULATOR_HOST=127.0.0.1:8080: `firebase.cmd emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore ".\gradlew.bat :domain:test :server:test :server:integrationTest --write-locks --no-daemon"`: APROBADO, Gradle26s y emulators:exec código0. Emuladores cerrados automáticamente.
- Resultados: servidor29/29 unitarias (incluidas5 de configuración), integración8/8 (FirestoreAccess5 y fixtures3), dominio5/5 verde con tarea al día. Sin fallos/errores/omitidas. Firestore: lectura/escritura separadas por UID, cierre/reapertura de cliente conservando documentos, rutas inválidas, HTTP503 seguro y token real de Auth Emulator verificado por Admin con revocación habilitada. No simula caída de red ni acredita firma/Google reales.
- Evidencia: `.tooling/t012-unit-build.log`, `.tooling/t012-emulators.log`, XML en server/build/test-results/test, server/build/test-results/integrationTest y domain/build/test-results/test. Dependencia Firestore3.47.0 explícita (misma versión ya traída por Admin9.11.0), pins/lockfile actualizados.
- server/README.md documenta comandos/variables/puertos, identidad runtime dedicada y ADC, seis permisos mínimos previstos con sus motivos: datastore.entities.get/list/create/update, datastore.databases.get y firebaseauth.users.get. Sin permisos de borrado físico, administración, despliegue o creación de cuentas Auth en runtime. IAM remoto NO PROBADO; no se crearon recursos ni se desplegó. TLS del despliegue y Google real tampoco probados.
- Fuentes: https://firebase.google.com/docs/firestore/security/iam, https://docs.cloud.google.com/iam/docs/roles-permissions/firebaseauth y https://firebase.google.com/docs/emulator-suite/connect_auth; diagnóstico contrastado con el sources.jar oficial google-cloud-firestore3.47.0.
- Sin extensions.yml, por lo tanto sin hooks posteriores registrados. T012 completada y SIN COMMIT, pendiente de revisión por tarea. Tras el commit autorizado se continuará directamente con T013.

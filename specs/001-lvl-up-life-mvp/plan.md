# Implementation Plan: Lvl Up Life MVP

**Branch**: `main` (rama real; identificador de funcionalidad `001-lvl-up-life-mvp`) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: `specs/001-lvl-up-life-mvp/spec.md`, 57 RF y 131 AC; [PRD](../../PRD.md), [constitución](../../.specify/memory/constitution.md) y AGENTS.md.

## Summary

Construir el MVP Android de registro de actividades y progresión RPG, con Google Auth, historial privado, clases, skins y reinicio conservando datos. Android presenta resultados de una API Kotlin autoritativa; un motor Kotlin puro implementa las reglas una sola vez. Firestore confirma conjuntamente cambios, progreso y recibos idempotentes. Room conserva pendientes por cuenta tras cerrar la app; solo la instalación que los conserva recupera su seguimiento. Otro dispositivo consulta los datos ya guardados tras iniciar sesión con la misma cuenta de Google. Sin actividades nuevas offline, funciones sociales ni recomendaciones.

## Technical Context

**Language/Version**: Kotlin 2.4.20, JVM 17; Android API mínima 29, compileSdk/targetSdk 37. Compose compiler plugin 2.4.20; AGP 9.3.1, Gradle 9.5.0 y JDK 17. AGP usa Kotlin integrado: fijar KGP 2.4.20 en el classpath del build raíz, sin aplicar `org.jetbrains.kotlin.android`; módulos puros usan `org.jetbrains.kotlin.jvm` 2.4.20. Ver compatibilidad y fuentes en investigación.

**Primary Dependencies**: Compose Material 3, ViewModel/StateFlow y coroutines, Credential Manager/Google ID y Firebase Auth mediante Firebase BoM; Room para pendientes. Servicio Ktor/JVM, Firebase Admin Java y Google Cloud Firestore Java, desplegable en Cloud Run. Gradle Kotlin DSL y catálogo de versiones con pins y lockfiles al bootstrap; sin dependencias dinámicas.

**Storage**: Firestore como fuente de verdad, solo accesible desde API; Room privado por UID para sobres pendientes y borradores. Sin historial completo local ni secretos persistidos en Room. Catálogos y recursos visuales versionados con el producto.

**Testing**: kotlin.test/JUnit en dominio y servidor; pruebas contractuales HTTP, integración Firestore/Auth Emulator; pruebas Android/Compose y Room. Reloj e interrupciones inyectables solo por configuración de test. TDD obligatorio para progresión.

**Target Platform**: Android 10+; servidor Linux JVM en Cloud Run, Firestore y Firebase Auth del mismo proyecto. Misma región de servicio y base cuando sea posible. SDK/target API compatible con el build elegido; sin promesa de soporte de versiones Android futuras no probadas.

**Project Type**: aplicación móvil nativa y un servicio HTTP de confianza.

**Performance Goals**: SC-001 apertura p95 ≤3 s, SC-002 escritura con progreso p95 ≤2 s, SC-003 historial p95 ≤3 s; tres interacciones y mediana ≤15 s para carga frecuente; disponibilidad objetivo 99,5 %. Condiciones y carácter orientativo de muestras según PRD. No son mediciones realizadas.

**Constraints**: seis atributos, XP automática, topes por fecha/ciclo, UTC−3 fijo, edición/eliminación <300 s al validar en servidor, atomicidad/idempotencia, cero acceso cruzado, TLS ≥1.2. Protección ante sesión cambiada y respuesta perdida. Sin implementación de notificaciones excluidas del MVP.

**Scale/Scope**: un personaje por UID; seis historias, 11 actividades básicas, 26 clases con tres etapas. Ensayos orientativos 0/1.000/10.000 registros en hasta 365 fechas y varios ciclos. Sin volumen de usuarios objetivo ni límite nuevo de registros. Recálculo completo y serialización por cuenta pueden limitar latencia; medir antes de optimizar.

## Constitution Check

Evaluación previa a investigación: los ocho principios son aplicables; ninguna excepción solicitada. Evaluación posterior al diseño:

| Principio | Resultado de diseño | Evidencia |
|---|---|---|
| I Modularidad | Conforme | UI → repositorios HTTP; `domain` puro; adaptadores Firestore solo en `server`. |
| II Determinismo | Conforme | Único motor, entradas temporales explícitas y orden fecha/creación/ID; sin fórmulas en Compose. |
| III Seguridad | Conforme | UID validado, autorización API, Firestore cerrado al cliente, IAM y sin credenciales versionadas. |
| IV Test-First | Conforme como obligación de ejecución | Prueba roja antes del código para cada regla crítica; no se acredita todavía su ejecución. |
| V Simplicidad | Conforme | Tres módulos justificados por límites Android/JVM, un servicio, sin event bus ni microservicios. |
| VI Persistencia | Conforme | Revisión por cuenta y commit atómico de registro/proyección/recibo; reintento recupera resultado. |
| VII Validación y errores | Conforme | Esquemas estrictos, reloj autoritativo, estados definidos y pruebas negativas planificadas. |
| VIII Especificaciones | Conforme | Trazabilidad abajo; diseño alineado con la aclaración de RF-49/RF-55 y AC-117, sin traslado de pendientes entre dispositivos; tareas se generan en otra fase. |

Este control evalúa el diseño, no el software. Las 36 casillas de `checklists/integridad.md` permanecen sin modificar por instrucción del usuario; no se usa su estado como evidencia de una revisión aprobada. Antes de implementar, resolver su evaluación conforme al flujo de implementación. No hay violaciones constitucionales justificadas ni enmiendas en este plan.

## Project Structure

### Documentation (this feature)

```text
specs/001-lvl-up-life-mvp/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api.md
│   └── ui.md
└── checklists/
```

`tasks.md` se generará con `$speckit-tasks`; esta fase no lo crea.

### Source Code (repository root, estructura propuesta)

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
domain/src/{main,test}/kotlin/
app/src/main/kotlin/lvluplife/
├── ui/                  # pantallas, navegación, ViewModels
├── application/         # casos de uso de cliente y sesión
└── data/                # HTTP, Firebase Auth, Room de pendientes
app/src/{test,androidTest}/
server/src/main/kotlin/lvluplife/
├── api/                 # autenticación, DTO, errores
├── application/         # comandos y consultas
└── persistence/         # Firestore, revisión, recibos
server/src/{test,integrationTest}/
firebase/                # emuladores, reglas deny-all e índices
```

**Structure Decision**: tres módulos compilables separan Android del servidor y permiten probar el motor sin servicios externos. Dentro de cada módulo se usan paquetes por responsabilidad; no se crea un módulo por pantalla. Los directorios de código todavía no existen.

## Phase 0: investigación resuelta

Ver [research.md](research.md): stack, confianza, snapshot estable/revisión, transacción corta, reloj por intento, recibos duraderos, Room y validación. Las decisiones se basan en documentación oficial; compatibilidad real y rendimiento se comprobarán durante implementación.

## Phase 1: diseño

### Flujo autoritativo

1. Firebase autentica Google; el servicio verifica token y deriva UID. Alta y reserva de nombre normalizado son atómicas y únicas.
2. Android guarda un sobre pendiente antes de su primer envío; doble toque reutiliza identidad. Solo envía con conexión y sesión de su propietario.
3. El servicio busca recibo; si existe devuelve resultado original. Si no, obtiene snapshot estable por revisión y reconstruye el candidato con el motor puro.
4. Una transacción corta comprueba recibo, revisión, versión y tiempo vigente; guarda cambio/proyección/recibo o rechazo funcional definitivo. Si la revisión o fecha usada cambió, vuelve a leer y reconstruir.
5. Android distingue confirmado, rechazado y desconocido. La instalación que conserva el pendiente consulta recibos con sesión válida de la cuenta original; no interpreta ausencia como rechazo. La proyección de un recibo antiguo no reemplaza progreso actual.
6. Progreso se reconstruye también al consultar, incluyendo días cerrados; historial se filtra por UTC−3 y abarca ciclos conservados. Un segundo dispositivo obtiene perfil, progreso, actividades propias e historial mediante consultas autenticadas, sin importar pendientes ni necesitar sus UUID.

Detalles de invariantes, validación y transiciones: [data-model.md](data-model.md). Contratos HTTP: [contracts/api.md](contracts/api.md). Pantallas y estados: [contracts/ui.md](contracts/ui.md).

### Trazabilidad y orden de entrega para tareas futuras

| Área | Requisitos | Diseño y evidencia a preparar |
|---|---|---|
| Acceso y alta | RF-01–05; AC-01–05, 67–68, 70, 72, 90, 94–95 | Token, UID, nombre único y un personaje; concurrencia y acceso cruzado. |
| Catálogo y actividades propias | RF-06–12, 45; AC-06–15, 69, 73–81 | Catálogo inmutable y validación de definiciones; reutilización. |
| Motor crítico | RF-17–40, 44 | Tests rojos de XP, niveles, topes, inactividad, clases y etapas antes del motor; AC numéricos de spec como fixtures. |
| Historial y correcciones | RF-13–16, 46–48 | Orden estable, versiones, límites temporales y consultas por período; AC-19–24, 85–86, 93, 96–109, 110–111. |
| Reinicio | RF-41–43, 50 | Nuevo ciclo atómico y datos preservados; AC-63–66, 87–88, 99, 102, 114–115. |
| Recuperación y sesión | RF-49, 51–57 | Recibos, Room por UID y recuperación desde la instalación que conserva el pendiente; acceso a datos guardados desde otro dispositivo sin trasladar pendientes; fallos inyectados; AC-112–113, 116–131. |
| Calidad transversal | RNF-01–13, SC-001–013 | Suites de seguridad, consistencia, accesibilidad y ensayos orientativos del PRD. |

Orden: bootstrap y arnés de pruebas; dominio mediante TDD; servidor y persistencia con integración; Android por historias priorizadas; recuperación/concurrencia y calidad transversal. Las pruebas críticas deben preceder cada implementación, no constituir una fase final separada.

### Validación y riesgos

[quickstart.md](quickstart.md) describe comandos previstos y resultados esperados, sin afirmar que existan hoy. Antes de entregar código: pruebas unitarias, integración autoritativa, límites de tiempo, aislamiento, reintentos y app ensamblada. Integración Google real requiere proyecto, OAuth y dispositivo configurados; emuladores no acreditan ese intercambio. Evaluar recursos para todas las clases/etapas y usabilidad de tres interacciones antes de cerrar UI. Desplegar, activar facturación o crear recursos remotos no forma parte de esta ejecución.

### Revisión del diseño tras aclaración — 2026-11-05

RF-49, RF-55 y AC-117 delimitan la recuperación al cliente que conserva el sobre pendiente. Se reutiliza la investigación existente: UUID y recibos siguen siendo necesarios para respuestas perdidas y reintentos locales; Room conserva el seguimiento tras cerrar el proceso. No se introduce sincronización de pendientes, descubrimiento de operaciones desde otro dispositivo ni endpoint para importarlas. Las versiones y herramientas del diseño previo no cambian en esta revisión.

La evaluación constitucional previa y posterior mantiene los ocho principios conformes a nivel de diseño: siguen vigentes aislamiento por UID, determinismo, atomicidad, topes y conflictos entre dispositivos. No hay nuevas incógnitas funcionales ni excepciones. La matriz concreta de dispositivos para SC-009 corresponde a validación técnica durante implementación.

## Complexity Tracking

Sin excepciones a la constitución. El servicio es necesario para autorización y cálculo autoritativo; Room es necesario para pendientes que sobreviven al proceso. Una revisión monotónica por cuenta evita transacciones crecientes y locks distribuidos adicionales. Si el replay no cumple objetivos con volúmenes representativos, documentar mediciones y evolución equivalente antes de introducir checkpoints.

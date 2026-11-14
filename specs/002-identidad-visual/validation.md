# Validación — Feature 002: identidad visual y navegación

Registro por tarea de lo ejecutado y sus resultados. Las corridas usan el emulador
Pixel 6a API 30 y los emuladores locales de Firebase (proyecto `demo-lvluplife`).

## T001 — Fuentes (2026-11-14)

- Origen: repositorio oficial `google/fonts`, carpeta `ofl/`, descargado con autorización del
  titular.
  - `res/font/fredoka.ttf`: Fredoka variable, 159 184 bytes, SHA-256
    `2ba02e68b152868aef9ba28e24b3648c7d457fe6f25c761f2c2c53fb61a73fc8`.
  - `res/font/nunito.ttf`: Nunito variable, 276 932 bytes, SHA-256
    `bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207`.
  - Licencias SIL OFL 1.1 en `assets/licenses/fredoka-OFL.txt` y `nunito-OFL.txt`.
- Los dos archivos tienen cabecera TTF válida (`00 01 00 00`).
- `assembleDebug`: correcto; `R.txt` incluye `font fredoka` y `font nunito`.
- Commit: d37ccd7.

## T002 — `xpRequired` en `GET /progress` (2026-11-14)

- RED 1: `ProgressContractTest` no compila porque falta `progressPayload`.
- RED 2: con `progressPayload` devolviendo `progressionResult` sin cambios, fallan 2 de 4 pruebas:
  `progressPublishesXpRequiredForTheNextLevelOfEveryAttribute` y
  `maximumLevelPublishesAnExplicitNullRequirement`. Las dos que protegen los campos existentes y
  los recibos pasan.
- Implementación: `progressPayload` en `server/src/main/kotlin/lvluplife/api/ProgressRoutes.kt`
  agrega a cada atributo `xpRequired` desde `ProgressionThresholds.attribute`, con `null` en
  nivel 50. `RecordOperations.progressionResult` (recibos y proyección persistida) no cambia.
- Integración: nueva `progressPublishesXpRequiredButReceiptAndStoredProjectionDoNot` en
  `CreateRecordContractTest`, contra Firestore y Auth locales. `PerformanceScenarios` compara el
  endpoint con `progressPayload`, que es lo publicado.
- Durante la tarea, una corrida de integración falló al compilar porque `progressPayload` era
  `internal` y no era visible desde `integrationTest`. Se hizo pública y se borraron los
  resultados anteriores antes de volver a correr.
- Resultados finales:
  - `:domain:test`: 322 pruebas, 0 fallas.
  - `:server:test`: 39 pruebas, 0 fallas.
  - `:server:integrationTest`: 235 pruebas, 0 fallas.
- Contrato documentado en `specs/001-lvl-up-life-mvp/contracts/api.md`.
- No ejecutado en esta tarea: pruebas de la app. Android todavía no lee el campo; eso es T003.

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

## T003 — Lectura de `xpRequired` en la app (2026-11-14)

- RED 1: `ProgressRepositoryTest` no compila porque `RecordAttributeView` no tiene `xpRequired`.
- RED 2: con el campo agregado y el repositorio devolviendo siempre `null`, fallan 2 pruebas:
  `readsTheServerPublishedXpRequiredForEachAttribute` y `missingOrIncoherentXpRequiredIsUnreadable`.
- Implementación:
  - `RecordAttributeView.xpRequired: Int?` en `RecordFlowPorts.kt`.
  - `ProgressRepository` exige el campo presente: entero mayor que `xp` en niveles 1 a 49 y `null`
    en nivel 50. Si falta, es `null` fuera de nivel 50, es menor o igual que `xp`, negativo,
    fraccionario o texto, o tiene valor en nivel 50, rechaza con `UNREADABLE_RESPONSE`.
  - La app no verifica `30 × nivel`: esa regla vive solo en el dominio.
- Se actualizaron los 9 constructores sintéticos de las pruebas Android con valores fijos de
  fixture; no cambia lo que verifican.
- Resultados:
  - `assembleDebug`: correcto.
  - `testDebugUnitTest`: 148 pruebas, 0 fallas (antes 145; 3 nuevas).
  - `connectedDebugAndroidTest`: 137 pruebas. En una corrida sin emuladores de Firebase falló
    solo `FirebaseSdkIdentityTest`, que los requiere. Repetida con `firebase emulators:exec`,
    pasa. Las 136 restantes pasaron en la primera corrida.
- Commit: de66893.

## T004 — Tema «Arcade vivo» (2026-11-14)

- Pruebas: `AppearanceContrastTest` compara contra los valores fijos de FR-001, no contra el tema.
  Mide los píxeles reales de texto y fondo. Nuevas:
  - `themeExposesTheArcadePaletteFontsAndRoundedShapes`: esquema de colores, colores por atributo,
    Fredoka en títulos y botones, Nunito en el texto, y formas de 14 y 16 dp;
  - `everyPaletteTextAndControlPairMeets45`: todos los pares de la paleta, incluidos los 6 colores
    de texto por atributo sobre tarjeta blanca;
  - `attributeTextColorsRenderReadablyOnCardsAndKeepTheirWrittenCode`;
  - `systemDarkModeStillShowsTheLightArcadeScheme`: simula el modo oscuro del sistema con
    `NightSystem` (`androidTest/SystemNightMode.kt`) y verifica primero que el sistema quedó oscuro.
- RED 1: no compila porque faltan `AttributeColors`, `LvlUpFonts` y `LvlUpTheme` sin parámetro.
- RED 2: con esas piezas y la paleta Material por defecto, fallan 7 de 9. En modo oscuro el tema
  devolvía `#141218`.
- Implementación:
  - `Theme.kt`: `ArcadeColors`, esquema claro único (la superficie es el fondo #EAF1FC y los
    contenedores son blancos), tipografías variables con peso por `FontVariation` y formas
    redondeadas. `LvlUpTheme` ya no recibe `dark` (FR-004).
  - `AttributeColors.kt`: relleno y texto por sigla.
  - `AccessibilityTest` conserva sus dos pasadas, ahora con el sistema en modo claro y en modo
    oscuro.
- Contraste medido (mínimo 4,5:1): el par más bajo es DEX sobre tarjeta, 4,89; texto secundario
  sobre fondo, 4,98; botón principal, 5,90; texto sobre fondo, 14,06. Los colores de texto por
  atributo solo alcanzan 4,5:1 sobre blanco, así que se usan sobre tarjetas.
- No hizo falta ajustar ningún valor de la paleta.
- Resultados, con emuladores de Firebase:
  - `assembleDebug`: correcto.
  - `testDebugUnitTest`: 148 pruebas, 0 fallas.
  - `connectedDebugAndroidTest`: 141 pruebas, 0 fallas (antes 137; 4 nuevas).
- Commit: fd4685d.

## T005 — Rutas de pestañas y `selectTab` (2026-11-14)

- Pruebas nuevas en `SessionControllerTest` (`app/src/test`):
  - orden de las pestañas: PJ, Atributos, Info, Historial;
  - elegir una pestaña reemplaza la raíz y cierra lo secundario;
  - `back()` cierra lo secundario, después vuelve a PJ y en PJ no hace nada;
  - cambiar de pestaña conserva los datos confirmados, así no se vuelve a pedir el progreso;
  - solo se eligen pestañas, nunca sin sesión ni mientras falta el nombre de usuario;
  - cerrar sesión o cambiar de cuenta olvida la pestaña elegida.
- RED 1: no compila porque faltan `ATTRIBUTES`, `INFO`, `tabs` y `selectTab`.
- RED 2: con `selectTab` apilando como `navigate`, fallan 4 de las 7.
- Implementación en `SessionController.kt`: `SessionRoute.ATTRIBUTES`, `INFO` y `tabs`;
  `selectTab` exige una pestaña y no actúa sin sesión ni con `PROFILE` abierto; `back()` vuelve
  a PJ desde la raíz de otra pestaña.
- Resultados:
  - `assembleDebug` y compilación de pruebas Android: correctos.
  - `testDebugUnitTest`: 155 pruebas, 0 fallas (antes 148; 7 nuevas).
  - Android, clases que usan la navegación (`SessionNavigationTest`, `AccessFlowTest`,
    `HistoryFlowTest`): 35 pruebas, 0 fallas. La suite completa se corre en T007.

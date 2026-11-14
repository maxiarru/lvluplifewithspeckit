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

## T006 a T013 — Registro retroactivo (2026-11-14)

Estas tareas no registraron su entrada al cerrarse; se completa en T014 con lo ejecutado en cada
una. Todas las corridas Android usan Pixel 6a API 30. Hasta T013, `FirebaseSdkIdentityTest` falló
en cada corrida completa por falta del emulador de Auth (`SignInFailure: UNAVAILABLE`); en T014,
con los emuladores de Firebase levantados, pasa.

- T006 (5739e64): `MainTabs` con barra inferior y controladores por sesión; `SessionHost` sin
  cabecera, «Historial» ni «Cerrar sesión». `MainTabsTest` 2/2 (un toque por pestaña, semántica
  `selected`, indicador no cromático, progreso pedido una sola vez en 7 cambios de pestaña).
  `assembleDebug` y `testDebugUnitTest` correctos.
- T007 (ebe3b8f): corrida inicial 112/140 (27 fallas por los selectores de cabecera, más Firebase).
  Selectores migrados a `isTab(...)` en Historial, Acceso, Navegación y matriz de dispositivos;
  «Cerrar sesión» siempre visible mientras carga el perfil. Final: 139/140.
- T008 (a344c47): PJ rediseñada. `PjScreenTest` 4/4 (progreso confirmado, Novato, nombre largo en
  una línea sin desplazar la imagen, avisos de desactualizado y error). Dos aserciones de
  `AppearanceContrastTest` adaptadas al diseño nuevo. Suite: 143/144.
- T009 (03266a7): Registrar desde PJ con Hoy/Ayer/Anteayer y detalle AC-06. TDD en
  `RecordRepositoryTest`: RED por compilación, RED de 3 pruebas por aserción, GREEN.
  `RecordScreenTest` 4/4, incluida RNF-04 en 3 interacciones. Suite: 147/148.
- T010 (2a82865): «+ Crear actividad personalizada» al final de la lista; vuelve con la actividad
  elegida por el `activityId` del recibo y la fecha conservada. `CustomFromRecordTest` 2/2 (flujo
  con homónimo y rechazo sin CON, AC-69). Suite: 149/150.
- T011 (1a23894): `AttributesScreen` con «X / Y XP al siguiente nivel» y «Nivel máximo» en 50.
  `AttributesScreenTest` 3/3 con `xpRequired` arbitrarios. Suite: 155/156.
- T012 (c83bff3): `InfoScreen` con reglas verificadas contra el PRD (RF-13, RF-17, RF-18, RF-20,
  RF-23, RF-24, RF-27, RF-30, RF-32 a RF-34, RF-37), catálogo que vuelve a Info, reinicio y cierre
  de sesión. Se corrigieron dos simplificaciones de la maqueta (topes de CON y regla de sueño solo
  para Dormir). `InfoScreenTest` 4/4. Suite: 159/160.
- T013 (0c4ef46): estilo nuevo en `HistoryScreen`; `HistoryFlow` sin cambios. `HistoryFlowTest`
  23/23 y `AccessibilityTest` 15/15 sin modificar las pruebas. Suite: 159/160.

## T015 — Registro rápido de Comida y Dormir (2026-11-14)

- Origen: revisión de las capturas de T014. El titular decidió quitar «Entrenar», dejar «Comida» y
  «Dormir» en un toque (Dormir = 7 horas) y acotar RNF-04 del PRD a comida y sueño. Se agregaron
  FR-015 y FR-016, el escenario 4 de US3 y la línea del registro rápido en FR-007.
- `RecordFormController.quick` envía su propia operación sin llamar a `change`, así no pisa el
  borrador de «+ Registrar actividad»; un resultado `TEMPORARILY_UNAVAILABLE` se reintenta con el
  mismo id y los mismos valores.
- `AccessibilityTest`: se reemplazaron las 8 pruebas del diálogo anterior por 7: comida y sueño en
  un toque, sin botón de entrenamiento, borrador intacto, sin conexión, resultado pendiente y
  reintento con el mismo id. `InfoScreenTest` verifica la tarjeta «Registro rápido».
  `DeviceMatrixStages` registra el entrenamiento desde Registrar (no ejecutada en esta feature).
- Durante la tarea el emulador Android se cerró; se volvió a levantar (`Pixel_6a_API_30`, sin
  ventana) y arrancó en unos 12 s.
- Suite completa con emuladores de Firebase: BUILD SUCCESSFUL en 5 min 7 s.
  - `:domain:test` 322, `:server:test` 39, `testDebugUnitTest` 158, `:server:integrationTest` 235
    y `connectedDebugAndroidTest` 159 pruebas; 0 fallas en todas.
- Commit: 94ff397.

## T014 — Validación final (2026-11-14)

- Primera corrida, antes de T015, con emuladores de Firebase (Auth 9099, Firestore 8080, proyecto
  `demo-lvluplife`), `LVLUP_ENV=emulator`:
  `firebase emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore ".\gradlew.bat assembleDebug :domain:test :server:test testDebugUnitTest :server:integrationTest connectedDebugAndroidTest --no-daemon --continue"`.
  BUILD SUCCESSFUL en 6 min 23 s, con 322, 39, 158, 235 y 160 pruebas sin fallas (incluida
  `FirebaseSdkIdentityTest`). Emuladores cerrados por `emulators:exec` con código 0.
- Resultado vigente: la corrida completa de T015, con el mismo comando, sin fallas (159 pruebas
  Android).
- Capturas en `visual/` (1080 × 2211, Pixel 6a API 30), generadas por `VisualCaptureTest` con datos
  sintéticos (Ana, Guerrero etapa II, nivel general 20) y los mismos puertos que la app, incluidas
  las actividades personalizadas. Se copian con
  `adb exec-out run-as lvluplife.android cat files/visual/<nombre>.png` y la prueba se corre con
  `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`:
  `pj.png`, `atributos.png`, `info.png`, `registrar.png` e `historial.png`.
  - La primera tanda no pasaba las actividades personalizadas y por eso Registrar no mostraba
    «+ Crear actividad personalizada»; se corrigió la prueba y se rehicieron las capturas después
    de T015.
- Observaciones para la revisión:
  - `info.png` muestra el comienzo de Info; Niveles, Clases, Tu personaje y Cerrar sesión siguen al
    desplazar.
  - `DeviceMatrixStages` (excluida por `ProcessStage`) se migró en T007, T009, T010, T012 y T015
    pero no se ejecutó en esta feature.
- SC-005 (aprobación del titular contra la maqueta): APROBADO por el titular el 2026-11-14, sobre
  las capturas rehechas después de T015.

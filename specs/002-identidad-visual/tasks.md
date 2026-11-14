# Tasks: Identidad visual y navegación de Lvl Up Life

**Input**: `specs/002-identidad-visual/spec.md` y `plan.md`

**Prerequisites**: spec.md y plan.md aprobados (commits dc16a57 y b567dc9).

**Organization**: una tarea por vez, con su propio commit en Conventional Commits en español
(AGENTS.md). Cada tarea informa qué se ejecutó y su resultado antes de pedir el commit.

**Tests**: obligatorios. El cambio de contrato (T002) sigue TDD con el fallo observado antes de
implementar. En las tareas de UI, la prueba de comportamiento se escribe junto con la pantalla y
se verifica en el emulador.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede hacerse en paralelo (otros archivos, sin dependencias).
- **[Story]**: historia de la spec (US1 a US6).

## Phase 1: Setup

- [ ] T001 Incorporar las fuentes Fredoka y Nunito en `app/src/main/res/font/`, junto con el
  texto de la licencia SIL OFL, descargados del repositorio oficial de Google Fonts con
  autorización previa del titular. Verificar con `assembleDebug` que los recursos compilan.

## Phase 2: Foundational (bloquea las historias)

- [ ] T002 [US5] Publicar `xpRequired` por atributo en `GET /progress` (FR-010). En TDD:
  - RED: una prueba de `:server:test` sobre `progressResponse` espera `xpRequired = 30 × nivel`
    en niveles 1, 6 y 49, y `null` en nivel 50. Otra prueba verifica que
    `RecordOperations.progressionResult` (recibos y proyección) no incluye el campo.
  - Implementar en `server/src/main/kotlin/lvluplife/api/ProgressRoutes.kt` usando
    `ProgressionThresholds.attribute`.
  - Cubrir el endpoint real en `:server:integrationTest`.
  - Documentar el campo en `specs/001-lvl-up-life-mvp/contracts/api.md`.
- [ ] T003 [US5] Leer y validar `xpRequired` en la app. En TDD:
  - RED: `ProgressRepositoryTest` (`app/src/test`) espera `RecordAttributeView.xpRequired` y el
    rechazo `UNREADABLE_RESPONSE` cuando falta, cuando es menor o igual que `xp`, cuando es
    negativo, o cuando no es `null` en nivel 50.
  - Implementar en `RecordFlowPorts.kt` y `ProgressRepository.kt`, y actualizar los
    constructores sintéticos de las pruebas existentes.
- [ ] T004 [US1] Tema «Arcade vivo» en `app/src/main/kotlin/lvluplife/ui/theme/Theme.kt` y el
  nuevo `AttributeColors.kt`:
  - paleta de FR-001 y tipografías de FR-002;
  - formas redondeadas;
  - siempre modo claro (FR-004).
  Actualizar `AppearanceContrastTest` para verificar, con la paleta nueva, al menos 4,5:1 en
  todos los pares texto/fondo y control/fondo, incluidos los colores de texto por atributo
  (FR-003). Agregar una prueba con el sistema en modo oscuro que verifique que se muestra el
  esquema claro.

## Phase 3: US2 — Navegación por pestañas (P1)

- [ ] T005 [US2] Agregar `SessionRoute.ATTRIBUTES` e `INFO` y `SessionController.selectTab`
  (reemplaza la pestaña raíz; `back()` cierra lo secundario y después vuelve a PJ). Pruebas
  unitarias en `app/src/test` de selección, vuelta atrás y limpieza al cerrar sesión o cambiar
  de cuenta.
- [ ] T006 [US2] Crear `MainTabs.kt` con la barra inferior y los controladores compartidos por
  sesión (que hoy crea `RecordFlow`). Además:
  - quitar la cabecera, «Historial» y «Cerrar sesión» de `SessionHost`;
  - conectar en `AppNavigation`.
  Prueba Android: cada pestaña se abre con un toque, la activa se anuncia como seleccionada y
  tiene un rasgo no cromático, y cambiar de pestaña no vuelve a pedir el progreso.
- [ ] T007 [US2] Migrar los selectores de las pruebas Android existentes a la navegación nueva,
  sin cambiar lo que verifican (SC-004). Correr `connectedDebugAndroidTest` completo.

## Phase 4: US3 — PJ (P1)

- [ ] T008 [US3] Rediseñar `HomeScreen.kt` como PJ:
  - saludo con el nombre de usuario e imagen grande recortada;
  - clase, etapa y nivel general;
  - mosaico de 6 atributos con barra chica;
  - «+ Registrar actividad» y «Actualizar progreso»;
  - avisos de carga, error y desactualizado de la feature 001.
  Prueba Android con progreso sintético, incluidos Novato y un nombre de usuario largo.

## Phase 5: US4 — Registrar y crear personalizadas (P2)

- [ ] T009 [US4] Abrir Registrar como pantalla secundaria desde PJ con el estilo nuevo
  (`RecordScreen.kt`, `ActivityPicker.kt`, `QuickRecordActions.kt`):
  - fechas Hoy, Ayer y Anteayer;
  - atributo de cada actividad;
  - categoría, modalidad y atributos de la elegida (AC-06).
  Prueba Android de RNF-04: una actividad guardada se registra en tres interacciones o menos.
- [ ] T010 [US4] Agregar «+ Crear actividad personalizada» al final de la lista y, al guardarla,
  volver al registro con esa actividad seleccionada por el id confirmado y la fecha conservada
  (FR-008). Prueba Android del flujo y de un rechazo (CON, AC-69).

## Phase 6: US5 — Atributos (P2)

- [ ] T011 [US5] Crear `AttributesScreen.kt`:
  - una tarjeta por atributo con sigla, nombre y nivel;
  - barra con «X / Y XP al siguiente nivel», usando `xpRequired`;
  - en nivel 50, «Nivel máximo» sin barra.
  Prueba Android con valores sintéticos, incluido el nivel 50.

## Phase 7: US6 — Info (P3)

- [ ] T012 [US6] Crear `InfoScreen.kt` con:
  - tarjetas de reglas de XP y niveles, con textos verificados contra el PRD (FR-012);
  - acceso al catálogo de clases con el estilo nuevo, que vuelve a Info;
  - reinicio de la progresión con confirmación (RF-43);
  - cierre de sesión.
  Prueba Android de cada acceso.

## Phase 8: Historial y cierre

- [ ] T013 Aplicar el estilo nuevo a `HistoryScreen.kt` y `HistoryFlow.kt` sin cambiar su
  comportamiento (FR-013), y verificar con las pruebas existentes de historial.
- [ ] T014 Validación final:
  - correr la suite completa con emuladores de Firebase: `assembleDebug`, `:domain:test`,
    `:server:test`, `testDebugUnitTest`, `:server:integrationTest` y
    `connectedDebugAndroidTest`;
  - capturar PJ, Atributos, Info, Registrar e Historial en
    `specs/002-identidad-visual/visual/`;
  - registrar resultados en `specs/002-identidad-visual/validation.md`;
  - pedir la aprobación del titular contra la maqueta (SC-005).

## Dependencies

- T001 antes de T004. T002 antes de T003, y T003 antes de T011.
- T004 antes de todas las tareas de UI (T006 a T013).
- T005 → T006 → T007; las fases 4 a 8 empiezan después de T007.
- T009 antes de T010.
- T008, T011, T012 y T013 son independientes entre sí una vez cerrada T007, pero se ejecutan de
  a una por la regla de AGENTS.md.

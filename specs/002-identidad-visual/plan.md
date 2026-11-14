# Implementation Plan: Identidad visual y navegación de Lvl Up Life

**Branch**: `main` (un commit por tarea) | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)

**Input**: Especificación de `specs/002-identidad-visual/spec.md`

## Summary

La feature cambia la presentación y la navegación de la app Android:

- tema «Arcade vivo» en modo claro, con tipografías Fredoka y Nunito;
- cuatro pestañas fijas: PJ, Atributos, Info e Historial;
- PJ con el personaje recortado como protagonista;
- registro con creación de actividades personalizadas;
- barra de progreso por atributo.

El único cambio de backend es publicar en `GET /progress` la XP necesaria para el siguiente
nivel de cada atributo (`xpRequired`). Ese valor ya lo define el dominio en
`ProgressionThresholds.attribute`. Las reglas de XP, clases, topes, recálculo, reinicio y
autorización no cambian.

## Technical Context

**Language/Version**: Kotlin 2.4.20, JDK 17 (toolchain del proyecto)

**Primary Dependencies**: Jetpack Compose Material 3 (app), Ktor + Firebase Admin (server). No se
agregan dependencias nuevas; las fuentes se incluyen como recursos `res/font`.

**Storage**: sin cambios. `xpRequired` no se persiste: se deriva al responder.

**Testing**:
- `:domain:test` y `:server:test` para el contrato de progreso;
- `:server:integrationTest` con emuladores de Firebase para el endpoint;
- `testDebugUnitTest` para el parseo y la validación en la app;
- `connectedDebugAndroidTest` para pantallas, navegación, contraste y accesibilidad.

**Target Platform**: Android 10 (API 29) o superior; emulador de referencia Pixel 6a API 30.

**Project Type**: app móvil más API (`app/`, `server/`, `domain/`)

**Performance Goals**: se mantienen RNF-01 (PJ interactiva en 3 s o menos) y RNF-04 (como
máximo tres interacciones para registrar una actividad guardada). Cambiar de pestaña no debe
volver a pedir el progreso al servidor.

**Constraints**: contraste de al menos 4,5:1 y ninguna información solo por color (RNF-12);
siempre modo claro; sin fórmulas de progresión en la app (constitución, principio II).

**Scale/Scope**: unas 12 pantallas o composables de `app/src/main/kotlin/lvluplife/ui`, un
endpoint, y un campo nuevo del contrato.

## Constitution Check

| Principio | Cómo se cumple |
|---|---|
| I. Arquitectura modular | La UI sigue leyendo los controladores de `application`; las pestañas no acceden a Firestore ni calculan progresión. |
| II. Reglas centralizadas | `xpRequired` sale de `ProgressionThresholds.attribute` en el servidor. La app solo valida coherencia (`xp < xpRequired`) y muestra el valor. |
| III. Seguridad | `GET /progress` mantiene la autenticación y la restricción por UID. Cerrar sesión limpia las pestañas igual que hoy. |
| IV. Test-First | El campo nuevo se agrega con TDD: prueba de contrato del servidor y de parseo en la app, con fallo observado antes de implementar. Las reglas de progresión no cambian. |
| V. Simplicidad | Sin librerías nuevas; el contenedor de pestañas reemplaza el `if` actual de rutas, sin framework de navegación. |
| VI. Persistencia | Sin cambios: `xpRequired` no se guarda en recibos ni en proyecciones, así no altera la idempotencia de operaciones ya registradas. |
| VII. Validación y errores | La app rechaza una respuesta sin `xpRequired` coherente con `UNREADABLE_RESPONSE`, como hace hoy con los demás campos. Se mantienen los avisos de carga, error y datos desactualizados. |
| VIII. Guiado por spec | Todo se deriva de la spec 002. FR-014 prohíbe cambios de reglas de producto. |

Resultado: sin violaciones, así que no hace falta la tabla de Complexity Tracking.

## Diseño

### 1. Contrato de progreso (`xpRequired`)

- `server/.../api/ProgressRoutes.kt`: `progressResponse` agrega a cada atributo
  `xpRequired = ProgressionThresholds.attribute(level)`: un entero, o `null` en nivel 50.
  `RecordOperations.progressionResult` no cambia, porque también se usa en recibos y en la
  proyección persistida.
- `specs/001-lvl-up-life-mvp/contracts/api.md`: documentar el campo en `GET /progress`.
- App:
  - `RecordAttributeView` suma `xpRequired: Int?`.
  - `ProgressRepository` exige `xpRequired > xp` si `level < 50`, y `xpRequired == null` si
    `level == 50`.
  - Cualquier otra combinación se rechaza como `UNREADABLE_RESPONSE`.

### 2. Tema (`ui/theme`)

- `Theme.kt`:
  - `lightColorScheme` con la paleta de FR-001; `LvlUpTheme` ignora `isSystemInDarkTheme`
    (FR-004);
  - `Typography` con Fredoka (títulos, números, botones) y Nunito (texto);
  - formas redondeadas: 16 dp en tarjetas y 14 dp en botones.
- Nuevo `AttributeColors.kt`: color de relleno y de texto por sigla. Son colores de
  presentación, sin reglas.
- `res/font/`: archivos TTF de Fredoka y Nunito (licencia SIL OFL, con el texto de la licencia
  incluido en el repo).
- Se mantienen `ActionButton`, `ChoiceChip` e `InputField` como puntos únicos de estilo; se
  agrega `PrimaryActionButton` para «+ Registrar actividad».

### 3. Navegación por pestañas

- `SessionRoute` suma `ATTRIBUTES` e `INFO`. `HOME` pasa a ser PJ.
- `SessionController` suma `selectTab(route)`, que reemplaza la pestaña raíz en lugar de apilarla.
  `back()` cierra primero las pantallas secundarias y después vuelve a PJ.
- Nuevo `MainTabs.kt`:
  - crea una vez, por sesión, los controladores hoy creados en `RecordFlow` (progreso, registro,
    personalizadas, reinicio y catálogo), para que PJ y Atributos compartan el mismo progreso
    confirmado;
  - dibuja la barra inferior con las cuatro pestañas;
  - la pestaña activa tiene fondo acento, texto en negrita y semántica `selected`.
- `SessionHost` deja de dibujar los botones «Historial» y «Cerrar sesión» y la cabecera. Conserva
  los avisos de carga y de pendientes, y la limpieza por cambio de sesión.

### 4. Pantallas

| Pestaña o pantalla | Archivo | Cambios |
|---|---|---|
| PJ | `HomeScreen.kt` | Saludo con el nombre, imagen grande, clase y etapa, nivel general, mosaico de 6 atributos con barra chica, «+ Registrar actividad» y «Actualizar progreso». |
| Atributos | nuevo `AttributesScreen.kt` | Una tarjeta por atributo: sigla y nombre en su color, «LVL N», barra y «X / Y XP al siguiente nivel»; en nivel 50, «Nivel máximo». |
| Info | nuevo `InfoScreen.kt` | Tarjetas de reglas (texto fijo alineado al PRD), acceso a `ClassCatalogScreen`, `ResetScreen` y «Cerrar sesión». |
| Registrar | `RecordScreen.kt`, `ActivityPicker.kt`, `QuickRecordActions.kt` | Pantalla secundaria abierta desde PJ; fechas Hoy, Ayer y Anteayer como segmentos; atributo de cada actividad; «+ Crear actividad personalizada» al final. |
| Crear personalizada | `CustomActivityScreen.kt` | Se abre desde Registrar; al guardar vuelve con la actividad seleccionada. |
| Catálogo de clases | `ClassCatalogScreen.kt` | Estilo nuevo; se abre desde Info y vuelve a Info. |
| Historial | `HistoryScreen.kt`, `HistoryFlow.kt` | Solo estilo; mismo comportamiento. |

La selección automática tras crear una actividad personalizada usa el id que devuelve la
creación confirmada. La app no la infiere por el nombre.

### 5. Pruebas afectadas

- Las pruebas Android que buscan «Historial», «Volver», «Cerrar sesión», «Clases» o «Volver a
  principal» como botones de cabecera se actualizan a las pestañas o a Info. Lo que verifican no
  cambia (SC-004).
- `AppearanceContrastTest` pasa a verificar los pares de la paleta nueva, con al menos 4,5:1.
- Pruebas nuevas:
  - navegación entre pestañas y estado seleccionado;
  - PJ, Atributos e Info con datos sintéticos;
  - crear una personalizada desde Registrar y volver con ella seleccionada;
  - RNF-04 en tres interacciones;
  - modo oscuro del sistema mostrado en claro.

## Project Structure

### Documentation (this feature)

```text
specs/002-identidad-visual/
├── spec.md
├── plan.md      # este archivo
└── tasks.md     # siguiente paso
```

No se crean `research.md`, `data-model.md`, `quickstart.md` ni `contracts/` propios: las decisiones
están en este plan, el modelo no cambia y el único cambio de contrato se documenta en
`specs/001-lvl-up-life-mvp/contracts/api.md`. La guía de ejecución sigue siendo la de la
feature 001.

### Source Code (repository root)

```text
server/src/main/kotlin/lvluplife/api/ProgressRoutes.kt          # xpRequired
server/src/test/... y server/src/integrationTest/...            # contrato de progreso
app/src/main/kotlin/lvluplife/application/
├── RecordFlowPorts.kt          # RecordAttributeView.xpRequired
└── SessionController.kt        # ATTRIBUTES, INFO, selectTab
app/src/main/kotlin/lvluplife/data/ProgressRepository.kt         # parseo y validación
app/src/main/kotlin/lvluplife/ui/
├── theme/Theme.kt, theme/AttributeColors.kt
├── MainTabs.kt (nuevo), AttributesScreen.kt (nuevo), InfoScreen.kt (nuevo)
├── SessionHost.kt, AppNavigation.kt, RecordFlow.kt (se reparte en MainTabs)
└── HomeScreen.kt, RecordScreen.kt, ActivityPicker.kt, QuickRecordActions.kt,
    CustomActivityScreen.kt, ClassCatalogScreen.kt, ResetScreen.kt, HistoryScreen.kt
app/src/main/res/font/            # Fredoka y Nunito + licencia OFL
app/src/test/... y app/src/androidTest/...
```

**Structure Decision**: se mantiene la estructura de la feature 001 (`domain/`, `server/`, `app/`).
No se crean módulos nuevos.

## Riesgos

- **Muchas pruebas Android dependen de los botones de cabecera actuales.** Mitigación: una tarea
  dedicada a migrar los selectores, que corre la suite completa antes de seguir.
- **Descarga de fuentes.** Se bajan los TTF oficiales del repositorio de Google Fonts, previa
  autorización del titular para la descarga.
- **Tamaño del APK.** Las 76 imágenes pesan unos 200 MB. Está fuera del alcance de esta feature,
  pero queda anotado para una optimización futura (redimensionar o pasar a WebP).

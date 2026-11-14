# Feature Specification: Identidad visual y navegación de Lvl Up Life

**Feature Branch**: `main` (sin rama dedicada; un commit por tarea)

**Created**: 2026-11-14

**Status**: Draft

**Input**: User description: "La parte visual no es de mi agrado. Quiero que la app se sienta como un juego, en modo claro, con el personaje sin fondo como protagonista, los atributos debajo y un menú inferior tipo juego de celular con PJ, Atributos, Info e Historial."

## Contexto

La feature 001 entregó el MVP funcional con pantallas Material por defecto. Esta feature
define la identidad visual y la navegación de la app sin cambiar las reglas de producto del
PRD. Decisiones del titular, tomadas el 2026-11-14:

- Paleta «Arcade vivo» en modo claro y tipografía de juego.
- Cuatro pestañas fijas abajo: PJ, Atributos, Info e Historial.
- «+ Registrar actividad» como acción principal en PJ.
- Crear una actividad personalizada se hace desde el flujo de registro.
- Info reúne las reglas, el catálogo de clases (todas visibles) y el reinicio de la progresión.
- Las 76 imágenes de clases se muestran recortadas, sin fondo (ya aplicado en el commit 104bbfe).

Referencia visual aprobada: `design/maqueta/arcade.html`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tema visual «Arcade vivo» (Priority: P1)

Al abrir la app, el usuario ve una interfaz clara, colorida y con aire de juego: fondo
celeste muy claro, tarjetas blancas redondeadas, botón principal azul, acento amarillo,
títulos en una tipografía redondeada y un color propio para cada atributo.

**Why this priority**: Es la base sobre la que se construyen todas las demás historias.
Sin el tema, cada pantalla seguiría viéndose como la versión actual.

**Independent Test**: Abrir cualquier pantalla existente y comprobar que usa la paleta, la
tipografía y las formas definidas, con contraste suficiente en todos los textos y controles.

**Acceptance Scenarios**:

1. **Given** la app abierta con sesión válida, **When** se muestra cualquier pantalla,
   **Then** los colores de fondo, superficie, texto, acción principal y acento son los de la
   paleta «Arcade vivo» definida en FR-001.
2. **Given** cualquier texto o control visible, **When** se mide su contraste contra su
   fondo, **Then** la relación es de al menos 4,5:1 (RNF-12).
3. **Given** un atributo mostrado con su color, **When** se lo observa sin percibir color,
   **Then** se lo identifica igual por su sigla escrita (STR, DEX, CON, INT, WIT, MEN).
4. **Given** el sistema en modo oscuro, **When** se abre la app, **Then** se muestra igual
   en modo claro.

---

### User Story 2 - Navegación por pestañas (Priority: P1)

El usuario cambia de sección con una barra fija abajo de cuatro botones grandes: PJ,
Atributos, Info e Historial. La pestaña activa se distingue claramente.

**Why this priority**: Resuelve la principal queja sobre la app actual: es incómodo moverse
entre secciones.

**Independent Test**: Con sesión válida, tocar cada pestaña y comprobar que se muestra su
sección y que la pestaña activa queda marcada.

**Acceptance Scenarios**:

1. **Given** la sesión iniciada, **When** termina de cargar la app, **Then** se muestra PJ con
   la barra de pestañas visible.
2. **Given** cualquier pestaña activa, **When** el usuario toca otra, **Then** se muestra esa
   sección con una sola interacción.
3. **Given** la pestaña activa, **When** se la observa, **Then** se distingue por color y
   además por otro rasgo no cromático (peso del texto, ícono o indicador), y los servicios de
   accesibilidad la anuncian como seleccionada.
4. **Given** una pantalla secundaria abierta (registro, catálogo de clases, crear actividad),
   **When** el usuario vuelve, **Then** regresa a la pestaña desde la que la abrió.
5. **Given** que el usuario cierra sesión o cambia de cuenta, **When** se muestra la pantalla de
   inicio de sesión, **Then** no queda ninguna pestaña ni dato de la cuenta anterior (contrato UI
   de la feature 001).

---

### User Story 3 - PJ: el personaje como protagonista (Priority: P1)

En PJ, el usuario ve su personaje grande y sin fondo, su clase y etapa, su nivel general, un
resumen de los seis atributos y el botón «+ Registrar actividad».

**Why this priority**: Es la pantalla que se abre siempre y la que transmite la sensación de
juego.

**Independent Test**: Con un progreso confirmado de ejemplo, abrir PJ y comprobar que muestra
la imagen de la clase y etapa vigentes, el nivel general, los seis atributos y el botón.

**Acceptance Scenarios**:

1. **Given** un progreso confirmado, **When** se abre PJ, **Then** se muestran el saludo con el
   nombre de usuario, la imagen recortada de la clase y etapa vigentes (RF-37), el nombre de la
   clase, la etapa, el nivel general y los seis atributos con su nivel.
2. **Given** PJ abierta, **When** el usuario toca «+ Registrar actividad», **Then** se abre el
   flujo de registro.
3. **Given** un progreso desactualizado o un error de carga, **When** se abre PJ, **Then** se
   muestran los mismos avisos y la acción para actualizar que exige el contrato UI de la
   feature 001.

---

### User Story 4 - Registrar y crear actividades personalizadas (Priority: P2)

Desde «+ Registrar actividad», el usuario elige la fecha (Hoy, Ayer, Anteayer), la actividad
y la duración. Al final de la lista aparece «+ Crear actividad personalizada»: al guardarla,
vuelve al registro con esa actividad ya elegida.

**Why this priority**: Mantiene el flujo principal de uso con el nuevo diseño y une la creación
de actividades al momento en que se necesitan.

**Independent Test**: Desde PJ, registrar una actividad guardada y luego crear una
personalizada desde el registro y comprobar que queda seleccionada.

**Acceptance Scenarios**:

1. **Given** una actividad guardada, **When** el usuario la registra desde PJ, **Then** lo logra
   con un máximo de tres interacciones (RNF-04).
2. **Given** el registro abierto, **When** se elige una actividad, **Then** se muestran su
   categoría, modalidad y atributos (AC-06).
3. **Given** el registro abierto, **When** el usuario toca «+ Crear actividad personalizada» y
   guarda una válida, **Then** vuelve al registro con esa actividad seleccionada y sin perder la
   fecha elegida.
4. **Given** una actividad personalizada inválida (por ejemplo, con CON), **When** se intenta
   guardar, **Then** se rechaza con las mismas reglas y mensajes de la feature 001 (AC-69, AC-74,
   AC-76).

---

### User Story 5 - Atributos con progreso visible (Priority: P2)

En Atributos, el usuario ve una tarjeta por atributo con su sigla, su nombre, su nivel en grande
y una barra de progreso.

**Why this priority**: Mostrar el avance es lo que motiva a seguir registrando.

**Independent Test**: Con un progreso confirmado de ejemplo, abrir Atributos y comprobar las
seis tarjetas con los valores recibidos del servidor.

**Acceptance Scenarios**:

1. **Given** un progreso confirmado, **When** se abre Atributos, **Then** se muestran seis
   tarjetas, una por atributo, con sigla, nombre, nivel y XP.
2. **Given** un atributo, **When** se muestra su barra, **Then** el avance se acompaña de su
   valor en texto (por ejemplo «95 / 180 XP al siguiente nivel»), con la XP necesaria publicada
   por el servidor y sin depender solo del color o del largo de la barra.
3. **Given** un atributo en nivel 50, **When** se muestra su tarjeta, **Then** indica el nivel
   máximo en lugar de una barra de avance (RF-24, RF-25).

---

### User Story 6 - Info: reglas, clases y personaje (Priority: P3)

En Info, el usuario encuentra cómo se gana XP, cómo suben los niveles, el catálogo de las 26
clases y la opción de reiniciar la progresión.

**Why this priority**: Es contenido de consulta. Hoy ya existe, aunque disperso.

**Independent Test**: Abrir Info, abrir el catálogo de clases y abrir el reinicio sin
confirmarlo.

**Acceptance Scenarios**:

1. **Given** Info abierta, **When** el usuario la recorre, **Then** ve las reglas de XP y niveles
   en tarjetas agrupadas por tema.
2. **Given** Info abierta, **When** el usuario abre el catálogo, **Then** ve las 26 clases con sus
   condiciones y sus 76 imágenes recortadas, marcadas como desbloqueadas o bloqueadas según su
   nivel general (AC-61, RF-40).
3. **Given** Info abierta, **When** el usuario elige reiniciar la progresión, **Then** se pide
   confirmación explícita y se conservan cuenta, nombre de usuario, actividades personalizadas e
   historial (RF-43).
4. **Given** Info abierta, **When** el usuario elige cerrar sesión, **Then** se cierra la sesión
   igual que en la feature 001.

---

### Edge Cases

- Pantallas angostas (360 dp) o con fuente del sistema agrandada: la barra de pestañas y los
  botones no se cortan ni se superponen, y las tarjetas crecen hacia abajo.
- Sin conexión o con progreso desactualizado: se mantienen los avisos de la feature 001 dentro de
  cada pestaña, sin datos inventados.
- Novato: la imagen y el catálogo muestran solo Etapa I.
- Nombre de usuario largo: el saludo se recorta con puntos suspensivos sin desplazar la imagen.
- Imagen de clase desconocida: se mantiene el comportamiento de la feature 001 (catálogo completo
  fallido, sin imágenes sustitutas).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La app DEBE usar la paleta «Arcade vivo» en modo claro:
  - fondo #EAF1FC, superficie #FFFFFF, texto #14213D y texto secundario #5A6785;
  - acción principal #2F5BD3 con texto #FFFFFF, y acento #FFC531 con texto #14213D;
  - pistas de barras #D6E0F3;
  - por atributo, un color de relleno (STR #E5484D, DEX #12A594, CON #E38B0B, INT #7C5CE0,
    WIT #2F7DE1, MEN #D6409F) y un color de texto (STR #C62A2F, DEX #0B7F72, CON #A85E00,
    INT #6346C8, WIT #1F63BE, MEN #B02A80).
  Los valores pueden ajustarse solo para cumplir FR-003.
- **FR-002**: La app DEBE usar Fredoka para títulos, números destacados y botones, y Nunito para
  el texto, ambas incluidas en la app sin descarga en tiempo de ejecución.
- **FR-003**: Todo texto y control DEBE tener un contraste de al menos 4,5:1 con su fondo, y
  ninguna información esencial DEBE comunicarse solo con color (RNF-12).
- **FR-004**: La app DEBE mostrarse siempre en modo claro, aunque el sistema esté en modo oscuro.
- **FR-005**: Con sesión válida, la app DEBE mostrar una barra inferior fija con las pestañas PJ,
  Atributos, Info e Historial, y abrir PJ al iniciar.
- **FR-006**: La pestaña activa DEBE distinguirse por color y por un rasgo no cromático, y
  exponerse como seleccionada a los servicios de accesibilidad.
- **FR-007**: PJ DEBE mostrar:
  - el saludo con el nombre de usuario;
  - la imagen recortada de la clase y etapa vigentes;
  - la clase, la etapa y el nivel general;
  - un resumen de los seis atributos;
  - la acción «+ Registrar actividad»;
  - la acción para actualizar el progreso.
- **FR-008**: El flujo de registro DEBE ofrecer, al final de la lista de actividades, «+ Crear
  actividad personalizada». Al guardar una actividad válida, DEBE volver al registro con ella
  seleccionada.
- **FR-009**: Atributos DEBE mostrar una tarjeta por atributo con sigla, nombre, nivel y XP.
- **FR-010**: Cada atributo DEBE mostrar una barra de progreso hacia su siguiente nivel con el
  texto «X / Y XP al siguiente nivel». Y (la XP necesaria para el siguiente nivel) DEBE venir
  publicada por el servidor junto al nivel y la XP; la app NO DEBE calcularla (constitución,
  principio II). Para eso se amplía el contrato de progreso con ese único dato por atributo.
  No se muestran barras de nivel general ni de tope diario.
- **FR-011**: Info DEBE agrupar en tarjetas las reglas de XP y niveles, el acceso al catálogo
  completo de clases, el reinicio de la progresión y el cierre de sesión.
- **FR-012**: Los textos de reglas en Info DEBEN coincidir con el PRD vigente y no con versiones
  anteriores de la app.
- **FR-013**: Historial DEBE conservar su comportamiento actual (filtros, edición y eliminación
  dentro de la ventana permitida) con el nuevo estilo.
- **FR-014**: Esta feature NO DEBE cambiar reglas de XP, niveles, clases, topes, recálculo,
  inactividad, reinicio ni autorización.

### Key Entities

- **Tema visual**: paleta, tipografías, formas y colores por atributo usados por todas las
  pantallas.
- **Pestaña**: sección principal de la navegación (PJ, Atributos, Info, Historial) con su
  estado de selección.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los pares texto/fondo y control/fondo verificados en las pantallas de
  esta feature alcanza al menos 4,5:1.
- **SC-002**: Desde cualquier pestaña se llega a cualquier otra con una interacción.
- **SC-003**: Una actividad guardada se registra desde PJ con un máximo de tres interacciones.
- **SC-004**: Todas las pruebas existentes de comportamiento de la feature 001 siguen pasando o
  se actualizan solo en sus selectores visuales, sin cambiar lo que verifican.
- **SC-005**: El titular aprueba las capturas finales de PJ, Atributos, Info, Registrar e
  Historial contra la maqueta `design/maqueta/arcade.html`.

## Assumptions

- Las fuentes Fredoka y Nunito tienen licencia SIL Open Font License y pueden incluirse en la
  app.
- Se reutilizan los controladores, puertos y datos actuales; esta feature cambia la
  presentación y la navegación, no la lógica de negocio. La única ampliación es FR-010: el
  servidor publica, por atributo, la XP necesaria para el siguiente nivel, que el dominio ya
  calcula (RF-23).
- No se agrega modo oscuro en esta feature.
- Los nombres de los atributos en español (Fuerza, Destreza, Constitución, Intelecto, Ingenio,
  Descanso) son textos de presentación y no cambian las siglas del dominio.

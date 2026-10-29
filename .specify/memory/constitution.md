# Lvl Up Life Constitution

## Core Principles

### I. Arquitectura modular

El código DEBE separar interfaz, lógica de negocio y persistencia mediante límites
explícitos de responsabilidad. La interfaz NO DEBE calcular la progresión ni acceder
directamente a Firestore. La lógica de negocio DEBE poder probarse sin Compose,
Android ni Firebase; la persistencia DEBE conectarse mediante contratos que permitan
sustituirla en pruebas. La división en módulos físicos DEBE justificarse por necesidades
concretas, sin exigir un módulo Gradle por cada responsabilidad.

### II. Reglas centralizadas y deterministas

Las reglas de XP, niveles, atributos y clases DEBEN tener una única implementación
canónica de dominio, sin fórmulas duplicadas en pantallas o adaptadores de persistencia.
Con las mismas entradas y el mismo contexto temporal explícito, el cálculo DEBE
producir el mismo resultado. El reloj y las dependencias externas NO DEBEN consultarse
implícitamente dentro del cálculo; sus valores DEBEN proporcionarse como entradas.
Los topes diarios y las reglas de progresión DEBEN respetar el PRD. El usuario NO DEBE
poder asignar XP manualmente ni superar los límites diarios por atributo.

### III. Seguridad y privacidad desde el diseño

Las credenciales privadas, claves de servicio y tokens NO DEBEN incluirse en el código,
versionarse ni exponerse en logs. Cada operación DEBE validar la identidad y restringir
el acceso a los datos del usuario autenticado mediante controles del servidor.
Ocultar datos en la interfaz NO constituye autorización. Las pruebas DEBEN verificar
que una cuenta no pueda leer ni modificar atributos, actividades o historial de otra.
Los errores y diagnósticos NO DEBEN revelar secretos ni datos de otras cuentas.

### IV. Test-First para la progresión crítica

Todo cambio en la lógica crítica de XP, niveles, atributos, clases, topes, recálculo,
inactividad o reinicio DEBE seguir el ciclo TDD: escribir primero una prueba que exprese
el comportamiento requerido, ejecutarla y comprobar que falla por la ausencia de ese
comportamiento, implementar lo mínimo para que pase y refactorizar manteniéndola verde.
Las pruebas DEBEN incluir límites y casos relevantes del PRD, controlar el tiempo cuando
corresponda y ejecutarse sin depender de servicios reales para el cálculo de dominio.
Una prueba añadida después de implementar NO acredita el cumplimiento de Test-First.

### V. Simplicidad y mantenibilidad

Cada componente DEBE tener una responsabilidad clara y nombres que expresen su propósito.
Las abstracciones, dependencias y capas adicionales DEBEN responder a una necesidad
actual del alcance aprobado o a una separación exigida por estos principios.
NO DEBEN incorporarse mecanismos para funcionalidades hipotéticas. Los cambios DEBEN
mantener el código legible, eliminar duplicación de reglas y documentar decisiones
cuyo motivo no resulte evidente en la implementación.

### VI. Persistencia consistente

Guardar un registro y aplicar su efecto en la progresión DEBE preservar la consistencia
ante errores, reintentos y operaciones concurrentes. La estrategia de persistencia
DEBE definir atomicidad e idempotencia para que repetir una misma operación no duplique
registros ni XP. Un error NO DEBE dejar un registro confirmado con un efecto parcial
o duplicado en el progreso. Las pruebas de integración DEBEN cubrir los reintentos,
fallos y concurrencia relevantes para la operación modificada.

### VII. Validación, errores y pruebas automatizadas

Las entradas DEBEN validarse en los límites de confianza y en las reglas de dominio
aplicables; la validación visual NO sustituye la validación autoritativa.
Los errores previstos DEBEN manejarse explícitamente y mostrar al usuario un resultado
comprensible, sin presentar una operación fallida como exitosa ni descartar fallos
silenciosamente. Los cambios funcionales DEBEN incluir pruebas automatizadas de sus
reglas, entradas inválidas y errores relevantes. La cobertura DEBE verificar
comportamientos observables y riesgos concretos, sin duplicar la implementación.

### VIII. Desarrollo guiado por especificaciones

Toda implementación DEBE corresponder a una especificación con alcance y criterios de
aceptación definidos, coherente con `PRD.md`. El plan y las tareas DEBEN derivarse de
esa especificación y comprobar estos principios antes de implementar.
NO DEBEN implementarse funcionalidades fuera del alcance definido; una ampliación
requiere aprobación del responsable del producto y actualización de la especificación.
Los conflictos entre documentos DEBEN explicitarse y resolverse antes de implementar
la parte afectada, sin inventar requisitos para completar vacíos.

## Restricciones del proyecto

- Android nativo con Kotlin 2.4.20 y Jetpack Compose; mínimo Android 10 (API 29).
- Cloud Firestore para persistencia y Firebase Authentication con Google.
- Las notificaciones locales del alcance aprobado utilizan las APIs de Android.
- `PRD.md` define reglas y alcance del producto; `AGENTS.md` contiene instrucciones
  operativas y restricciones del repositorio.
- Reiniciar la progresión DEBE exigir confirmación explícita y conservar cuenta,
  nombre de usuario, actividades personalizadas e historial.
- Las reglas temporales DEBEN utilizar la zona de negocio UTC−3 definida en el PRD.

## Flujo de desarrollo y calidad

Antes de implementar, la especificación, el plan y las tareas DEBEN identificar las
reglas afectadas, criterios de aceptación y comprobaciones de esta constitución.
Las tareas de lógica crítica DEBEN ordenar la prueba y su fallo observado antes del
código de producción. La revisión DEBE comprobar separación de responsabilidades,
centralización de reglas, autorización, consistencia y manejo de errores aplicables.

Cuando exista el proyecto Android, las verificaciones pertinentes se ejecutan desde
la raíz con `.\gradlew.bat assembleDebug`, `.\gradlew.bat testDebugUnitTest` y,
para pruebas Android con dispositivo o emulador, `.\gradlew.bat connectedDebugAndroidTest`.
Los resultados DEBEN indicar qué se ejecutó, qué falló y qué no pudo verificarse.
La revisión documental NO DEBE presentarse como evidencia de pruebas ejecutadas.
Los estudios y muestras futuros orientativos del PRD mantienen su carácter no bloqueante
para el MVP; esto no exime del TDD ni de las pruebas automatizadas exigidas aquí.

## Governance

Esta constitución rige las decisiones de arquitectura, desarrollo y revisión del
proyecto. Los detalles de producto permanecen en el PRD y las especificaciones.
Los conflictos con instrucciones del repositorio DEBEN registrarse y elevarse al
responsable del producto antes de modificar la regla afectada.

Una enmienda DEBE documentar motivo, impacto y adaptación de artefactos afectados,
y contar con aprobación del responsable del producto. El versionado sigue
MAJOR.MINOR.PATCH: MAJOR para retirar o redefinir principios de forma incompatible;
MINOR para incorporar principios o ampliar obligaciones; PATCH para aclaraciones
sin cambios de significado. Toda enmienda DEBE actualizar la fecha de modificación.
Cada revisión DEBE verificar los principios aplicables; las desviaciones DEBEN
corregirse o resolverse mediante una enmienda aprobada antes de aceptar el cambio.

**Version**: 1.0.0 | **Ratified**: 2026-10-28 | **Last Amended**: 2026-10-28

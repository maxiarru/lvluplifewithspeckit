# Checklist de calidad de requisitos: integridad del MVP de Lvl Up Life

**Purpose**: Revisar claridad, completitud, consistencia y cobertura de los requisitos de progresión, persistencia, seguridad y manejo de errores.
**Created**: 2026-11-05
**Feature**: [Especificación del MVP](../spec.md)
**Profundidad y destinatario**: Estándar, para el autor y revisor de requisitos antes de implementar, con plan y tareas disponibles.
**Contexto**: Especificación con las aclaraciones aceptadas y constitución vigente; plan.md y tasks.md disponibles. Revisión documental completada el 2026-11-05: 36/36 aprobados con evidencia en validation.md; no acredita implementación ni pruebas ejecutadas.

**Note**: Checklist personalizado generado mediante `$speckit-checklist`.
**Review Ownership**: La evaluación pertenece al revisor. Marcar `[x]` solo cuando determine que el criterio de calidad de requisitos está satisfecho.
**Marker Semantics**: `[x]` significa aprobación de la calidad del requisito; no acredita implementación ni pruebas ejecutadas. Todos los ítems se generan pendientes.

## Completitud de requisitos

- [x] CHK001 ¿Están definidas las reglas de XP para duración simple, reparto entre atributos, sueño acumulado y comidas, incluidas sus excepciones y saldos? [Completitud, Spec §Reglas de XP, RF-17–RF-18] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK001.
- [x] CHK002 ¿Están documentados umbrales, sobrantes y máximos tanto de niveles de atributos como del nivel general? [Completitud, Spec §RF-23–RF-30] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK002.
- [x] CHK003 ¿Se especifican todos los datos que se reinician y todos los que se conservan al comenzar un ciclo, sin depender de supuestos del lector? [Completitud, Spec §RF-41–RF-43, Regla de recálculo de progresión] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK003.
- [x] CHK004 ¿Los requisitos describen la indivisibilidad entre cada cambio confirmado de un registro y su efecto en la progresión, también ante un fallo parcial? [Completitud, Gap, Spec §SC-012; Constitución §VI] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK004.
- [x] CHK005 ¿Se define el alcance de la autorización para cuentas, personajes, actividades personalizadas, registros e historial, tanto en lectura como en modificación? [Completitud, Spec §User Story 1, SC-007; Constitución §III] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK005.
- [x] CHK006 ¿Están definidos los resultados informados al usuario para entradas inválidas, falta de autorización, conflictos y fallos de servicios, sin confundir rechazo con resultado desconocido? [Completitud, Gap, Spec §RF-48–RF-50, Edge Cases; Constitución §VII] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK006.

## Claridad de requisitos

- [x] CHK007 ¿Son inequívocos el redondeo del reparto, el orden de aplicación de topes y la independencia del límite de cada atributo? [Claridad, Spec §Reglas de XP, RF-20–RF-21] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK007.
- [x] CHK008 ¿Se distingue explícitamente XP calculada, XP efectiva y progreso dentro del nivel para determinar actividad válida e inactividad? [Claridad, Spec §Inactividad por fecha y atributo, RF-25, RF-35] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK008.
- [x] CHK009 ¿Están precisados reloj autoritativo, zona UTC−3, fecha abierta o cerrada y límite exclusivo de 300 segundos para las operaciones temporales? [Claridad, Spec §Reglas de duración y fechas, Regla de recálculo de progresión, AC-103] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK009.
- [x] CHK010 ¿La identidad de una misma operación y su alcance entre dispositivos, reintentos y ciclos están definidos con precisión suficiente para distinguir un duplicado de dos registros legítimos? [Claridad, Ambiguity, Spec §RF-49–RF-50, SC-006, Edge Cases] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK010.
- [x] CHK011 ¿El estado desactualizado se define de forma que dos modificaciones basadas en el mismo estado tengan un resultado inequívoco, incluida la eliminación previa? [Claridad, Spec §RF-48, AC-110–AC-111, Edge Cases] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK011.

## Consistencia de requisitos

- [x] CHK012 ¿El recálculo conserva un orden determinista y es consistente con ediciones que mantienen identidad y creación y con registros eliminados excluidos? [Consistencia, Spec §Regla de recálculo de progresión, AC-93] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK012.
- [x] CHK013 ¿Los requisitos de cargas retroactivas, reversión de penalizaciones y comienzo de inactividad después de un reinicio son compatibles entre sí? [Consistencia, Spec §RF-31–RF-35, RF-44, AC-85, AC-102] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK013.
- [x] CHK014 ¿La conservación del historial y la prohibición de modificar ciclos anteriores son consistentes con el reinicio y los registros pendientes del ciclo cerrado? [Consistencia, Spec §RF-43, RF-46, RF-50, AC-114–AC-115] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK014.
- [x] CHK015 ¿La recuperación de operaciones pendientes respeta la exclusión del funcionamiento completo sin conexión y distingue esa recuperación de nuevas cargas sin conexión? [Consistencia, Spec §RF-49, Edge Cases, Assumptions, Fuera de alcance] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK015.
- [x] CHK016 ¿Las exigencias de validación autoritativa y rechazo de XP enviada por el cliente son consistentes con el cálculo automático y la prohibición de asignar XP manualmente? [Consistencia, Spec §RF-17, RF-19; Constitución §II, §VII] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK016.

## Calidad de criterios de aceptación

- [x] CHK017 ¿Los criterios de progresión incluyen resultados numéricos objetivos para redondeos, topes parciales, sobrantes y múltiples subidas? [Medibilidad, Spec §AC-25–AC-44, AC-78–AC-82, AC-89] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK017.
- [x] CHK018 ¿Los criterios de conflictos y confirmación pendiente distinguen aceptación, rechazo y resultado aún desconocido, y explicitan la ausencia de alteraciones indebidas? [Medibilidad, Spec §AC-110–AC-115] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK018.
- [x] CHK019 ¿Los objetivos de no duplicación, aislamiento entre cuentas y conservación de registros confirmados establecen alcance y resultados medibles? [Medibilidad, Spec §SC-006–SC-007, SC-012] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK019.
- [x] CHK020 ¿Está explícita la relación entre los objetivos orientativos de medición y las obligaciones funcionales, de autorización y de pruebas de la constitución? [Claridad, Spec §Success Criteria; Constitución §Flujo de desarrollo y calidad] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK020.

## Cobertura de escenarios

- [x] CHK021 ¿Los requisitos cubren el recorrido principal desde autenticación hasta registro, progreso e historial, y las alternativas de actividad personalizada y corrección? [Cobertura principal y alternativa, Spec §User Stories 1–4] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK021.
- [x] CHK022 ¿Se especifican las condiciones de consistencia para registros distintos simultáneos que compiten por XP diaria, minutos disponibles o un nombre de usuario? [Cobertura concurrente, Spec §AC-89–AC-90, AC-104] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK022.
- [x] CHK023 ¿Los requisitos de recuperación de resultado desconocido abarcan edición, eliminación y reinicio, o delimitan explícitamente por qué solo cubren la creación de registros? [Cobertura de recuperación, Gap, Spec §RF-14–RF-15, RF-41–RF-42, RF-49] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK023.
- [x] CHK024 ¿Está definido cómo se conserva o recupera la información de una operación pendiente si la aplicación se cierra antes de conocer su resultado? [Cobertura de recuperación, Gap, Spec §RF-49, SC-006, SC-012] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK024.
- [x] CHK025 ¿Se documenta el tratamiento de autenticación cancelada, sesión vencida y acceso denegado durante una operación, incluyendo el destino de cualquier resultado pendiente? [Cobertura de excepción, Gap, Spec §RF-01–RF-02, RF-49, SC-007] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK025.
- [x] CHK026 ¿Se especifica cómo distinguir datos confirmados, datos desactualizados y consultas fallidas de un historial realmente vacío? [Cobertura de excepción, Gap, Spec §RF-16, RF-44, SC-001, SC-003] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK026.

## Cobertura de límites

- [x] CHK027 ¿Están definidos los extremos de duración, minutos enteros, saldo de 1.440 minutos y cambios de fecha que liberan y consumen saldos diferentes? [Cobertura de límites, Spec §Reglas de duración y fechas, AC-96–AC-105] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK027.
- [x] CHK028 ¿Se cubren seis frente a siete fechas inactivas, XP insuficiente para la penalización y ausencia prolongada sin nuevas cargas? [Cobertura de límites, Spec §RF-31–RF-35, AC-83–AC-84, AC-106–AC-107] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK028.
- [x] CHK029 ¿Los requisitos precisan los extremos de nivel 50, prioridad de Novato, umbral de equilibrio sin redondeo y transiciones visuales 14/15 y 34/35? [Cobertura de límites, Spec §RF-24–RF-25, RF-36–RF-40, AC-82, AC-91–AC-92] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK029.
- [x] CHK030 ¿Está definido qué validaciones temporales prevalecen cuando una operación se envía antes de medianoche o del vencimiento de edición y se confirma después? [Cobertura de límites, Gap, Spec §RF-13–RF-15, Regla de recálculo de progresión, AC-103] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK030.

## Requisitos no funcionales de seguridad y fiabilidad

- [x] CHK031 ¿La protección de comunicaciones y credenciales abarca también mensajes de error y diagnósticos sin exposición de tokens, secretos ni datos ajenos? [Completitud de seguridad, Spec §Success Criteria, RNF-07; Constitución §III] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK031.
- [x] CHK032 ¿Está documentado el aislamiento de datos al cambiar de identidad o finalizar una sesión, incluyendo datos previamente consultados y operaciones pendientes? [Cobertura de privacidad, Gap, Spec §User Story 1, SC-007] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK032.
- [x] CHK033 ¿Las exigencias de integridad ante fallos y reintentos distinguen conservación de registros confirmados, ausencia de XP duplicada y consistencia del progreso reconstruido? [Claridad de fiabilidad, Spec §SC-006, SC-012; Constitución §VI] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK033.

## Dependencias y supuestos

- [x] CHK034 ¿Están documentadas las dependencias de identidad, persistencia y reloj del servidor, junto con los resultados esperados cuando no están disponibles? [Dependencias, Gap, Spec §Assumptions, RF-49, SC-005] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK034.
- [x] CHK035 ¿Se diferencia la confianza en la actividad declarada por el usuario de las validaciones obligatorias de identidad, entradas, fechas y topes? [Supuestos, Spec §Assumptions, Fuera de alcance, RF-19–RF-20, RF-47; Constitución §VII] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK035.

## Ambigüedades y conflictos entre fuentes

- [x] CHK036 ¿Las aclaraciones RF-48–RF-50 mantienen trazabilidad hacia sus decisiones y criterios, y queda claro cómo se resolvería cualquier contradicción con el PRD o la constitución? [Trazabilidad y consistencia, Spec §Clarifications, AC-110–AC-115; Constitución §VIII, Governance] — **Evaluación 2026-11-05:** APROBADO; [evidencia y justificación](../validation.md#revisión-documental-de-integridad--2026-11-05), fila CHK036.

## Notes

- Marcar `[x]` solo después de evaluar el criterio; dejar pendientes los ítems sin evaluar o que requieran aclaración o corrección.
- `[Gap]` y `[Ambiguity]` identifican aspectos a revisar; no constituyen por sí solos hallazgos confirmados ni agregan requisitos al alcance.
- Registrar evidencia, observaciones y enlaces junto al ítem durante la revisión.
- `$speckit-implement` lee el estado del checklist como condición de entrada y no modifica sus marcas.
- `checklists/requirements.md` tiene un ciclo separado, mantenido por `$speckit-specify` y `$speckit-clarify`.
- Este documento revisa la redacción de requisitos; no es un plan de pruebas de implementación.

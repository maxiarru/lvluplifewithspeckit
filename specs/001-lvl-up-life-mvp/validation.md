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

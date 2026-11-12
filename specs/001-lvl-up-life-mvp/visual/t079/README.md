# T079: contraste y flujo frecuente

Ejecución del 2026-11-12 en Pixel_6a_API_30 (`emulator-5554`), con APK debug y datos sintéticos privados de prueba. La matriz de tamaños/APIs de T080 todavía no se ejecutó.

## Contraste

[Resumen legible por máquina](summary.json), [190 mediciones](t079-contrast.jsonl), [paleta clara](light-overview.png) y [paleta oscura](dark-overview.png). Se conservan 69 capturas originales; las hojas de contacto solo las escalan y reúnen para revisión, sin alterar colores de los originales. Mínimo observado: **5,258364:1**, superior al requisito de 4,5:1.

Cada medición captura el nodo real y exige encontrar píxeles exactos del texto y del fondo esperados. No alcanza con comparar colores declarados en el tema. Se usan interiores de glifos y fondos, sin promediar bordes suavizados. Para cada canal sRGB normalizado, `c/12,92` si `c ≤ 0,04045`, o `((c+0,055)/1,055)^2,4`; luminancia `0,2126R + 0,7152G + 0,0722B`; razón `(Lmax+0,05)/(Lmin+0,05)`. Referencia de la fórmula: [WCAG 2.2](https://www.w3.org/TR/WCAG22/#dfn-contrast-ratio).

La matriz incluye acceso, nombre, principal, carga de comida/duración/sueño, definición propia, historial, corrección, clases y diálogos de eliminación/reinicio. Se cubren estados aplicables de carga, confirmado, vacío, error, desactualizado, pendiente y rechazo; texto, etiquetas/valores de campos, controles habilitados/deshabilitados y opciones seleccionadas/no seleccionadas. La selección añade marca visible y descripción semántica, además del color; las clases conservan texto Bloqueada/Desbloqueada. Procesamiento, errores y resultados tienen mensajes explícitos.

Estas son muestras automatizadas de los componentes/estados y revisión visual en este dispositivo, no certificación integral WCAG ni una sesión manual con TalkBack. Las ilustraciones decorativas no son el único medio para identificar clase/etapa: existen nombre, etapa y descripción accesible por separado.

## Interacciones desde principal

[Eventos registrados](t079-gestures.jsonl) y [principal con accesos](t079-light-principal-quick.png). La prueba exige controles visibles, cuenta cada toque o selección, cuenta el tipeo continuo en un único campo como una interacción, y espera «Actividad guardada». No usa un toque oculto para enfocar: se exige que el campo tenga foco automáticamente antes de escribir.

| Actividad disponible | Secuencia real | Total |
|---|---|---:|
| Comida saludable | Comida → Guardar | 2 |
| Entrenamiento, 30 min | Entrenar → 30 min → Guardar | 3 |
| Sueño, 7 h | Dormir → 7 h → Guardar | 3 |
| Entrenamiento, 1:15 | Entrenar → escribir `1:15` en el campo enfocado → Guardar | 3 |
| Sueño, 7:35 | Dormir → escribir `7:35` en el campo enfocado → Guardar | 3 |

El diálogo muestra la fecha de hoy en UTC−3. La duración libre usa un solo campo `h:mm`, de 0:01 a 24:00. Entrada inválida conserva lo escrito, explica el rango y deshabilita Guardar. Cancelar o estar offline no guarda ni envía. Un resultado incierto bloquea nuevas cargas y reintenta la operación original. El formulario general sigue disponible para otras fechas, actividades o duraciones.

Los cinco ensayos usan el controlador y la navegación reales con un puerto de registros sintético: verifican una intención UUID válida, la cuenta, fecha/duración enviadas y una sola entrega por ensayo. No miden latencia HTTP real ni ejecutan fórmulas de XP. La mediana de 15 s y el estudio con diez participantes continúan como estudio orientativo futuro del PRD; no se acreditan con instrumentación.

## Repetición

Con SDK/JDK/caché configurados y dispositivo conectado, compilar y ejecutar la clase:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=lvluplife.ui.AccessibilityTest"
```

El arnés de esta sesión usó además Auth Emulator local `demo-lvluplife` para los tests de regresión de identidad. La clase escribe los PNG y ambos JSONL en el cache privado de `lvluplife.android`; el proceso de QA los exporta sin copiar Room ni credenciales. Los detalles de builds/regresiones y límites se encuentran en [validation.md](../../validation.md).

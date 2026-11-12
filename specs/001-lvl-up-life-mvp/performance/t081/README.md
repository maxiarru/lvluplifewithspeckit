# Arnés orientativo de rendimiento e idempotencia — T081

`PerformanceScenarios` ejecuta el servidor Netty real por HTTP loopback, con
verificación real de Firebase Auth Emulator y persistencia Firestore Emulator.
No sustituye la red por el cliente de `testApplication`. Los puertos del servidor
son efímeros; los emuladores deben estar en 127.0.0.1:8080 y 127.0.0.1:9099,
con proyecto demo-lvluplife y LVLUP_ENV=emulator. Las guardas impiden usar el
arnés contra servicios de producción.

## Invocación reproducible

Desde la raíz en PowerShell, con las herramientas locales ya instaladas:

```powershell
$env:JAVA_HOME=(Resolve-Path '.tooling/jdk-17.0.21+9').Path
$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
$env:GRADLE_USER_HOME=Join-Path $PWD '.tooling/gradle-user-home'
$env:ANDROID_HOME='C:/Users/maxi1/AppData/Local/Android/Sdk'
$env:LVLUP_ENV='emulator'
$env:GOOGLE_CLOUD_PROJECT='demo-lvluplife'
$env:GCLOUD_PROJECT='demo-lvluplife'
$env:LVLUP_PERF_REVISION=(git rev-parse --short HEAD).Trim()
if (git status --porcelain) { $env:LVLUP_PERF_REVISION+='-working-tree' }
$env:LVLUP_PERF_PROFILE='smoke'
$env:LVLUP_PERF_N='3'
$env:LVLUP_PERF_WARMUP='1'
$env:LVLUP_PERF_WORKERS='2'
$env:LVLUP_PERF_SEED='81001'
$env:LVLUP_PERF_COLD_N='1'
node .tooling/firebase-cli/node_modules/firebase-tools/lib/bin/firebase.js emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore 'gradlew.bat :server:integrationTest --tests *PerformanceScenarios* --no-daemon'
```

El comando interno equivale a
`./gradlew.bat :server:integrationTest --tests "*PerformanceScenarios*" --no-daemon`.
Dentro de `emulators:exec` en Windows se usa `gradlew.bat`, porque Firebase
lanza su comando mediante cmd. No se necesita un dispositivo Android.

Los JSON/CSV quedan en `server/build/reports/performance/profile-PERFIL-VOLUMEN.*`.
Gradle registra los parámetros como entradas y el directorio de reportes como
salida: cambiar de perfil, semilla o tamaño de muestra invalida UP-TO-DATE.
Si se repite exactamente la misma configuración, puede reutilizar resultados;
para otra muestra independiente se puede usar `--rerun-tasks`.

| Variable | Valores / significado | Predeterminado |
| --- | --- | --- |
| LVLUP_PERF_PROFILE | smoke: 12; 0, 1000 o 10000: volumen elegido; all: 0/1000/10000 | smoke |
| LVLUP_PERF_N | Iteraciones secuenciales y cantidad de tandas concurrentes, entero positivo | 3 |
| LVLUP_PERF_WARMUP | Iteraciones previas excluidas de percentiles, entero no negativo | 1 |
| LVLUP_PERF_WORKERS | Solicitudes de trabajadores por cuenta/tanda, mínimo 2 | 2 |
| LVLUP_PERF_SEED | Semilla del conjunto de fechas/duraciones | 81001 |
| LVLUP_PERF_COLD_N | Cantidad de JVM nuevas por volumen, positivo y sin calentamiento | 1 |
| LVLUP_PERF_REVISION | Revisión del código; usar sufijo working-tree si corresponde | UNSPECIFIED |

Para los perfiles amplios, cambiar `LVLUP_PERF_PROFILE` por `1000`, `10000` o
`all` y volver a ejecutar el mismo comando. Son ensayos disponibles, no muestras
ya ejecutadas. Estas opciones pertenecen exclusivamente al arnés; no imponen
límites nuevos al número de registros ni al uso de la app.

## Recorridos y oráculos

Cada volumen crea una cuenta sintética propia y un ciclo activo. La preparación
siembra directamente registros válidos en Firestore, distribuidos en hasta
365 fechas mediante la semilla. Esta preparación queda fuera de los percentiles
y tiene su duración separada. No se siembran valores de XP ni proyecciones
precocinadas. La API reconstruye la progresión con el motor real.

Tras el calentamiento, cada iteración mide GET progreso, GET historial mensual,
CREATE, EDIT y DELETE.
Cada registro medido cambia de 30 a 45 minutos antes de eliminarse; se comprueba
la duración persistida y que los registros sembrados permanezcan idénticos.
GET progreso reconstruye y republica la proyección bajo una barrera de revisión;
su tiempo incluye esa publicación.
Las tandas concurrentes hacen ese recorrido con varios
trabajadores sobre la misma cuenta y registros distintos. El ciclo se lee
completo: no se pagina ni recorta para mejorar artificialmente el resultado.
DELETE conserva tombstones: el volumen es la cantidad inicial sembrada, y el
reporte registra el crecimiento del ciclo antes/después de las operaciones.

Otra tanda envía concurrentemente el mismo CREATE/UUID y exige el mismo recibo
y registro. La inyección poscommit ejecuta primero el handler y commit reales;
después sustituye la respuesta por 503, una vez por CREATE, EDIT y DELETE.
Se consulta cada recibo y se reenvían exactamente el mismo sobre y UUID. Se
exigen respuestas idénticas, versiones 1/2/3, un registro por intención y un
recibo por operación. No se simula un corte real del socket.

Al finalizar las mutaciones se comprueban cantidades de registros/recibos,
historial sin duplicados ni registros eliminados y proyección idéntica al
ReplayEngine sobre todos los documentos del ciclo, incluidos tombstones.
El reloj autoritativo de estos recorridos está fijado y se registra: esto evita
confundir el plazo de corrección con el tiempo consumido por el ensayo.

El arranque frío va separado: una JVM nueva ejecuta `ServerKt`, con los recursos
y reloj reales del servicio, y se mide desde crear el proceso hasta responder
correctamente al primer progreso autenticado. Incluye inicialización y sondeos
de disponibilidad, excluye el cierre del proceso. Host, emuladores y cachés del
sistema operativo permanecen calientes. El classpath se pasa por archivos
temporales para evitar el límite de comandos de Windows; allí se usa javaw.exe
sin consola visible. El proceso hijo se cierra y se espera su terminación.

## Lectura de JSON y CSV

Ambos formatos incluyen semilla, perfil/volumen, entorno, versión, calentamiento,
n, percentiles nearest-rank p50/p95, errores, reintentos y duración. JSON incluye
además cada muestra, tamaño de respuesta, límites de interpretación y los
oráculos verificados. CSV contiene una fila por fase/operación. El calentamiento
de la fase cold-jvm es siempre cero; su n es independiente del resto.

`errors` cuenta estados distintos de los esperados. `httpErrors` incluye también
los 503 inyectados y `expectedFailures` los identifica expresamente. Los
reintentos por 503 transitorio repiten la misma solicitud/sobre, hasta tres
intentos; la latencia incluye esa espera. Los tres reenvíos poscommit se cuentan
aparte. Los sondeos de arranque frío tampoco se presentan como reintentos de
una mutación.

Se cuentan las solicitudes HTTP de lectura por fase y método y los documentos
que componen el ciclo antes/después. Las lecturas facturables/RPC de Firestore
y sus reintentos transaccionales internos **no están instrumentados**: figuran
null en JSON y NOT_INSTRUMENTED en CSV. La suma de duraciones de solicitudes
concurrentes puede superar la duración de pared del perfil.

Las pruebas comprueban configuración, percentiles con muestras pequeñas,
esquema/cardinalidad de JSON/CSV, n secuencial/concurrente, finitud de tiempos y
ausencia de errores inesperados, además de las invariantes de persistencia.

## Límites

Son medidas locales loopback, sin TLS ni transporte Android y con un conjunto
sintético por ciclo. No acreditan p95 de producción, SC-001/002/003, 99,5 % de
disponibilidad ni el umbral 995/1000 sesiones de SC-010. Un percentil con n=1 es
solo ese valor observado. Las muestras amplias y sus metas siguen orientativas;
el cierre del arnés se basa en el smoke y sus comprobaciones funcionales.
Las identidades provienen de signUp anónimo de Auth Emulator; no se prueba
OAuth ni el selector Google real.

En CSV, http_get_requests es el total de la fase, repetido en sus filas; no se
suma entre operaciones. El mapa JSON conserva los totales únicos por fase/método.

## Resultados efectivamente ejecutados — 2026-11-12

| Perfil | Registros iniciales | Iteraciones | Warmup | Trabajadores | Muestras | Resultado |
| --- | --- | --- | --- | --- | --- | --- |
| smoke | 12 registros | 3 | 1 | 2 | 61 | APROBADO |
| 0 | 0 registros | 1 | 0 | 2 | 31 | APROBADO |
| 1000 | 1000 registros | — | — | — | 0 | NO EJECUTADO |
| 10000 | 10000 registros | — | — | — | 0 | NO EJECUTADO |

En ambos perfiles ejecutados: cero errores inesperados, tres 503 esperados
tras commits reales y tres reenvíos poscommit con el mismo UUID, sin duplicados.
Los checks de recálculo completo/historial, datos sembrados inmutables y
contención del mismo UUID pasaron. Cada perfil ejecutó una JVM nueva aparte.

Resumen del smoke local (ms; p95 descriptivo con n pequeño):

| Fase | Operación | n | p50 ms | p95 ms |
| --- | --- | --- | --- | --- |
| sequential | progress | 3 | 24.369 | 26.185 |
| sequential | history | 3 | 16.401 | 17.104 |
| sequential | CREATE | 3 | 26.611 | 29.452 |
| sequential | EDIT | 3 | 25.705 | 28.559 |
| sequential | DELETE | 3 | 28.345 | 30.091 |
| concurrent | progress | 6 | 2501.591 | 4335.159 |
| concurrent | history | 6 | 12.681 | 15.159 |
| concurrent | CREATE | 6 | 20.021 | 22.606 |
| concurrent | EDIT | 6 | 21.837 | 27.424 |
| concurrent | DELETE | 6 | 23.298 | 2773.844 |
| cold-jvm | first-progress | 1 | 3143.511 | 3143.511 |

Duración de pared del perfil smoke: 19.113 s; perfil 0: 15.378 s.

Progreso concurrente muestra p95 4335.160 ms con n=6 y DELETE concurrente
2773.844 ms con n=6. Se registra esta dispersión sin atribuir una causa exacta
ni extrapolar a producción; las metas no se declaran acreditadas. Arranque
frío del smoke: 3143.511 ms, n=1, sin calentamiento de esa JVM.

Reportes originales: [smoke JSON](profile-smoke-12.json),
[smoke CSV](profile-smoke-12.csv), [perfil 0 JSON](profile-0-0.json) y
[perfil 0 CSV](profile-0-0.csv). No contienen UID ni tokens de las cuentas de prueba.
Se revisaron independientemente con el parser csv de Python: 24 columnas,
metadatos, n, cardinalidad de filas, errores esperados y percentiles recalculados
sobre las muestras crudas. Se conservan los XML de los dos casos del arnés:
[smoke](smoke-tests.xml) y [perfil 0](empty-tests.xml).

Regresión final: integración234/234 ejecutadas verdes (232 previas +2 nuevas),
dominio322/322 y unitarias servidor34/34 UP-TO-DATE en esa corrida; CLI0,
BUILD SUCCESSFUL2m. El perfil 0 definitivo terminó en BUILD SUCCESSFUL30s,
2/2 pruebas ejecutadas verdes. [Resumen](full-regression-summary.json),
[log final](t081-final-regression.txt), [primera regresión](t081-regression.txt)
y [perfil 0](empty-profile.txt).

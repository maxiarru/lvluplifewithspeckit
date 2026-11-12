# Comprobación de quickstart desde checkout de prueba — T083

Fecha:2026-11-12. Código fuente:362be0b (T082), clonado localmente mediante
`git clone --shared --no-hardlinks . .tooling/t083-checkout`.
El clon no contenía outputs build ni node_modules. Su único cambio versionado
durante las comprobaciones fue la guía candidata, idéntica a la del checkout
principal. No se cambió código de la app, servidor, tests, dependencias ni lock.

## Resultados ejecutados

| Comando / paso | Resultado | Evidencia |
| --- | --- | --- |
| Clone local | APROBADO; revisión362be0b | [log](clone.txt) |
| npm.cmd ci --no-audit --no-fund --cache RUTA_AISLADA | APROBADO con Node22; lock idéntico | [log](npm-ci.txt) |
| firebase.cmd --version local | 13.35.1, coincide con el pin | [versión](firebase-version.txt) |
| npm.cmd run verify:emulators | APROBADO; Auth disponible y4 rechazos esperados | [log](verify-emulators.txt) |
| assembleDebug :domain:test :server:test testDebugUnitTest --no-daemon | APROBADO;3m15s,60 tareas ejecutadas | [log](quick-check.txt) |
| firebase.cmd emulators:start | APROBADO; Auth9099/Firestore8080 | [Hub](manual-emulators.json) |
| :server:run --no-daemon | APROBADO; servicio8081 responde401 sin token | [sondeo](service-probe.json) |
| installDebug --no-daemon | APROBADO;25s, instalado en1 dispositivo | [log](install.txt) |
| adb shell am start -W -n lvluplife.android/lvluplife.MainActivity | APROBADO; Status ok, pantalla de acceso | [log](launch.txt), [captura](launch.png) |
| :server:integrationTest --no-daemon con emuladores activos | APROBADO;234/234,2m15s | [log](integration.txt) |
| Comando automático completo de quickstart | APROBADO;6m30s,34 ejecutadas/62 UP-TO-DATE | [log](automatic-validation.txt) |

El comando automático se ejecutó con la CLI13.35.1 instalada por npm ci en el
clon y Node22.23.5, no con la CLI13.31.2 usada en T082. Inició sus propios
emuladores y los cerró al terminar. Su comando hijo fue:

```text
gradlew.bat assembleDebug :domain:test :server:test testDebugUnitTest :server:integrationTest connectedDebugAndroidTest --no-daemon
```

Los dos recorridos tienen distintos comportamientos de caché, registrados sin
sumar dos veces las pruebas:

| Suite | Pruebas | Ejecutadas en el clon durante | En comando automático final |
| --- | --- | --- | --- |
| Dominio | 322/322 | Comando rápido | UP-TO-DATE |
| Servidor unitarias | 34/34 | Comando rápido | UP-TO-DATE |
| Android JVM | 142/142 | Comando rápido | UP-TO-DATE |
| Servidor integración | 234/234 | Integración manual | UP-TO-DATE |
| Android conectadas | 136/136 | Comando automático | Ejecutadas |

Total868 casos JUnit ejecutados entre las fases, cero fallos/errores/omitidas.
Los XML originales se conservan en [reports](reports); cantidades y fases en
[results.json](results.json). Las136 conectadas pasaron en una sola invocación
de connectedDebugAndroidTest sobre Pixel_6a_API_30/emulator-5554,1080×2400px/420dpi.

## Configuración de prueba y límites de reproducibilidad

PowerShell7.6.9, Temurin17.0.21+9, Gradle9.5.0, Node22.23.5/npm10.9.9,
Firebase CLI13.35.1 y cliente11.10.0, proyecto demo-lvluplife. Se usaron JDK/SDK
Android ya instalados y caché compartida de dependencias Gradle. No se acredita
una instalación nueva de Android Studio/SDK/JDK ni provisión de dispositivo
desde cero. Los outputs del clon se generaron allí durante esta comprobación.

La semilla del smoke fue81001, n3, warmup1 y2 trabajadores, una JVM fría,
LVLUP_PERF_REVISION=362be0b-quickstart-candidate. Los hosts y el modo se fijaron
explícitamente como muestra quickstart. No se usaron credenciales ni datos reales.

Node22 se descargó del sitio oficial y su archivo
`node-v22.23.5-win-x64.zip` se verificó contra
[SHASUMS256.txt](https://nodejs.org/dist/latest-v22.x/SHASUMS256.txt):
`4a191082e9706866830379d577d2cc8a50c776fcd9436a8d061b063ed0d9a0e3`.
El ZIP y las herramientas locales no se agregan al repositorio.

## Incidencias observadas y resueltas

- La primera instalación se lanzó por error en el checkout principal, sin
  cambios versionados; se repitió dentro del clon. La evidencia final procede
  del clon.
- Node25 instalaba con EBADENGINE para superstatic (declara18/20/22). La guía y
  la comprobación definitiva usan Node22.
- Una reinstalación Node22 con la caché previa finalizó pero la CLI no cargaba
  yaml/dist/index.js. Repetir npm ci con caché aislada restauró la instalación;
  se comprobó la carga de CLI13.35.1 y su ejecución. No se cambió el lock ni se
  copiaron node_modules del checkout principal para hacer pasar la prueba.

El servicio manual y los emuladores se detuvieron con Ctrl+C, por lo que sus
consolas largas terminan por interrupción controlada, no por BUILD SUCCESSFUL.
El sondeo401 demuestra su arranque; la instalación y las suites tienen código0.
Se comprobaron los puertos libres tras el cierre manual
([resultado](manual-stop-ports.json)) y también al terminar el comando automático
([results.json](results.json)). El emulador Android del usuario se conserva.

## Pasos externos y no repetidos

Google real permanece NO DISPONIBLE: no hay google-services.json ni
default_web_client_id. La captura muestra la app abierta con el aviso de acceso
no disponible; no acredita un login Google. No se configura infraestructura ni
se despliega un servicio para esta comprobación.

La rama condicional de permiso local API37 no se volvió a ejecutar en T083;
su evidencia es la matriz [T080](../../matrix/t080/README.md). Los drivers de
varios procesos y los perfiles amplios de rendimiento se enlazan en la guía,
sin declararlos reejecutados aquí. No se acreditan p95, disponibilidad ni1000
sesiones de producción con este recorrido.

Los logs de texto normalizan espacios finales y saltos de línea. No se alteran
los resultados ni los XML. El hash de la guía candidata y el estado del clon
quedan registrados en results.json.

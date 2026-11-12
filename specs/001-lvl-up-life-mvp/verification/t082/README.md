# Validación completa — T082, 2026-11-12

Revisión probada: `113b057`. Sin modificaciones de producción, configuración
funcional ni pruebas durante esta tarea. Ejecución forzada con `--rerun-tasks`:
BUILD SUCCESSFUL en 11m56s, 96 tareas ejecutadas, Firebase CLI terminó con código0.

## APROBADO

| Comprobación / comando Gradle | Resultado | Informe |
| --- | --- | --- |
| assembleDebug | APK generado | [hashes](apk-hashes.json) |
| :domain:test | 322/322 | [XML](reports/domain) |
| :server:test | 34/34 | [XML](reports/server-unit) |
| testDebugUnitTest | 142/142 | [XML](reports/android-jvm) |
| :server:integrationTest | 234/234 | [XML](reports/server-integration) |
| connectedDebugAndroidTest | 136/136 | [XML](reports/android-connected) |

Total:868 pruebas JUnit ejecutadas, cero fallos, errores u omitidas. No se
cuentan suites cacheadas como ejecutadas. Las 234 de integración incluyen
los dos casos de PerformanceScenarios y su smoke local; las 61 muestras
de ese smoke no se suman otra vez como pruebas JUnit.

Chequeo adicional `node firebase/verify-emulators.mjs`: cuatro intentos de
lectura/escritura directa de cliente rechazados por reglas Firestore, sin sesión
y después de autenticación anónima; disponibilidad Auth verificada. Código0.
Los PERMISSION_DENIED del [log](rules-verification.txt) son los rechazos esperados.

## FALLIDO

Ninguna comprobación fallida ni fallo crítico abierto en esta ejecución.
Las advertencias del compilador existentes no impidieron build ni pruebas.

## NO DISPONIBLE

Google real: no existe `app/google-services.json` ni recurso fuente
`default_web_client_id`. Se comprobó presencia de configuración, sin exportar
credenciales: [resultado](google-configuration.json). No se intentó iniciar
sesión Google real ni crear/configurar infraestructura. No se acredita selector,
OAuth, cuentas Google reales ni despliegue.

## Comando y entorno efectivamente utilizados

PowerShell, desde la raíz del checkout, SDK instalado y emulador ya disponible:

```powershell
$env:JAVA_HOME=(Resolve-Path '.tooling/jdk-17.0.21+9').Path
$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
$env:GRADLE_USER_HOME=Join-Path $PWD '.tooling/gradle-user-home'
$env:ANDROID_HOME='C:/Users/maxi1/AppData/Local/Android/Sdk'
$env:ANDROID_SERIAL='emulator-5554'
$env:LVLUP_ENV='emulator'
$env:GOOGLE_CLOUD_PROJECT='demo-lvluplife'
$env:GCLOUD_PROJECT='demo-lvluplife'
$env:LVLUP_PERF_REVISION='113b057'
$env:LVLUP_PERF_PROFILE='smoke'
$env:LVLUP_PERF_N='3'
$env:LVLUP_PERF_WARMUP='1'
$env:LVLUP_PERF_WORKERS='2'
$env:LVLUP_PERF_COLD_N='1'
$env:LVLUP_PERF_SEED='81001'
node .tooling/firebase-cli/node_modules/firebase-tools/lib/bin/firebase.js emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore 'gradlew.bat assembleDebug :domain:test :server:test testDebugUnitTest :server:integrationTest connectedDebugAndroidTest --rerun-tasks --no-daemon'
```

El chequeo de reglas se ejecutó contra esos mismos emuladores mientras estaban
activos, con GCLOUD_PROJECT=demo-lvluplife y FIRESTORE_EMULATOR_HOST/
FIREBASE_AUTH_EMULATOR_HOST fijados a127.0.0.1:8080/9099. Sin otro arranque ni
apagado paralelo de emuladores. Todos los datos son sintéticos.

JDK Temurin17.0.21+9, Gradle9.5.0, Kotlin2.4.20, AGP9.3.1, Node25.1.0.
Firebase CLI efectivamente usada:13.31.2 del directorio local .tooling;
package.json fija13.35.1 y esa versión también está instalada en node_modules.
Se registra la diferencia; no se presenta como una validación de la CLI fijada.
Cliente Firebase del chequeo de reglas:11.10.0.

Dispositivo: Pixel_6a_API_30, emulator-5554, Android11/API30, ABIx86,
1080×2400px/420dpi. Fingerprint:
`google/sdk_gphone_x86/generic_x86_arm:11/RSR1.201013.001/6903271:user/release-keys`.
No se alteró su geometría. `connectedDebugAndroidTest` ejecutó los136 casos
en una sola invocación, con cero omitidos.

## Evidencia y alcance

[Resultados estructurados](results.json), [log completo](full-validation.txt),
XML por suite y hashes de ambos APK. Los logs de texto conservan el contenido
de salida con espacios finales normalizados; no se modifican los resultados.
Se conserva también el [JSON](performance-smoke.json)/[CSV](performance-smoke.csv)
del smoke de esta corrida, con revisión113b057.

Los métodos ProcessStage requieren sus drivers de varias ejecuciones y se
excluyen deliberadamente del runner conectado estándar. La matriz de procesos
de [T080](../../matrix/t080/README.md) mantiene su evidencia previa: no se vuelve
a contar como ejecutada aquí ni se extiende este resultado a todas las APIs.
Los tiempos del smoke no acreditan p95/99,5%/1000 sesiones de producción.

La guía quickstart todavía contiene texto y comandos previstos de la fase de
planificación; esta corrida incorpora el modo emulador explícito que exige
ServerConfig. Su actualización y comprobación desde checkout de prueba
pertenecen a T083, que no se inicia antes de revisar esta tarea.

# Matriz Android T080

La fuente de resultados de la última invocación es `results.json`, con resumen
en `results.csv`. Una ejecución filtrada conserva las celdas no ejecutadas como
`NO DISPONIBLE`; no representa una aprobación de toda la matriz.

## Resultado definitivo

Ejecución completa del 2026-11-12, iniciada a las 2026-11-12T16:43:23.175Z y terminada a las 2026-11-12T16:54:32.251Z. Las doce celdas aprobaron sus dos etapas con los mismos APK. Se revisaron las 108 capturas originales; los contactos son vistas reducidas de esas imágenes.

| API / Android | Dispositivo | Tamaño real (dp) | Orientación | Resultado | Capturas |
| --- | --- | --- | --- | --- | --- |
| 29 / 10 | phone | 360×640 | portrait | APROBADO | [contacto](api29-phone-portrait/contact.png) |
| 29 / 10 | phone | 640×360 | landscape | APROBADO | [contacto](api29-phone-landscape/contact.png) |
| 29 / 10 | tablet | 800×1280 | portrait | APROBADO | [contacto](api29-tablet-portrait/contact.png) |
| 29 / 10 | tablet | 1280×800 | landscape | APROBADO | [contacto](api29-tablet-landscape/contact.png) |
| 31 / 12 | phone | 360×640 | portrait | APROBADO | [contacto](api31-phone-portrait/contact.png) |
| 31 / 12 | phone | 640×360 | landscape | APROBADO | [contacto](api31-phone-landscape/contact.png) |
| 31 / 12 | tablet | 800×1280 | portrait | APROBADO | [contacto](api31-tablet-portrait/contact.png) |
| 31 / 12 | tablet | 1280×800 | landscape | APROBADO | [contacto](api31-tablet-landscape/contact.png) |
| 37 / 17 | phone | 360×640 | portrait | APROBADO | [contacto](api37-phone-portrait/contact.png) |
| 37 / 17 | phone | 640×360 | landscape | APROBADO | [contacto](api37-phone-landscape/contact.png) |
| 37 / 17 | tablet | 800×1280 | portrait | APROBADO | [contacto](api37-tablet-portrait/contact.png) |
| 37 / 17 | tablet | 1280×800 | landscape | APROBADO | [contacto](api37-tablet-landscape/contact.png) |

Los 24 logs de instrumentación quedan junto al reporte. Cada par identifica
el cambio de PID y la coincidencia de las huellas de identidad y progreso.
En las doce celdas se conservó también el borrador privado de Room.

SHA-256 de los APK efectivamente ejecutados:

- `app/build/outputs/apk/debug/app-debug.apk`: `4f64bf371cfc532961a0025783da643a56cc9015062ba26ef5f02feaa8703a3a`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: `3f3a9008dd2735c54bddb29fff3e33eaf2afaa86edcba0037582ce779ff3df5a`

## Entorno y reproducción

Se usan imágenes Google APIs x86_64, WHPX y renderizado SwiftShader en Windows:

| API | Imagen SDK | AVD privado |
| --- | --- | --- |
| 29 | `system-images;android-29;google_apis;x86_64` | `LvlUp_Matrix_API29` |
| 31 | `system-images;android-31;google_apis;x86_64` | `LvlUp_Matrix_API31` |
| 37 | `system-images;android-37.0;google_apis;x86_64` | `LvlUp_Matrix_API37_0` |

Emulator 37.2.12 (build 16428233); revisiones de imagen: API29 rev13,
API31 rev12 y API37.0 rev7. Los fingerprints observados por celda se conservan
en results.json.

Los tres AVD se crean con `avdmanager` dentro de `.tooling/t080-avd`.
En cada `config.ini`, la pantalla base es 1080×1920 px a 320 dpi. Esto evita
el límite de escalado de Android 10 que recortaba el ancho solicitado al partir
del dispositivo genérico de 320×640 px. El runner exige la geometría real,
sin ajustar el resultado esperado a un recorte del emulador.

El runner utiliza exclusivamente `emulator-5560`, comprueba el nombre del AVD
antes de cerrarlo y deja intacto el emulador habitual en `emulator-5554`.
Deshabilita animaciones y aplica `wm size` y `wm density`: teléfono
720×1280 px, tablet 1600×2560 px, ambos a 320 dpi. Cada uno se prueba en
portrait y landscape: 360×640, 640×360, 800×1280 y 1280×800 dp.
La prueba Android comprueba API, orientación y dimensiones realmente observadas.
En API37 la orientación se fija con `wm user-rotation lock 0|1`: escribir únicamente
`settings system user_rotation` no produjo landscape en API 37 y fue detectado
por la aserción de orientación antes de ejecutar el recorrido.
En API37 se habilita `wm fixed-to-user-rotation enabled` para impedir que preferencias
del launcher o de ventanas sobreescriban la orientación solicitada.
API29/31 utilizan `settings put system user_rotation 0|1`; Android10 no
ofrece el comando nuevo de política de rotación. Todas las APIs conservan
la misma comprobación instrumentada de geometría y orientación reales.
Cada emulador usa cuatro núcleos y 4096 MB, con `-gpu swiftshader` (el modo
`swiftshader_indirect` está
[obsoleto](https://developer.android.com/studio/run/emulator-acceleration)).
Después de boot_completed se esperan treinta segundos de estabilización.
Se rechazan ventanas `Application Not Responding` o `Application Error`
antes y después de cada etapa instrumentada.

Desde PowerShell, con JDK 17, SDK instalado y Firebase CLI disponible:

```powershell
$env:JAVA_HOME=(Resolve-Path '.tooling/jdk-17.0.21+9').Path
$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
$env:GRADLE_USER_HOME=Join-Path $PWD '.tooling/gradle-user-home'
$env:ANDROID_HOME='C:/Users/maxi1/AppData/Local/Android/Sdk'
$env:ANDROID_AVD_HOME=Join-Path $PWD '.tooling/t080-avd'
./gradlew.bat :server:installDist :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
$env:LVLUP_ENV='emulator'
$env:GOOGLE_CLOUD_PROJECT='demo-lvluplife'
$env:GCLOUD_PROJECT='demo-lvluplife'
node .tooling/firebase-cli/node_modules/firebase-tools/lib/bin/firebase.js emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore 'node scripts/test-device-matrix.mjs C:/Users/maxi1/AppData/Local/Android/Sdk'
```

Auth y Firestore se ejecutan en 9099 y 8080; el servidor Netty real en 8081.
El script rechaza otros modos/proyectos/hosts y exige el puerto del servidor
libre antes de iniciarlo. No usa ni despliega servicios de producción.
Para diagnóstico se pueden añadir filtros como `api37 phone portrait` al comando
del runner; para aprobar T080 se requiere una invocación completa sin filtros.

## Oráculo y evidencia

Cada celda ejecuta dos métodos de `DeviceMatrixStages`, separados por
`am force-stop`. Exige PID distinto, la misma identidad y la misma huella del
progreso confirmado. El primer método atraviesa acceso, nombre, registros
rápidos, historial, corrección y eliminación, fecha inválida, actividad propia,
clases y reinicio confirmado. Guarda después un borrador manual de 25 minutos
en Room, esperando su persistencia antes del cierre.

El segundo método verifica los mismos bytes del borrador, el progreso del ciclo
nuevo, el historial anterior retenido con la corrección de 45 minutos y sin
edición después del reinicio, la actividad propia y logout/reingreso. Se comparan
los campos estables del progreso; la revisión y computedAt se excluyen porque
la lectura de progreso republica legítimamente su proyección.

Los controles accionados deben estar completos dentro del área visible y se
pulsan por coordenadas mediante `performTouchInput`. Los logs registran las
aserciones y huellas, nunca credenciales. Las capturas originales de
UiAutomation incluyen diálogos y barras del sistema: perfil, eliminación,
error de historial, clases, reinicio, estado antes del cierre, borrador,
historial y estado reabiertos. Un fallo interrumpe la ejecución y conserva
diagnóstico; no se transforma en una celda aprobada.

## Límites de la evidencia

La autenticación ejecuta Firebase Auth SDK y Auth Emulator con credenciales
Google sintéticas; no verifica el selector ni OAuth Google real. Las pruebas
usan el servidor HTTP, Firestore Emulator y Room reales. No cubren dispositivos
físicos ARM, GPU física, animaciones habilitadas ni todas las versiones o
personalizaciones Android.

La matriz tiene doce combinaciones y dos ejecuciones instrumentadas por celda.
Permite evaluar SC-009 para estos recorridos y configuraciones concretos.
No equivale a 1000 sesiones ni permite acreditar el umbral 995/1000 de SC-010.
Los resultados parciales o fallidos tampoco acreditan SC-009 completo.

## Compatibilidad del arnés

La primera ejecución completa aprobó las ocho celdas API 29/31 y se detuvo
antes del recorrido de API 37: Espresso 3.5.0 intentaba acceder por reflexión
a `InputManager.getInstance`, eliminado en esa versión de Android.
Se actualizan únicamente dependencias de instrumentación a runner 1.7.0,
JUnit Android 1.3.0 y Espresso 3.7.0. Sus
[notas oficiales](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)
documentan el uso de getSystemService en lugar de esa reflexión.
La aprobación exige repetir las pruebas con el APK de instrumentación actualizado.
También se espera la aparición del catálogo después de cargarlo por HTTP antes
de desplazarlo, para no confundir una carga todavía en curso con una lista ausente.

Android 17 también exige
[`ACCESS_LOCAL_NETWORK`](https://developer.android.com/privacy-and-security/local-network-permission)
para conexiones al host local de desarrollo. Se declara exclusivamente en el
manifest debug; el runner lo otorga por `pm grant` solo en API 37 o superior.
Las pruebas JVM inspeccionan los manifests fusionados para exigir su presencia
en debug y su ausencia en release. No se agrega una solicitud de permiso al
flujo de producción, que utiliza servicios remotos.

La revisión visual invalidó una ejecución preliminar aunque sus aserciones de
la app habían pasado: sus capturas contenían un diálogo de ANR de System UI.
ActivityManager registró que KeyguardService excedió 20038 ms durante el
arranque, antes de la instrumentación. Esto motivó la estabilización previa,
la configuración actual del emulador y el rechazo de ventanas de error por
el runner. Esas ejecuciones preliminares no acreditan la matriz final.

## Regresión adicional

Las 136 pruebas Android anteriores se ejecutaron nuevamente en
Pixel_6a_API_30, emulator-5554, API30, 1080×2400 px a 420 dpi, sin cambiar
su geometría. Resultado: 15/15 de accesibilidad (94.301 s), 17/17 de reinicio
(42.922 s) y 104/104 anteriores (339.264 s). Firebase Auth Emulator local;
CLI terminada con código cero. Esta regresión es adicional a las 24 etapas
de la matriz; no se cuenta como 136 sesiones ni como cobertura de otras APIs.

Evidencia: [resumen de verificaciones](regression/verification.json),
[accesibilidad](regression/t080-regression-accessibility.log),
[reinicio](regression/t080-regression-reset.log),
[104 casos anteriores](regression/t080-regression-reset-regression.log),
[ejecución de matriz](regression/t080-matrix-final.log) y
[ejecución de regresión](regression/t080-native-regression.log).
Los logs Gradle conservan las compilaciones y verificaciones JVM; el resumen
indica expresamente que dominio quedó UP-TO-DATE.

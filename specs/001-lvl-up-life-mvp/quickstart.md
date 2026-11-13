# Ejecutar y validar Lvl Up Life

La app Android y el servicio Kotlin están implementados. Trabajar desde la raíz
del checkout en PowerShell 7. Esta guía usa datos sintéticos y emuladores locales.
La evidencia de comprobación desde un clon local está en
[verification/t083/README.md](verification/t083/README.md); la validación integral
previa está en [T082](verification/t082/README.md).

## Prerrequisitos y configuración de la consola

- Git, JDK17, Android SDK con plataforma37, build-tools compatibles y platform-tools.
- Un dispositivo/emulador API29+ ya configurado para las pruebas conectadas.
- Node22 y npm. Entorno comprobado: Node22.23.5; Firebase CLI13.35.1 y cliente11.10.0
  fijados por package-lock.json. No se usa una CLI global ni npx que descargue otra.
- Gradle9.5.0 se descarga mediante el wrapper. Kotlin2.4.20 y AGP9.3.1 están fijados
  en el repositorio. Java/Kotlin usan JDK17. Docker no es necesario para este flujo.

Instalar primero esas herramientas y aceptar las licencias del SDK por sus
instaladores. En cada consola configurar rutas válidas para esa máquina:

```powershell
$env:JAVA_HOME='C:/ruta/al/jdk-17'
$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
# Si Node22 se usa desde un ZIP local, anteponer su directorio a PATH.
# $env:PATH='C:/ruta/a/node-22;'+$env:PATH
$env:ANDROID_HOME='C:/ruta/al/Android/Sdk'
# Opcional: caché compartida de Gradle, fuera de los archivos versionados.
# $env:GRADLE_USER_HOME='C:/ruta/a/cache-gradle'
$env:ANDROID_SERIAL='emulator-5554'
$env:LVLUP_ENV='emulator'
$env:GOOGLE_CLOUD_PROJECT='demo-lvluplife'
$env:GCLOUD_PROJECT='demo-lvluplife'
$env:PORT='8081'
```

No usar cuentas/registros reales ni ADC de producción para estas pruebas.
ServerConfig exige LVLUP_ENV=emulator, el proyecto demo y los hosts locales;
omitir ese modo no activa emulación implícitamente. Configuración Firebase,
credenciales y claves de firma se mantienen fuera de Git.

Resolver Node exactamente desde el lock y comprobar la CLI local:

```powershell
$npmCachePath=Join-Path $PWD '.tooling/npm-cache'
npm.cmd ci --no-audit --no-fund --cache $npmCachePath
.\node_modules\.bin\firebase.cmd --version
```

La caché npm es local y puede apuntarse a otro directorio de prueba. En la
comprobación se usó una caché aislada después de
una extracción incompleta con la caché previa; no se cambió el lockfile.

Puertos que deben estar libres antes de iniciar emuladores: Auth9099,
Firestore8080 (y auxiliares del Emulator Suite). El servicio manual usa8081.
No ejecutar emulators:start y emulators:exec simultáneamente sobre esos puertos.
No detener procesos ajenos para liberarlos: cerrar el proceso propio o elegir
un entorno de prueba sin conflictos.

## 1. Comprobaciones rápidas y validación automática

Build y unitarios no necesitan un servicio manual en8081:

```powershell
.\gradlew.bat assembleDebug :domain:test :server:test testDebugUnitTest --no-daemon
```

Comprobar Auth y las reglas Firestore con clientes reales contra emuladores:

```powershell
npm.cmd run verify:emulators
```

El script levanta y cierra sus emuladores; son esperados cuatro rechazos de
lectura/escritura directa de cliente antes/después de autenticarse. El Admin SDK
del servidor omite reglas y no reemplaza esta comprobación.

Para ejecutar build, unitarios, integración y Compose/Room en el dispositivo
conectado, dejar libres los puertos de emuladores y ejecutar:

```powershell
.\node_modules\.bin\firebase.cmd emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore 'gradlew.bat assembleDebug :domain:test :server:test testDebugUnitTest :server:integrationTest connectedDebugAndroidTest --no-daemon'
```

Firebase configura FIRESTORE_EMULATOR_HOST y FIREBASE_AUTH_EMULATOR_HOST para
el comando hijo. Su launcher Windows usa cmd: allí se escribe gradlew.bat,
sin el prefijo POSIX ./ ni anidar otra consola. La CLI cierra los emuladores al
terminar. Las suites de integración crean servidores/cuentas de test aislados;
no iniciar :server:run para esa ejecución. Sin dispositivo disponible, las
pruebas conectadas quedan pendientes: no equivalen a una validación aprobada.

Para exigir otra ejecución aunque Gradle encuentre resultados UP-TO-DATE,
añadir --rerun-tasks al comando Gradle. Los métodos ProcessStage requieren sus
drivers separados y se excluyen del runner estándar; ver
[matriz de procesos y pantallas](matrix/t080/README.md),
[scripts de recuperación](../../scripts/test-pending-process.ps1) y
[dos instalaciones](../../scripts/test-account-installations.mjs).

La integración incluye por defecto el smoke de PerformanceScenarios. Sus
parámetros, perfil0 y perfiles1000/10000 están documentados en
[performance/t081/README.md](performance/t081/README.md). Sus muestras no se
suman otra vez a los casos JUnit ni acreditan metas de producción.

## 2. Emuladores y servicio manual

Alternativa para depurar el servicio: en una consola con el entorno anterior:

```powershell
.\node_modules\.bin\firebase.cmd emulators:start --project demo-lvluplife --config firebase/firebase.json --only auth,firestore
```

En otra consola, configurar también JDK/proyecto/modo y los dos hosts explícitos:

```powershell
$env:LVLUP_ENV='emulator'
$env:GOOGLE_CLOUD_PROJECT='demo-lvluplife'
$env:GCLOUD_PROJECT='demo-lvluplife'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099'
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8080'
$env:PORT='8081'
.\gradlew.bat :server:run --no-daemon
```

En una tercera consola PowerShell7, comprobar servicio y guardia de identidad:

```powershell
(Invoke-WebRequest 'http://127.0.0.1:8081/v1/me' -SkipHttpErrorCheck).StatusCode
```

Se espera401 sin token; no un perfil público. No introducir tokens en la línea
de comandos, historial ni logs. Para terminar, Ctrl+C en la consola del servidor
y después en la de Firebase. Si ya están activos y se quiere ejecutar solo
integración, usar otra consola con las mismas variables de modo/hosts:

```powershell
.\gradlew.bat :server:integrationTest --no-daemon
```

La preparación de fixtures es exclusiva del arnés de pruebas: no hay endpoints
administrativos de siembra abiertos en el servicio.

## 3. Instalar y abrir Android

La elección posterior del proyecto real `liveluplife`, sus límites y la
preparación pendiente están en [Firebase real](firebase-real.md). No se debe
ejecutar este arnés de emuladores contra esa base ni copiar el JSON de la app
anterior. La configuración para pruebas locales ya está aplicada y el ingreso
Google llegó al alta de nombre; faltan comandos funcionales reales desde Android. El documento enlazado
detalla el modo local sin costo y sus límites de transporte.

Comprobar el dispositivo y luego instalar:

```powershell
$adb=Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adb devices -l
.\gradlew.bat installDebug --no-daemon
& $adb -s $env:ANDROID_SERIAL shell am start -W -n lvluplife.android/lvluplife.MainActivity
```

Debug usa por defecto http://10.0.2.2:8081 para el API local del host. HTTP se
permite solo para localhost/127.0.0.1/10.0.2.2; release exige HTTPS. En API37+, el
acceso al host local requiere el permiso declarado exclusivamente en debug:

```powershell
$api=[int](& $adb -s $env:ANDROID_SERIAL shell getprop ro.build.version.sdk)
if($api -ge 37) {
    & $adb -s $env:ANDROID_SERIAL shell pm grant lvluplife.android android.permission.ACCESS_LOCAL_NETWORK
}
```

Los tests automatizados usan Auth Emulator y adaptadores de acceso de test.
La app manual no agrega un botón de login sintético ni conecta automáticamente
el proveedor Google real a Auth Emulator. Sin configuración Google, instalar y
abrir verifica el arranque/pantalla de acceso, no un login completo.

Para Google real hacen falta un proyecto Firebase de integración autorizado,
registro Android/huellas de firma, proveedor Google, google-services.json local,
OAuth web client ID/default_web_client_id y una API HTTPS configurada del mismo
proyecto mediante -Plvlup.apiBaseUrl. No mezclar ese flujo con el backend demo.
No se crea ni despliega infraestructura para ejecutar esta guía; sin esa
configuración, Google real queda NO DISPONIBLE y no se acredita con emulación.

## 4. Escenarios verificables

| Escenario | Preparación y resultado esperado | Referencia |
|---|---|---|
| Identidad y nombre | Dos identidades compiten por `Martin`/`martin`: solo una obtiene nombre; cada UID recupera su único personaje. | AC-03–05, 90 |
| Registro y progreso | Cuenta bajo topes; 30 min STR →6 XP; 30 min dual →4/2; sueño 30+30 misma fecha →3 MEN; quinta comida se guarda con 0 XP. | AC-25–32, 80–81 |
| Concurrencia | Dos envíos distintos compiten por 1 XP o 60 minutos restantes: nunca exceden tope; dos correcciones desde misma versión no se confirman ambas. | AC-89, 104, 110–111 |
| Fechas y recálculo | Reloj de prueba en UTC−3; 299/300 s, cruce de medianoche, días 6/7 y consultas repetidas conservan resultados exactos de spec. | AC-103, 106–107, 123–125 |
| Reinicio | Cancelar no cambia nada; confirmar conserva datos anteriores y crea solo un ciclo. Registro concurrente respeta orden de confirmación. | AC-63–66, 114–115 |
| Respuesta perdida | Inyectar pérdida después de commit para crear/editar/eliminar/reiniciar; desde la instalación que conserva el pendiente, consultar y reenviar el mismo UUID con sesión válida de la cuenta original conserva resultado sin duplicar nada. Fallo antes de commit no debe simular éxito. | AC-112–118 |
| Cierre y sesión | Cerrar proceso con pendiente, reabrir, vencer sesión e ingresar con otra cuenta: no exposición ni reasignación; cuenta original recupera seguimiento. | AC-118–120 |
| Acceso desde otro dispositivo | A confirma cambios y conserva un pendiente cuya respuesta se perdió después del commit; B inicia sesión con la misma cuenta de Google, consulta los datos guardados y no importa el pendiente ni necesita su UUID. A resuelve su pendiente sin repetir efectos; no se exige a B recuperar operaciones de A. | Historia 1, RF-49, RF-55, AC-117 |
| Dos cargas iguales | Nuevo envío intencional genera registro distinto; doble toque del mismo envío no duplica. | AC-126–127 |
| Consultas y errores | Fallo de consulta no se muestra vacío; datos previos se marcan desactualizados. Rechazo conserva entrada; acceso denegado no reintenta automáticamente. | AC-121–122, 128–131 |
| Aislamiento | Identidades A/B intentan leer/escribir recursos ajenos y Firestore directo: siempre rechazo sin datos ni cambios ajenos. | AC-67–68, SC-007 |
| Clases y UI | Datos preparados prueban Novato, pares, equilibrio y niveles 14/15/34/35; 26 clases y 76 imágenes: Novato solo con Etapa I desbloqueada y sin II/III; las otras 25 con tres etapas, estados y contraste definidos. | AC-51–62, 91–92, SC-011 |

Contratos detallados: [API](contracts/api.md), [UI](contracts/ui.md), [modelo](data-model.md). No duplicar suites completas en este documento.

## 5. Evidencia y mediciones

Registrar comandos, versiones, reloj/fixtures usados y resultados reales. Fallos de red y cierre de proceso deben inyectarse en puntos conocidos antes/después del commit, no deducirse de una excepción genérica. Ejecutar autorización API además de reglas: Admin SDK omite reglas Firestore.

Las muestras de 1.000 reintentos, estabilidad, volúmenes 0/1.000/10.000, recuperación de servicio y estudios con usuarios conservan el carácter orientativo definido en PRD. Las pruebas automatizadas pertinentes de autorización, invariantes, recuperación y progresión siguen siendo obligatorias. No afirmar cumplimiento de p95, disponibilidad, Google real o durabilidad de servicio basándose solo en tests locales.

Para futuros ensayos de rendimiento usar las condiciones del PRD, incluir lecturas del ciclo completo, contención y arranque en frío del servicio; no recortar historial ni imponer un tope funcional para aparentar cumplimiento. Si falla una meta, reportar evidencia y ajustar el diseño antes de declarar cumplimiento.

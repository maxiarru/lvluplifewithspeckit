# T096 — Ajustes de uso tras prueba en teléfono

Fecha: 2026-11-14. Estado: ambos ajustes verificados; revisión aprobada y commit autorizado.

El titular mostró dos «Cargando…» durante el ingreso y un aviso permanente
«Datos confirmados» encima del personaje. La carga se dibujaba tanto en el
contenedor de sesión como en la pantalla que consultaba el perfil. El contenedor
también imprimía cada lectura confirmada aunque su contenido ya estuviera visible.
Las capturas recibidas también acreditan que el titular pudo ingresar desde su
teléfono con la entrega anterior y acceder a su personaje; el primer ingreso
físico que había quedado pendiente en T095 ya fue realizado por el titular.

Se elimina del contenedor el feedback de carga y éxito normal: cada pantalla
conserva su indicación de carga. Se mantienen los avisos de error, desactualización,
resultados vacíos y operaciones pendientes, sin repetir avisos globales del mismo
tipo ni reservar espacio cuando no hay ninguno. El ajuste de avisos no cambia
la autenticación, las consultas, las operaciones ni la persistencia.

La primera entrega se identifica como 0.1.1 (versionCode 2) y se firma con la misma clave
privada externa para actualizar la versión release anterior sin desinstalar.
El APK permanece fuera del repositorio y mantiene la API estable existente.

## Verificación inicial de avisos — 0.1.1

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` con
  la API HTTPS estable: BUILD SUCCESSFUL. 158 tests unitarios, cero fallos/errores.
- Pruebas Android existentes `SessionNavigationTest`, `MainTabsTest` y
  `VisualCaptureTest`: OK (6 tests), en Android 11. Incluyen cierre de sesión,
  aislamiento de cuenta, navegación y conservación de avisos de error, datos
  desactualizados y operaciones pendientes.
- Captura PJ sintética revisada visualmente: empieza directamente en el saludo,
  sin «Datos confirmados» ni espacio reservado para el aviso ausente. Los datos
  sintéticos de la captura no se escriben en Firebase.
- `:app:assembleRelease`: BUILD SUCCESSFUL. Firma v3 y alineación de 16 KB
  verificadas por el script de entrega; misma huella de certificado que 0.1.0.
- Actualización sobre el release 0.1.0 en el emulador separado: `adb install -r`
  exitoso, sin desinstalar; Activity abierta, versión instalada 0.1.1, código 2.
- APK fuera del repo:
  `C:/Cerus/Curso/APKs/LvlUpLife/LvlUpLife-0.1.1-release-2026-11-14.apk`.
- SHA-256:
  `E713381837AADC6F0B47535B6A2FACDD9C576A2F7C9CB7445DF527B17EA74772`.
- Instrucciones y hash externos actualizados. No se modificó el servidor ni se
  realizaron escrituras de ensayo en Firebase. La revisión visual de 0.1.1 en
  el teléfono del titular queda para su prueba de la actualización.

## Duración al registrar y editar — entrega 0.1.2

Antes de aprobar el commit, el titular informó que ingresar una hora sin completar
minutos impedía guardar. Se amplía la misma T096 para admitir un componente vacío
como cero, tanto al registrar como al editar, normalizando únicamente el comando
nuevo y conservando el borrador original. Los reintentos siguen usando el sobre
original persistido; no se reescriben operaciones pendientes.

Ambos campos vacíos o duración cero siguen siendo inválidos, al igual que números
mal formados, minutos fuera de 0–59 o totales mayores a 24 horas. Los límites del
servidor y el cálculo de XP no cambian. Se mueve el error de registro encima del
botón Guardar y se aclaran los límites si se rechaza una duración.

Verificación 0.1.2: `:app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest` aprobados, 161 tests unitarios sin fallos/errores.
Las nuevas regresiones verifican los comandos de creación y edición con un campo
vacío, el máximo 24 h y el rechazo antes de crear/enviar operaciones cuando el
total es cero o inválido. Las duraciones se envían como enteros y se conservan
la versión y el ciclo observados al editar.

- `RecordScreenTest` y `RecordFlowTest`: OK (25 tests), en Android 11. Incluyen
  guardado, mensaje visible de rechazo, conservación del borrador y reintentos.
- `:app:assembleRelease`, firma v3 y alineación de 16 KB: aprobados. Misma clave
  de distribución externa que los releases anteriores.
- Actualización del release 0.1.1 a 0.1.2 mediante `adb install -r`: éxito,
  sin desinstalar; Activity abierta y versionCode 3 verificado.
- APK final externo:
  `C:/Cerus/Curso/APKs/LvlUpLife/LvlUpLife-0.1.2-release-2026-11-14.apk`.
- SHA-256:
  `73E842DBBBD70FBF7777F3D48FAE5DD03C424F3C0FFC3FECC88DCB277482379D`.
- Hash, metadata e instrucciones de la carpeta externa actualizados. No se
  escribieron registros reales ni se alteraron operaciones pendientes del titular.
  Emuladores cerrados conservando datos. Revisión en teléfono a cargo del titular.

Ambos ajustes quedan dentro de T096. El titular aprobó la revisión y autorizó el
commit individual el 2026-11-14, con los límites de verificación documentados.
No se realiza push.

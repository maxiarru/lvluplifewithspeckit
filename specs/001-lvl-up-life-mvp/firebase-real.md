# Firebase real: decisión y preparación

Fecha: 2026-11-12. Evaluación previa a T084; no constituye una prueba de integración real.

## Decisión acordada

Reutilizar el proyecto Firebase `liveluplife` para esta versión del curso.
La versión anterior, con paquete `com.lvlup.life`, no necesita seguir funcionando.
La app actual conserva su applicationId `lvluplife.android`: se debe registrar
como otra app Android dentro del mismo proyecto, sin copiar el JSON anterior.
Los datos anteriores se conservan. No se autorizó borrarlos, migrarlos ni
sobrescribirlos automáticamente. No se modifica el PRD: esta decisión concreta
el entorno técnico sin agregar requisitos de producto o migración.

La configuración para pruebas locales con Firebase real está aplicada. El usuario
completó login Google en Android y se observó la pantalla de alta de nombre.
Faltan los comandos funcionales con esa sesión. No se inició T084.

## Condición de costo y ejecución local

El usuario estableció costo cero como condición obligatoria. Se confirmó por
Google Cloud API `billingEnabled=False`. Se conserva Spark: no se vinculó una
cuenta de facturación, no se activaron créditos de prueba y no se desplegó Cloud Run.
La CLI, IAM y ADC no son un alojamiento del servidor. El JVM corre en esta PC y
debe permanecer activo durante las pruebas. No hay servicio remoto permanente.

En este ensayo debug, Android usa el transporte local HTTP ya permitido por el
proyecto (`10.0.2.2:8081`), con el servidor limitado a `127.0.0.1`. Las conexiones
del SDK a Firebase real usan TLS. Es una excepción de desarrollo local respecto
al recorrido HTTPS remoto previsto; no acredita TLS de una API desplegada ni
permite HTTP en release. No se instala una CA ni se omite validación de certificados.

Arranque desde PowerShell en la raíz, después de `gradlew.bat :server:installDist`:

```powershell
.\scripts\run-firebase-real.ps1
```

El script exige ADC con impersonación de la cuenta dedicada, elimina realmente
las variables de emulación y restaura el entorno del proceso al terminar.
`LVLUP_BIND_HOST=127.0.0.1` limita el listener; sin esa variable se conserva el
comportamiento previo de bind. Se usa Java con wildcard de classpath porque el
BAT generado supera el límite de longitud de comandos Windows.

### Resultado efectivamente comprobado

- ADC guardado localmente y renovación por impersonación aprobada, sin claves
  privadas descargadas. Login CLI y ADC se completaron como autorizaciones distintas.
- JVM usando `ServerResources`: crea, lee y actualiza un documento nuevo en
  `integrationChecks/560de0df-2764-4fa7-8e37-daa451fa4e6b`. Se conserva ese marcador
  diagnóstico; no tiene datos personales y no se pidió permiso de borrado físico.
  Esto acredita IO remoto e IAM, no un comando funcional autenticado de Android.
- Endpoint Admin Auth accesible con la cuenta dedicada; no se exportaron usuarios.
- Consultas con la forma del historial (rango activityDate/orden ID) y filtro
  cycleId aprobadas en rutas de diagnóstico vacías. Los índices automáticos
  existentes resultaron suficientes para estas consultas; no se modificaron índices.
- Reglas deny-all publicadas y release confirmado. Fuente anterior conservada
  localmente en `.tooling/firestore-rules-before-real.json`. La app anterior ya
  no debe depender de acceso directo. Lectura y escritura de cliente sin sesión:
  ambas 403. Cliente autenticado directo todavía pendiente.
- API local real: listener 127.0.0.1:8081, GET /v1/me sin token e inválido: ambos 401.
- Build Android con JSON real aprobado, default_web_client_id generado; APK
  instalado y Activity abierta en emulator-5554. Servicios Google presentes.
- Se reabrió Pixel_6a_API_30 con ventana, sin wipe-data, conservando cuentas y
  datos. El usuario completó el ingreso Google y avisó que se solicitaba nombre.
  Se observó visualmente «Elegí tu nombre de usuario». El flujo real de app
  intercambia Google por Firebase y consulta /v1/me antes de esa pantalla.
  Esto acredita ingreso hasta alta pendiente, sin crear personaje ni registros.
- La inspección de la persistencia debug no permitió obtener el UID de sesión:
  el valor de usuario está cifrado. No se descifró ni exportó contenido privado.
  Una correlación alternativa con la lista de Auth fue rechazada por revisión
  automática antes de ejecutarse, por abarcar más cuentas de las necesarias.
  La compatibilidad de esta cuenta debe verificarse con su UID exacto, sin listar
  otras cuentas; no se asume que el usuario actual sea el de la raíz heredada.
- Tras agregar bind local: :server:test y :server:installDist aprobados, 35 tests,
  cero fallos/errores. La nueva prueba se ejecutó en verde; no se observó un ciclo
  rojo/verde para ese ajuste de configuración.
- Esquema heredado: un identificador bajo users con raíz ausente y datos debajo.
  Se preservó; no se migraron cuentas, reservas ni registros. No usar ese UID
  para escribir antes de acordar cómo preservar/separar su información heredada.

Evidencia resumida en [verification/firebase-real](verification/firebase-real/README.md).
Los resultados T082/T083 no se alteran retroactivamente.

## Avance de configuración

- Registrada en Firebase la app `lvluplife.android`, alias `Lvl Up Life Spec Kit`.
  Se verificó su ficha remota después del alta. La app anterior se conserva.
- Huella SHA-1 debug obtenida con keytool y confirmada como guardada en Firebase.
  No se exportó el keystore ni su clave privada.
- El usuario proporcionó el JSON descargado de Firebase. Se validó proyecto,
  paquete y presencia de clientes OAuth Android y web antes de copiarlo a
  `app/google-services.json`; `git check-ignore` confirma su exclusión.
- `:app:processDebugGoogleServices assembleDebug --no-daemon`: BUILD SUCCESSFUL
  en 52 s, 14 tareas ejecutadas y 26 UP-TO-DATE. La primera invocación dentro del
  sandbox no pudo conectar al daemon; se repitió con el permiso de ejecución
  correspondiente. Log local sin versionar: `.tooling/firebase-real-build.log`.
- Cuenta `lvlup-server-test@liveluplife.iam.gserviceaccount.com` creada y
  confirmada habilitada, sin claves. Todavía sin permisos runtime asignados.
- Google Cloud CLI 584.0.0 descargada desde Google en `.tooling/gcloud-584`,
  archivo oficial verificado por SHA-256. No modifica PATH global. Su configuración
  local se mantiene en `.tooling/gcloud-config`, excluida de Git.
- El intento de login CLI sin navegador no pudo completarse por falta de entrada
  interactiva. No se configuró ADC. La sesión de consola no sustituye este paso.
- Login CLI completado. Rol personalizado `lvlupServerRuntime` creado con los
  seis permisos del manifiesto y asignado a la cuenta dedicada. Operador autorizado
  con Service Account Token Creator solo sobre esa cuenta para impersonación.
- ADC y acceso JVM comprobados posteriormente, según el resultado anterior.

## Observado inicialmente en las consolas, en modo lectura

| Componente | Observación |
|---|---|
| Apps Firebase | Una app Android, `com.lvlup.life`, con una huella SHA-1. No aparece `lvluplife.android`. |
| Authentication | Proveedor Google habilitado. |
| OAuth | Cliente Android para la app anterior y cliente web existentes. Audiencia externa, estado «En producción». |
| Firestore | Base `(default)`, región `southamerica-east1`, con colecciones `accounts`, `usernames` y `users`. |
| Reglas publicadas | Permiten operaciones directas de clientes de la versión anterior; no son las reglas deny-all del repositorio actual. No se verificó exhaustivamente cada cláusula remota. |
| Índices | Sin índices manuales; indexación automática ascendente/descendente por colección habilitada. No se acredita todavía cada consulta del servidor real. |
| Cuenta de servicio | La lista muestra una cuenta Firebase Admin habilitada, sin claves, con roles de administrador de Authentication, agente Firebase Admin SDK y creador de tokens de cuenta de servicio. No aparece una identidad dedicada al servidor actual. |
| Plan | Spark. No se activó prueba gratuita, facturación ni infraestructura. |

La revisión fue visual en Firebase Console y Google Cloud Console; no se
exportaron claves, tokens ni contenido de documentos como evidencia. La sesión
de consola del operador no configura ADC para el proceso JVM del servidor.
La existencia de OAuth y una cuenta de servicio no demuestra acceso funcional.

## Riesgos de reutilización que deben resolverse antes de escribir

El servidor actual usa `users/{uid}` y subcolecciones `records`, `cycles`,
`projections`, `activities` y `operations`; también reserva nombres en
`usernames/{clave}`. Ya hay colecciones `users` y `usernames` en la base real.
Cambiar el paquete Android no separa identidades Auth ni documentos Firestore.

Antes de cualquier escritura, inspeccionar solo el esquema y la existencia de
las rutas de las cuentas de ensayo. Elegir cuentas/rutas sin colisión y nombres
de prueba no reservados. Si aparecen documentos heredados incompatibles, detener
esa preparación y acordar una estrategia que los preserve; no asumir que el
servidor puede interpretarlos ni que un alta sea inocua. Una eventual migración
o separación de namespaces requiere una decisión adicional.

Las reglas deny-all impedirán el acceso directo de la app anterior. Esa pérdida
de funcionamiento es aceptada; no implica borrar sus documentos. Firebase Admin
omite reglas, por lo que IAM y autorización por UID del servidor siguen siendo
necesarios.

## Secuencia acordada y seguimiento

El rol personalizado propuesto está en
[`firebase/server-runtime-role.yaml`](../../firebase/server-runtime-role.yaml).
El rol ya se creó y asignó a la cuenta dedicada. La impersonación se autorizó
al operador sobre esa cuenta, sin concederle al runtime roles administrativos.
Para repetir únicamente el login interactivo CLI desde
la raíz, ejecutar `./scripts/login-firebase-real.ps1`. Ese script no asigna IAM,
no configura ADC, no cambia reglas y no escribe documentos.

La secuencia siguiente conserva el plan acordado; el estado efectivo y sus
excepciones locales están en el resultado anterior. No tratarla como pasos
pendientes en su totalidad.

1. Registrar `lvluplife.android`, comprobar la firma del APK de ensayo y asociar
   su SHA-1; agregar SHA-256 cuando corresponda al flujo elegido. Descargar su
   propio `app/google-services.json`, excluido de Git, y comprobar la generación
   de `default_web_client_id`. Reutilizar el cliente OAuth web del mismo proyecto
   sujeto a esa comprobación; no se necesita su secreto en Android ni en el servidor.
2. Resolver las colisiones de datos indicadas arriba y acordar cuentas de ensayo.
3. Preparar una identidad dedicada al servidor y ADC sin claves descargadas,
   preferentemente mediante impersonación para desarrollo. Revisar permisos
   mínimos contra `server/README.md`, incluyendo acceso a Firestore/transacciones
   y `firebaseauth.users.get`; los permisos para impersonar se tratan por separado.
   No reutilizar como runtime la cuenta administrada de Firebase Admin ni sus
   roles amplios por comodidad.
4. Elegir dónde ejecutar el servidor y cómo proporcionar HTTPS accesible desde
   Android. Servidor local con HTTPS y despliegue remoto son alternativas por
   acordar; no se decidió Cloud Run ni se autorizó facturación.
5. Aplicar, tras revisión del cambio concreto, las reglas deny-all y comprobar
   índices requeridos por las consultas actuales. No desplegar ciegamente el
   archivo de índices sobre configuración ajena. Conservar los datos existentes.
6. Ejecutar el servidor con `LVLUP_ENV=production`,
   `GOOGLE_CLOUD_PROJECT=liveluplife` y `GCLOUD_PROJECT` coincidente si existe.
   Ambas variables de emuladores deben estar ausentes, incluso no vacías.
   Este nombre de modo selecciona servicios reales; no declara una entrega de
   producción. Configurar Android con `-Plvlup.apiBaseUrl` hacia la API HTTPS.

## Validación funcional real pendiente

- Google: ingreso completado por el usuario hasta alta de nombre, observado en
  Android. No se acreditan todavía refresh, revocación o segunda cuenta real.
- API: token Firebase aceptado por Firebase Admin con comprobación de revocación;
  sin token o con token inválido se rechaza. GET /v1/me no crea perfil por sí solo.
- Escritura: completar perfil y registrar una actividad válida mediante comandos
  funcionales de la API, con UUID y protocolo de commit actuales.
- Lectura: recuperar perfil, progreso e historial; reiniciar el servidor y volver
  a leer. Confirmar acceso ADC/IAM efectivo, no solo inicialización del SDK.
- Aislamiento: segunda cuenta no accede a recursos ajenos y cliente directo
  Firestore no puede leer/escribir, incluso autenticado.
- Registrar comandos, entorno y resultados sin credenciales ni datos personales.
  No ejecutar las suites de integración sintéticas contra el proyecto real: están
  diseñadas para `demo-lvluplife` y los emuladores.

T082 y T083 conservan sus resultados históricos y Google real NO DISPONIBLE en
esas ejecuciones. Esta evaluación posterior no convierte sus pruebas en OAuth,
IAM, TLS ni persistencia remota aprobados.

Referencias: [Google Android](https://firebase.google.com/docs/auth/android/google-signin),
[seguridad Firestore](https://firebase.google.com/docs/firestore/security/get-started),
[ADC](https://cloud.google.com/docs/authentication/application-default-credentials).

# T095 — API de producción y APK instalable

**Fecha**: 2026-11-14. **Estado**: entrega revisada; commit individual autorizado por el titular.

## Solicitud y alcance

Tras aprobar y cerrar T094, el titular pidió terminar la entrega: publicar la API,
validar el flujo real y entregar fuera del repositorio un APK firmado para su
teléfono. Se conserva Vercel Hobby y Firebase `liveluplife`, sin habilitar
facturación ni planes pagos. No se hace push.

## Plan de entrega

1. Usar el mismo proyecto Vercel verificado en T094. Agregar únicamente el
   subject de su entorno production a la federación existente, manteniendo los
   IDs exactos de equipo/proyecto y el rol mínimo de datos de la cuenta de servidor.
2. Configurar variables propias de production, sin emuladores. Usar protección
   estándar de Vercel: previews y URLs de despliegue protegidas, dominio estable
   de producción accesible por la app. La API sigue exigiendo Firebase ID token
   y autorización por UID; acceso público a HTTP no habilita acceso público a datos.
3. Publicar el código revisado y comprobar HTTPS, rechazo sin sesión, intercambio
   de identidad y lecturas. Antes de escrituras reales, comprobar la compatibilidad
   de la cuenta exacta, sin listar otras cuentas ni alterar datos heredados.
4. Verificar alta/perfil, registro, progreso e historial según el estado de esa
   cuenta. No reiniciar progresión ni borrar cuenta, actividades o historial.
   Cualquier registro de ensayo debe identificarse y corregirse dentro de la
   ventana permitida, conservando el estado previo. Si aparece una colisión de
   datos heredados, detener la escritura y acordar su preservación.
5. Crear o reutilizar una clave de firma Android de distribución fuera del repo,
   con contraseña protegida localmente. Registrar las huellas públicas de esa firma
   exclusivamente para la app Firebase `lvluplife.android`; verificar OAuth Google.
6. Compilar release con la URL estable, alinear, firmar y verificar el APK.
   Entregar APK, instrucciones y SHA-256 en `C:/Cerus/Curso/APKs/LvlUpLife`,
   manteniendo la clave de firma en una carpeta privada separada.
7. Registrar resultados, límites y material de recuperación. Presentar T095 para
   revisión y decisión de commit individual antes de iniciar otra tarea.

## Criterios de aceptación

- Dominio HTTPS estable accesible sin login Vercel; `/v1/me` sin Firebase token
  devuelve 401 JSON. Las previews mantienen su protección.
- Federación válida exclusivamente para el equipo/proyecto y los entornos
  autorizados preview/production; sin claves de servicio ni aumento de roles.
- Evidencia de las operaciones comprobadas y de cualquier intervención pendiente
  del titular; ninguna limitación se presenta como una validación ejecutada.
- APK release firmado, `debuggable=false`, minSdk 29, API HTTPS estable, firma y
  huellas verificadas; sin secretos privados ni enlaces temporales dentro del APK.
- APK fuera del repo, hash de integridad y conservación de la clave para futuras
  actualizaciones. Planes Hobby/Spark conservados y facturación Google deshabilitada.

## Estado y evidencia

El titular autorizó expresamente dejar el servidor accesible para su teléfono y
sus amigos y darle acceso a Firebase para atender las cuentas autenticadas. Esa
autorización comprende el dominio estable público y el subject production. El
APK es una entrega personal externa al repo, sin publicación en una tienda.

### Servidor

- Proyecto existente `lvlup-life-server-trial`, despliegue production
  `dpl_CbkfWekUkNFBVVRHpaG8yfoJQYrE`, estado READY.
- API incorporada al APK:
  `https://lvlup-life-server-trial-ceruslh-3548.vercel.app`.
- Se desplegó el mismo paquete JVM de 110 archivos verificado en T094. No incluye
  Android, ADC ni claves privadas. No hubo cambios de lógica del servidor.
- Federación limitada al equipo `team_6HCYiMZ3T6X4c4FlVcprlb7a` y proyecto
  `prj_im9FuEBirdp1JWBYoG58QmDbKzeI`, entornos preview/production. Se agregó
  únicamente el subject production a `roles/iam.workloadIdentityUser` de la misma
  cuenta de servidor; su rol de datos no se amplió.
- Variables production propias, sin hosts de emuladores. Protección Vercel
  `prod_deployment_urls_and_all_previews`: dominio estable accesible, previews y
  URLs individuales protegidas.
- GET público `/v1/me` sin sesión: 401 JSON `AUTH_REQUIRED`, sin página de login
  Vercel. Sesión Firebase real desde Android: GET `/v1/me` 200; log production
  `Workload federation ready`, acreditando STS e impersonación.
- Token inválido: 401 JSON. Preview sin enlace compartido: 302 de protección.
- Inspección de metadatos exclusivamente del UID de esa sesión verificada:
  raíz existente, perfil compatible, sin raíz ausente con subcolecciones.
  No se listaron otras cuentas ni se guardaron sus tokens o UID en la evidencia.
- La app debug se actualizó conservando sus datos y usando directamente HTTPS
  estable: abrió el perfil existente y mostró progreso confirmado de nivel 1.
  Se preservó un aviso de operación pendiente que ya existía en esa instalación;
  no se borró su journal ni se forzó el reenvío de comandos anteriores.
- Google Cloud: `billingEnabled=False` comprobado después del despliegue.
  Se conservaron Vercel Hobby y Firebase Spark, sin activar planes pagos.

### APK

- Archivo externo:
  `C:/Cerus/Curso/APKs/LvlUpLife/LvlUpLife-0.1.0-release-2026-11-14.apk`.
- 134859746 bytes; applicationId `lvluplife.android`; versión 0.1.0 (código 1),
  mínimo Android 10/API 29; release sin `debuggable`.
- SHA-256:
  `246D390B27D55CE35E618E502D9224D731D55B61055AEB4A875A35D09A52573F`.
- Clave RSA 3072 de distribución fuera del repo y de la carpeta compartible,
  protegida por ACL de usuario/SYSTEM y contraseña cifrada con DPAPI de Windows.
  `initialize-android-release-signing.ps1` reutiliza la clave existente.
  `build-installable-apk.ps1` compila, alinea, firma, verifica manifiesto/firma
  y copia al destino externo; ambos scripts se ejecutaron satisfactoriamente.
- Huellas SHA-1 y SHA-256 públicas registradas únicamente para la app Firebase
  Android correcta. Configuración local descargada y validada: cliente OAuth
  Android de release presente y cliente OAuth web conservado. No se versionó JSON.
- `apksigner verify --verbose --print-certs`: firma v3 válida; `zipalign -c -P 16 4`
  aprobado. Huella SHA-256 del certificado:
  `1A268A58E1A8279B0826777EEEACA02BF5B3504B565E6DA9AE83EBABB82DE224`.
- APK final instalado con éxito en un emulador Android 11 separado, sin borrar
  la instalación anterior; Activity abierta y pantalla «Continuar con Google».
- `:app:testDebugUnitTest :app:assembleDebug` con la API estable: BUILD SUCCESSFUL,
  158 tests, cero fallos/errores. `:app:assembleRelease`: BUILD SUCCESSFUL.
- Sintaxis PowerShell de ambos scripts y `git diff --check`: aprobados. Se cerraron
  ambos emuladores y el diagnóstico temporal; sus credenciales no se persistieron.
- Carpeta de entrega: APK, `SHA256-release.txt`, `release-metadata.json` e
  instrucciones. La clave privada no se entrega junto al APK.

### Límites y recuperación

No se creó otro perfil ni se registraron/corrigieron actividades reales durante
T095. No se atribuyen como ejecutadas esas comprobaciones: sus contratos siguen
respaldados por los tests de servidor de T094. El primer ingreso Google con la
firma release y el uso en el teléfono físico quedan para la prueba del titular;
se verificaron su configuración OAuth y la instalación/apertura del APK. No se
probó una segunda cuenta real ni una renovación de credenciales durante horas.

La carpeta privada `C:/Users/maxi1/.lvlup-life-signing` debe conservarse para
firmar futuras actualizaciones. Su README explica que DPAPI depende de esta
cuenta de Windows: copiar sus archivos a otra máquina no basta para recuperar
la contraseña. Antes de migrar/reinstalar Windows se necesita una copia privada
de la clave y recuperación segura de su contraseña desde la cuenta actual.

El APK debug anterior queda identificado como una prueba para emulador. Si está
instalado en un teléfono, su firma distinta impide actualizarlo directamente con
release; las instrucciones advierten conservar pendientes antes de desinstalar.
No se desinstaló ni se borró información del emulador existente.

T094 y su evidencia permanecen en [vercel-trial.md](vercel-trial.md). El titular
aprobó la revisión y autorizó el commit individual de T095 el 2026-11-14, con los
límites de verificación documentados. No se realizó push.

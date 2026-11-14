# Resultado de la preparación — 2026-11-14

Estado: prueba de viabilidad con federación y lectura Android/Firebase reales aprobada; revisión cerrada y commit autorizado por el titular.
Código de partida: `9cf4546`, con árbol limpio antes de esta preparación.

## Ejecutado

- `scripts/prepare-vercel-trial.ps1`: paquete de 108 archivos y 712.421 bytes,
  incluyendo `SHA256SUMS`. Fuentes y configuración elegidas explícitamente.
  No incluye Android, `.tooling`, `google-services.json`, ADC ni claves privadas.
  El wrapper Linux se normaliza a LF.
- Build independiente desde el paquete, sin `ANDROID_HOME` ni
  `ANDROID_SDK_ROOT`, con JDK 17.0.21+9 y el wrapper Gradle 9.5.0:
  `:domain:test :server:test :server:installDist --no-daemon --console=plain`.
  Resultado: **BUILD SUCCESSFUL** en 1 min 25 s; 323 pruebas de dominio y
  39 de servidor, sin fallas ni errores. No se modificaron reglas de dominio.
- Distribución generada iniciada mediante `java -cp lib/*
  lvluplife.api.ServerKt` en `127.0.0.1:18081`, con el modo emulador explícito
  `demo-lvluplife`, sin ADC local. El proceso terminó al cerrar la comprobación.
- Sin Authorization: `/v1/me`, `/v1/progress`, `/v1/catalog` y
  `/v1/history?period=day&date=2026-11-14` devolvieron **401**.
  `/v1/me` con un token deliberadamente inválido también devolvió **401**.
  Estas peticiones no escriben datos. No se conectó a Firebase real.

Evidencia local no versionada:
`.tooling/vercel-trial/20261114-153924-129/`: informes JUnit bajo cada módulo,
`smoke-results.json` y salida del proceso. Esa carpeta contiene resultados de
build y no es el paquete limpio que se debe subir.

## Ejecución remota

- Conexión y autorización del equipo `ceruslh-3548` verificadas. La cuenta
  autenticada informa `billing.plan=hobby`, `status=active`, `trial=null`.
- Proyecto exclusivo: `lvlup-life-server-trial`
  (`prj_im9FuEBirdp1JWBYoG58QmDbKzeI`), framework Container, fuente privada y
  protección de Vercel activa. Configuración inicial de prueba solo en preview:
  proyecto `demo-lvluplife`, modo emulador y endpoints loopback. No da acceso a
  Firebase real; esos endpoints no tienen emuladores desplegados.
- Primer despliegue `dpl_CHsDVwtrhATF86Z4QpDD7aqqp1Fg` cancelado al comprobar
  que Vercel asigna producción al primer despliegue aunque se omita `target`.
  La API rechaza `target=preview`; su valor explícito para preview es `staging`.
- Despliegue de prueba `dpl_FiYz1iCY9bhjxQBARUgRDNc4EYkb`, destino `staging`
  (entorno preview): **READY**. Construcción Linux/amd64 mediante Buildah,
  JDK/JRE Temurin 17 y Gradle 9.5.0; build completado en aproximadamente 3 min.
  URL: `https://lvlup-life-server-trial-l6w3ukjn5-ceruslh-3548.vercel.app`.
  La imagen final es
  `sha256:3884071fedc9ee841781e57280eda1760a1bf51fef188bb19819da718d3ff415`.
- Tras la aprobación explícita del usuario del enlace temporal, se comprobó el
  acceso HTTPS manteniendo la protección de Vercel. Sin sesión: `/v1/me`,
  `/v1/progress`, `/v1/catalog` y `/v1/history?period=day&date=2026-11-14`
  devolvieron **401**, `application/json`, código **AUTH_REQUIRED**.
  `/v1/me` con un token deliberadamente inválido también devolvió ese rechazo.
  Primera petición: 4.952 ms; medición puntual, no un percentil de rendimiento.
  Evidencia sin cookies ni enlace: `.tooling/vercel-trial/remote-smoke-results.json`.
- La revisión automática rechazó la creación explícita de un enlace compartible
  de hasta 23 horas porque ampliaría el acceso al despliegue privado. Se pidió
  aprobación explícita al usuario, que luego la concedió. No se desactivó la
  protección. El enlace se reutilizó únicamente después de esa aprobación.
- Se comprobó, sin modificar IAM, la identidad existente
  `lvlup-server-test@liveluplife.iam.gserviceaccount.com` y su rol
  `projects/liveluplife/roles/lvlupServerRuntime`: los seis permisos del archivo
  `firebase/server-runtime-role.yaml`. No hay proveedores WIF existentes.

## Federación y lectura reales

- Despliegue `dpl_L8WkR61KE23ZRUb1zozsaA4Qwk31`, preview, **READY**:
  `https://lvlup-life-server-trial-qc0dx8xtu-ceruslh-3548.vercel.app`.
- Preview se configuró con `liveluplife`, modo production del backend (servicios
  Firebase reales) y los siete valores de federación. Las variables de emuladores
  quedaron limitadas a development y no están presentes en preview.
- Se habilitaron IAM, IAM Credentials y STS. Facturación consultada después:
  **False**. No se cambió el plan de Vercel ni se habilitó facturación Google.
- Preflight con token de usuario inválido: **401 AUTH_REQUIRED**, 1.824 ms desde
  el acceso de ensayo. El log `Workload federation ready` acredita que el SDK
  completó el intercambio STS y la impersonación antes de rechazar ese token.
- La app instalada en `Pixel_6a_API_30` restauró su sesión Google existente y
  obtuvo **200** en `/v1/me`, mediante Vercel y Firestore real. Se usó un proxy
  exclusivamente local, con cookies y tokens solo en memoria, sin imprimir
  respuestas de perfil ni UID. Primera lectura: 11.751 ms.
- Tras más de 45 s sin peticiones al despliegue se repitió la lectura desde la
  app: **200**, 7.671 ms. Los logs muestran otra inicialización de federación.
  Son dos muestras, no percentiles ni garantía de cumplir siempre el timeout.
- Solo `/v1/me` llegó a la API en esta comprobación Android. Las otras consultas
  que intentó la app y los comandos quedaron bloqueados localmente. `/me` solo
  lee la cuenta: no se acreditan escrituras funcionales con el usuario real.
- Evidencia local sin tokens, cookies ni cuerpos de perfil:
  `.tooling/vercel-trial/federation-preflight.json`, `proxy.stdout.log` y
  `proxy-cold.stdout.log`. Proxy cerrado y emulador apagado al terminar; datos
  del emulador conservados. Los enlaces de ensayo tienen vencimiento temporal.

## Límites y revisión

- La lectura con sesión Firebase real está aprobada. No se probaron comandos
  reales de escritura, una segunda cuenta real ni una entrega pública con APK.
- El despliegue `dpl_BUzekbv2Y99TFWskipi9gxsnPWPy` confirmó arranque en preview
  y **401 AUTH_REQUIRED**, pero informó que el archivo OIDC rotativo no está
  disponible. Se reemplazó esa propuesta por `VercelWorkloadIdentity`, que recibe
  tokens por petición y verifica firmas RS256, issuer, audience, vencimiento y
  equipo/proyecto/entorno antes del intercambio STS. No conserva tokens en disco.
  La sesión del usuario sigue verificándose separadamente con Firebase Admin.
- Adaptador compilado y 49 pruebas unitarias de servidor aprobadas (10 nuevas),
  incluyendo firmas RSA reales con claves sintéticas/JWKS local, token falsificado,
  claims incorrectos, expiración/rotación, configuración incompleta y la imposibilidad
  de autenticar un usuario únicamente con el token de Vercel. Esas pruebas locales
  se complementan con el intercambio STS real descrito arriba. El endpoint público de claves fue confirmado
  por discovery: `https://oidc.vercel.com/ceruslh-3548/.well-known/jwks`.
- Federación OIDC configurada con la identidad de permisos mínimos de Firebase.
  Intercambio real comprobado; expiración/rotación cubiertas localmente. No se
  observó la renovación prolongada de una instancia caliente durante dos horas.
  La revisión automática rechazó crear el pool/proveedor y conceder impersonación
  porque la autorización de la prueba no cubría ese cambio IAM persistente exacto.
  Se preparó la configuración limitada a equipo/proyecto/preview y se pidió
  aprobación explícita. El titular luego autorizó la federación limitada si era
  la alternativa recomendada; se confirmó esa recomendación y se aplicó el
  pool/proveedor y el binding exactos. La lectura posterior confirmó proveedor
  ACTIVE y las condiciones por equipo/proyecto/preview esperadas. No se crearon
  claves ni se amplió el rol de datos de la identidad existente.
- Regresión completa con Auth/Firestore Emulator, proyecto `demo-lvluplife`:
  `:server:integrationTest`, **BUILD SUCCESSFUL**, 238 pruebas, cero fallas y errores.
  Tras confirmar el endpoint JWKS público se repitió `:server:test`: 49 pruebas,
  cero fallas y errores. No se cambiaron las reglas de dominio.
- Se repitió la integración del servidor con emuladores (238 pruebas). No se
  ejecutó la suite instrumentada Android completa; se probó directamente la app
  existente con su sesión Google y la lectura remota de perfil.
- La entrega pública y el APK final requieren una tarea posterior a la revisión
  de T094. El permiso aprobado se limita a preview y la API conserva protección
  de Vercel: el APK entregado anteriormente mantiene su endpoint del emulador.

No hubo cambio de plan ni habilitación de facturación. El titular autorizó el
commit individual de T094 el 2026-11-14. No se autorizó push.

Seguimiento de SpecKit: [T094](../../specs/001-lvl-up-life-mvp/vercel-trial.md),
con referencias en spec, plan y tasks. La revisión de la tarea está cerrada.

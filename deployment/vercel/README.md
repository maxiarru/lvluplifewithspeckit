# Prueba de Vercel Hobby

Esta preparación conserva el servidor Ktor y Firestore. No publica nada, no activa
facturación y no copia la configuración ADC de esta PC.

## Preparar el paquete

Desde PowerShell 7 en la raíz:

```powershell
$uploadPath = ./scripts/prepare-vercel-trial.ps1
```

El script crea un directorio nuevo en `.tooling/vercel-trial/`, con un build Gradle
independiente que incluye solamente `domain` y `server`. Usa las mismas fuentes,
versiones y lockfiles del proyecto, sin configurar Android ni requerir su SDK.
Incluye las pruebas JVM; `SHA256SUMS` permite revisar la lista exacta de archivos.
Solo se debe subir ese directorio, nunca la raíz del repositorio.

Para comprobar ese build localmente, con JDK 17 configurado:

```powershell
& "$uploadPath/gradlew.bat" -p $uploadPath :domain:test :server:test :server:installDist --no-daemon
```

Con Docker disponible, desde la raíz:

```powershell
docker build --tag lvlup-vercel-trial $uploadPath
```

La imagen vuelve a ejecutar las pruebas JVM al compilar. El runtime ejecuta solo
la distribución JVM, como usuario sin privilegios, y escucha en `0.0.0.0:8081`.
El `Dockerfile` es para una prueba: las imágenes base usan etiquetas de Java 17;
no se declara una imagen reproducible por digest ni una entrega de producción.

## Condiciones de la prueba remota

1. Verificar que la cuenta/equipo destino usa **Hobby**. No activar Pro, pruebas
   pagas, complementos ni facturación en Google Cloud.
2. Crear un proyecto exclusivo para la prueba y seleccionar Container.
3. Configurar `PORT=8081`, `GOOGLE_CLOUD_PROJECT=liveluplife`,
   `LVLUP_ENV=production` y `LVLUP_BIND_HOST=0.0.0.0`. No configurar variables de
   emuladores. El `PORT` explícito es necesario: Vercel usa 80 por defecto y el
   servidor usa 8081.
4. Preparar credenciales federadas OIDC para el proyecto y entorno exactos de
   Vercel, con una identidad de permisos mínimos en Google Cloud. El servidor ya
   usa ADC en otros entornos. En Vercel, `VercelWorkloadIdentity` recibe el token
   por `x-vercel-oidc-token`, verifica firma RS256 con JWKS de Vercel, issuer,
   audience, vencimiento y equipo/proyecto/entorno exactos; luego Google STS
   verifica la confianza IAM antes de impersonar la cuenta mínima. Nunca subir
   ADC local, refresh tokens del operador ni claves privadas de servicio.

   Variables adicionales requeridas en preview:

   - `LVLUP_WIF_PROVIDER=projects/<numero>/locations/global/workloadIdentityPools/<pool>/providers/<proveedor>`
   - `LVLUP_WIF_SERVICE_ACCOUNT=<identidad>@<proyecto>.iam.gserviceaccount.com`
   - `LVLUP_VERCEL_TEAM_SLUG`, `LVLUP_VERCEL_TEAM_ID`
   - `LVLUP_VERCEL_PROJECT_NAME`, `LVLUP_VERCEL_PROJECT_ID`
   - `LVLUP_VERCEL_ENVIRONMENT=preview`

   Exige `VERCEL=1` y modo production (Firebase real). Rechaza otro ADC simultáneo
   y la opción de omitir firmas de la biblioteca OAuth. No se deben copiar estas
   variables a los emuladores. La identidad federada permite acceso del servidor;
   cada usuario sigue necesitando su propio Firebase ID token. El adaptador no
   usa el token de Vercel para elegir un UID.
5. Publicar únicamente el paquete preparado. Verificar arranque, HTTPS y
   `/v1/me` sin credenciales: debe responder 401, sin información privada.
6. Con una cuenta de ensayo acordada, comprobar login, perfil, registro, progreso,
   historial y reintento sin duplicados. No ejecutar suites sintéticas contra
   Firebase real ni modificar datos heredados.
7. Comprobar otra petición después de que el contenedor quede inactivo y vuelva
   a arrancar; los datos deben conservarse en Firestore. Medir su demora frente
   al timeout actual de Android (15 s) y la renovación de credenciales.
8. Solo con la prueba remota aprobada, compilar un APK con
   `-Plvlup.apiBaseUrl=https://<dominio-de-la-prueba>`.

Hasta superar esos pasos, no se acredita compatibilidad funcional con Vercel ni
se reemplaza el endpoint del APK entregado.

La primera creación de despliegue puede ser asignada a producción por Vercel.
En la API, indicar `target=staging` para esta prueba preview; `preview` no es un
valor válido de ese campo. Verificar siempre el destino devuelto antes de seguir.

## Referencias consultadas

- [Container Runtime](https://vercel.com/docs/functions/runtimes/container): beta
  en todos los planes; contenedor HTTP; escala a cero tras inactividad.
- [Hobby](https://vercel.com/docs/plans/hobby): cuotas gratuitas y suspensión al
  superar límites.
- [OIDC con Google Cloud](https://vercel.com/docs/oidc/gcp) y
  [referencia OIDC](https://vercel.com/docs/oidc/reference): federación sin claves
  de servicio; validar especialmente la renovación en Java/contenedores.

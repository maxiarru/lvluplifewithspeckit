# T094 — Prueba de alojamiento en Vercel Hobby

**Fecha**: 2026-11-14. **Estado**: prueba de viabilidad cerrada con revisión aprobada y commit autorizado por el titular.

## Solicitud y alcance autorizado

Después del MVP y de la identidad visual, el titular pidió un APK para usar en
su teléfono. Se comprobó que la API seguía ejecutándose en la PC y que el APK
debug apuntaba a `10.0.2.2:8081`, una dirección del emulador Android.

El titular autorizó probar Vercel, conectó su cuenta, autorizó el acceso al equipo
`ceruslh-3548` y aprobó un enlace temporal de hasta 23 horas para verificar el
servidor protegido. La cuenta consultada usa Hobby activo, sin prueba paga.
Se mantiene la condición de no habilitar facturación ni activar planes pagos.

La prueba conserva Kotlin/Ktor, Firebase Authentication y Firestore. No cambia
reglas de XP, privacidad por UID, contratos funcionales ni datos de cuentas.
El destino de ensayo es `lvlup-life-server-trial`, entorno preview. No constituye
una aprobación del alojamiento definitivo ni de una entrega de producción.

## Decisión técnica en evaluación

- Empaquetar únicamente `domain` y `server` en un contenedor Java 17. La
  compilación independiente excluye Android, sus credenciales y las herramientas
  locales. Las versiones y lockfiles existentes se conservan.
- Mantener la protección de Vercel durante la prueba. El enlace temporal permite
  verificar el despliegue; no se incorpora al APK ni reemplaza el login Google.
- Usar Workload Identity Federation para el acceso de Vercel a Google, sin claves
  privadas ni ADC del operador en el despliegue.
- El contenedor no ofrece `VERCEL_OIDC_TOKEN_FILE`. El adaptador preparado recibe
  `x-vercel-oidc-token`, verifica firma RS256 y los claims esperados, y lo entrega
  al proveedor de credenciales de Google. La identidad de cada usuario continúa
  verificándose con Firebase Admin de forma independiente.

## Federación limitada autorizada

Se creó el pool/proveedor `lvlup-vercel-trial/vercel` en `liveluplife` y se permitió
impersonación de `lvlup-server-test@liveluplife.iam.gserviceaccount.com` únicamente
al equipo `team_6HCYiMZ3T6X4c4FlVcprlb7a`, proyecto
`prj_im9FuEBirdp1JWBYoG58QmDbKzeI`, entorno `preview` y subject exacto
`owner:ceruslh-3548:project:lvlup-life-server-trial:environment:preview`.

La identidad tiene el rol existente `lvlupServerRuntime`: lectura, listado,
creación y actualización de entidades Firestore, acceso a la base y verificación
de usuarios Auth. No se propone ampliar ese rol.

La revisión automática rechazó el cambio IAM por ser un permiso persistente no
cubierto explícitamente por la autorización inicial de la prueba. Se pidió
aprobación del cambio exacto. El titular pidió una recomendación; se recomendó
autorizar la federación limitada. Esa consulta no se trató como consentimiento.
Posteriormente el titular autorizó esa alternativa si era la opción técnica
correcta. Se confirmó que la federación sin claves era la opción recomendada para
esta prueba en Vercel y se aplicó exclusivamente el permiso detallado arriba.
La configuración remota se volvió a consultar: proveedor ACTIVE, issuer/audience
del equipo y condición por IDs de equipo/proyecto y entorno preview correctos.
El rol de datos existente no se amplió.

## Evidencia y pendientes

- Contenedor Linux construido en Vercel: READY.
- HTTPS: cuatro consultas sin sesión y una petición con token inválido devolvieron
  401 JSON con `AUTH_REQUIRED`. Primera petición medida: 4.952 ms.
- Adaptador: 49 pruebas unitarias de servidor, incluidas 10 nuevas, sin fallas.
- Regresión con Auth/Firestore Emulator: 238 pruebas de integración, sin fallas.
- Despliegue real de la prueba: `dpl_L8WkR61KE23ZRUb1zozsaA4Qwk31`, READY,
  entorno preview. El log `Workload federation ready` se emitió tras el intercambio
  STS y la obtención de credenciales mediante impersonación de la cuenta mínima.
- La instalación Android existente, conservando su sesión Google, consultó
  `/v1/me` a través de la API de Vercel y Firebase real: **200**. El acceso local
  de ensayo no imprimió tokens, UID ni respuestas de perfil. La consulta `/me`
  solo lee la cuenta; no se enviaron comandos de escritura.
- Se repitió la consulta después de más de 45 segundos sin peticiones: **200**,
  7.671 ms. El nuevo log de federación acredita otra inicialización de credenciales.
  La primera consulta desde Android tardó 11.751 ms. Son muestras puntuales,
  no una medición de percentiles ni una garantía de latencia.
- Se habilitaron las APIs IAM, IAM Credentials y STS requeridas por la federación.
  Se volvió a consultar la facturación: `billingEnabled=False`. Hobby se conservó.
- La rotación/expiración del proveedor se cubrió con pruebas unitarias; no se
  observó un proceso caliente durante dos horas ni una renovación de larga duración.
- No se acreditan todavía comandos funcionales con la cuenta real, otros usuarios
  reales, entrega pública de producción ni APK autónomo conectado a esa entrega.
- El proxy de ensayo y el emulador que se iniciaron para esta prueba se cerraron.
  Los datos del emulador se conservaron. El despliegue permanece protegido.
- El titular aprobó la revisión de esta prueba y autorizó su commit individual el
  2026-11-14. La preparación de una entrega pública con su APK corresponde a la
  siguiente tarea autorizada. No se autorizó push.

Resultados detallados y límites:
[`deployment/vercel/verification.md`](../../deployment/vercel/verification.md).
Preparación reproducible:
[`deployment/vercel/README.md`](../../deployment/vercel/README.md).

Este registro se incorporó retrospectivamente durante la prueba. No modifica
las aprobaciones históricas ni presenta la planificación como anterior al código.

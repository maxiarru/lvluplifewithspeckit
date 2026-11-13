# Servicio de Lvl Up Life

Servidor JVM 17/Ktor. Firestore es la fuente de verdad; los clientes Android no
acceden directamente a sus documentos. La configuración de T012 abre las
conexiones y verifica identidad; los handlers de datos aún no implementados
responden 503. No se despliega ni se crea infraestructura con estos comandos.

## Emuladores locales

Desde la raíz, con JDK 17 y Node disponibles en PATH, ejecutar `npm.cmd ci` si
todavía no se instalaron las herramientas fijadas en package-lock.json. Después:

```powershell
$env:LVLUP_ENV = 'emulator'
$env:GOOGLE_CLOUD_PROJECT = 'demo-lvluplife'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080'
.\node_modules\.bin\firebase.cmd emulators:start --project demo-lvluplife --config firebase/firebase.json --only auth,firestore
```

En otra terminal, configurar las mismas cuatro variables y ejecutar:

```powershell
.\gradlew.bat :server:run
```

El servicio escucha en PORT (8081 por defecto). Auth usa 9099 y Firestore 8080;
los hosts permitidos en este modo son únicamente 127.0.0.1/localhost. El modo
emulador exige el proyecto exacto demo-lvluplife, ambos endpoints y coincidencia
con las variables reales del proceso que utiliza Firebase Admin. No requiere
ADC: Firestore usa las credenciales de emulación de su SDK y Auth recibe
credenciales vacías, sin token ni capacidad de refrescar frente a Google.
Se fijan host y emulatorHost a 127.0.0.1:8080; localhost se normaliza a esa IP.
No se usa NoCredentials, que selecciona un camino heredado del SDK que ignora
emulatorHost. Las pruebas comprueban ambos destinos antes de cualquier RPC.
Nunca hay fallback de la aplicación a producción.

Pruebas reproducibles contra emuladores, desde una terminal con JAVA_HOME/JDK 17
y las cuatro variables anteriores:

```powershell
.\node_modules\.bin\firebase.cmd emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore ".\gradlew.bat :server:test :server:integrationTest --write-locks --no-daemon"
```

FirestoreAccessTest falla si no está en el entorno demo explícito, en lugar de
omitirse o probar un proyecto real. Usa cuentas/IDs sintéticos distintos por
ejecución; no imprime tokens. Cubre namespaces por UID, cierre/reapertura de
cliente, datos conservados, rutas relativas inválidas, error seguro y un token
real del emulador verificado por Firebase Admin. Las otras suites de integración
se añadirán en sus tareas; esto no acredita aún atomicidad/idempotencia ni Google
real. La reconexión probada reconstruye el cliente; no simula una caída de red.

IsolationTest usa tokens reales de Auth Emulator con el mismo serverModule que
main. Comprueba que parámetros de propietario, rutas adivinadas y UID en comandos
no seleccionan otra cuenta; una cabecera X-User-Id tampoco sustituye al token.
El mismo UUID en cuentas distintas tiene recibos independientes. Un nombre ocupado
no revela al propietario ni modifica sus datos. Comprueba también63 intentos REST
directos a Firestore:7 rutas privadas/globales ×3 identidades (sin sesión,
propietario y otra cuenta) × GET/PATCH/DELETE, todos403 PERMISSION_DENIED y sin
alterar las instantáneas preparadas por Admin. Incluye cuenta, ciclo, proyección,
actividad personalizada, registro, recibo y reserva de nombre.

El alcance es US1 y la frontera común de confianza. Consultas/comandos aún no
implementados de progreso/registros/historial tendrán sus pruebas en sus historias;
un503 de funcionalidad pendiente no se considera evidencia de autorización.
La prueba nativa de dos instalaciones usa el runtime Netty local y está documentada
en [app/README.md](../app/README.md). Ninguna de estas pruebas acredita Google real,
IAM, TLS de producción ni un despliegue remoto.

## Producción y ADC

Para el ensayo autorizado con `liveluplife`, usar
[`scripts/run-firebase-real.ps1`](../scripts/run-firebase-real.ps1). Exige ADC con
impersonación de la cuenta dedicada y limita el listener a localhost mediante
`LVLUP_BIND_HOST=127.0.0.1`. Sin esta variable se conserva bind a `0.0.0.0`;
solo esos dos valores son válidos. Proyecto, evidencia, condición de costo cero
y validación funcional pendiente en
[Firebase real](../specs/001-lvl-up-life-mvp/firebase-real.md).

LVLUP_ENV ausente o `production` selecciona producción. GOOGLE_CLOUD_PROJECT es
obligatorio; GCLOUD_PROJECT, si existe, debe coincidir. Se rechazan proyectos
demo y la presencia de cualquiera de las variables de emulación, incluso vacías.
PORT debe estar entre 1 y 65535. Los errores de configuración nombran la clave,
sin reflejar el valor recibido. No se registran credenciales ni causas privadas
de errores de inicialización o persistencia.

Se utiliza GoogleCredentials.getApplicationDefault(). En Cloud Run, la identidad
prevista es una cuenta de servicio de runtime dedicada, adjunta al servicio,
mediante ADC del entorno. En desarrollo autorizado puede usarse ADC local o
federación/impersonación ya configurada. No se distribuyen archivos de claves de
servicio ni se guardan en Git, Room o logs. No se configura ADC ni IAM remoto con
esta implementación.

Firestore usa el endpoint del SDK con TLS en producción y la base `(default)`.
La entrada HTTP local de Ktor no acredita HTTPS del despliegue. El futuro entorno
de despliegue deberá proporcionar HTTPS/TLS mínimo 1.2; no se ha desplegado ni
medido esa conexión. Las comprobaciones de sesión llaman verifyIdToken con
checkRevoked=true; no se autorizan operaciones si falla el proveedor.

## IAM mínimo previsto para el runtime del MVP

Definir un rol personalizado con estos permisos, asignado solo a la identidad
del servicio y al proyecto/base necesarios, con el alcance más reducido que
admita cada recurso. El alcance IAM de Firestore no sustituye la autorización
por UID: las bibliotecas de servidor omiten las Security Rules.

| Permiso | Motivo concreto |
|---|---|
| datastore.entities.get | Leer cuenta, registros, proyección y recibos propios seleccionados por el servidor. |
| datastore.entities.list | Consultar registros y actividades para replay e historial del MVP. |
| datastore.entities.create | Crear documentos de cuenta, ciclos, registros, actividades y recibos. |
| datastore.entities.update | Actualizar revisión/proyección, corregir registros y marcar tombstones. |
| datastore.databases.get | Comenzar o revertir las transacciones de revisión/commit previstas en T087. |
| firebaseauth.users.get | Verificar revocación y deshabilitación de la identidad autenticada. |

No se requiere datastore.entities.delete: eliminar registros en el MVP usa
tombstones. No se conceden permisos para crear/borrar bases, índices, recursos,
cuentas Auth, políticas IAM o claves. Las altas de Auth del test solo suceden en
el emulador y no justifican permisos administrativos en producción. Tampoco se
conceden Owner, Editor, Firebase Admin ni roles de service agent al runtime.
Permisos de despliegue, impersonación e invocación de Cloud Run pertenecen a
identidades/políticas separadas y no se han configurado aquí.

La lista cubre el runtime del MVP planeado; las lecturas y transacciones se
validaron contra emuladores locales. **IAM remoto no probado**: los
emuladores no acreditan estos permisos ni los límites/región de producción.

Fuentes: [IAM Firestore](https://firebase.google.com/docs/firestore/security/iam),
[permisos Auth](https://docs.cloud.google.com/iam/docs/roles-permissions/firebaseauth),
[Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth).

## Límites del adaptador

FirestoreAccess solo construye rutas bajo users/{uid}; acepta raíz o pares
colección/documento relativos, sin segmentos vacíos, barras, controles, puntos de
escape ni IDs de más de 1500 bytes. El UID debe provenir de la sesión verificada;
aceptar un UID del cliente antes de llamar al adaptador rompería esa autorización.

Las peticiones esperan como máximo 10 s y cancelan el future si falla la espera.
Una escritura con timeout puede haber sido aplicada: la capa superior debe
conservar el resultado desconocido y consultar recibo; nunca tratarla como rechazo
funcional. Los errores del SDK no se exponen ni se retienen como causas públicas.

read/write son primitivas de IO; **ningún escritor funcional debe usarlas fuera
del protocolo de revisión y commit atómico** de RevisionTransaction. CompleteProfile
lo reutiliza para cuenta, ciclo, proyección, reserva de nombre y recibo; las rutas
HTTP del alta usan este servicio mediante accountHandlers. Los fixtures que escriben fuera del protocolo
son exclusivamente datos sintéticos/corrupción deliberada en emuladores aislados.
No hay rutas para fixtures, reloj ni inyección de fallos en producción.

Las reservas globales de nombre se leen antes de escribir y se crean con
precondición de ausencia, dentro del mismo commit por UID. La revisión sigue
controlada por el protocolo; los conflictos obligan a reconstruir el candidato,
con un máximo de cuatro intentos del caso de uso además del límite del SDK.
Los fallos transitorios conservan resultado desconocido para el cliente.

El ID de reserva usa usernameKey salvo que coincida con `__.*__`: en ese caso
se antepone `~` para cumplir las restricciones de Firestore sin prohibir nombres
válidos. Las reglas de acceso directo siguen deny-all; el cliente no conoce ni
administra estas rutas. No se agregaron permisos IAM ni recursos remotos.

## Rutas de cuenta

El módulo usado por main instala accountHandlers y el verificador Firebase de
ServerResources. GET /v1/me solo consulta el perfil del UID verificado; no da de
alta por autenticar. POST /v1/commands con COMPLETE_PROFILE invoca el servicio
atómico existente. IO bloqueante de Firestore corre en Dispatchers.IO.

Los recibos confirmados devuelven200; rechazos funcionales persistidos,409/422
con código/mensaje seguros. Otros comandos y consultas mantienen503 hasta sus
respectivas historias. No se devuelve requestHash ni claves de reserva en la API.
La prueba runtime usa Ktor testApplication con Auth/Firestore locales y el mismo
módulo que main; no acredita Google real, Netty/TLS desplegado ni IAM remoto.

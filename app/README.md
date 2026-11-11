# Android: configuración de acceso

El adaptador usa Credential Manager para el botón explícito de Google, valida
el nonce del intento y entrega el Google ID token a Firebase Authentication.
Solo el **Firebase ID token** entra en SessionToken/Authorization de la API.
Firebase verifica las credenciales Google; leer el nonce localmente no verifica
por sí solo una firma JWT. La app no incluye el SDK cliente de Firestore.

## Configuración local para Google real

Usar un proyecto Firebase ya configurado con el proveedor Google habilitado,
la app Android `lvluplife.android` y sus fingerprints de firma registrados.
Colocar su `google-services.json` en `app/`; está excluido de Git. El plugin
Google services se aplica solo si ese archivo existe y genera, entre otros,
`default_web_client_id`, que debe corresponder al OAuth client **web**.
No se crean proyectos/clientes OAuth ni se habilitan proveedores desde el build.

Sin configuración, el proyecto compila para pruebas y la fábrica devuelve
Unavailable sin abrir el selector ni enviar credenciales. No se incluye una
configuración ficticia de producción. MainActivity conecta el adaptador al flujo de acceso y perfil.
El acceso Google real y los fingerprints/OAuth de un proyecto remoto no se
acreditan con las pruebas locales.

## Sesión y limpieza

`restore` usa la identidad Google de Firebase y `getIdToken(false)` (renovación
automática del SDK cuando corresponde); `refresh(uid)` exige el UID original y
fuerza renovación sin abrir otro selector ni consultar el token de otra cuenta.
Cancelación/denegación no reintentan automáticamente ni consultan/crean perfiles.
No se escribe un token en Room, logs ni almacenamiento propio de la app;
Firebase administra su persistencia de sesión.

Logout invalida la generación, llama FirebaseAuth.signOut y limpia el estado de
Credential Manager. La UI debe limpiar primero su SessionController, sin esperar
red. Los adaptadores de distintas Activity comparten coordinación por FirebaseAuth:
una Task en curso se deja terminar y limpia identidad obsoleta antes de permitir
otra autenticación, incluso si su coroutine fue cancelada. Esto no borra cuentas,
historial ni pendientes durables.

## Verificación local

Desde la raíz, con JDK17/Android SDK configurados:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:testDebugUnitTest
.\node_modules\.bin\firebase.cmd emulators:start --project demo-lvluplife --config firebase/firebase.json --only auth
```

Con el emulador Android iniciado, en otra terminal instalar los APK debug y
androidTest con adb. Ejecutar `am instrument -w -r -e class` para
`lvluplife.data.GoogleCredentialRequestsTest,lvluplife.data.FirebaseSdkIdentityTest,lvluplife.ui.SessionNavigationTest,lvluplife.ui.AccessFlowTest,lvluplife.ui.ProfileFlowTest,lvluplife.data.PendingStoreTest`,
usando `lvluplife.android.test/androidx.test.runner.AndroidJUnitRunner`.
FirebaseSdkIdentityTest usa una FirebaseApp nombrada de `demo-lvluplife` y
Auth Emulator en `10.0.2.2:9099` (host del equipo desde Android Emulator).
Las claims Google son sintéticas: prueba el SDK Firebase real local, no Google real.
`connectedDebugAndroidTest` también requiere este servicio para dicho test;
AccessFlowTest y ProfileFlowTest prueban acceso/alta con puertos simulados; no requieren Google real.

## API y navegación

AndroidAppGraph vive en Application y conecta sesión, Room y ApiAccessAccounts.
Debug usa `http://10.0.2.2:8081` por defecto (API local, no Auth Emulator).
Para otro servidor, compilar con `-Plvlup.apiBaseUrl=https://api.example.com`.
Release requiere una URL HTTPS válida; sin URL configurada muestra error seguro
al consultar la cuenta. Los controles de ApiEndpoint rechazan HTTP remoto y URLs
locales en release. La URL no lleva secretos ni rutas de prueba.

La autenticación restaura pendientes propios y consulta `/me`. Solo
`profileRequired:true` ofrece crear personaje; un fallo de consulta muestra
error y reintento. El nombre se envía mediante COMPLETE_PROFILE persistido en
Room antes del POST. Un rechazo permite corregirlo con una intención nueva;
un resultado incierto conserva el sobre y su UUID, y permite reintentar esa
operación. El borrador pertenece al UID y no contiene credenciales.
La confirmación se sigue de otra lectura de `/me`, sin inventar progreso local.

Cerrar sesión limpia inmediatamente datos, formulario y navegación en memoria,
y después cierra Firebase/Credential Manager. Las respuestas tardías se descartan.
La limpieza mantiene los pendientes durables ligados a su cuenta original.

## Validar US1 y una segunda instalación

Usar un emulador Android descartable con soporte multiusuario (se verificó API30,
Pixel_6a_API_30) y usuario0 en primer plano. El arnés rechaza dispositivos físicos.
Desde la raíz, con JAVA_HOME/JDK17, Android SDK y las variables locales de
[server/README.md](../server/README.md) configurados:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :server:installDist --write-locks --no-daemon
.\node_modules\.bin\firebase.cmd emulators:exec --project demo-lvluplife --config firebase/firebase.json --only auth,firestore "node scripts/test-us1.mjs C:/ruta/al/Android/Sdk/platform-tools/adb.exe emulator-5554"
```

8081 debe estar libre. test-us1 arranca el runtime Netty compilado con los mismos
handlers y verificador de main, ejecuta tres etapas de AccountInstallationStages,
las28 pruebas nativas de acceso/perfil/SDK/navegación/Room y cierra el servidor.
Las etapas usan la anotación ProcessStage para quedar fuera de la ejecución
Android común: necesitan orden, mismo run ID y dos instalaciones.

test-account-installations instala los mismos APK en usuario0 y en un usuario
Android nuevo. Cada instalación tiene UID Linux, preferencias, FirebaseAuth y
Room privados. Ambas usan el SDK Firebase real contra Auth Emulator con las
mismas claims Google sintéticas. El alta de la primera llega a Netty/Firestore
y el transporte de prueba pierde la respuesta después de verificar CONFIRMED;
el coordinador conserva Unknown. La segunda consulta solo `/me`, sin crear un
perfil ni consultar/importar el UUID pendiente. El arnés compara hashes del UID
Firebase y del perfil completo, comprueba UIDs Linux distintos y reabre la
primera después de force-stop para verificar su borrador/pendiente originales.
Los checkpoints no imprimen tokens, nombres, UID Firebase ni sobres.

El arnés elimina únicamente el usuario Android que creó; mantiene el usuario0
y su base normal pending.db. Los fixtures del usuario0 usan nombres privados
account-installation-<run ID>, separados de la base de la app. No borra cuentas
reales ni configura OAuth. Prueba dos instalaciones aisladas en un emulador,
no dos dispositivos físicos ni el selector Google real. La resolución HTTP
de recibos sigue planificada en T040; esta prueba conserva Unknown y no la acredita.

La regresión del servidor, incluidas las reglas de acceso directo y los intentos
de propietario ajeno, se ejecuta con el comando integrationTest de
[server/README.md](../server/README.md).

Versiones fijadas: Credentials1.6.0, Google ID1.1.1, Firebase Auth25.0.0,
Google services4.5.0 y coroutines1.10.2; Kotlin/Compose compiler2.4.20 conservados.

Fuentes: [Firebase + Google](https://firebase.google.com/docs/auth/android/google-signin),
[Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation),
[configuración Firebase](https://firebase.google.com/docs/android/setup),
[releases Credentials](https://developer.android.com/jetpack/androidx/releases/credentials).

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
configuración ficticia de producción. Las pantallas conectan el adaptador en T023.
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
`lvluplife.data.GoogleCredentialRequestsTest,lvluplife.data.FirebaseSdkIdentityTest,lvluplife.ui.SessionNavigationTest,lvluplife.data.PendingStoreTest`,
usando `lvluplife.android.test/androidx.test.runner.AndroidJUnitRunner`.
FirebaseSdkIdentityTest usa una FirebaseApp nombrada de `demo-lvluplife` y
Auth Emulator en `10.0.2.2:9099` (host del equipo desde Android Emulator).
Las claims Google son sintéticas: prueba el SDK Firebase real local, no Google real.
`connectedDebugAndroidTest` también requiere este servicio para dicho test;
AccessFlowTest tiene sus casos pendientes de implementación UI en T023.

Versiones fijadas: Credentials1.6.0, Google ID1.1.1, Firebase Auth25.0.0,
Google services4.5.0 y coroutines1.10.2; Kotlin/Compose compiler2.4.20 conservados.

Fuentes: [Firebase + Google](https://firebase.google.com/docs/auth/android/google-signin),
[Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation),
[configuración Firebase](https://firebase.google.com/docs/android/setup),
[releases Credentials](https://developer.android.com/jetpack/androidx/releases/credentials).

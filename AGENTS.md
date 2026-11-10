## Propósito
Lvl Up Life es una app Android que gamifica las actividades cotidianas convirtiéndolas en experiencia y progreso de un personaje RPG.
Su propósito es motivar al usuario a mantener una rutina activa y visualizar sus avances diarios de forma simple y entretenida.

## Stack
- Android nativo: Kotlin 2.4.20 y Jetpack Compose; mínimo Android 10 (API 29).
- Firebase: Cloud Firestore para persistencia y Firebase Authentication para autenticación mediante Google.
- Notificaciones de recomendación locales mediante las APIs de Android.

## Cómo correr
Desde la raíz del proyecto, en PowerShell:
- Resolver dependencias y compilar: `.\gradlew.bat assembleDebug`
- Instalar y correr en desarrollo: `.\gradlew.bat installDebug`; luego abrir Lvl Up Life en el dispositivo o emulador conectado.
- Tests unitarios: `.\gradlew.bat testDebugUnitTest`
- Tests Android (dispositivo o emulador conectado): `.\gradlew.bat connectedDebugAndroidTest`

## Qué NO hacer
- No permitir asignar XP manualmente ni superar los topes diarios por atributo.
- No exponer atributos, actividades ni historial de otras cuentas.
- No reiniciar la progresión sin confirmación explícita ni borrar la cuenta, el nombre de usuario, las actividades personalizadas o el historial al reiniciar.

## Flujo de tareas y commits
- Trabajar una tarea T a la vez; no iniciar la siguiente antes de cerrar la revisión de la actual.
- Al finalizar cada T, informar cambios y resultados de sus verificaciones, y esperar la decisión del usuario sobre el commit antes de continuar.
- Crear un commit individual por cada T autorizada, con Conventional Commits en español; no agrupar varias T en un mismo commit.
- No incluir cambios de otras tareas en el commit de la T actual. No hacer push.

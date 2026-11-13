# Configuración real previa a T084

2026-11-12, proyecto `liveluplife`; cambios de esta configuración sin commit.
Decisión y alcance en [firebase-real.md](../../firebase-real.md).

| Comprobación | Resultado |
|---|---|
| Facturación | billingEnabled=False, Spark conservado, sin despliegue |
| App Android | lvluplife.android registrada, firma SHA-1 debug y JSON local validado |
| Build Android | processDebugGoogleServices + assembleDebug, 52 s, aprobado |
| Recursos | default_web_client_id generado |
| Instalación y Activity | adb install -r aprobado, am start Status ok, emulator-5554 |
| Cuenta servidor | lvlup-server-test, ADC impersonado, rol personalizado de seis permisos |
| JVM Firestore real | Crear/leer/actualizar marcador separado: aprobado |
| Admin Auth | Consulta de usuarios permitida, sin exportar datos personales |
| Índices | Consultas de historial y ciclo aceptadas en rutas vacías |
| Reglas | deny-all publicado y verificado; 403 cliente sin sesión para leer/escribir |
| API local | Listener 127.0.0.1:8081; sin token e inválido, ambos 401 |
| Servidor unitarias | 35 pruebas, cero fallos/errores; installDist aprobado |
| Google desde Android | Usuario completó ingreso; pantalla «Elegí tu nombre de usuario» observada. Perfil aún no creado. |
| Comandos API con sesión Google real | PENDIENTE |
| Cliente Firestore autenticado y aislamiento con dos cuentas reales | PENDIENTE |
| API HTTPS remota | No desplegada; debug usa HTTP local, SDK Firebase usa TLS |

El marcador `integrationChecks/560de0df-2764-4fa7-8e37-daa451fa4e6b` se conserva
en estado completed. Ningún documento previo fue borrado o migrado. La inspección
acotada de users encontró una raíz inexistente con datos debajo; debe evitarse
ese UID para escribir hasta resolver su compatibilidad.

Los sondeos JVM fueron diagnósticos puntuales en `.tooling`, no suites de
integración ejecutadas contra producción ni evidencia TDD. Se conserva salida
sanitizada de IO, Auth, reglas, denegaciones y sondeo local en esta carpeta.

Incidencias resueltas: sandbox bloqueaba conexión a daemon Gradle; Rules API
requirió x-goog-user-project; BAT de servidor excedía longitud de Windows;
variables de emuladores debían eliminarse en vez de asignar cadena vacía.

T084 no iniciada. No se declara integración funcional completa por tener acceso
Admin a Firestore ni por instalar la aplicación.

Tras el login personal, la evaluación de configuración queda operativa para
continuar con la matriz documental de T084, conservando explícitamente los
pendientes anteriores. No declarar aprobación integral del recorrido de comandos
reales ni compatibilidad de datos heredados. Antes de crear el perfil comprobar
solo el UID exacto de esa sesión. Una consulta que correlacionaba el último login
con una lista de Auth fue bloqueada por revisión automática antes de ejecutarse.

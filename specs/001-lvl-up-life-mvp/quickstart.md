# Guía de ejecución y validación prevista

Este repositorio todavía no contiene la aplicación ni el servicio. Los comandos siguientes son el contrato de ejecución que debe proporcionar la implementación; no son resultados ejecutados en esta fase. Trabajar desde la raíz en PowerShell.

## Prerrequisitos

JDK 17, Android Studio/SDK compatibles con el build fijado, emulador o dispositivo Android API 29+, Node LTS compatible con Firebase CLI para emuladores y Firebase CLI fijada por el proyecto al implementar. Java/Kotlin del servidor usan la misma toolchain del dominio. Docker es opcional para empaquetar Cloud Run, no requisito del cálculo unitario.

Usar proyecto de prueba `demo-lvluplife` con Auth/Firestore Emulator y datos sintéticos. No usar cuentas ni registros reales. Configuración local y secretos fuera de Git; ADC solo para entorno autorizado. Nunca activar reloj controlado, emuladores o rutas de fixtures en producción.

## 1. Pruebas primero

Antes de cada cambio crítico, escribir el caso esperado, ejecutar su prueba y observar el fallo por comportamiento ausente; después implementar lo mínimo y refactorizar. Comandos previstos:

```powershell
.\gradlew.bat :domain:test :server:test
.\gradlew.bat testDebugUnitTest
```

Esperado: motor puro sin Firebase, Android ni reloj implícito; fixtures cubren todos los límites de XP, reparto, sueño, comidas, niveles, inactividad, recálculo, clases, reinicio y tiempo. Registrar el ciclo rojo/verde de cada regla crítica en la evidencia de implementación; una suite verde añadida al final no acredita TDD.

## 2. Emuladores y servicio local

Una vez creados `firebase/firebase.json` y los adaptadores:

```powershell
firebase emulators:start --project demo-lvluplife --config firebase/firebase.json --only auth,firestore
```

En otra consola, servicio local apuntado explícitamente a emuladores:

```powershell
$env:GOOGLE_CLOUD_PROJECT = 'demo-lvluplife'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080'
.\gradlew.bat :server:run
```

Servidor local previsto en puerto 8081. Pruebas de integración deben configurar las mismas variables y arrancar un servicio de test aislado:

```powershell
.\gradlew.bat :server:integrationTest
```

Preparación de cuentas y fixtures ocurre en el arnés de integración, no mediante endpoints administrativos abiertos. No guardar tokens en comandos, historial de shell ni logs. Los emuladores permiten identidades sintéticas; no demuestran el intercambio real de Google ni la durabilidad de infraestructura de producción.

## 3. Android

El build debug debe permitir configurar URL del servicio y host de Auth Emulator solo para desarrollo. Android Emulator accede al host mediante `10.0.2.2`: servicio 8081, Auth 9099. Usar configuración debug separada para HTTP local; release exige HTTPS/TLS ≥1.2 y rechaza configuración de emuladores.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat connectedDebugAndroidTest
```

Abrir Lvl Up Life en el dispositivo. Para probar Google real se necesita proyecto Firebase de integración propio, registro Android y huellas de firma, proveedor Google habilitado y OAuth web/server client ID; configuración Android correspondiente fuera de secretos del repositorio. Este paso requiere Internet y no se sustituye por Auth Emulator. No se crea ni despliega infraestructura remota desde esta guía.

## 4. Escenarios verificables

| Escenario | Preparación y resultado esperado | Referencia |
|---|---|---|
| Identidad y nombre | Dos identidades compiten por `Martin`/`martin`: solo una obtiene nombre; cada UID recupera su único personaje. | AC-03–05, 90 |
| Registro y progreso | Cuenta bajo topes; 30 min STR →6 XP; 30 min dual →4/2; sueño 30+30 misma fecha →3 MEN; quinta comida se guarda con 0 XP. | AC-25–32, 80–81 |
| Concurrencia | Dos envíos distintos compiten por 1 XP o 60 minutos restantes: nunca exceden tope; dos correcciones desde misma versión no se confirman ambas. | AC-89, 104, 110–111 |
| Fechas y recálculo | Reloj de prueba en UTC−3; 299/300 s, cruce de medianoche, días 6/7 y consultas repetidas conservan resultados exactos de spec. | AC-103, 106–107, 123–125 |
| Reinicio | Cancelar no cambia nada; confirmar conserva datos anteriores y crea solo un ciclo. Registro concurrente respeta orden de confirmación. | AC-63–66, 114–115 |
| Respuesta perdida | Inyectar pérdida después de commit para crear/editar/eliminar/reiniciar; desde la instalación que conserva el pendiente, consultar y reenviar el mismo UUID con sesión válida de la cuenta original conserva resultado sin duplicar nada. Fallo antes de commit no debe simular éxito. | AC-112–118 |
| Cierre y sesión | Cerrar proceso con pendiente, reabrir, vencer sesión e ingresar con otra cuenta: no exposición ni reasignación; cuenta original recupera seguimiento. | AC-118–120 |
| Acceso desde otro dispositivo | A confirma cambios y conserva un pendiente cuya respuesta se perdió después del commit; B inicia sesión con la misma cuenta de Google, consulta los datos guardados y no importa el pendiente ni necesita su UUID. A resuelve su pendiente sin repetir efectos; no se exige a B recuperar operaciones de A. | Historia 1, RF-49, RF-55, AC-117 |
| Dos cargas iguales | Nuevo envío intencional genera registro distinto; doble toque del mismo envío no duplica. | AC-126–127 |
| Consultas y errores | Fallo de consulta no se muestra vacío; datos previos se marcan desactualizados. Rechazo conserva entrada; acceso denegado no reintenta automáticamente. | AC-121–122, 128–131 |
| Aislamiento | Identidades A/B intentan leer/escribir recursos ajenos y Firestore directo: siempre rechazo sin datos ni cambios ajenos. | AC-67–68, SC-007 |
| Clases y UI | Datos preparados prueban Novato, pares, equilibrio y niveles 14/15/34/35; todas las clases/etapas visibles, estados y contraste definidos. | AC-51–62, 91–92, SC-011 |

Contratos detallados: [API](contracts/api.md), [UI](contracts/ui.md), [modelo](data-model.md). No duplicar suites completas en este documento.

## 5. Evidencia y mediciones

Registrar comandos, versiones, reloj/fixtures usados y resultados reales. Fallos de red y cierre de proceso deben inyectarse en puntos conocidos antes/después del commit, no deducirse de una excepción genérica. Ejecutar autorización API además de reglas: Admin SDK omite reglas Firestore.

Las muestras de 1.000 reintentos, estabilidad, volúmenes 0/1.000/10.000, recuperación de servicio y estudios con usuarios conservan el carácter orientativo definido en PRD. Las pruebas automatizadas pertinentes de autorización, invariantes, recuperación y progresión siguen siendo obligatorias. No afirmar cumplimiento de p95, disponibilidad, Google real o durabilidad de servicio basándose solo en tests locales.

Para futuros ensayos de rendimiento usar las condiciones del PRD, incluir lecturas del ciclo completo, contención y arranque en frío del servicio; no recortar historial ni imponer un tope funcional para aparentar cumplimiento. Si falla una meta, reportar evidencia y ajustar el diseño antes de declarar cumplimiento.

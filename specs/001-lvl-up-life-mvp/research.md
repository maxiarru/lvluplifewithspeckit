# Investigación: MVP de Lvl Up Life

Fecha: 2026-11-05. Fuente funcional: [spec.md](spec.md), RF-01–57 y AC-01–131. Investigación documental, sin pruebas de compilación ni despliegues.

## 1. Stack y separación de responsabilidades

**Decisión**: Kotlin 2.4.20, Android nativo Compose, API mínima 29; dominio Kotlin/JVM puro, cliente Android y servicio Kotlin/JVM en Cloud Run. Servicio HTTP pequeño con Ktor, Firebase Admin Java y cliente Firestore Java. Un solo repositorio y tres módulos Gradle: `domain`, `app`, `server`. Inyección por constructores; sin framework de inyección inicial.

**Motivo**: el servidor debe impedir XP manual, aplicar tiempo autoritativo y proteger invariantes entre dispositivos. La lógica crítica vive solo en `domain`, invocada por el servicio; Android presenta sus resultados, sin un segundo cálculo. Kotlin 2.4.20 está publicado y mantiene la versión requerida por AGENTS y constitución. Las versiones compatibles de build se fijan en el plan; las dependencias de bibliotecas se fijarán en catálogo de versiones y lockfiles al crear el proyecto, sin rangos dinámicos.

**Alternativas**: reglas y cálculo solo en Android no son autoritativos; Functions en TypeScript requeriría otra implementación del dominio. Un servidor Kotlin evita esa duplicación sin introducir microservicios.

Fuentes: [release Kotlin](https://kotlinlang.org/docs/whatsnew2420.html), [matriz KGP](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler](https://kotlinlang.org/docs/compose-compiler-migration-guide.html), [arquitectura Android](https://developer.android.com/topic/architecture/recommendations), [Cloud Run JVM](https://docs.cloud.google.com/run/docs/quickstarts/build-and-deploy/deploy-java-service).

## 2. Autenticación y límites de confianza

**Decisión**: Credential Manager obtiene identidad Google y Firebase Authentication produce el ID token Firebase. Cada llamada HTTPS lleva ese token, validado por Admin SDK; el UID verificado determina todas las rutas de datos. Sin UID del cliente como autoridad. Firestore deniega todo acceso directo de clientes; solo el servicio accede con identidad de servicio e IAM mínimo. Los SDK de servidor no dependen de Security Rules: la autorización en servicio es obligatoria.

**Motivo**: cubre RF-01–05, RF-19, RF-52, RF-57 y AC-67–68. HTTPS con TLS mínimo 1.2. Sin tokens en logs, URL ni Room; credenciales de servicio mediante ADC, sin claves JSON en el repositorio. Revocación y cuenta deshabilitada se comprueban al autenticar peticiones protegidas. Un token vencido puede refrescarse con Firebase; si no se recupera una sesión válida se solicita autenticación y se suspenden pendientes.

**Alternativas**: UID enviado por el usuario, validación solo visual y acceso directo a documentos derivados no garantizan integridad. No se añade verificación invasiva de actividades ni funciones sociales.

Fuentes: [Google en Firebase Android](https://firebase.google.com/docs/auth/android/google-signin), [validar ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens), [seguridad de servidor](https://firebase.google.com/docs/firestore/security/rules-conditions).

## 3. Consistencia sin una transacción que reescriba todo el historial

**Decisión**: todos los escritores de una cuenta comparten una revisión monotónica en su documento raíz. Leer raíz, registros del ciclo y raíz nuevamente; aceptar el conjunto solo si la revisión coincide. Reconstruir con el dominio fuera de la transacción. En una transacción corta leer primero raíz y recibo de operación; si no existe recibo y la revisión sigue siendo la esperada, validar las precondiciones actuales y escribir registro afectado, proyección, raíz incrementada y recibo juntos. Reinicio agrega ciclo y cambia puntero en la misma transacción. Si cambia revisión, descartar el cálculo y repetir desde la lectura completa. Ningún escritor omite el incremento de revisión. Las consultas se realizan contra Firestore con consistencia fuerte, sin caché cliente.

**Motivo**: el documento raíz evita aceptar un cálculo obsoleto incluso si aparecieron registros durante una lectura paginada. La transacción tiene pocas escrituras y no crece con el historial. El recálculo sigue usando todos los registros vigentes del ciclo como pide RF-44; no se añaden snapshots históricos ni reglas incrementales prematuras. Una consulta de progreso reconstruye y confirma una proyección con la misma barrera de revisión; no vuelve a descontar sobre el saldo anterior.

**Tiempo**: tomar `validationNow` del servidor dentro del intento de validación final, después de las lecturas transaccionales y antes de escribir. Evaluar ventana exclusiva de 300 s, fecha admitida y ciclo vigente. Si el día de negocio difiere del usado al reconstruir, abortar ese intento y reconstruir con el nuevo día. En cada reejecución transaccional se vuelve a obtener tiempo y comprobar precondiciones; las funciones de dominio reciben instantes explícitos. Un recibo existente se devuelve antes de reevaluar fechas, versión o ciclo. `createdAt` autoritativo del registro es el del intento aceptado, no el reloj del teléfono.

**Alternativas**: triggers de Firestore posteriores al guardado permiten progreso parcial; transacciones extensas que reescriben todos los registros crecen innecesariamente. No se confía en locks en memoria de una instancia Cloud Run.

**Límites**: Firestore impone límites de transacciones y documentos; no se guarda el historial entero en un documento. Recálculo completo tiene costo O(n log n) por ordenación y lecturas O(n), con contención por cuenta. No hay un máximo funcional nuevo. Medir 0/1.000/10.000 registros del PRD; si no alcanza objetivos, rediseñar con evidencia antes de declarar cumplimiento. Reintentos técnicos acotados por petición y respuesta transitoria; el pendiente conserva su identidad.

Fuentes: [transacciones](https://firebase.google.com/docs/firestore/manage-data/transactions), [contención](https://firebase.google.com/docs/firestore/transaction-data-contention), [límites](https://docs.cloud.google.com/firestore/quotas).

## 4. Idempotencia y recuperación

**Decisión**: UUID de operación creado y persistido antes del primer envío, por UID y envío intencional. Doble toque reutiliza el UUID; otra carga intencional crea otro. Recibo inmutable con hash canónico del comando, resultado y ciclo original. El mismo UUID con otro contenido se rechaza. Recibos confirmados y rechazos funcionales definitivos se conservan sin TTL en el MVP; no se borra la evidencia necesaria para reconocer un reintento. Un fallo transitorio no crea un rechazo definitivo. Consulta autenticada del recibo distingue resultado definitivo de ausencia; ausencia no demuestra que no exista una petición en vuelo.

**Motivo**: RF-49–55 exigen recuperar tras muerte de proceso y no duplicar ciclos. Una operación no se identifica comparando actividad/duración/fecha. La respuesta de un recibo es el resultado original; no se debe presentar su proyección antigua como el estado actual: después se consulta progreso vigente.

**Alternativas**: generar nuevo UUID en cada reintento duplica efectos; deduplicar por contenido impide dos cargas legítimas idénticas.

## 5. Persistencia local limitada y estados de interfaz

**Decisión**: Room guarda pendientes y borradores por UID en almacenamiento privado, excluido de backup y transferencia de dispositivo. No es una cola para crear actividades nuevas sin conexión. Cliente guarda el sobre antes de enviarlo y solo remueve el pendiente tras conocer resultado definitivo. Al iniciar/resumir o reconectar con el mismo UID consulta y, si procede, reenvía el sobre original. No se necesita un servicio permanente ni notificaciones. Sin sesión válida se suspenden los envíos; al cambiar cuenta se limpian estados en memoria y navegación y se filtra Room por UID. Datos anteriores de consulta se conservan solo en memoria de la sesión activa.

**Motivo**: satisface recuperación aprobada sin prometer operación offline completa. Room permite almacenar estados estructurados de forma transaccional. UI separa `Loading`, `Confirmed`, `Stale`, `Empty`, `Error` y `Pending`; una consulta fallida no produce `Empty`. Un error conocido conserva borrador; 401 suspende recuperación y 403 no reintenta automáticamente.

**Alternativas**: pendientes solo en ViewModel se pierden al morir el proceso; caché general Firestore introduce estados offline que no forman parte del MVP. Cifrado adicional y sincronización de borradores entre dispositivos no se añaden al alcance.

Fuente: [Room](https://developer.android.com/training/data-storage/room).

## 6. Calidad y alcance

**Decisión**: TDD en dominio con reloj explícito y fixtures compartidos; integración con emuladores Auth/Firestore y servicio local; pruebas Compose de sesión, errores, recuperación y accesibilidad. Pruebas de fallo inyectado antes y después del commit distinguen rechazo de respuesta perdida. No se presenta una revisión documental como resultado de esos ensayos.

**Motivo**: principios IV, VI y VII. Las muestras amplias y estudios del PRD son orientativos; no eximen pruebas de autorización, invariantes, límites temporales y reintentos. Las skins de 26 clases por tres etapas son recursos necesarios; placeholders de desarrollo no acreditan entrega visual completa.

**Alternativas**: pruebas añadidas al final no cumplen Test-First; usar servicios de producción en pruebas del dominio impide determinismo.

Fuente: [Emulator Suite](https://firebase.google.com/docs/emulator-suite).

## Cierre de fase 0

Resueltos los interrogantes de arquitectura, autoridad, idempotencia, concurrencia, tiempo y recuperación. No se requieren decisiones funcionales adicionales. La selección arquitectónica es una decisión de este plan, no una capacidad garantizada por Firebase. Las versiones y compatibilidades documentadas requieren validación de build al implementar.

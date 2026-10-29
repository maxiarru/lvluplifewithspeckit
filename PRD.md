# PRD-001: Lvl up Life — una forma de mantener tu rutina activa

> Versión revisada con decisiones del responsable del producto. La auditoría documental no acredita pruebas ejecutadas ni cumplimiento medido del software. Los planes de validación futuros son orientativos y no bloquean la entrega del MVP.

## Contexto y Problema

Martín tiene 34 años, trabaja, entrena cuando puede y quiere encontrar tiempo para leer, descansar mejor y comer de manera más saludable. Aunque durante la semana hace varias de esas cosas, al finalizar el día suele sentir que no avanzó porque sus logros cotidianos quedan dispersos y no puede verlos como parte de un progreso común. Probó aplicaciones de hábitos, pero las abandonó cuando registrar cada acción empezó a sentirse como una tarea más.

Sofía tiene 23 años, estudia y disfruta de los videojuegos. Quiere construir una rutina más equilibrada, pero las listas y calendarios no la motivan. Le resulta más atractivo recibir feedback inmediato, desarrollar un personaje y comparar su constancia con la de sus amigos, sin exponer públicamente los detalles de su vida. La comparación con amigos es una aspiración futura y no forma parte del MVP.

Lvl Up Life transforma las actividades que una persona ya realizó —entrenar, moverse, alimentarse bien, leer, disfrutar de un pasatiempo o descansar— en el progreso de un personaje RPG. Los registros pueden aportar experiencia a uno o dos atributos, según los topes diarios y de nivel, y ese progreso permite subir niveles y modificar la clase y la apariencia del personaje. La aplicación busca que el usuario reconozca sus avances y vuelva voluntariamente, sin convertir el seguimiento en otra obligación.

La primera versión está dirigida a jóvenes y adultos y se desarrolla para Android. Registra actividades realizadas; no funciona como agenda de tareas futuras ni pretende contabilizar todo el tiempo del día.

## Objetivos

- Lograr que el usuario se sienta motivado a registrar sus actividades y pueda reconocer sus avances cotidianos mediante el progreso de su personaje.
- Generar una sensación clara e inmediata de progreso mediante mecanismos de gamificación que ayuden al usuario a sostener una rutina activa.

## Requerimientos Funcionales

- RF-01: El sistema debe permitir registrarse mediante una cuenta de Google, sin administrar una contraseña propia de LUL.
- RF-02: El sistema debe permitir iniciar sesión mediante una cuenta de Google ya registrada, sin solicitar una contraseña propia de LUL.
- RF-03: El sistema debe mantener una única cuenta por identidad de Google.
- RF-04: El sistema debe permitir elegir un nombre de usuario único de entre 1 y 15 caracteres ASCII, limitado a letras, números y guion bajo, sin espacios, conservando su escritura visible y considerando iguales los nombres que solo difieren en mayúsculas y minúsculas.
- RF-05: El sistema debe crear exactamente un personaje por cuenta, inicialmente en nivel general 1, con los seis atributos en nivel 1, 0 XP y clase Novato.
- RF-06: El sistema debe ofrecer un catálogo inicial de actividades básicas con modalidad, categoría y atributos preasignados.
- RF-07: El sistema debe permitir definir una actividad personalizada por duración especificando nombre, categoría de texto libre, atributo principal y, opcionalmente, un atributo secundario compatible.
- RF-08: El sistema debe guardar la actividad personalizada cuando sus datos sean válidos y su atributo secundario, si se incluye, sea compatible con el principal.
- RF-09: El sistema debe permitir reutilizar las actividades personalizadas guardadas.
- RF-10: El sistema debe permitir registrar la duración de una actividad mediante horas y minutos, tanto para sueño como para las demás actividades por duración.
- RF-11: El sistema debe permitir registrar actividades mediante la modalidad por realización.
- RF-12: El sistema debe interpretar cada registro de la actividad «comida» como una comida saludable, sin exigir una confirmación adicional de esa condición.
- RF-13: El sistema debe permitir registrar actividades únicamente para hoy, ayer o anteayer.
- RF-14: El sistema debe permitir editar un registro del ciclo actual únicamente mientras hayan transcurrido menos de cinco minutos desde su creación, aplicando las validaciones del registro resultante: en registros por duración pueden cambiarse la actividad por otra de duración, la duración y la fecha; en comida solo puede cambiarse la fecha, sin convertir comida en otra actividad ni otra actividad en comida.
- RF-15: El sistema debe permitir eliminar un registro únicamente si pertenece al ciclo actual y han transcurrido menos de cinco minutos desde su creación.
- RF-16: El sistema debe permitir consultar las actividades registradas mediante vistas por día calendario, semana de lunes a domingo y mes calendario, en UTC−3.
- RF-17: El sistema debe calcular automáticamente la XP correspondiente a cada registro según las reglas de XP definidas en este PRD.
- RF-18: El sistema debe distribuir la XP entre uno o dos atributos según los atributos asociados a la actividad.
- RF-19: El sistema debe rechazar toda operación de registro o edición que incluya un valor de XP proporcionado por el cliente.
- RF-20: El sistema debe limitar la XP diaria de cada atributo dentro del ciclo actual a 48 XP para STR, DEX, INT y WIT; 30 XP para MEN; y 28 XP para CON, otorgando como máximo la XP restante para la fecha de actividad en ese ciclo.
- RF-21: El sistema debe permitir guardar actividades realizadas después de alcanzar el máximo diario de un atributo sin otorgar experiencia adicional a ese atributo durante ese día.
- RF-22: El sistema debe mantener una cantidad de XP individual para cada atributo: STR, DEX, CON, INT, WIT y MEN.
- RF-23: El sistema debe calcular de forma independiente el nivel de cada atributo entre 1 y 50: un atributo en nivel N, donde 1 ≤ N < 50, sube a N + 1 al acumular 30 × N XP dentro de ese nivel.
- RF-24: El sistema debe impedir que un atributo en nivel 50 aumente a un nivel superior.
- RF-25: El sistema debe mantener en 0 el progreso de XP de un atributo al alcanzar y mientras permanezca en nivel 50.
- RF-26: El sistema debe conservar la experiencia sobrante después de cada subida de nivel de un atributo cuyo nivel resultante sea menor que 50.
- RF-27: El sistema debe calcular el nivel general del personaje a partir de las subidas de sus atributos, requiriendo 1 subida de atributo en nivel general 1; 2 subidas por nivel entre los niveles 2 y 4; 3 entre los niveles 5 y 8; 4 entre los niveles 9 y 14; y 5 entre los niveles 15 y 49.
- RF-28: El sistema debe representar el progreso general del personaje sin una barra de XP general independiente.
- RF-29: El sistema debe consumir únicamente las subidas de atributos necesarias para cada transición de nivel general, conservando las subidas restantes de la misma operación para la siguiente transición mientras el nivel general sea menor que 50.
- RF-30: El sistema debe limitar el nivel general máximo del personaje a 50.
- RF-31: El sistema debe contar de forma independiente las fechas calendario consecutivas sin actividad válida de cada atributo, incluyendo como primera fecha la de creación o reinicio si no tiene actividad válida, o la primera fecha sin actividad válida posterior a la última fecha activa; los primeros seis días consecutivos no generan descuento.
- RF-32: El sistema debe descontar 5 XP del progreso dentro del nivel actual de un atributo al cierre del séptimo día calendario consecutivo de inactividad y de cada día posterior mientras continúe esa inactividad, sujeto al piso de 0 XP y a la conservación del nivel.
- RF-33: El sistema debe impedir que una penalización por inactividad reduzca el nivel de un atributo.
- RF-34: El sistema debe impedir que una penalización por inactividad genere XP negativa, limitando el saldo mínimo a 0 XP.
- RF-35: El sistema debe considerar interrumpida la inactividad de un atributo en una fecha del ciclo actual si recibió al menos 1 XP efectiva de actividades de esa fecha; los registros posteriores con 0 XP no anulan esa condición.
- RF-36: El sistema debe asignar automáticamente la clase del personaje según la cantidad y combinación de atributos equilibrados, considerando equilibrado todo atributo cuyo nivel sea al menos el 80 % del atributo de mayor nivel, y utilizando la Tabla de Clases definida en este PRD. La condición de Novato tiene prioridad: si los seis atributos permanecen en nivel 1, se asigna Novato.
- RF-37: El sistema debe mostrar la skin correspondiente a la clase actual y a la etapa visual determinada por el nivel general del personaje: Etapa I entre los niveles 1 y 14, Etapa II entre los niveles 15 y 34 y Etapa III entre los niveles 35 y 50.
- RF-38: El sistema debe actualizar la skin cuando cambie la clase o la etapa visual correspondiente al nivel general del personaje, utilizando la combinación de clase actual y etapa vigente.
- RF-39: El sistema debe ofrecer desde el comienzo un apartado que muestre el catálogo completo de clases definido en este PRD y las tres etapas visuales de cada clase.
- RF-40: El sistema debe indicar como desbloqueadas las etapas visuales alcanzadas según el nivel general del personaje y como bloqueadas las etapas superiores.
- RF-41: El sistema debe exigir una confirmación explícita antes de reiniciar la progresión del personaje.
- RF-42: El sistema debe iniciar un nuevo ciclo de progresión desde cero cuando el usuario confirme el reinicio, con nivel general 1, seis atributos en nivel 1 con 0 XP, clase Novato y skin de Etapa I.
- RF-43: El sistema debe conservar la cuenta, el nombre de usuario, las actividades personalizadas y el historial después de reiniciar la progresión del personaje.

- RF-44: El sistema debe reconstruir la progresión del ciclo actual al crear, editar o eliminar un registro y al consultar el progreso, incluyendo las penalizaciones correspondientes a las fechas cerradas aunque no haya nuevos registros, aplicando la Regla de recálculo de progresión.
- RF-45: El sistema debe admitir registros sucesivos de sueño sin un límite de cantidad de registros, sujetos a las validaciones de duración y fecha.
- RF-46: El sistema debe rechazar modificaciones de registros pertenecientes a ciclos anteriores, tanto su edición como su eliminación, aunque hayan transcurrido menos de cinco minutos desde su creación.

- RF-47: El sistema debe rechazar registros o ediciones por duración que incumplan las Reglas de duración y fechas, incluido el máximo acumulado de 1.440 minutos por fecha y ciclo.

### Catálogo inicial de actividades básicas

| Actividad | Categoría | Modalidad | Principal | Secundario |
|---|---|---|---|---|
| Entrenamiento de fuerza | Actividad física | Duración | STR | — |
| Actividad aeróbica | Actividad física | Duración | DEX | — |
| Comida saludable | Nutrición | Realización | CON | — |
| Leer / estudiar | Aprendizaje | Duración | INT | — |
| Ocio / hobby | Bienestar | Duración | WIT | — |
| Dormir | Descanso | Duración | MEN | — |
| CrossFit | Actividad física | Duración | STR | DEX |
| Artes marciales | Actividad física | Duración | DEX | STR |
| Yoga | Bienestar físico | Duración | WIT | DEX |
| Aprender una habilidad práctica | Aprendizaje | Duración | INT | DEX |
| Juegos de estrategia | Ocio / aprendizaje | Duración | WIT | INT |

Las actividades por duración usan los bloques de cinco minutos y el reparto
entre atributos de las Reglas de XP, excepto Dormir, que conserva su regla de
horas acumuladas. Comida saludable conserva su regla de 7 XP por registro con
un máximo de cuatro comidas puntuables por fecha. El usuario no modifica los
atributos preasignados de este catálogo.

### Reglas de actividades personalizadas

Todas las actividades personalizadas utilizan modalidad por duración. Una actividad personalizada es válida cuando:

- Tiene un nombre no vacío de entre 1 y 50 caracteres después de eliminar espacios iniciales y finales.
- Tiene una categoría de texto libre no vacía.
- Tiene exactamente un atributo principal perteneciente a STR, DEX, INT, WIT o MEN.
- Puede no tener atributo secundario.
- Si tiene atributo secundario, debe pertenecer a STR, DEX, INT, WIT o MEN y ser diferente del principal.
- CON está reservado exclusivamente para la actividad básica «comida» y no puede asignarse a actividades personalizadas.

Para el MVP, cualquier par de atributos diferentes dentro de STR, DEX, INT, WIT y MEN se considera compatible. Por ejemplo, STR + DEX, STR + MEN e INT + WIT son válidos; STR + STR es inválido.

### Reglas de duración y fechas

La duración se ingresa con horas y minutos enteros no negativos, con el componente de minutos entre 0 y 59. El total debe ser mayor que 0. Todas las actividades por duración, incluido Dormir, consumen el mismo saldo temporal: 1.440 minutos menos la suma de duraciones de registros vigentes de esa fecha y ciclo. La comida no consume minutos. La suma se valida también ante operaciones simultáneas.

Al editar, se excluye el valor anterior del registro antes de validar su reemplazo; si cambia de fecha, se libera el saldo de origen y se consume el de destino. Eliminar libera su duración. El reinicio comienza un ciclo desde cero: los registros del historial anterior no consumen el saldo del ciclo nuevo, al igual que no consumen sus topes de XP.

El cambio de actividad durante una edición solo se permite entre actividades por duración. Comida queda excluida como origen y destino de ese cambio: sus registros solo permiten editar la fecha dentro de la ventana vigente. Se ingresa duración en horas y minutos, no horarios de inicio y fin.

Se registran actividades ya realizadas, sin solicitar hora de inicio o fin. A las 23:00 puede registrarse una corrida de tres horas ya realizada, si hay saldo en esa fecha. El límite no son las horas que faltan hasta medianoche. Si una actividad cruzó medianoche, el usuario registra manualmente la duración correspondiente a cada fecha; no existe reparto automático entre días. Hoy, ayer y anteayer se determinan con el reloj del servidor en UTC−3 también al editar la fecha.
### Reglas de XP

Las cantidades de XP indicadas se aplican a atributos por debajo del nivel 50. Al llegar a nivel 50, se descarta el sobrante y el progreso queda en 0 XP; no aumenta su nivel ni su progreso, aunque las actividades pueden seguir registrándose.

Los topes y el conteo de comidas se calculan por la fecha de la actividad, en días calendario UTC−3, también cuando se registra una actividad de ayer o anteayer. El margen diario disponible es la diferencia entre el tope del atributo y la XP ya otorgada a ese atributo para esa fecha dentro del ciclo actual. Un reinicio confirmado elimina ese consumo del ciclo nuevo; el historial anterior se conserva.

Las actividades por duración, excepto el sueño, otorgan 1 XP por cada 5 minutos completos del registro. En actividades con dos atributos, 2/3 de la XP corresponden al atributo principal y 1/3 al secundario, redondeando el aporte del principal hacia arriba y asignando el resto al secundario. Este cálculo se realiza antes de aplicar los topes diarios y de nivel. Se conserva la duración total del registro; no se crean saldos de minutos por atributo. Por ejemplo, 10 minutos producen 2 XP y se reparten en 2 XP al principal y 0 XP al secundario. La fórmula general también se aplica a actividades personalizadas asociadas a MEN; la excepción de sueño pertenece exclusivamente a Dormir.

Ejemplos: 15 minutos otorgan 3 XP; 30 minutos otorgan 6 XP; 30 minutos con dos atributos otorgan 4 XP al principal y 2 XP al secundario.

El sueño conserva su regla específica: 3 XP a MEN por cada 60 minutos acumulados pendientes de contabilizar.

Los minutos pendientes de sueño se acumulan únicamente entre registros de la misma fecha de actividad en UTC−3. Los sobrantes no pasan a otra fecha: 30 minutos de ayer y 30 de hoy no otorgan XP por sueño.

| Registro | Aporte antes de aplicar el tope diario |
|---|---|
| 30 minutos de una actividad simple de STR, DEX, INT o WIT | 6 XP al atributo asociado |
| 30 minutos de una actividad con STR principal y DEX secundario | 4 XP a STR y 2 XP a DEX |
| Cada una de las primeras cuatro comidas saludables de una fecha | 7 XP a CON |
| Quinta comida saludable y posteriores de la misma fecha | 0 XP adicional a CON; el registro se conserva |
| 60 minutos acumulados de sueño pendientes de contabilizar | 3 XP a MEN |

CON solo obtiene XP mediante la actividad básica de comida saludable, denominada «comida» en este PRD. Cada registro de «comida» representa una comida declarada saludable. Sin registros de comidas en una fecha, se contabilizan 0 comidas saludables y 0 XP por comidas; con dos registros, se contabilizan dos comidas saludables. No se rechazan registros por superar cuatro comidas en el día: solo se limita su aporte de XP.

El aporte efectivo a cada atributo es el menor entre la XP calculada y el margen diario disponible. En actividades con dos atributos, cada uno aplica su propio límite. El nivel y el progreso de XP de un atributo en nivel 50 se rigen por RF-24 y RF-25.

### Regla de recálculo de progresión

Cuando un registro es creado, editado o eliminado, o el usuario consulta su progreso, el sistema debe reconstruir la progresión derivada del ciclo actual del personaje utilizando los registros vigentes y las fechas cerradas hasta ese momento. Al abrir la aplicación, el progreso mostrado debe incluir las penalizaciones acumuladas durante la ausencia, sin exigir una nueva carga de actividades.

Los registros se procesan primero por fecha de actividad en UTC−3 y, dentro de una misma fecha, por fecha y hora de creación y, ante igualdad, por identificador ascendente mediante comparación ordinal. La edición conserva la fecha y hora de creación y el identificador originales.

Un registro editado se procesa utilizando sus valores actuales. Un registro eliminado se excluye del cálculo.

Durante el recálculo se vuelven a aplicar las reglas de XP, topes diarios, niveles de atributos, nivel general, inactividad, clase y etapa visual definidas en este PRD.

Los registros pertenecientes a ciclos anteriores a un reinicio del personaje permanecen visibles en el historial, pero no participan del recálculo de la progresión actual ni pueden editarse o eliminarse. La pertenencia al ciclo se determina al crear el registro: una actividad registrada después del reinicio para ayer o anteayer pertenece al ciclo nuevo.

Cada ciclo nuevo comienza sin XP, contador de subidas generales, consumos de topes diarios, duración consumida, conteo de comidas puntuables ni minutos de sueño pendientes del ciclo anterior. El período de inactividad se reinicia. Se conservan cuenta, nombre de usuario, actividades personalizadas e historial.

El recálculo de un registro atrasado revierte las penalizaciones que no correspondan al considerar su fecha de actividad. Todas las fechas y límites de días se interpretan en UTC−3. El reloj del servidor determina la fecha actual y el tiempo transcurrido para la ventana de cinco minutos. La fecha de creación o reinicio cuenta como primer día de inactividad si no recibió actividad válida, aunque el ciclo haya comenzado después de medianoche. Si hubo actividad válida, el conteo comienza en la primera fecha posterior sin actividad válida. Por ejemplo, con actividad los días 1, 2 y 3 y ninguna posterior, los días 4 a 9 son los seis primeros días de inactividad sin descuento; al cerrar el día 10 se descuentan hasta 5 XP y se repite al cerrar cada día posterior inactivo. Nunca se cuentan fechas anteriores al ciclo actual: una carga de ayer o anteayer posterior al reinicio puede aportar XP, pero no adelanta el inicio del conteo a una fecha anterior al reinicio. Las penalizaciones se aplican una vez por fecha cerrada desde la séptima consecutiva sin actividad válida, después de procesar sus actividades; la fecha actual aún abierta no se penaliza. Reconstruir nuevamente los mismos registros y fechas cerradas no vuelve a descontar sobre el saldo ya reconstruido.

### Inactividad por fecha y atributo

Para inactividad, una fecha tiene actividad válida asociada a un atributo si sus registros vigentes del ciclo le otorgaron al menos 1 XP efectiva, antes de descontar penalizaciones. No se exige que cada registro del día aporte XP. Si el atributo alcanzó su tope diario, ya recibió XP en esa fecha y ya interrumpió su inactividad; las cargas posteriores con 0 XP no cambian esa situación.

Si todo el aporte diario es 0 XP, no se interrumpe la inactividad. Esto incluye un secundario que no recibe XP por redondeo y sueño que no completa ninguna hora acumulada en esa fecha. En nivel 50 no se otorga XP, pero cualquier penalización conserva nivel 50 y 0 XP. Una edición o eliminación puede modificar si esa fecha cuenta como activa; el recálculo vuelve a evaluar esa condición.

### Tabla de Clases

Primero se verifica la excepción inicial: si los seis atributos están en nivel 1, la clase es **Novato**. Esta condición tiene prioridad sobre el conteo de atributos equilibrados.

En cualquier otro caso, un atributo está equilibrado si su nivel es mayor o igual al 80 % del mayor nivel entre los seis atributos. La comparación se realiza contra ese valor, sin redondearlo. Se cuenta cuántos atributos cumplen la condición; el atributo de mayor nivel siempre está incluido, por lo que el conteo va de 1 a 6.

#### Clases puras: exactamente un atributo equilibrado

| Atributo | Clase |
|---|---|
| STR | Guerrero |
| DEX | Explorador |
| CON | Guardián |
| INT | Sabio |
| WIT | Monje |
| MEN | Místico |

#### Clases combinadas: exactamente dos atributos equilibrados

El orden de los dos atributos no cambia la clase asignada.

| Atributos | Clase |
|---|---|
| STR + DEX | Maestro de Armas |
| STR + CON | Paladín |
| STR + INT | Guerrero Arcano |
| STR + WIT | Luchador Monje |
| STR + MEN | Guerrero Místico |
| DEX + CON | Ágil Guardián |
| DEX + INT | Artífice |
| DEX + WIT | Ninja |
| DEX + MEN | Explorador de Sueños |
| CON + INT | Guardián de la Sabiduría |
| CON + WIT | Monje Guardián |
| CON + MEN | Druida |
| INT + WIT | Ilusionista |
| INT + MEN | Oráculo |
| WIT + MEN | Monje Espiritual |

#### Clases por equilibrio múltiple

| Cantidad de atributos equilibrados | Clase |
|---|---|
| 3 | Aventurero Versátil |
| 4 | Maestro del Equilibrio |
| 5 | Héroe Ascendente |
| 6 | Héroe Integral |

### Etapas visuales y catálogo

Cada clase, incluido Novato, tiene tres skins identificadas por su clase y etapa. La skin activa depende de la clase actual y del nivel general actual.

| Etapa | Nivel general para usar la skin | Desbloqueo en el catálogo |
|---|---|---|
| Etapa I | 1–14 | Desde nivel 1 |
| Etapa II | 15–34 | Desde nivel 15 |
| Etapa III | 35–50 | Desde nivel 35 |

Todas las clases pueden consultarse desde el comienzo. Lo que se desbloquea según el nivel general son las etapas visuales: en nivel 8 solo Etapa I; en nivel 20, Etapas I y II; y en nivel 40, las tres etapas.

## Requerimientos No Funcionales

- RNF-01: En el 95 % de las aperturas de la aplicación, la pantalla principal debe quedar disponible para interacción en un máximo de 3 segundos.
- RNF-02: El 95 % de los registros, ediciones y eliminaciones de actividades debe guardarse y reflejar el nuevo progreso en un máximo de 2 segundos.
- RNF-03: El 95 % de las consultas de historial debe responder en un máximo de 3 segundos.
- RNF-04: Una actividad guardada previamente debe poder registrarse con un máximo de tres interacciones desde la pantalla principal y en una mediana de 15 segundos o menos durante una prueba con al menos diez usuarios.
- RNF-05: El servicio debe alcanzar una disponibilidad mensual mínima del 99,5 %, excluyendo mantenimientos programados informados con antelación.
- RNF-06: Una prueba de 1.000 reintentos o dobles interacciones sobre la misma operación debe producir 0 registros y 0 asignaciones de XP duplicados.
- RNF-07: El 100 % de las comunicaciones debe utilizar HTTPS con TLS 1.2 o superior; las contraseñas de Google no deben almacenarse y los tokens no deben exponerse en código fuente, logs ni respuestas ajenas al intercambio necesario para autenticar y mantener la sesión.
- RNF-08: El 100 % de los casos de la suite de autorización debe impedir que una cuenta consulte, cree, modifique o elimine atributos, actividades o historial pertenecientes a otra cuenta.
- RNF-09: El 100 % de los casos de prueba relacionados con días, edición e inactividad debe utilizar UTC−3 como zona horaria de negocio.
- RNF-10: El 100 % de los flujos críticos debe completarse sin errores funcionales en dispositivos o emuladores con Android 10 (API 29) o superior. Se consideran flujos críticos: iniciar sesión, registrar una actividad, editarla o eliminarla dentro del período permitido, consultar el historial, actualizar XP/niveles/clase y reiniciar el personaje.
- RNF-11: La aplicación debe mantener al menos un 99,5 % de sesiones sin cierres inesperados, medido sobre un mínimo de 1.000 sesiones de prueba ejecutadas en dispositivos o emuladores con Android 10 (API 29) o superior.
- RNF-12: Los textos y controles deben alcanzar una relación de contraste mínima de 4,5:1 y ninguna información esencial debe comunicarse únicamente mediante color.
- RNF-13: Las pruebas de reinicio de aplicación y servicio deben producir 0 pérdidas de registros confirmados.

## Criterios de Aceptación

- AC-01 (RF-01): Dada una identidad de Google válida no registrada, cuando el usuario completa el registro, entonces se crea una cuenta de LUL sin solicitar una contraseña propia.
- AC-02 (RF-02): Dada una cuenta de LUL existente vinculada a Google, cuando su titular completa la autenticación con esa identidad de Google, entonces accede a su cuenta sin introducir una contraseña propia de LUL.
- AC-03 (RF-03): Dada una identidad de Google ya registrada, cuando el usuario vuelve a autenticarse, entonces el sistema recupera la misma cuenta y no crea otra.
- AC-04 (RF-04): Dado un nombre de usuario ya utilizado, cuando otra persona intenta confirmarlo con la misma escritura o con diferencias únicamente de mayúsculas y minúsculas, entonces el sistema rechaza la asignación y no crea el perfil.
- AC-05 (RF-05): Dado un nombre de usuario disponible, cuando el usuario lo confirma y se crea el personaje, entonces existe exactamente un personaje con nivel general 1, los seis atributos en nivel 1 con 0 XP y clase Novato.
- AC-06 (RF-06): Dado el catálogo inicial, cuando el usuario selecciona una actividad básica, entonces se muestran su modalidad, categoría y atributos preasignados sin permitir modificar sus atributos ni su puntuación.
- AC-07 (RF-07): Dado el flujo de creación de una actividad personalizada, cuando el usuario define su nombre, categoría y atributo principal, entonces puede completar la definición sin incluir un atributo secundario.
- AC-08 (RF-07, RF-08): Dada una actividad personalizada que cumple las Reglas de actividades personalizadas definidas en este PRD y no incluye atributo secundario, cuando el usuario la guarda, entonces queda almacenada y disponible para registros posteriores.
- AC-09 (RF-07, RF-08): Dada una actividad personalizada que cumple las Reglas de actividades personalizadas definidas en este PRD e incluye un atributo secundario del conjunto permitido diferente del principal, cuando el usuario la guarda, entonces queda almacenada y disponible para registros posteriores.
- AC-10 (RF-08): Dada una actividad personalizada cuyo atributo secundario es igual al principal, cuando el usuario intenta guardarla, entonces el sistema rechaza la operación y la actividad no queda almacenada.
- AC-11 (RF-09): Dada una actividad personalizada guardada previamente, cuando el usuario vuelve a seleccionarla, entonces puede utilizarla para realizar un nuevo registro sin crear nuevamente la actividad.
- AC-12 (RF-10): Dada una actividad por duración y una fecha permitida, cuando el usuario informa horas y minutos que cumplen las validaciones de duración, entonces el registro conserva esa duración total.
- AC-13 (RF-10, RF-45): Dado un día con registros de sueño previos, cuando el usuario registra otro período de sueño con duración y fecha válidas, entonces queda guardado sin rechazo por cantidad de registros, aunque MEN haya alcanzado su tope diario.
- AC-14 (RF-11): Dada una actividad por realización, cuando el usuario confirma que la realizó, entonces se guarda una sola ocurrencia sin solicitar duración.
- AC-15 (RF-12): Dada la actividad «comida», cuando el usuario registra una ocurrencia, entonces el sistema la contabiliza como una comida saludable sin solicitar una confirmación adicional.
- AC-16 (RF-12, RF-17, RF-20, RF-21): Dado que el usuario ya registró al menos cuatro comidas saludables en un mismo día calendario, cuando registra otra comida para esa fecha, entonces la actividad queda registrada pero CON recibe 0 XP adicional.
- AC-17 (RF-13): Dada una actividad válida, cuando el usuario elige hoy, ayer o anteayer, entonces el sistema la registra en esa fecha.
- AC-18 (RF-13): Dada una actividad válida, cuando el usuario intenta registrarla en una fecha distinta de hoy, ayer o anteayer, entonces el sistema rechaza el registro.
- AC-19 (RF-14, RF-44): Dado un registro por duración del ciclo actual creado hace menos de cinco minutos, cuando el usuario cambia la actividad por otra de duración, la duración o la fecha por valores válidos, entonces se guardan esos valores sin alterar la fecha y hora de creación ni el identificador, y se reconstruye la progresión según los registros vigentes.
- AC-20 (RF-14): Dado un registro creado hace cinco minutos o más, cuando el usuario intenta editarlo, entonces el sistema impide la operación y conserva el registro.
- AC-21 (RF-15): Dado un registro del ciclo actual creado hace menos de cinco minutos, cuando el usuario lo elimina, entonces el sistema elimina el registro, lo excluye del cálculo y la progresión resultante coincide con la obtenida al reprocesar los registros vigentes del ciclo actual según la Regla de recálculo de progresión definida en este PRD.
- AC-22 (RF-15): Dado un registro creado hace cinco minutos o más, cuando el usuario intenta eliminarlo, entonces el sistema impide la operación y conserva el registro.
- AC-23 (RF-16): Dadas actividades pertenecientes a distintas fechas, cuando el usuario abre una vista diaria, semanal o mensual, entonces se muestran únicamente las actividades comprendidas en el período seleccionado.
- AC-24 (RF-16, RF-43): Dado un reinicio del personaje, cuando el usuario consulta un período anterior, entonces el historial continúa visible y sus actividades no vuelven a otorgar XP.
- AC-25 (RF-17): Dado un atributo STR, DEX, INT o WIT por debajo del nivel 50 y con al menos 6 XP disponibles antes de alcanzar su máximo diario en la fecha de la actividad, cuando el usuario registra 30 minutos de una actividad simple asociada a ese atributo, entonces el sistema otorga 6 XP.
- AC-26 (RF-17, RF-20): Dado un atributo STR, DEX, INT o WIT por debajo del nivel 50 y con un margen diario disponible mayor o igual a 0 y menor que 6 XP para la fecha de la actividad, cuando una actividad válida genera 6 XP, entonces el sistema otorga únicamente la XP restante hasta alcanzar 48 XP diarias.
- AC-27 (RF-18): Dados 30 minutos de una actividad con STR principal y DEX secundario, ambos por debajo de nivel 50 y con margen diario suficiente, cuando se calcula el aporte, entonces STR recibe 4 XP y DEX recibe 2 XP, conservándose una duración total de 30 minutos en el registro.
- AC-28 (RF-18, RF-20): Dada una actividad con dos atributos asociados por debajo del nivel 50, al menos uno de los cuales tiene menos margen diario disponible que la XP que le corresponde, cuando el sistema calcula el aporte para la fecha de la actividad, entonces cada atributo recibe el menor valor entre su aporte calculado y su propio margen diario disponible.
- AC-29 (RF-12, RF-17): Dado que CON está por debajo del nivel 50 y se registraron menos de cuatro comidas saludables en la fecha de la actividad, cuando se registra una nueva comida para esa fecha, entonces CON recibe exactamente 7 XP.
- AC-30 (RF-12, RF-17): Dado un día sin registros de comidas saludables, cuando el sistema calcula la XP de CON por comidas de esa fecha, entonces el aporte es 0 XP.
- AC-31 (RF-17): Dado que MEN está por debajo del nivel 50 y tiene al menos 3 XP disponibles antes de alcanzar su máximo diario en la fecha del sueño, cuando se acumulan 60 minutos válidos de sueño pendientes de contabilizar, entonces MEN recibe 3 XP.
- AC-32 (RF-17, RF-20): Dado que MEN está por debajo del nivel 50 y tiene un margen diario disponible mayor o igual a 0 y menor que 3 XP para la fecha del sueño, cuando se acumulan 60 minutos válidos de sueño pendientes de contabilizar, entonces el sistema otorga únicamente la XP restante hasta alcanzar 30 XP diarias.
- AC-33 (RF-19): Dado un usuario autenticado que envía una operación de creación o edición con un valor de XP, cuando el sistema procesa la operación, entonces la rechaza sin guardar ni modificar el registro o su progresión.
- AC-34 (RF-20): Dado un día calendario en UTC−3 dentro del ciclo actual, cuando STR, DEX, INT o WIT alcanza 48 XP, MEN alcanza 30 XP o CON alcanza 28 XP para esa fecha, entonces el sistema no otorga más XP a ese atributo por actividades de esa fecha dentro del mismo ciclo.
- AC-35 (RF-21, RF-35): Dado un atributo que ya recibió su tope diario de XP en una fecha del ciclo actual, cuando se registra otra actividad válida de esa fecha, entonces queda en el historial con 0 XP adicional y esa fecha sigue interrumpiendo la inactividad por la XP otorgada anteriormente.
- AC-36 (RF-22): Dados los valores de XP de los seis atributos y una actividad simple válida que aporta XP a uno solo, cuando se registra esa actividad sin que corresponda una penalización por inactividad, entonces solo cambia la XP del atributo asociado y los otros cinco conservan su XP.
- AC-37 (RF-23): Dado un atributo en nivel N, donde 1 ≤ N < 50, cuando únicamente ese atributo acumula 30 × N XP dentro de ese nivel, entonces sube a N + 1 sin aumentar los niveles de los demás atributos.
- AC-38 (RF-24, RF-25): Dado un atributo en nivel 50 con 0 XP, cuando se registra una actividad asociada o se aplica una penalización por inactividad, entonces conserva nivel 50 y 0 XP.
- AC-39 (RF-26): Dado un atributo en nivel 1 con 28 XP, cuando recibe 7 XP, entonces sube a nivel 2 y conserva 5 XP para la siguiente subida.
- AC-40 (RF-27): Dado un personaje en nivel general 1, cuando cualquiera de sus atributos sube por primera vez, entonces el personaje alcanza el nivel general 2.
- AC-41 (RF-27): Dado un personaje en nivel general G, donde 1 ≤ G < 50, cuyo contador está a una subida del requisito definido para ese nivel, cuando se registra la última subida necesaria, entonces el nivel general aumenta a G + 1.
- AC-42 (RF-29): Dado un personaje en nivel general 1 y contador 0, cuando una misma actividad provoca dos subidas de atributos, entonces alcanza nivel general 2 y conserva una subida contabilizada de las dos necesarias para alcanzar nivel general 3.
- AC-43 (RF-30): Dado un personaje en nivel general 49 cuyo contador está a una subida de atributo de completar el requisito de ese nivel, cuando se registra la última subida necesaria, entonces el personaje alcanza el nivel general 50.
- AC-44 (RF-30): Dado un personaje en nivel general 50, cuando posteriormente se produce una nueva subida válida de cualquiera de sus atributos, entonces el nivel general permanece en 50 y no se inicia una nueva transición de nivel general.
- AC-45 (RF-31): Dado un atributo con cinco fechas consecutivas sin actividad válida, cuando cierra la sexta fecha consecutiva sin actividad, entonces se contabilizan seis días de inactividad y no se descuenta XP.
- AC-46 (RF-31): Dado un atributo con seis fechas consecutivas sin actividad válida, cuando cierra la séptima fecha consecutiva sin actividad, entonces se determina que corresponde aplicar la penalización.
- AC-47 (RF-32): Dado un atributo con al menos 5 XP dentro de su nivel actual y seis fechas consecutivas sin actividad válida, cuando cierra la séptima fecha consecutiva sin actividad, entonces pierde 5 XP de su progreso dentro del nivel actual.
- AC-48 (RF-33): Dado un atributo en nivel N, cuando se aplica una penalización por inactividad, entonces conserva el nivel N.
- AC-49 (RF-34): Dado un atributo con menos de 5 XP dentro de su nivel actual, cuando corresponde aplicar la penalización por inactividad, entonces su XP queda en 0.
- AC-50 (RF-35): Dados STR y DEX por debajo de nivel 50 y con margen suficiente, cuando se registran 30 minutos con STR principal y DEX secundario, entonces reciben 4 y 2 XP respectivamente y esa fecha interrumpe la inactividad de ambos.
- AC-51 (RF-36): Dado un personaje cuyos seis atributos permanecen en nivel 1, cuando el sistema calcula su clase, entonces asigna la clase Novato.
- AC-52 (RF-36): Dado un personaje que ya no es Novato y en el que exactamente un atributo tiene un nivel igual o superior al 80 % del mayor nivel actual entre los seis atributos, cuando el sistema calcula la clase, entonces asigna la clase pura de ese atributo según la Tabla de Clases.
- AC-53 (RF-36): Dado un personaje en el que exactamente dos atributos tienen un nivel igual o superior al 80 % del atributo de mayor nivel, cuando el sistema calcula la clase, entonces asigna la clase combinada correspondiente a ese par según la Tabla de Clases.
- AC-54 (RF-36): Dado un personaje en el que exactamente tres atributos tienen un nivel igual o superior al 80 % del atributo de mayor nivel, cuando el sistema calcula la clase, entonces asigna Aventurero Versátil.
- AC-55 (RF-36): Dado un personaje en el que exactamente cuatro atributos tienen un nivel igual o superior al 80 % del atributo de mayor nivel, cuando el sistema calcula la clase, entonces asigna Maestro del Equilibrio.
- AC-56 (RF-36): Dado un personaje en el que exactamente cinco atributos tienen un nivel igual o superior al 80 % del atributo de mayor nivel, cuando el sistema calcula la clase, entonces asigna Héroe Ascendente.
- AC-57 (RF-36): Dado un personaje en el que los seis atributos tienen un nivel igual o superior al 80 % del atributo de mayor nivel y el personaje ya no es Novato, cuando el sistema calcula la clase, entonces asigna Héroe Integral.
- AC-58 (RF-37): Dado un personaje con una clase asignada y un nivel general entre 1 y 50, cuando se muestra su apariencia, entonces la skin pertenece a su clase actual y corresponde a Etapa I para niveles 1–14, Etapa II para niveles 15–34 o Etapa III para niveles 35–50.
- AC-59 (RF-38): Dado un personaje cuya combinación de atributos provoca un cambio de clase sin cambio de nivel general, cuando el sistema recalcula su clase, entonces muestra la skin de la nueva clase y conserva la etapa visual correspondiente a su nivel general.
- AC-60 (RF-38): Dado un personaje cuyo nivel general cambia por una subida o corrección, cuando el sistema actualiza su apariencia, entonces la skin de su clase actual corresponde a la etapa definida para el nivel general resultante.
- AC-61 (RF-39): Dado el catálogo de clases definido en este PRD, cuando el usuario abre el apartado de clases, entonces visualiza todas las clases, sus atributos o condiciones de equilibrio y las tres etapas visuales de cada una.
- AC-62 (RF-40): Dado un personaje con nivel general entre 1 y 50, cuando consulta el catálogo, entonces Etapa I aparece desbloqueada desde nivel 1, Etapa II desde nivel 15 y Etapa III desde nivel 35, y las etapas cuyo umbral aún no alcanzó aparecen bloqueadas.
- AC-63 (RF-41): Dado que el usuario solicita reiniciar el personaje, cuando todavía no confirmó explícitamente la operación, entonces el sistema no modifica la progresión.
- AC-64 (RF-41): Dado que el usuario solicita reiniciar el personaje, cuando cancela la confirmación, entonces el sistema no realiza cambios.
- AC-65 (RF-42): Dado que el usuario confirma el reinicio, cuando finaliza el proceso, entonces el nivel general y los seis atributos vuelven a 1, la XP y el contador de subidas generales quedan en 0, la clase es Novato, la skin es Etapa I y el nuevo ciclo no conserva consumos diarios, comidas puntuables, minutos de sueño pendientes ni inactividad del ciclo anterior.
- AC-66 (RF-43): Dado que el reinicio del personaje fue completado, cuando el usuario vuelve a acceder a su cuenta, entonces la cuenta, el nombre de usuario, las actividades personalizadas y el historial se conservan.
- AC-67 (RNF-08): Dadas dos cuentas distintas A y B autenticadas, cuando el usuario A intenta consultar atributos, actividades o historial pertenecientes al usuario B, entonces el sistema rechaza el acceso y no devuelve ningún dato de B.
- AC-68 (RNF-08): Dadas dos cuentas distintas A y B autenticadas, cuando el usuario A intenta crear, modificar o eliminar atributos, actividades o historial pertenecientes al usuario B, entonces el sistema rechaza la operación y los datos de B permanecen sin cambios.
- AC-69 (RF-07, RF-08): Dada una actividad personalizada en la que el usuario intenta seleccionar CON como atributo principal o secundario, cuando intenta guardar la actividad, entonces el sistema rechaza la operación y la actividad no queda almacenada.

- AC-70 (RF-04): Dado un nombre de usuario disponible que cumple las restricciones de RF-04, cuando el usuario lo confirma, entonces ese nombre queda asignado a su cuenta.
- AC-71 (RF-28): Dado un personaje creado, cuando el usuario consulta su progreso general, entonces no se muestra una barra de XP general independiente.

- AC-72 (RF-04): Dado un nombre de usuario de 16 caracteres, cuando se intenta asignarlo, entonces se rechaza y no queda asignado.
- AC-73 (RF-07, RF-08): Dada una actividad personalizada sin secundario y con un principal permitido, cuando se guarda con nombre válido y una categoría no vacía escrita libremente, entonces queda disponible con modalidad por duración y esa categoría.
- AC-74 (RF-08): Dados nombres vacíos, compuestos solo por espacios o de 51 caracteres después de recortar espacios extremos, cuando se intenta guardar una actividad personalizada con cada uno, entonces se rechaza cada operación y no se crea la actividad.
- AC-75 (RF-08): Dadas actividades personalizadas con nombres de 1 y de 50 caracteres tras recortar espacios extremos y demás datos válidos, cuando se guardan, entonces ambas quedan almacenadas.
- AC-76 (RF-08): Dada una actividad personalizada con categoría vacía y demás datos válidos, cuando se intenta guardar, entonces se rechaza sin crear la actividad.
- AC-77 (RF-10): Dada una actividad por duración, cuando se intenta registrar con 0 horas y 0 minutos o con duración negativa, entonces se rechaza sin guardar ni otorgar XP.
- AC-78 (RF-17): Dado STR por debajo de nivel 50 y con margen diario suficiente, cuando se registran actividades simples de 4, 5 y 9 minutos, entonces sus aportes respectivos son 0, 1 y 1 XP.
- AC-79 (RF-17, RF-18): Dados STR principal y DEX secundario por debajo de nivel 50 y con margen suficiente, cuando se registra una actividad de 10 minutos, entonces se generan 2 XP y se asignan 2 a STR y 0 a DEX.
- AC-80 (RF-17): Dado MEN por debajo de nivel 50, con margen diario suficiente, sin registros ni minutos pendientes de sueño en la fecha y ciclo evaluados y con al menos 60 minutos disponibles en esa fecha permitida, cuando se registran dos períodos de sueño de 30 minutos en la misma fecha y ciclo, entonces el primero aporta 0 XP y el segundo completa 3 XP en total.
- AC-81 (RF-17): Dado MEN por debajo de nivel 50 y sin otros minutos de sueño en esas fechas, cuando se registran 30 minutos de ayer y 30 minutos de hoy, entonces ambos aportan 0 XP por sueño.
- AC-82 (RF-25, RF-26): Dado un atributo en nivel 49 con 1.469 XP y margen diario de al menos 6 XP, cuando recibe 6 XP de una actividad, entonces alcanza nivel 50 con 0 XP y descarta el sobrante.
- AC-83 (RF-32): Dado un atributo con 12 XP y seis fechas consecutivas de inactividad sin descuentos, cuando cierran las fechas séptima, octava y novena sin actividad, entonces sus saldos son respectivamente 7, 2 y 0 XP, sin pérdida de nivel.
- AC-84 (RF-35): Dado un atributo con seis fechas consecutivas de inactividad, cuando en la séptima fecha solo se registra una actividad de cuatro minutos asociada a él, entonces no se reinicia su período de inactividad y al cierre corresponde la penalización.
- AC-85 (RF-44): Dado un atributo penalizado por siete fechas consecutivas sin actividad y sin otras operaciones posteriores, cuando se registra una actividad válida de ayer que interrumpe esa inactividad, entonces se reconstruye su progreso con el aporte de esa actividad y sin la penalización que esa fecha evita.
- AC-86 (RF-46): Dado un registro creado hace menos de cinco minutos en un ciclo anterior, cuando se intenta editar o eliminar después del reinicio, entonces ambas operaciones se rechazan y el registro conserva sus valores.
- AC-87 (RF-42, RF-44): Dado STR con el tope de 48 XP consumido hoy antes de un reinicio confirmado, cuando en el ciclo nuevo se registran 30 minutos de actividad simple de STR para hoy, entonces el registro pertenece al ciclo nuevo y otorga 6 XP.
- AC-88 (RF-42, RF-44): Dado un reinicio confirmado hoy y sin registros del ciclo nuevo, cuando se carga una actividad simple de STR de 30 minutos para ayer, entonces pertenece al ciclo nuevo y aporta 6 XP a su progresión.
- AC-89 (RF-20): Dado STR por debajo de nivel 50 con 47 XP otorgados hoy en el ciclo actual y al menos diez minutos disponibles en esa fecha, cuando se confirman simultáneamente dos registros distintos y válidos de cinco minutos de STR para hoy, sin otras operaciones concurrentes, entonces ambos se guardan y su aporte conjunto es exactamente 1 XP, sin superar 48 XP diarias.
- AC-90 (RF-04): Dadas dos cuentas sin nombre asignado y el nombre Martin disponible sin distinguir mayúsculas de minúsculas, cuando intentan confirmar simultáneamente Martin y martin sin otras asignaciones concurrentes de ese nombre, entonces exactamente una obtiene el nombre y la otra recibe un rechazo.
- AC-91 (RF-36): Dados STR en nivel 10, DEX en nivel 8 y los demás en nivel 1, cuando se calcula la clase, entonces resulta Maestro de Armas; con DEX en nivel 7 y los demás valores iguales, resulta Guerrero.
- AC-92 (RF-37, RF-40): Dada una clase actual, cuando se evalúan niveles generales 14, 15, 34 y 35, entonces las skins activas son respectivamente I, II, II y III; las etapas desbloqueadas son respectivamente I, I y II, I y II, y las tres.
- AC-93 (RF-44): Dados dos registros del mismo ciclo con igual fecha de actividad y fecha y hora de creación, cuando se reconstruye la progresión repetidas veces, entonces se procesan en orden ordinal ascendente de identificador y producen el mismo resultado en cada reconstrucción.

- AC-94 (RF-04): Dados nombres vacíos, con espacios, con tildes o con caracteres distintos de letras ASCII, números y guion bajo, cuando se intenta asignar cada uno, entonces se rechaza sin asignarlo.
- AC-95 (RF-04): Dados nombres disponibles de 1 y 15 caracteres permitidos, cuando se confirman, entonces quedan asignados conservando su escritura visible.
- AC-96 (RF-10, RF-47): Dada una fecha con 23 horas de duración registrada en el ciclo actual, cuando se intenta guardar una actividad de 1 hora y 1 minuto, entonces se rechaza sin guardar ni otorgar XP; una actividad de exactamente 1 hora es aceptada.
- AC-97 (RF-14, RF-15, RF-47): Dada una fecha con 24 horas registradas en el ciclo actual, cuando se edita válidamente un registro de 3 horas para que dure 2, entonces queda 1 hora disponible; al eliminar válidamente ese registro de 2 horas quedan 3 horas disponibles.
- AC-98 (RF-10, RF-47): Dadas las 23:00 del servidor en UTC−3 y al menos 3 horas disponibles en la fecha actual, cuando se registra una corrida ya realizada de 3 horas, entonces se guarda en esa fecha sin repartirla automáticamente entre días.
- AC-99 (RF-42, RF-47): Dadas 24 horas registradas hoy antes de un reinicio confirmado, cuando se registra 1 hora para hoy en el nuevo ciclo, entonces se guarda y quedan 23 horas disponibles en ese ciclo; los registros del ciclo anterior siguen visibles.
- AC-100 (RF-35): Dados STR y DEX sin XP otorgada hoy, por debajo de nivel 50 y con margen suficiente, cuando se registra una actividad de 10 minutos con STR principal y DEX secundario, entonces STR recibe 2 XP e interrumpe su inactividad, mientras DEX recibe 0 XP y no la interrumpe.
- AC-101 (RF-17, RF-35): Dado MEN sin XP otorgada hoy, por debajo de nivel 50, sin registros ni minutos pendientes de sueño para hoy en el ciclo actual y con al menos 60 minutos disponibles, cuando se registra un primer sueño de 30 minutos, entonces no interrumpe la inactividad; al registrar otros 30 minutos para la misma fecha y ciclo recibe 3 XP y esa fecha interrumpe su inactividad.
- AC-102 (RF-31, RF-42, RF-44): Dado un personaje reiniciado el lunes, cuando después del reinicio se carga una actividad puntuable del domingo y no se carga actividad del lunes, entonces aporta al ciclo nuevo sin contar inactividad anterior al reinicio y el lunes cuenta al cierre como el primer día de inactividad del nuevo ciclo.
- AC-103 (RF-14): Dado un registro del ciclo actual con valores válidos, cuando se intenta editar a los 299 segundos desde su creación según el servidor, entonces se admite; a los 300 segundos se rechaza aunque el teléfono indique una hora distinta.
- AC-104 (RF-47): Dada una fecha con 23 horas registradas en el ciclo actual, cuando se confirman simultáneamente dos registros distintos de 1 hora, entonces exactamente uno se guarda y el otro se rechaza, conservando el total de 24 horas.
- AC-105 (RF-10, RF-47): Dado un ingreso por duración, cuando horas o minutos contienen fracciones, valores negativos o minutos superiores a 59, entonces se rechaza sin guardar el registro.

- AC-106 (RF-31, RF-32): Dada una cuenta y su personaje creados el día 1 sin ningún registro de actividades, cuando cierran los días 1 a 6, entonces se cuentan respectivamente de uno a seis días de inactividad sin descuento; al cerrar el día 7 corresponde la primera penalización y los seis atributos conservan nivel 1 y 0 XP.
- AC-107 (RF-31, RF-32, RF-44): Dado un atributo con actividad válida los días 1, 2 y 3 y saldo de 12 XP al cerrar el día 3, sin actividad posterior, cuando se consulta su progreso después del cierre de los días 9, 10, 11 y 12, entonces muestra respectivamente 12, 7, 2 y 0 XP, conservando su nivel; repetir la consulta sin nuevos registros ni fechas cerradas conserva el mismo saldo.
- AC-108 (RF-14): Dados un registro de comida y otro por duración del ciclo actual, ambos creados hace menos de cinco minutos, cuando se intenta convertir comida en una actividad por duración o el registro por duración en comida, entonces se rechaza cada cambio y se conservan registros, saldo temporal y progresión.
- AC-109 (RF-14, RF-44): Dado un registro de comida del ciclo actual creado hace menos de cinco minutos, cuando se cambia su fecha por otra permitida, entonces se guarda la nueva fecha sin solicitar duración y se recalcula el aporte de CON según las comidas de cada fecha.

## Fuera de Alcance

- Selección de una clase objetivo.
- Recomendaciones de actividades.
- Notificaciones asociadas a recomendaciones de actividades.
- Registro de actividades laborales.
- Planificación de tareas futuras, agenda y calendario.
- Aplicación para iOS o versión web.
- Funcionamiento completo sin conexión a Internet.
- Más de un personaje por cuenta.
- Visualización pública de los atributos, las actividades o el historial de otros usuarios.
- Verificación de actividades mediante fotografías, GPS u otras pruebas.
- Integración con dispositivos wearables o aplicaciones de salud.
- Ligas privadas creadas mediante invitación.
- Torneos globales mensuales, ascensos y descensos de liga.
- Monedas, compras con dinero real y personalización cosmética independiente de la clase.
- Ligas globales.
- Ligas de amigos.
- Sistema de Amigos.
- Captura de horarios de inicio y fin o reparto automático de actividades entre fechas.

Estas funcionalidades podrán evaluarse para versiones posteriores.

## Riesgos y Dependencias

### Riesgos

- **Riesgo:** El registro manual puede resultar tedioso y provocar abandono.
  **Mitigación:** reutilizar actividades guardadas, limitar el flujo frecuente a tres interacciones y medir el tiempo real de registro (RNF-04).

- **Riesgo:** El usuario puede registrar actividades inexistentes o explotar el sistema únicamente para obtener puntos.
  **Mitigación:** utilizar confianza declarada, puntuación calculada por el sistema, límites diarios dentro de cada ciclo, sin afirmar que la actividad fue verificada (RF-17, RF-19 y RF-20).

- **Riesgo:** La velocidad de progreso puede ser demasiado lenta, rápida o favorecer ciertos atributos.
  **Mitigación:** simular distintas rutinas antes del desarrollo final y ajustar tasas, límites y costos de nivel con datos de pruebas.

- **Riesgo:** La penalización por inactividad puede desalentar el regreso.
  **Mitigación:** impedir la pérdida de niveles, limitar el descuento a la XP actual y validar la regla con usuarios (RF-32, RF-33 y RF-34).

- **Riesgo:** La gamificación puede volverse más importante que el bienestar real del usuario.
  **Mitigación:** evitar penalizaciones severas, no exigir pruebas invasivas y evaluar percepción de ayuda, no solo cantidad de registros.

- **Riesgo:** Una falla de autorización puede exponer información privada.
  **Mitigación:** aplicar controles de acceso en el servidor y mantener una suite específica con 0 accesos indebidos (RNF-08).

- **Riesgo:** La cantidad de clases, evoluciones y skins puede aumentar el costo del MVP.
  **Mitigación:** utilizar la Tabla de Clases y las tres etapas visuales definidas en este PRD, y reutilizar una estructura visual común antes de producir todos los recursos.

### Dependencias

- Servicio de autenticación de Google.
- Backend y almacenamiento persistente para cuentas, actividades, progreso.
- Conexión a Internet para registrar, sincronizar y consultar información.
- Catálogo inicial aprobado de actividades básicas; las categorías de actividades personalizadas son texto libre.
- Recursos visuales para las tres etapas de Novato y de las demás clases.
- Simulaciones de rutinas para validar la curva de progresión y las penalizaciones.
- Dataset de pruebas temporales para validar UTC−3, cargas atrasadas.

### Alcance de la validación

Los RNF expresan los objetivos de calidad del producto; no se presentan como resultados medidos. Por indicación del responsable del producto, ejecutar los estudios y muestras de validación siguientes no es una obligación personal ni una condición para entregar el PRD o el MVP. Las 1.000 sesiones son una muestra para automatización futura, no una tarea manual. Las metas con usuarios son hipótesis orientativas que requerirían participantes reales para acreditarse; no añaden funcionalidades a la app ni se consideran criterios de aceptación bloqueantes.
### Plan futuro orientativo de medición

- Rendimiento (RNF-01, RNF-02 y RNF-03): usar un dispositivo físico de referencia con Android 10, 4 GB de RAM y modelo documentado, y otro con la versión Android más alta soportada al probar. Red controlada de 10 Mbps de bajada y subida, 100 ms de latencia de ida y vuelta y sin pérdida inducida. Ensayar cuentas con 0, 1.000 y 10.000 registros, distribuidos en hasta 365 fechas y varios ciclos. Ejecutar 100 mediciones por operación, dispositivo y volumen; exigir los umbrales en cada combinación, sin mezclar muestras. Para edición y eliminación, preparar registros recientes. Documentar también el máximo de registros del ciclo actual usado al medir recálculo.
- Apertura: medir desde iniciar la app con proceso cerrado y sesión válida hasta pantalla principal interactiva con progreso cargado. El ingreso de credenciales en Google no forma parte de ese intervalo.
- Guardado: medir desde confirmar una operación válida hasta confirmación persistente del servidor y progreso actualizado visible. Una operación pendiente solo en el dispositivo no se considera confirmada. Historial: desde seleccionar el período hasta mostrar todos sus resultados o su estado vacío.
- Usabilidad (RNF-04): diez participantes distintos realizan un registro de comida, uno de entrenamiento y uno de sueño, todos previamente disponibles, desde la pantalla principal. Reportar mediana por tipo de tarea. Definición de interacción para el ensayo: cada toque, selección o gesto; el tipeo continuo dentro de un campo cuenta como una interacción, y pasar a otro campo cuenta aparte. Medir el tiempo desde la primera acción hasta confirmación visible. El máximo de tres interacciones debe cumplirse en cada flujo evaluado; esta definición puede exigir revisar el flujo de horas/minutos antes de cerrar el diseño.
- Disponibilidad (RNF-05): ejecutar una comprobación por minuto de autenticación con credenciales de prueba válidas, escritura/lectura de un registro de prueba y consulta de progreso. Una comprobación falla si el circuito no completa en 10 segundos. Calcular comprobaciones exitosas / comprobaciones previstas fuera de mantenimiento por mes calendario UTC−3; las comprobaciones omitidas cuentan como fallidas. Excluir solo ventanas notificadas con al menos 24 horas de antelación. Las fallas de proveedores que impidan el circuito cuentan como indisponibilidad del producto.
- Compatibilidad (RNF-10): ejecutar todos los flujos críticos en cada API Android soportada, desde API 29 hasta la más alta declarada por la versión en prueba, registrando modelo o configuración del emulador. No se exige compatibilidad anticipada con versiones futuras no probadas.
- Estabilidad (RNF-11): ejecutar 1.000 sesiones, repartidas por igual entre el dispositivo Android 10 y el segundo dispositivo de referencia. Cada sesión recorre todos los flujos críticos con datos de prueba, finalizando con reinicio confirmado. Sesión sin cierre inesperado significa que ninguna parte de ese recorrido termina por un cierre no solicitado. Exigir al menos 995 sesiones sin cierre inesperado.
- Persistencia (RNF-13): 100 ejecuciones con cierre forzado de la app después de una confirmación y 100 con interrupción de conexión y nueva sesión después de una confirmación. Verificar por identificador que todos los registros confirmados siguen persistidos y que el progreso coincide con su recálculo. La recuperación de conexión no equivale a reiniciar infraestructura de Firebase. Para evaluar la parte de servicio de RNF-13 se necesita además un entorno controlado de pruebas de persistencia con interrupción y recuperación del servicio; una prueba solo de cliente no acredita esa parte del requisito.
- Seguridad, fechas y accesibilidad: registrar la suite y versión ejecutadas, verificar todos los casos de autorización con identidades distintas, probar límites temporales con un reloj controlado de ensayo y comprobar cada texto/control y cada información esencial frente a los requisitos ya establecidos. Mantener los 1.000 reintentos de RNF-06 y agregar los casos simultáneos AC-89 y AC-90.

### Hipótesis orientativas para validar los objetivos

Prueba exploratoria con diez usuarios durante siete días. Al finalizar, sin ayuda, al menos ocho deben identificar una actividad de su historial y explicar qué atributo y progreso produjo; al menos siete deben puntuar con 4 o 5, en escala de 1 a 5, que la app les ayudó a reconocer avances cotidianos. Las respuestas pueden recogerse fuera de la aplicación: no se propone añadir encuestas, telemetría ni funcionalidades. Estos umbrales son una propuesta, no evidencia de eficacia ni garantía de mantener hábitos a largo plazo.

### Auditoría final del template y checklist del curso

| Control | Resultado documental |
|---|---|
| Template completo | Cumple: Contexto y Problema, Objetivos, Requerimientos Funcionales, Requerimientos No Funcionales, Criterios de Aceptación, Fuera de Alcance, Riesgos y Dependencias. |
| Cada RF es atómico y dice «debe» | Cumple: 47 RF, cada uno con una obligación principal. Los campos de una operación y sus condiciones no se presentan como funcionalidades independientes. |
| Cada RNF tiene un número concreto | Cumple: 13 RNF con umbrales numéricos. Se documenta un plan de medición futuro y su carácter no bloqueante. |
| Cada RF tiene al menos un AC | Cumple: los 47 RF tienen referencias en los 109 AC; las reglas de duración, inactividad, reinicio, niveles y acceso tienen casos explícitos. |
| Cada AC es binario y Dado/Cuando/Entonces | Cumple: 109 AC expresan condiciones y resultados verificables, con ejemplos numéricos y límites. |
| Fuera de Alcance explícito | Cumple: conserva las exclusiones aprobadas, incluyendo amigos, recomendaciones y reparto automático entre fechas. |
| AC de control de acceso | Cumple: AC-67 rechaza lectura y AC-68 rechaza escritura sobre datos de otra cuenta. |

Auditoría documental del checklist completada. No se ejecutaron pruebas de la aplicación, sesiones de estabilidad ni estudios con usuarios como parte de la elaboración de este documento. Los valores y objetivos anteriores no son evidencia de cumplimiento del software.

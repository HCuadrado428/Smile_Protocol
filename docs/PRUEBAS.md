# Cómo probar lo implementado (fases 1 a 11)

## Preparar el entorno

1. Instala **Java 21** (no hace falta instalar Gradle: el repositorio trae el wrapper).
2. Compila con `./gradlew build` (en Windows, `gradlew.bat build`). Salen dos jar:
   - `fabric/build/libs/smileprotocol-fabric-1.21.1-<versión>.jar`
   - `neoforge/build/libs/smileprotocol-neoforge-1.21.1-<versión>.jar`
3. Para probar: `./gradlew :fabric:runClient` o `./gradlew :neoforge:runClient` (y `runServer`).
4. Cada push compila también en GitHub Actions. Los jar quedan en la pestaña **Actions**, como artefacto `smileprotocol-jars`.

**Mods necesarios en la carpeta `mods` (Minecraft 1.21.1):**

| | Fabric | NeoForge |
|---|---|---|
| Loader | Fabric Loader 0.16.14 o superior + Fabric API | NeoForge 21.1.172 o superior |
| GeckoLib | `geckolib-fabric-1.21.1` 4.7.2 o superior | `geckolib-neoforge-1.21.1` 4.7.2 o superior |
| Veil | `veil-fabric-1.21.1` 4.3.0 | `veil-neoforge-1.21.1` 4.3.0 |
| Sound Physics Remastered | versión Fabric 1.21.1 | versión NeoForge 1.21.1 |
| Simple Voice Chat | opcional | opcional |

La configuración está en `config/smileprotocol.json`.

> En el entorno de desarrollo el cliente carga mods internos de las librerías. Si el aviso de mods no permitidos sale en rojo al conectarte, añade esos mod id a `allowed_mods` en `config/smileprotocol-common.toml`. Los administradores en creativo no cuentan para los ascensores, así que para probarlos hay que estar en aventura o supervivencia.

## Lobby (fase 5)

1. En el overworld, colócate donde quieras que aparezcan los jugadores.
2. `/spadmin lobby build`: muestra la zona que se va a ocupar (unos 43 × 12 × 44 bloques).
3. `/spadmin lobby build confirm`: construye la recepción y registra:
   - la región protegida,
   - el punto de aparición (también el del mundo),
   - los cinco ascensores: `mini1`, `mini2`, `mega`, `mini3` y `mini4`.
4. `/spadmin lobby info` lista lo registrado.

**Qué comprobar:**
- **Protecciones.** En aventura no se puede romper ni colocar, no hay daño ni hambre, no aparecen mobs y solo se interactúa con los bloques del mod.
- **Modo libre (por defecto).** El ascensor de servicio (mega) muestra «Cerrado» y los cuatro pequeños «Libre».
- **Mini ascensor.**
  - Entra con 2 jugadores y cada uno pulsa el botón del fondo de la cabina. El HUD muestra «X/Y listos».
  - Con todos listos empieza la cuenta atrás. Al llegar a cero se cierran las puertas, la pantalla se funde a negro y bajáis a la sala de pruebas.
  - Si un séptimo jugador entra, sale expulsado. Si uno no pulsa en 30 s mientras el resto está listo, también.
- **Modo evento.** Con `/smileprotocol mode event` los pequeños se cierran y el mega se abre. Con 4 jugadores en el lobby, cuando entran los 4 en el mega, baja solo.
- **Forzar la bajada.** `/smileprotocol start mini1 force` hace bajar a quien esté dentro.

## Sesión y partida (fases 2 y 3)

1. `/spadmin test enter`: entra en la sala de pruebas (`smileprotocol:smile_protocol`, en 0 100 0).
2. **Dentro de la sala, comprueba:**
   - el inventario real está vacío y tienes 4 huecos (1-4 y rueda);
   - solo se ve la barra de vida;
   - E, F3 y F5 no hacen nada y no se puede atacar, romper ni colocar;
   - el hambre no baja y la vida no se regenera;
   - `/sp lobby` no funciona.
3. `/smileprotocol leave` (o `/spadmin test leave`): vuelves al lobby con el inventario, el modo y la vida de antes.
4. **Crash simulado.** Desconéctate dentro de la sala, cierra el servidor y vuelve a entrar. Sigues en la partida y, al salir, recuperas el inventario.

## Movimiento (fase 7)

Dentro de una partida:
- **Correr** con Ctrl hace más ruido que andar, y **agachado** casi no hace.
- **Agachado = el de vanilla** (Shift). **Conductos:** al acercarte a un hueco de un bloque de alto te tumbas solo con el gateo de Minecraft (0,6 de alto) y entras; dentro sigues gateando hasta que haya sitio para levantarte. Gatear hace el mismo ruido que ir agachado.
- **Dive**: corriendo, pulsa **Shift**. Te deslizas tumbado unos metros y cabes por debajo de las tuberías caídas de los pasillos transversales. Tiene 3 s de espera.
- **Vault**: corriendo o andando hacia un obstáculo de 1 bloque (barriles, losas, escombros), pulsa **Espacio** y lo saltas apoyando la mano.
- **Mirar atrás**: mantén **C**. Gira la cámara, no la dirección en que corres.
- **Asomarse**: mantén **Q** (izquierda) o **E** (derecha), de pie o agachado. La cámara se desplaza y se inclina, sin atravesar paredes.
- **Soltar** el objeto de la mano: **G**.

Las teclas se cambian en `config/smileprotocol.json` (`key_look_back`, `key_lean_left`, `key_lean_right`, `key_drop`). No chocan con las de Minecraft.

Con `/spadmin debug on` se ven los ruidos como anillos de partículas. `/spadmin noise test <radio>` hace un ruido de prueba.

## Animaciones (fase 8)

Con **PlayerAnimator** instalado, los demás jugadores te ven:
- inclinarte al asomarte y girar el torso al mirar atrás;
- echarte hacia delante al correr y estirar los brazos en el dive;
- apoyar la mano en el vault;
- girar la válvula, meter el fusible, empujar la puerta, recoger del suelo o pasar la tarjeta.

Sin PlayerAnimator todo funciona igual; solo faltan estas animaciones.

## Interacción, fusibles, puertas y válvulas (fase 9)

Todo se usa **mirándolo y manteniendo el clic derecho**. Abajo del punto de mira sale qué vas a hacer, y una barra mientras lo mantienes. Si te mueves, se cancela.
- **Objetos del suelo** (fusibles, llaves, tarjetas, rueda): se recogen en 0,3 s. Con los 4 huecos llenos no se puede.
- **Caja de fusibles**: «Insertar fusible (1/2)». El piloto pasa de rojo a verde cuando el circuito está completo. Los fusibles se ven dentro de la caja. Los fundidos no dan corriente, y también se pueden sacar fusibles. Cuantos menos jugadores, menos fusibles hacen falta.
- **Puertas**:
  - normales: agachado se abren despacio y sin ruido;
  - con llave: llave roja o azul;
  - con tarjeta: nivel 1, 2 o 3;
  - eléctricas: se abren solas con su circuito;
  - hidráulicas: se abren al girar sus válvulas;
  - atascadas: hacen falta 2 jugadores a la vez.
- **Válvulas**: se tardan 3 s en girar y hacen ruido. En `lab_generators` van en orden (1 y luego 2) y a una le falta la rueda, que está en el suelo de otra sala. En `lab_greenhouse` hay dos que hay que girar a la vez.

Para construir, mirando el bloque:
- `/spadmin door set <tipo> [parámetro]`
- `/spadmin lock set <llave> [consume]`
- `/spadmin circuit set <circuito> [fusibles]`
- `/spadmin valve set <grupo> [orden] [coop]`

Para probar sin buscar: `/spadmin give fuse|key red|keycard 2|wheel`.

## Escondites (fase 10)

Taquillas, armarios (caben 2), contenedores, camillas (se entra tumbado) y cortinas (ocultación parcial).
1. Mantén clic derecho mirándolo: se abre, entras y se cierra.
2. Dentro eres invisible, no te puedes mover y la vista está limitada. Se ven rendijas según el tipo.
3. **Shift** aguanta la respiración, mientras dure la barra de aliento. Si se agota, exhalas y haces ruido.
4. **Clic derecho** para salir.

Con `/spadmin hide set <tipo>` conviertes el bloque que miras en escondite.

## Berserker y derribo (fase 11)

El Berserker es ciego: solo oye. Corre al sitio donde sonó el ruido y golpea **esa zona**, no a ti.

1. **Aparece solo en las plantas.** 1 en la recepción y 2 o 3 en las demás plantas (más o menos según el tamaño del grupo), siempre lejos de la llegada. No se mueve hasta que el grupo está cerca. Con `/spadmin debug on` se ven los anillos de ruido.
2. **Uno a mano.** `/spadmin mob spawn berserker` lo pone donde estás; `/spadmin mob clear` quita los de alrededor.
3. **Agachado no te oye.** Andar agachado (radio 3 × 1,5 = 4,5) queda por debajo de su umbral de 5. Andar normal (8 × 1,5 = 12) o correr sí lo atrae. Las paredes atenúan el ruido.
4. **La luz no le importa.** Puedes iluminarlo con la linterna a un metro: no reacciona.
5. **Golpe de área.** Al llegar al punto se yergue con los brazos arriba (unos 0,6 s de aviso) y golpea un radio de 2,5 bloques. Si te apartas antes del golpe, falla. Si sigues dentro, caes **derribado**.
6. **Cambio de objetivo.** Mientras va hacia un ruido solo cambia de objetivo si oye otro un 50 % más fuerte. Haz ruido en un lado (`/spadmin noise test 12`) y luego en otro más flojo: no cambia.
7. **Después del golpe** se queda 1,5 s recuperándose y luego escucha unos 6 s girando la cabeza. Si nadie hace ruido, vuelve a merodear.
8. **Escondites.** No los revisa, pero si haces ruido dentro (por ejemplo, al quedarte sin aliento), golpea ese sitio y te saca derribado.
9. **Puertas.** Abre las puertas normales del laboratorio (haciendo ruido). Las de seguridad, con llave o tarjeta, le cortan el paso.
10. **Derribado.** Te arrastras muy despacio, se oye al arrastrarte y gimes de vez en cuando. Tienes 60 s antes de desangrarte. Si el Berserker te oye, viene y te **remata** (ver la fase 12). Para probarlo sin mob: `/spadmin mob down`.

11. **Agarre y QTE (mob de pruebas).** `/spadmin mob spawn smiler_test` pone un smiler que **sí ve**: te persigue y, si te alcanza, te agarra: te arrastra hasta sus manos, te sujeta por los hombros pegado a él y te sacude. Sale el minijuego:
    - **MASH:** machaca ESPACIO (es el que usa el mob de pruebas; se cambia en `data/smileprotocol/mobs/smiler_test.json`, campo `qte.type`);
    - **TIMING:** ESPACIO cuando la marca pasa por la zona (3 aciertos; 2 fallos y fuera);
    - **SEQUENCE:** las teclas W A S D en el orden que se muestra (una mala quita tiempo);
    - **HOLD:** mantén ESPACIO para llevar la marca a la zona, que se mueve.
    Si te sueltas, te empuja hacia atrás y el mob queda aturdido unos segundos. Si fallas, caes derribado. Con un **dive** justo cuando te va a coger, lo esquivas. El tiempo del minijuego crece en grupos pequeños (2 jugadores: ×1,4). El servidor valida las pulsaciones: no se puede hacer trampa con macros de más de 40 por segundo.
12. **Sonidos.** Pasos, respiración, chasquidos al oír algo, olfateo, el chillido del aviso y el golpe del Berserker, el quejido del derribado, el latido y el agarre son propios (provisionales, generados con `tools/sounds.py`). Activa los subtítulos para verlos.

**Configuración nueva:** `bleed_out_time`, `downed_noise_interval`, `noise_crawl`, `noise_groan`, `berserkers_by_difficulty` y `mobs_wait_for_players`. La ficha del mob está en `data/smileprotocol/mobs/berserker.json`.

## Derribado, reanimación, remates y muertes (fase 12)

Hacen falta **dos jugadores** para reanimar y ayudar. Comandos útiles: `/spadmin player <jugador> down|revive|kill`, `/spadmin mob execute <jugador>` (el mob más cercano lo derriba y lo remata) y `/spadmin mob kill <jugador>` (muerte instantánea del mob más cercano).

1. **Reanimar.** Mira a un compañero derribado y mantén **click derecho** (6 s). Te arrodillas y lo zarandeas. Si te mueves, sueltas o te agarran, se corta. Con dos rescatadores tarda menos. Hace ruido.
   - El derribado ve «Te están reanimando» con una barra y **no se desangra** mientras tanto.
   - Al acabar, se levanta (tumbado → a cuatro patas → de rodillas, alzando la mano hacia quien le ayuda → de pie) con el **30 %** de la vida y **3 s de protección** contra remates.
2. **Ayudar en un agarre.** Si a un compañero lo tiene agarrado un mob, mírale y mantén **click derecho** (2 s) tirando de él. Al acabar se suelta, como si hubiera ganado el minijuego (el mob queda aturdido).
3. **Daño letal = derribo.** Dentro de la partida no hay pantalla de muerte de vanilla: si la vida llega a 0 (una caída, por ejemplo) quedas derribado. Si ya lo estabas, mueres. `/kill` y el vacío matan directamente.
4. **Remates** (no se pueden cancelar una vez empiezan; la víctima es invulnerable y la cámara se clava en la cara del mob, tiembla con cada golpe, la sangre salpica la pantalla y resbala, y se funde a negro). Cada golpe suelta sangre y deja charcos en el suelo y manchas en las paredes cercanas:
   - **Berserker:** si golpea donde hay un derribado, lo aplasta tres veces alternando los brazos, le rompe los brazos y las piernas, lo levanta por la espalda, lo desgarra y lo estampa contra el suelo; queda retorcido y con espasmos.
   - **Smiler de pruebas** (`/spadmin mob spawn smiler_test`): tras dejarte derribado por fallar el agarre se aparta 5 s y vuelve a por ti si te ve: te levanta la cabeza, te la gira del todo y te la arranca; se queda mirándola con ella en la mano mientras el cuello chorrea.
5. **Muerte instantánea** (`/spadmin mob spawn smiler_kill`, los dos ojos abiertos): te persigue y, al alcanzarte, sin minijuego ni derribo, te levanta del suelo, te muerde la cara dos veces partiéndote el cuello, te arranca la garganta de un tirón y te deja caer desangrándote.
6. **Muerte final.** Pasas a **espectador** de tus compañeros vivos («HAS MUERTO»): click izquierdo y derecho cambian de compañero. Sigues en el grupo; al subir el grupo en el ascensor reaparecerás en la cabina (fase 16). Si mueren todos, el grupo queda eliminado y volvéis al lobby al acabar la partida.

**Configuración nueva:** `revive_time` (ticks, 120), `revive_health_percent` (30), `revive_protection` (ticks, 60), `noise_revive` (7) y `qte_help_time` (ticks, 40). Las fichas de los mobs admiten `"sequences": {"execution": ..., "kill": ...}` con `BERSERKER_EXECUTION`, `SMILER_EXECUTION` o `SMILER_KILL`.

La jeringa de adrenalina (levantarse solo) es de la fase 13.

## Objetos: consumibles y lanzables (fase 13)

Dámelos con `/spadmin give medkit|battery|flare|adrenaline|can|bottle`. En las plantas también aparecen sueltos, al azar, en los sitios de fusibles que sobran (más latas y botellas que curas).

1. **Prioridad del clic derecho:** si miras algo interactuable (puerta, fusible, compañero...), interactúas. Si no, se usa lo que tengas en la mano. El HUD lo indica («Usar: Botiquín», «Apuntar: Lata»).
2. **Consumibles** (mantén clic derecho; barra de progreso; te quedas quieto mientras lo usas; se cancela si sueltas, cambias de hueco, te agarran o te derriban; hacen algo de ruido):
   - **Botiquín** (2,5 s): cura 4 corazones. Te agachas y te vendas el brazo. Con la vida llena no deja usarlo.
   - **Batería** (1,2 s): recarga la linterna entera. Con la linterna cargada no deja usarla.
   - **Bengala** (0,8 s): la enciendes y la sueltas delante. Arde 40 s con luz roja (Veil), chispas y humo, y **cada 2 s hace ruido**: el Berserker va hacia ella. Sirve para alumbrar o para distraer.
   - **Jeringa de adrenalina** (4 s): **solo derribado**. Te la clavas en el muslo y te levantas solo con el 20 % de la vida.
3. **Lanzables** (lata, botella): mantén clic derecho para apuntar (levantas el brazo y aparece una trayectoria de puntos con una marca roja donde caerá); **clic izquierdo** lanza; soltar el clic derecho cancela sin gastarlo. No se puede corriendo. Al caer hace ruido en ese punto (lata 11, botella 15) con el sonido de la superficie: los mobs van a mirar. La **botella se rompe**; la **lata rebota y se queda en el suelo** para volver a cogerla. No hacen daño. 0,75 s de espera entre lanzamientos.
4. **Soltar (G)** sigue funcionando con todo.

**Configuración nueva:** `noise_flare` (10) y `throw_cooldown` (ticks, 15). Las fichas están en `data/smileprotocol/consumables/*.json` (tiempo de uso, efecto y cantidad, ruido, gesto, sonido, si se puede derribado) y `data/smileprotocol/throwables/*.json` (ruido, si se rompe y fuerza).

## Misiones (fase 14)

Cada planta tiene su misión: una lista de objetivos que se muestran de uno en uno en una nota arriba a la izquierda («OBJETIVO 2/4: Abre el almacén cerrado con la llave roja»). Al cumplir uno suena una campanilla y la nota destella. Son del grupo entero y cooperativos.

1. **Orden libre.** Si hacéis las cosas en otro orden (por ejemplo, poner los fusibles antes de abrir la puerta), los objetivos ya hechos se saltan solos.
2. **El ascensor de salida espera a la misión.** Aunque tenga corriente, no arranca hasta que el único objetivo pendiente sea subir: en su panel pone «Faltan objetivos».
3. **Plantas actuales:**
   - Recepción: fusibles del ascensor → ascensor.
   - Almacén: llave roja → puerta del almacén → fusibles → ascensor.
   - Administración: tarjeta 1 → sala de seguridad → fusibles → ascensor.
   - Morgue: llave azul → cámara → puerta atascada → fusibles → ascensor.
   - Planta de energía: rueda de válvula → válvulas de vapor en orden (al terminar se apagan las luces) → circuito auxiliar → fusibles → ascensor.
   - Invernadero: válvulas de niebla (entre dos) → tarjeta 2 → laboratorio sellado → fusibles → ascensor.
   - Criogenización: tarjeta 3 → sala de criogenización → corriente a las cápsulas (se despierta un Berserker) → aguantar 25 s → ascensor.
4. **Fichas:** `data/smileprotocol/missions/<planta>.json`. Tipos de objetivo: `FIND_ITEM`, `OPEN_DOOR` (o `forced` para las atascadas), `INSERT_FUSES`, `TURN_VALVES`, `POWER`, `REACH` (marcador), `SURVIVE` (segundos) y `ESCAPE`. Eventos al cumplirlos (`on_complete`): `message`, `sound`, `spawn_mob`, `lights_off`, `lights_on` y `power`. Una planta sin fichero usa «fusibles del ascensor → ascensor».

## Eventos de laboratorio y sustos (fase 15)

Lo que hacéis en el laboratorio puede provocar eventos: unos solo asustan y otros son peligro de verdad (hacen ruido o traen un mob). Están en `data/smileprotocol/lab_events/*.json` y se pueden editar sin recompilar. Cada jugador recibe como mucho un susto cada 20 s (`jumpscare_cooldown`).

1. **Primer fusible de la planta:** las luces parpadean con un zumbido de sobretensión.
2. **Mover un fusible de una caja a otra:** parpadeo, sobretensión y la **cara de Smiley** a pantalla completa con un golpe de sonido (solo quien lo movió).
3. **Poner un fusible** (25 %, como mucho cada minuto): apagón de 6 s y ruido que atrae mobs.
4. **Recoger una tarjeta:** salta la **alarma** 5 s, las luces parpadean, mucho ruido y un mensaje por radio.
5. **Esconderse** (20 %): golpes en la pared al rato y la cámara tiembla.
6. **Forzar una puerta atascada:** chirrido y ruido fuerte.
7. **Encender la linterna** (12 %): un smiler se asoma a lo lejos un segundo y desaparece en humo. No ataca.
8. **Al azar** de vez en cuando: una puerta abierta cercana se cierra de golpe y se apagan algunas luces, o se oye un grito lejano. También un grito al poco de llegar a cada planta.
9. **Caer derribado:** mensaje por radio para todo el grupo. **Cumplir un objetivo** (30 %): destello y parpadeo.
10. **Invernadero, al abrir las válvulas de niebla:** apagón, la cara y aparece un Berserker.

Disparadores: `fuse_inserted`, `fuse_removed`, `fuse_moved`, `circuit_powered`, `circuit_unpowered`, `door_opened`, `door_closed`, `door_forced`, `valve_turned`, `valve_group_complete`, `item_picked`, `item_dropped`, `hide_enter`, `hide_exit`, `zone_entered`, `flashlight_on`, `player_downed`, `player_revived`, `mission_objective_completed` y `timer` (cada segundo; la ficha pone la probabilidad). Condiciones: `once`, `cooldown` (ticks), `chance`, `min_players`, `zone`; `arg` admite `*` al final. Alcance: `trigger_player`, `players_in_zone` o `all`. Acciones: `jumpscare` (`visual`, `audio`, `flash`, `peek`, `environment`), `light_flicker`, `blackout`, `lights_off`/`lights_on`, `alarm`, `sound`, `lock_door`/`unlock_door`, `door_slam`, `set_circuit`, `noise_burst`, `spawn_mob`, `message`, `radio_message`, `camera_effect` y `ambient_damage`.

## Ascensores de zona, game over y fin de partida (fase 16)

1. **Los muertos vuelven en el ascensor.** Cuando el grupo sube, los compañeros muertos (espectadores) aparecen dentro de la cabina con los demás, curados, pero **pierden lo que llevaban** (salvo la linterna), igual que los heridos, los derribados y los que se quedaron fuera (`elevator_strip`).
2. **Viaje:** pantalla negra y la cabina tiembla al salir y al frenar.
3. **GAME OVER.** Si mueren todos los del grupo, sale la pantalla de GAME OVER (estática, letras rojas que tiemblan, en qué planta caísteis y el tiempo) con **«Volver al lobby»** o **«Seguir mirando»**. Los eliminados pasan a mirar a los jugadores vivos de **cualquier partida en curso** (`spectate_scope`: `ALL_RUNS` u `OWN_RUN`); clic para cambiar. Si no queda nadie a quien mirar, vuelves al lobby al acabar la partida.
4. **Escapar.** Al subir en el último ascensor el grupo termina: pantalla «HABÉIS ESCAPADO» con la planta y el tiempo.
5. **Resultados.** Al acabar la partida aparece una hoja con los resultados de cada grupo (estado, planta, tiempo y supervivientes) hasta volver al lobby.

La **batalla final contra Smiley** sigue pendiente de diseño (README 27); de momento la sala final acaba con su misión (despertar al Berserker y aguantar).

## Conductos de ventilación (fase 17)

1. **Rejillas.** En cada planta hay al menos un conducto con dos rejillas en el suelo de dos salas alejadas. Mantén el clic derecho sobre una rejilla 2 s: hace ruido y queda abierta (un agujero de un bloque).
2. **Entrar.** Baja al agujero y avanza: te tumbas a gatas solo (pose de gateo de vanilla). Dentro no puedes correr, hacer dive, vault, asomarte ni apuntar lanzables; la linterna y los consumibles sí funcionan.
3. **Ruido.** Gatear sobre la chapa hace más ruido que gatear normal (`noise_vent_mult`, 1,6 por defecto). Al entrar puede sonar algo arañando la chapa (`vent_scratch`).
4. **Salir.** Al llegar a la otra rejilla, quítala desde abajo mirando hacia arriba, levántate y sube de un salto.
5. **Sellada (morgue).** Una de las rejillas pone «Sellada: sin corriente»; mete un fusible en la caja del circuito `vent` y ya se puede retirar.
6. **Ventilador (planta de energía).** A mitad del conducto hay un ventilador girando que no deja pasar; se para al dar corriente al circuito auxiliar (`aux`).

## Voz: Simple Voice Chat (fase 18)

Hace falta Simple Voice Chat en el servidor y en los clientes (es opcional: sin él todo lo demás funciona igual).

1. **Hablar hace ruido.** Con la depuración activa (`/spadmin debug`), habla cerca de un mob: aparece el anillo de ruido. Susurrando (tecla de susurro de Simple Voice Chat) el radio es pequeño; hablando normal, medio; gritando, grande (`noise_voice_whisper`, `noise_voice`, `noise_voice_shout`).
2. **Espectadores.** Al morir del todo pasas a un grupo de voz oculto «Espectadores …»: solo te oyen los otros muertos de tu partida y ya no oyes a los vivos. Al volver en el ascensor sales del grupo.
3. **Sin grupos a mano.** Dentro de la partida, intentar entrar o salir de un grupo desde el menú de Simple Voice Chat no hace nada (en el lobby sí).

## Marcadores y walkies (fase 19)

1. **Marcador rápido.** Pulsa **R** mirando a un sitio: aparece un círculo con «!» y la distancia, del color de tu jugador, para todo tu grupo (a través de las paredes). Los compañeros oyen un aviso suave. Desaparece a los 20 s (`marker_duration`).
2. **Rueda.** Mantén **R** y mueve el ratón: arriba **Peligro** (triángulo), derecha **Ir allí** (flecha), abajo **Despejado** (✓), izquierda **Necesito ayuda** (cruz). Suelta para ponerlo. La vista no se mueve mientras eliges.
3. **Límites.** Como mucho 3 marcadores tuyos (`marker_max`); uno nuevo del mismo tipo sustituye al anterior. Agarrado o en un remate no se puede marcar. Derribado, R pone **Necesito ayuda** encima de ti y sigue contigo hasta que te levantan.
4. **Fuera de la pantalla.** Si el marcador queda detrás o fuera, se ve más pequeño pegado al borde.
5. **Walkie** (con Simple Voice Chat). Empiezas con uno. Con él en la mano y sin nada delante, mantén el clic derecho para encenderlo o apagarlo. Con él en la mano y encendido, mantén **B** para hablar: la HUD pone «TRANSMITIENDO» y tus compañeros con el walkie encendido (en cualquier hueco) te oyen con sonido de radio, estén donde estén; a ellos les sale «Se oye: tu nombre» y su walkie cruje (eso hace ruido). Los muertos del grupo también lo oyen. Cerca de un mob con `causes_interference` el sonido se corta y chisporrotea.

## Ambientación (fase 20)

1. **Tensión.** Acércate a un Berserker (o deja que te agarren, o cae derribado): la imagen se desatura, los bordes se separan en rojo y azul, ondula un poco, sale grano y la viñeta late. Al alejarte vuelve a la normalidad poco a poco. Se quita con `tension_shader: false` o se suaviza con `tension_strength`.
2. **Ambiente.** Dentro de la partida suena un zumbido grave constante que de vez en cuando se apaga del todo unos segundos (silencio) y que baja cuando hay tensión. Cada 20-60 s cruje la estructura o gotea algo a unos metros (con Sound Physics Remastered se oye el eco de la sala). Se quita con `ambience: false`.

## Smiley (fase 21)

1. **De lejos.** En cada planta, entre 1 y 3 minutos después de llegar, aparece una vez al fondo de un pasillo mirándote. Si lo miras un rato o te acercas, se funde en una mancha negra y desaparece. No ataca. Se quita con `smiley_cameos: false`.
2. **Intro.** En la sala final (Laboratorio Cero), al adentrarte unos bloques se apagan las luces y aparece en el centro: se le enciende el ojo y se le abre la sonrisa. A los 5 s vuelven las luces y empieza a moverse. Con `/spadmin mob smiley` sale con la intro donde estés.
3. **Generadores.** Hay tres: en el suelo (al norte), en el anillo de pasarelas (al este) y arriba (al oeste). Cada uno necesita sus fusibles, que están repartidos por los tres niveles. Al encender uno, sube de fase y grita.
4. **Fase 1.** Anda despacio y teatral. Si te coge, sale un minijuego (cada vez uno distinto: machacar, secuencia, ritmo, aguja). Si fallas, te levanta hasta su cara y te arranca la garganta.
5. **Fase 2.** Más rápido. Invoca a 1 o 2 smilers (rastreador, apagón o berserker) lejos de vosotros y apaga las luces de alrededor unos segundos.
6. **Fase 3.** La sala se queda a oscuras. Corre y mata al instante, sin minijuego.
7. **Linternas.** Si le dan dos linternas a la vez (o una, si solo queda uno), se tapa la cara y retrocede; a veces se va por la pared.
8. **Mancha.** Si lleva un rato persiguiendo a alguien sin alcanzarlo (o se aleja mucho), se funde en la pared y sale detrás de otro jugador.
9. **Grito.** De cerca y viéndote, grita: la pantalla tiembla, vas lento y casi a ciegas un par de segundos.
10. **Susurros.** Cada cierto tiempo a uno le sale abajo una frase con su nombre y lo oye susurrar.
11. **Escondites.** Si te escondes delante de él, va al escondite y te saca.
12. **Final.** Con los tres generadores encendidos, el ascensor baja: hay que aguantar 45 s. Al llegar, Smiley queda encerrado tras unas rejas en el centro, con el ojo apagado y la sonrisa encendida, y se abre el ascensor.
13. **Si caéis todos.** Al último que queda en pie lo coge del tobillo y se lo lleva a rastras hacia la oscuridad; se ríe y se acaba la partida.

## Depuración

- `/spadmin debug on`: muestra el panel de estado (sesión, mods, lobby y ascensores). Solo lo ven los operadores.
- Los eventos importantes se escriben en `sp-debug.log`, en la carpeta del servidor.

## Partidas, grupos e instancias (fase 6)

Al bajar un ascensor se crea una **partida** (`R1`, `R2`...) con uno o varios **grupos** (`R1.1`, `R1.2`...). Cada grupo juega en su propio hueco de la dimensión, a 4096 bloques de los demás.

**Plantas provisionales.** Hasta que se diseñe el mapa (fase 21) hay 7 **plantas** de laboratorio. Cada ascensor te baja a una planta entera; al llegar sale el rótulo «PLANTA -N» con su nombre:
- el vestíbulo de llegada da a un pasillo principal;
- hay tres pasillos transversales y dos perimetrales;
- a ambos lados hay unas 45 salas (laboratorios, despachos, almacenes, morgue, vestuarios, servidores…), cada una con su puerta y su cartel;
- al fondo está el vestíbulo del ascensor de salida.

Las plantas son `start_corridor`, `lab_storage`, `lab_offices`, `lab_morgue`, `lab_generators`, `lab_greenhouse` y `final_room` (intermedias) y la sala del jefe `smiley_lab` (final). Cada partida elige **3 intermedias** al azar.

**Qué comprobar:**
1. **Mini ascensor.** Bajad 2 jugadores por `mini1`. Aparecéis en la cabina del pasillo y el chat dice «Grupo R1.1 · 2 jugadores». La pantalla del ascensor pasa a «En curso, pasillo».
2. **Varias a la vez.** A la vez, otros 2 bajan por `mini2`. Cada grupo está en su propia copia (no os veis). `/smileprotocol runs` lista las dos partidas.
3. **Ascensor de salida.**
   - Sus puertas están cerradas hasta que el circuito **exit** de la planta tiene corriente: hay que encontrar los fusibles y meterlos en sus cajas.
   - Con corriente, las puertas se abren y se encienden las luces del circuito. Entra en la cabina: empieza la cuenta atrás de 15 s y sale cuando estáis todos.
   - Al bajar se cura a todos. El que estaba herido o fuera de la cabina pierde sus objetos.
   - También se puede avanzar con `/smileprotocol zone R1.1 next`.
4. **Terminar.** Un `next` más allá de la sala final marca el grupo como **completado**. Salen los resultados en el chat (zona, tiempo y supervivientes) y a los 10 s volvéis al lobby. El ascensor vuelve a «Libre».
5. **Eliminación.** Si todos los del grupo salen (`/smileprotocol leave` o morir), el grupo queda eliminado y la partida termina igual.
6. **Desconexión.** Desconéctate dentro y vuelve a entrar antes de 120 s: vuelves con tu grupo, y si el grupo ha cambiado de zona apareces en la actual. Si no queda nadie conectado durante 120 s, el grupo queda eliminado.
7. **Reinicio del servidor.** Reinicia con una partida en marcha. La partida, los grupos y la zona siguen, y el ascensor sigue «En curso».
8. **Mega ascensor.**
   - Pasa a modo evento (`/smileprotocol mode event`). Con 8 jugadores salen 2 grupos de 4 y con 9, uno de 5 y otro de 4. `/smileprotocol groups` muestra el reparto que saldría.
   - Al terminar la partida, el mega muestra «Preparando (30 s)» y se reabre solo.
   - `/smileprotocol newrun 1234` fija la semilla de la siguiente.
9. **Grupos a mano.** `/smileprotocol groups set 1 <jugadores>` fija un grupo para el mega. `groups move <jugador> <n>` mueve a un jugador y `groups clear` borra los fijados.
10. **Dificultad.** Con `/spadmin debug on`, el panel muestra tu grupo, su hueco, los multiplicadores (2 jugadores: fusibles ×0,5, mobs ×0,6, QTE ×1,4) y cuántos puntos de mobs y fusibles se han elegido en la zona.

Los eliminados y los que han terminado pueden volver antes con `/sp lobby`.

**Configuración nueva** (`config/smileprotocol.json`):
- `max_groups`, `instance_spacing`, `intermediate_zones`, `avoid_repeat_last`, `per_group_zones`, `group_mode`;
- `mini_return_delay`, `disconnect_grace`, `replayable`, `max_plays_per_player`, `lobby_reset_delay`, `auto_newrun`;
- `start_zone` y `final_zone`.

La tabla de dificultad está en `data/smileprotocol/difficulty/group_scaling.json`. Un datapack la puede sustituir.

**Para el diseño del mapa.** Una zona es una plantilla de estructura (`.nbt`) más un JSON en `data/<ns>/zone/`. Los puntos se marcan dentro del juego con **bloques de estructura en modo DATA**:
- `sp:entry 0` (donde aparece el grupo y hacia dónde mira);
- `sp:exit_cabin` (dos esquinas de la cabina de salida);
- `sp:mob_spawn` y `sp:fuse`.

Las intermedias que pueden salir se listan en `data/<ns>/zone_pool/`.

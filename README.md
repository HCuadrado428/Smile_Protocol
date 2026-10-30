# Smile Protocol

Mod para **Minecraft 1.21.1** en **Fabric y NeoForge** (Java 21; un jar por loader) para un evento de terror multijugador en un laboratorio abandonado, con misiones, sigilo y objetivos. Los jugadores **no atacan**: son las víctimas.

> ## Implementación: TODO lo hace Claude
>
> **Este mod lo desarrolla íntegramente Claude, de principio a fin.** No hay otro desarrollador: Claude escribe y mantiene el código Java, el `build.gradle` y la configuración de Gradle, `mods.toml`, los registros, los paquetes de red, los datapacks y JSON (dimensión, misiones, escondites, cinemáticas), los shaders GLSL, los modelos y animaciones en JSON (GeckoLib, PlayerAnimator), los ficheros de idioma (es/en), las pruebas y la documentación. Para los recursos binarios (texturas `.png`, sonidos `.ogg`) Claude genera versiones provisionales de forma procedural o con scripts, y quedan marcadas para sustituirlas por arte definitivo si se desea.
>
> Este documento es la especificación completa de **mecánicas**. El **mapa se diseña en una fase posterior** (ver sección 22), también a cargo de Claude. Cuando algo dependa de una API externa (**Veil**, PlayerAnimator, Simple Voice Chat, **Sound Physics Remastered**, GeckoLib), **verifica la versión exacta compatible con 1.20.1 antes de usarla**. Si algo es ambiguo, pregunta antes de inventar.

---

## 1. Resumen del concepto

- El evento se juega en un **servidor dedicado aparte**. Todos los jugadores aparecen en un **lobby** en el overworld, una **recepción de laboratorio y hospital** con muchos detalles. Un comando (`/sp lobby`) lleva a los jugadores ahí (ver 5).
- Al fondo del lobby hay **dos tipos de ascensor** (ver 6.2):
  - **Mini ascensores** (por defecto 4): cada uno lleva a **un grupo de 2 a 6 jugadores** a su **propia partida**, cuando ese grupo quiere. Es el **modo libre**: para jugar cuando se quiera y rejugar enseguida.
  - **Mega ascensor**: el del **día del evento**. Baja a **todo el lobby a la vez**, repartido en **grupos de 4 o 5** (ver 6.3). Cuando está el modo evento activo, los mini ascensores se bloquean.
- Al bajar, **la pantalla se pone en negro** (ver 6.2). **Siempre juegan en grupo** (nadie juega solo). Cada grupo juega **su propia copia del mismo mapa**, y la **dificultad se ajusta al tamaño del grupo** (ver 6.3).
- El mapa tiene un **pasillo inicial** fijo, **zonas intermedias que cambian entre partidas** y una **sala final de criogenización y experimentos** con la batalla final. Cada zona acaba en un **ascensor** a la siguiente (ver 6.5).
- Si **nadie del grupo** llega al ascensor, **game over** para el grupo y pasan a **espectar a otros grupos** (ver 6.7). Si **uno** llega, los demás **reviven en el ascensor** y se curan al 100 %, pero **pierden las curaciones y los objetos que lleven** (ver 6.5).
- Dentro de la partida:
  - Sin inventario normal: **4 huecos**.
  - HUD propio: **solo una barra de vida**. No hay hambre ni saturación (nunca se pierden).
  - Sin tecla **E**, sin **F3**, sin ataques, sin romper/colocar.
  - **Iluminación dinámica real** con la linterna (cono de luz) gracias a **Veil**, librería obligatoria (ver 2 y 21.1).
  - **Acústica realista** (reverberación y oclusión por paredes y puertas) con **Sound Physics Remastered**, incluido en el modpack del evento (ver 7.5).
  - **Todos los mods quedan inhabilitados salvo el propio mod (y sus librerías), Simple Voice Chat y Sound Physics Remastered.**
  - Mecánicas de **sigilo y movimiento**: correr, agacharse, *dive*, saltar obstáculos, mirar atrás.
  - **Escondites** de varios tipos, **fusibles, llaves, tarjetas y puertas** con circuitos.
  - **Animaciones propias** del jugador (esconderse, correr, saltar, agarrado...).
  - **Eventos de laboratorio y jumpscares** que se disparan al encender, retirar o mover fusibles y con otras acciones (14.7).
  - **Estado derribado** con reanimación entre jugadores, remates y muertes instantáneas, todos con animación (9.3).
  - **Consumibles** que se usan con el click derecho (10.1), **marcadores** con la tecla **R** (8.4) y **walkies** para hablar con el equipo (17.3).
  - **Conductos de ventilación** que conectan zonas internas de cada zona (12.9).
- Los mobs, si te atrapan, activan un **minijuego de escape (QTE)**; según el mob, te **derriban**, te **rematan** o te **matan al instante** (ver 9.3 y 18.1).
- **Es rejugable:** un grupo eliminado (o que ha terminado) vuelve al lobby y puede entrar **enseguida** en otro mini ascensor libre, sin esperar a nadie. Cada partida tiene **otra secuencia de zonas intermedias** (ver 6.5 y 6.8).
- **Hay varias partidas a la vez** (`Run`): una por mini ascensor en marcha, o una sola partida con todos los grupos en el mega (ver 6.1).

**Regla de oro:** las restricciones y mecánicas de terror se aplican **solo mientras el jugador está en una partida** (`inSP`). En el **lobby** (overworld) rige el juego normal con las protecciones de la sección 4.2.

---

## 2. Dependencias y compatibilidad

| Necesidad | Herramienta | Notas |
|---|---|---|
| Base | Minecraft 1.21.1, **Fabric** (Loader 0.16.14 + Fabric API) y **NeoForge** 21.1, Java 21, mappings oficiales. Proyecto multiloader: `common` (código compartido), `fabric` y `neoforge` | La versión Forge 1.20.1 está en la rama `forge-1.20.1` |
| Modelos 3D y animaciones de mobs/bloques/items | **GeckoLib 4** (4.7.2 para 1.21.1, Fabric y NeoForge) | Dependencia obligatoria |
| **Iluminación dinámica** (linterna con cono de luz, luces de circuitos) | **Veil** (FoundryMC, build Forge 1.20.1) | **Dependencia obligatoria.** Sin ella el mod no carga. Existe build Forge 1.20.1 (por ejemplo `Veil-forge-1.20.1-1.0.0.9`); **verificar la versión exacta, las coordenadas Maven y si debe estar en cliente y servidor** antes de fijarla en `build.gradle` y `mods.toml` (`mandatory=true`) |
| Animaciones del **jugador** | **PlayerAnimator** (KosmX, build Forge 1.20.1) | Recomendado. Las animaciones se crean en Blockbench (plugin Emotecraft) y se exportan en JSON. Alternativa: renderer propio (ver 13.4). **Verificar disponibilidad** |
| Voz de proximidad | **Simple Voice Chat** (mod id `voicechat`) | Dependencia **opcional** (soft dependency). Mod externo permitido en el evento (junto con Sound Physics Remastered) |
| Acústica del sonido | **Sound Physics Remastered** (henkelmax, build Forge 1.20.1) | Mod **permitido e incluido en el modpack del evento**. Efecto de audio de cliente (en servidor es opcional). El mod id y la versión exacta, **verificar**. No es una librería de código: el mod propio no depende de sus clases (ver 7.5) |
| Red | `CustomPacketPayload` (1.21.1), registrado por cada loader | |
| Shaders | `PostChain` de vanilla (JSON + GLSL) | Incompatible con shaders de terceros, que quedan prohibidos |
| Mixins | Los incluidos con Forge | Solo si hace falta |
| Mapa | WorldEdit + bloques de estructura (NBT) | Fase posterior |
| Sonido | `.ogg` + `SoundEvent` | |

Librerías como GeckoLib, **Veil** y PlayerAnimator se empaquetan como dependencias del propio mod (jar-in-jar) o se instalan junto a él. **No cuentan como "mods de terceros"** a efectos de la sección 7.

---

## 3. Estructura de paquetes sugerida

```
com.smileprotocol
├── SmileProtocol.java
├── registry/            // ModBlocks, ModItems, ModEntities, ModSounds, ModCreativeTabs, ModCapabilities
├── dimension/           // SPDimension, SPTeleporter
├── block/               // LobbyElevatorBlock, ElevatorBlock, FuseBoxBlock, LockedDoorBlock, ValveBlock, HidingSpotBlock...
├── blockentity/
├── item/                // FuseItem, KeyItem, KeycardItem, FlashlightItem...
├── command/             // SPCommands (juego) y SPAdminCommands (herramientas de construcción)
├── session/             // SPSessionManager, CinematicManager, ResultsManager
├── run/                 // RunManager, Run, RunType (MINI | MEGA), RunState, LobbyMode (FREE | EVENT), DifficultyScaler
├── lobby/               // LobbyManager, LobbyElevatorController (un controlador por ascensor: mini o mega), ElevatorBoard (pantalla de estado)
├── group/               // GroupManager (algoritmo de grupos), GroupState, SpectatorManager
├── instance/            // InstanceManager, InstanceContext, ZonePlacer, ZoneTemplate, ZoneSequence
├── player/
│   ├── SPPlayerData.java        // capability
│   ├── SPInventory.java         // 4 huecos
│   ├── PlayerActionState.java    // enum de estados (correr, dive, vault, hide...)
│   └── movement/                 // DiveHandler, VaultHandler, LookBackHandler, LeanHandler, ThrowHandler, NoiseEmitter
├── hiding/              // HidingSpotType, HidingController
├── circuit/             // CircuitManager (SavedData), Lock, Circuit
├── interaction/         // Interactable, HoldInteraction
├── noise/               // NoiseSystem, NoiseEvent
├── compat/
│   ├── ModIsolation.java        // lista blanca de mods
│   └── voicechat/               // plugin de Simple Voice Chat
├── event/
│   ├── CommonEvents.java
│   └── client/ ClientEvents, HudOverlay, KeyBindings, CameraEffects
├── entity/              // SPMonster, SmilerBase (rasgos de Smiley, 20.1) y cada mob (Berserker, Smiley...)
├── qte/
├── mission/
├── network/
├── client/              // ClientState, shaders, renderers, animaciones
│   └── light/           // FlashlightLightManager (luces dinámicas con Veil)
└── mixin/
```

Recursos:
```
assets/smileprotocol/  geo/ animations/ player_animations/ textures/ sounds/ lang/ models/ blockstates/ shaders/post/
data/smileprotocol/    dimension/ dimension_type/ structures/ zone_pool/ lab_events/ consumables/ mobs/ loot_tables/ missions/ hiding_spots/ tags/
```

---

## 4. Mundo: lobby e instancias

El evento se juega en un **servidor dedicado aparte**, con un **modpack cerrado** (ver 7). El mundo tiene dos partes:

- **El lobby**, en el **overworld** del servidor: una recepción de laboratorio y hospital (ver 5.2). Aquí el juego es normal salvo las protecciones del lobby.
- **Las instancias**, en una **dimensión propia** del mod (`smileprotocol:smile_protocol`): cada grupo juega su propia copia del mapa (ver 6.3).

**Convención de este documento:** «dentro de la dimensión» o «dentro de la partida» significa que el jugador está **en una instancia** (`inSP = true`). Todas las restricciones y mecánicas de terror (4 huecos, HUD propio, sigilo, mobs...) rigen **solo** ahí.

### 4.1 Dimensión de instancias

Definida por datapack dentro del mod.

- **`dimension_type`**: sin ciclo día/noche, luz ambiente baja, sin cielo, sin clima, sin camas ni respawn anchors.
- **`dimension`**: generador de **vacío**. Las zonas se colocan con estructuras NBT (6.3).
- Sin spawn de mobs vanilla ni de otros mods. `doMobGriefing` desactivado.
- La **regeneración natural** no se controla con gamerules por dimensión (son globales): se cancela por código (ver 9.2).

```java
public static final ResourceKey<Level> SP_DIM = ResourceKey.create(
    Registries.DIMENSION, new ResourceLocation("smileprotocol", "smile_protocol"));
```

### 4.2 Protecciones del lobby

El lobby está definido por una **región** (`BoundingBox`, configurable con `/spadmin`). Dentro de ella, para jugadores que **no** estén en creativo:

- No se puede romper, colocar ni interactuar con bloques que no sean del mod. No hay PvP ni daño, ni spawn de mobs, ni hambre.
- Modo de juego **aventura** al entrar (`lobby_gamemode`, configurable).
- Los administradores en creativo pueden construir y no cuentan como jugadores listos (6.2).

---

## 5. Lobby, comandos y sesión

### 5.1 Comandos

**Para jugadores:**

| Comando | Función |
|---|---|
| `/sp lobby` | **Lleva al jugador al lobby** (overworld). Disponible para todos (nivel 0, configurable). No funciona dentro de una partida, con un cooldown corto. Los eliminados o los que han terminado sí pueden usarlo |

**Para administradores (nivel 2):**

| Comando | Función |
|---|---|
| `/smileprotocol lobby [jugadores]` | Lleva a otros jugadores al lobby |
| `/smileprotocol leave [jugadores]` | Saca a los jugadores de la partida, restaura su estado y los lleva al lobby |
| `/smileprotocol mode <free\|event>` | **Interruptor de administrador** entre **modo libre** (mini ascensores activos, mega cerrado) y **modo evento** (solo el mega; los mini se bloquean). Ver 6.2 |
| `/smileprotocol start <ascensor> [force]` | Fuerza el descenso de un ascensor (`mega` o `mini1`, `mini2`...) con los jugadores que estén dentro |
| `/smileprotocol runs [list\|end <run>]` | Lista las partidas en curso o termina una |
| `/smileprotocol newrun [semilla]` | Prepara una **partida nueva del mega ascensor** con otra secuencia de zonas (con la semilla indicada o aleatoria) y lo reabre. Normalmente ocurre sola al acabar el evento (ver 6.8). Los mini ascensores generan su secuencia solos en cada bajada |
| `/smileprotocol groups <list\|set\|move>` | Ver o editar los grupos antes del descenso (6.3) |
| `/smileprotocol skipcinematic [grupo]` | Salta la cinemática de llegada de un grupo |
| `/smileprotocol setlobby` | Cambia el punto de aparición del lobby |
| `/smileprotocol zone <grupo> <id>` | Lleva a un grupo a otra zona de su secuencia |
| `/smileprotocol mission <start\|complete\|reset> ...` | Gestión de misiones (de la instancia del jugador indicado) |
| `/smileprotocol spawn <mob> [pos]` | Spawnea un mob (en la instancia donde esté el comando) |

Herramientas de construcción (mirando un bloque), ver secciones 14 y 15:

| Comando | Función |
|---|---|
| `/spadmin lobby region <pos1> <pos2>` | Define la región protegida del lobby |
| `/spadmin lobby elevator <mega\|mini> <id> <pos1> <pos2>` | Define la cabina de un ascensor del lobby (mega o mini) donde se cuentan los listos |
| `/spadmin lock set <lock_id> [consume]` | Asigna cerradura a una puerta |
| `/spadmin circuit set <circuit_id> <fusibles>` | Define circuito y fusibles necesarios |
| `/spadmin hide set <tipo>` | Convierte el bloque en un escondite |
| `/spadmin noise test <radio>` | Visualiza un ruido de prueba |

### 5.2 El lobby (recepción de laboratorio y hospital)

Todos los jugadores aparecen aquí al conectarse (el **spawn del overworld** se fija en el lobby) o al usar `/sp lobby`. Es el sitio donde esperan al resto antes de bajar.

**Ambientación (con muchos detalles):**
- Una **recepción** de laboratorio y hospital: mostrador con ordenador y papeles, carteles de señalización, sala de espera con sillas, máquinas expendedoras, televisores apagados o con estática, tablones con avisos, taquillas, plantas secas, camillas y sillas de ruedas aparcadas, un cartel de «suelo mojado».
- Iluminación normal pero ligeramente **inquietante** (algún fluorescente que parpadea, un hilo musical lejano). Todavía no hay terror activo: es la calma antes de bajar.
- Los detalles son decoración y **no interactúan** (salvo lo que se decida más adelante). El lobby se construye en la **fase del mapa** (22).
- **Voz de proximidad** activa (Simple Voice Chat) para que los jugadores se coordinen y formen equipo.

**Los ascensores del fondo:**
- **Mega ascensor:** en el **centro del fondo**, un ascensor grande de servicio con puertas animadas (GeckoLib), un panel con un botón y una **pantalla** con el contador de listos y su estado (6.2). Es el del día del evento.
- **Mini ascensores:** junto al mega, **4 por defecto** (`max_mini_runs`), más pequeños (caben de 2 a 6 jugadores), con puertas animadas y **un botón de «listo» dentro de la cabina** para cada jugador.
- Cada ascensor tiene una **pantalla de estado** (`ElevatorBoard`) con su estado: **«Libre»**, **«2/4 listos»**, **«En curso, zona 2»** o **«Cerrado»** (modo evento o sin hueco). Si todos están ocupados, las pantallas lo indican.
- La cabina de cada ascensor es una **región** (`/spadmin lobby elevator`) donde se cuentan los jugadores.

**Protecciones:** las de 4.2.

### 5.3 Sesión (bajar y salir)

`SPSessionManager` guarda y restaura el estado del jugador al entrar y salir de una partida.

**Al bajar** (descenso del lobby, 6.3):
1. Guardar en la capability `SPPlayerData`: inventario completo (armadura y offhand), modo de juego, posición y dimensión de origen, vida y hambre.
2. Vaciar el inventario real.
3. Modo aventura como red de seguridad.
4. `inSP = true` y el grupo asignado, guardados en NBT persistente.
5. Fijar hambre y saturación (ver 9.2).
6. Teletransportar a la instancia de su grupo y sincronizar con el cliente.

**Al terminar o salir** (final, eliminación con salida al lobby, `leave`; en un mini ascensor, al acabar su partida): restaurar todo (inventario, modo, posición, hambre, vida) y avisar al cliente. La restauración del inventario se puede desactivar (`restore_inventory`) si el servidor es solo para el evento.

**Casos límite (obligatorio):**
- `PlayerLoggedOutEvent`: el estado queda en NBT, no se pierde nada.
- `PlayerLoggedInEvent`: reparar incoherencias (`inSP` sin estar en una instancia, o al revés) y **reincorporar** al jugador a su grupo si la partida sigue (6.3).
- `PlayerChangedDimensionEvent`: si sale por cualquier vía, restaurar.
- `PlayerEvent.Clone` y `PlayerRespawnEvent`: conservar la capability.
- Crash del servidor: el inventario original **nunca se pierde** y el progreso de cada grupo se recupera.

**Muerte:** el inventario original nunca se dropea. Qué pasa al morir se describe en 9.3 y 6.7.

---

## 6. Flujo de partida

### 6.1 Estados y partidas (`Run`)

Hay **varias partidas a la vez**. Cada partida es un `Run`, con su **propio estado y su propia secuencia de zonas**, y tiene **uno o varios grupos**:

| Tipo de `Run` | Origen | Grupos | Secuencia de zonas |
|---|---|---|---|
| **`MINI`** | Un mini ascensor | **Uno** (2 a 6 jugadores) | Propia, con semilla nueva en cada bajada |
| **`MEGA`** | El mega ascensor | **Todos los del evento** (grupos de 4 o 5) | Una sola, igual para todos los grupos (6.5) |

Estados:

- **Modo del lobby** (`LobbyMode`, global, en `SavedData`): `FREE` o `EVENT` (ver 6.2).
- **Partida** (`RunState`, por `Run`, en `SavedData`, sincronizado al cliente): `WAITING → DESCENT → RUNNING → ENDED`. `WAITING` es el estado del ascensor antes de bajar.
- **Grupo** (`GroupState`): `INTRO → ZONE_n → FINAL → COMPLETED` o `ELIMINATED`.
- Dentro de cada zona, el estado del ascensor de zona (`ElevatorState`, 6.5).

Todos los grupos de una partida juegan **a la vez** y **de forma independiente**, y las partidas **no se afectan entre sí**: ningún sistema usa estado global de partida (6.3). `RunManager` crea, vigila y cierra los `Run`.

### 6.2 Lobby y ascensores de bajada

Hay dos tipos de ascensor y un interruptor de administrador (`/smileprotocol mode`) que decide cuáles funcionan.

**Modos del lobby (`LobbyMode`):**

| Modo | Mini ascensores | Mega ascensor |
|---|---|---|
| **Libre** (`FREE`, por defecto) | Activos | **Cerrado** |
| **Evento** (`EVENT`) | **Bloqueados** («Cerrado») | Activo |

- Al pasar a modo evento, las partidas **mini en curso terminan con normalidad** (no se expulsa a nadie), pero **no se admiten nuevas**. El mega espera a que haya hueco suficiente (ver `max_groups` en 6.3) y su pantalla lo indica.
- Los administradores pueden probar cualquier ascensor con `/smileprotocol start <ascensor> force`.

#### Mini ascensores (modo libre)

1. Un grupo de amigos entra en la cabina de un mini ascensor **libre** (supervivencia o aventura). La cabina admite de **2 a 6 jugadores** (`mini_max_players`); con la cabina llena, la puerta no deja entrar a más.
2. **El contador solo cuenta a los de dentro de la cabina.** Cada jugador pulsa el **botón de «listo»** (interacción simple). La pantalla muestra **«X/Y listos»**, donde **Y** = jugadores dentro de la cabina y **X** = los que han pulsado. Quien sale de la cabina deja de contar y pierde su «listo».
3. **Mínimo 2 jugadores** (`mini_min_players`). Con menos, la pantalla muestra «Se necesitan al menos 2 jugadores».
4. Cuando **X = Y** (y Y ≥ 2) empieza una **cuenta atrás corta** (`descent_countdown`, 5 s). Si alguien sale o cambia su estado, se cancela.
5. **Las puertas se bloquean al empezar** y la pantalla se pone en negro (con fundido). Nadie más puede entrar y el ascensor pasa a **«En curso, zona N»**.
6. **Un jugador AFK dentro de la cabina** (sin pulsar «listo») no bloquea para siempre: pasados `mini_ready_timeout` segundos (30 s por defecto) desde que **todos los demás** están listos, se le **expulsa** de la cabina y se sigue. **Un AFK fuera de la cabina no afecta a nadie.**
7. Los jugadores de dentro forman **un único grupo** (6.3) y se crea un `Run` de tipo `MINI`.
8. Cuando la partida acaba (`ENDED`), el ascensor vuelve a **«Libre»** tras limpiar su instancia, y el grupo está de vuelta en el lobby (6.8).

#### Mega ascensor (modo evento)

1. Los jugadores esperan en el lobby (5.2). **No hay límite de tiempo** salvo el que decida el administrador.
2. Al meterse en la **cabina del mega ascensor** (en modo **supervivencia o aventura**), el jugador **cuenta como listo**. No hay botón individual.
3. A los jugadores de dentro se les muestra **«X/Y jugadores listos»** (HUD y pantalla del ascensor).
   - **Y** = jugadores conectados en **supervivencia o aventura** que pueden jugar, **en todo el lobby**. **Creativo y espectador** no cuentan.
   - **X** = los de Y que están dentro de la cabina.
4. **Mínimo 4 jugadores** (`min_group_size`) para bajar. Con menos, la pantalla muestra «Se necesitan al menos 4 jugadores» y el ascensor no arranca (el administrador puede forzarlo con `start mega force`). **La regla de «siempre en grupo, mínimo 4» sigue valiendo en el evento**, porque su objetivo era evitar multitudes.
5. Cuando **X = Y** (y Y ≥ 4) se inicia una **cuenta atrás corta** (`descent_countdown`, por defecto 5 s) y **se pulsa el botón** para bajar, con la animación del botón, las puertas cerrándose y el sonido. Si alguien sale de la cabina, la cuenta atrás se cancela.
6. **Entonces la pantalla se pone en negro** (con fundido) y empieza el descenso (6.3).
7. **Casos límite:** desconexiones y cambios de modo mientras se espera (se recalcula Y), jugadores AFK que bloquean el inicio (en el mega sigue siendo posible: ver 27), `start force` y un umbral de listos configurable (`required_ready_ratio`, por defecto 1,0).

#### Comprobaciones comunes

- El **handshake de mods** (7.1) se aplica igual: un jugador con mods no permitidos no cuenta como listo en ningún ascensor y se le muestra el motivo.
- Si ya se ha alcanzado `max_mini_runs` o `max_groups` (6.3), los mini ascensores muestran **«Cerrado»** hasta que se libere un hueco.

### 6.3 Descenso, grupos e instancias

**1. Formación de grupos.** Mientras la pantalla está en negro, el servidor forma los **grupos** (`GroupManager`).

**Mini ascensor:** el grupo es **exactamente quien está dentro de la cabina** (2 a 6 jugadores). No hay reparto ni algoritmo: un mini ascensor = un `Run` = un grupo.

**Mega ascensor:** se reparte a **todos los jugadores listos** con las reglas siguientes.

Reglas del mega:
- **Siempre hay grupo:** ningún jugador juega solo.
- **Mínimo 4 por grupo** (`min_group_size`).
- Se reparte lo más equitativamente posible: el número de grupos es `⌊N / 4⌋` y los jugadores sobrantes se **suman a grupos ya hechos, de uno en uno**. Así, si sobran jugadores que no llegan a formar otro grupo de 4 (por ejemplo, un grupo de 3), se hacen **grupos de 5** con esas personas.

```java
List<Integer> groupSizes(int n, int min /* 4 */) {
    if (n < min) return List.of();           // no se puede empezar
    int g = n / min;                         // número de grupos
    int base = n / g, extra = n % g;         // 'extra' grupos tienen base + 1
    // resultado: 'extra' grupos de (base + 1) y (g - extra) grupos de base
}
```

| Jugadores | Grupos resultantes |
|---|---|
| 4 | 4 |
| 7 | 7 |
| 8 | 4 + 4 |
| 9 | 5 + 4 |
| 10 | 5 + 5 |
| 11 | 6 + 5 |
| 12 | 4 + 4 + 4 |
| 15 | 5 + 5 + 5 |
| 19 | 5 + 5 + 5 + 4 |
| 23 | 5 + 5 + 5 + 4 + 4 |

Con **12 jugadores o más**, los grupos son siempre de **4 o 5**. Con menos puede haber un grupo de 6 o 7, porque **nunca se permite un grupo de menos de 4**.

- **Asignación:** aleatoria por defecto (`group_mode: RANDOM`). El administrador puede **fijar los grupos** a mano antes de bajar (`/smileprotocol groups set`).
- Los grupos y sus miembros quedan **guardados** (`SavedData`) para poder reincorporar a quien se desconecte.

**2. Instancias.** Cada grupo juega **su propia copia del mapa**, sin ver a los demás.

- `InstanceManager` asigna a cada grupo un **hueco** en la dimensión (4.1), con un origen separado por `instance_spacing` bloques (por defecto, miles de bloques).
- Las **zonas** se colocan desde **plantillas NBT** (`StructureTemplate`) en ese hueco. Solo hay **como máximo dos zonas cargadas por grupo**: la actual y la siguiente, que se **precarga mientras se juega** para que el ascensor no espere. Al sellar una zona, se **limpia** (se vacía, se retiran las entidades y se liberan los tickets de chunks) para limitar la memoria.
- **Todo el estado de juego es por instancia** (`InstanceContext`): circuitos, cerraduras, misiones, eventos (14.7), escondites, mobs, ruido, marcadores, luces dinámicas y el canal del walkie. **Ningún sistema usa estado global de partida**; cada consulta recibe el `InstanceContext` del jugador o de la posición.
- Los grupos **no se ven ni se oyen entre sí**: están a miles de bloques y los canales de voz y de walkie son por grupo.
- Se limita la carga con **dos topes**: `max_mini_runs` (por defecto 4, partidas mini a la vez) y `max_groups` (grupos simultáneos en total, contando los del mega). Cada partida carga su propia instancia, así que **cada grupo cuenta**. Si el mega necesita más huecos de los libres, espera a que haya sitio y su pantalla lo indica.
- **La carga con varias partidas a la vez es una prueba pendiente** (ver 25 y 27): varios grupos cargando zonas a la vez pesan más que uno solo, sobre todo con las luces dinámicas de Veil.

**3. Dificultad según el tamaño del grupo.** Un grupo de 2 lo tendría mucho más difícil que uno de 5, así que `DifficultyScaler` ajusta la partida **al empezar** según el número de jugadores del grupo (el valor no cambia si luego alguien muere). Es **data-driven** (`data/smileprotocol/difficulty/group_scaling.json`). Valores iniciales, **a ajustar en pruebas** (la base es un grupo de 4):

| Jugadores | Fusibles por circuito | Mobs por zona | Tiempo del QTE |
|---|---|---|---|
| 2 | ×0,5 (mín. 1) | ×0,6 | +40 % |
| 3 | ×0,75 | ×0,8 | +20 % |
| 4 | ×1 (base) | ×1 | ×1 |
| 5 | ×1 | ×1,15 | ×1 |
| 6 | ×1,25 | ×1,3 | ×1 |

Los fusibles y los mobs se escalan al **colocar la zona** (según los puntos de aparición de cada plantilla); el tiempo del QTE, al crear el minijuego (18). En el mega, cada grupo se escala con **su propio tamaño**.

**4. Bajada.** Con las zonas de entrada colocadas y cargadas, se **teletransporta a cada grupo** a la **cabina de la primera zona** (la del pasillo). Dura unos segundos de pantalla negra con sonido de ascensor y, después, **fundido y llegada** (6.4).

### 6.4 Llegada y cinemática inicial (por grupo)

- Cada grupo llega a la **cabina del pasillo inicial**: las puertas se abren y empieza su cinemática de llegada.
- Sincronizada para los miembros del grupo (paquete `PlaySequence` con marca de tiempo del servidor).
- Movimiento bloqueado, HUD oculto, barras negras, cámara con `ViewportEvent.ComputeCameraAngles`.
- Segmentos definidos en JSON (puntos de cámara, tiempos, sonidos, efectos).
- Al terminar: se entrega el equipo inicial (linterna y, con Simple Voice Chat, un walkie por jugador) y el grupo pasa a `INTRO`.

### 6.5 Zonas y ascensores

El recorrido de cada grupo es una **secuencia de zonas**:

```
Pasillo inicial  →  zona intermedia 1  →  ... →  zona intermedia K  →  Sala final
   (fija)            (cambian entre partidas)                      (criogenización y experimentos)
```

**Zonas que cambian.** El pasillo inicial y la sala final son **fijos**. Las **zonas intermedias cambian entre partidas**:
- Se eligen de un **catálogo** (`data/smileprotocol/zone_pool/*.json`), con una **semilla** (aleatoria en cada partida, o fija con `/smileprotocol newrun [semilla]`), sin repetir dentro de la partida, con dificultad creciente y **distinta de la partida anterior** (`avoid_repeat_last`).
- Todos los grupos de una misma partida (`Run`) juegan **la misma secuencia** (es justa y comparable); la **variación por grupo** queda como opción (`per_group_zones`, desactivada). Como cada mini ascensor es su propio `Run`, cada bajada de un mini tiene su propia secuencia.
- Cada plantilla de zona define su **cabina de entrada**, su **ascensor de salida**, los puntos de aparición, los circuitos y los eventos (14.7).
- Número de zonas intermedias: `intermediate_zones` (por defecto, 3).

**El ascensor de salida.** Cada zona acaba en un ascensor (`ElevatorBlock`, GeckoLib) a la siguiente. Se activa con su **circuito** (14.1), normalmente completando los fusibles de la zona. **No se puede volver atrás.**

**Subir (cuando uno llega).**
1. En cuanto **un jugador vivo entra en la cabina** (incluso derribado, arrastrándose), empieza una **cuenta atrás de gracia** (`elevator_grace`, por defecto 15 s) visible para todo el grupo, para que los demás vivos alcancen el ascensor.
2. El ascensor parte cuando **todos los jugadores vivos** están dentro o se acaba la cuenta atrás.
3. **Los demás reviven en el ascensor:** al cerrarse las puertas, **todos los miembros del grupo que estén muertos, derribados o fuera de la cabina** (incluidos los espectadores del propio grupo) **aparecen dentro de la cabina**.
4. **Todos se curan al 100 %.**
5. **Penalización:** a los jugadores que estaban **heridos, derribados, muertos o fuera de la cabina** se les **retiran las curaciones y los objetos que lleven** (`elevator_strip`, por defecto `HURT_OR_REVIVED`). Se conserva el **equipo base** (linterna y walkie). Los jugadores que llegan **sanos y dentro** conservan lo suyo. Alternativas configurables: `ALL` (a todos) o `REVIVED_ONLY`.
6. **Viaje:** la cabina se cierra, dura unos segundos (`elevator_ride_time`) con luces parpadeantes, sonidos y, si se define, un evento (14.7). Los mobs que no estén dentro se quedan atrás y no pueden entrar.
7. **Cómo se hace:** el servidor teletransporta a los jugadores a la **cabina idéntica** de la zona siguiente, con una breve pantalla negra y temblor de cámara. Las dos cabinas tienen la misma construcción para que no se note el salto.

**Al llegar a la zona nueva:**
- La zona anterior se **sella y se limpia** (6.3).
- Se guarda el **progreso del grupo** (`SavedData`, para reconexiones y reinicios del servidor; **no** para reintentos).
- Se reponen los items de misión que hagan falta y se siguen las reglas de 6.3.
- Es un momento de **respiro**, pero ojo: lo que no llevabas no vuelve.

### 6.6 Fase final

- Tras el último ascensor (con las mismas reglas de 6.5), el grupo llega a la **sala de criogenización y experimentos**, donde se libra la **batalla final**. Estado `FINAL`.
- El jefe es **Smiley** (20.1). Los jugadores **no atacan**, así que la batalla es de **objetivos y supervivencia**. Propuesta: completar varios objetivos a la vez (restablecer la energía, abrir las cápsulas, ejecutar el protocolo de purga) mientras el jefe persigue al grupo. El diseño concreto queda **pendiente** (ver 27).
- Si el grupo **supera la batalla**, se lanza la **cinemática final** y el grupo pasa a `COMPLETED`: se les muestra su resultado y quedan a salvo (no pueden volver a la partida).
- Si **todos mueren**, el grupo queda `ELIMINATED` (6.7).

### 6.7 Game over y espectadores

**Los miembros del grupo que mueren** no vuelven hasta el siguiente ascensor (6.5): pasan a **espectar a sus compañeros vivos** (cámara restringida a su grupo, `SPECTATE_TEAMMATES`).

**Si todos los jugadores del grupo están muertos**, antes de que ninguno llegue al ascensor, es **game over para ese grupo**:
- Pantalla de **GAME OVER** con estática y sonido, y el grupo queda `ELIMINATED`.
- No hay reintento ni vuelta al checkpoint.
- Los jugadores eliminados pasan a **espectar a otros grupos** que sigan en juego (`SpectatorManager`), **de cualquier partida en curso** (mini o mega, no solo la propia):
  - Pueden **cambiar de objetivo** entre los jugadores vivos de cualquier grupo activo. El servidor restringe el modo espectador a los grupos activos y fuera de los eliminados. Se puede limitar con `spectate_scope` (`ALL_RUNS` por defecto, `OWN_RUN`).
  - **No interactúan** con nada ni pueden hablar con quienes juegan: los espectadores van a un **grupo de voz aparte** (Simple Voice Chat), para que no pasen información por voz ni por walkie. Si hablan entre ellos es entre espectadores.
- Pueden **salir al lobby** en cualquier momento con `/sp lobby`, o con el **botón «Volver al lobby»** de la pantalla de GAME OVER, sin posibilidad de volver a esa partida. Si no hay otra partida que ver (por ejemplo, el grupo era el único en un mini ascensor), vuelven al lobby tras unos segundos (`mini_return_delay`, 10 s).
- **En el lobby pueden entrar enseguida en un mini ascensor libre** y empezar otra partida, sin esperar a que se llene otro grupo.
- Al acabar el evento ven la **pantalla de resultados** (6.8).

**Desconexiones:** un jugador desconectado no cuenta como muerto: se le espera un tiempo (`disconnect_grace`, por defecto 120 s) antes de contar a su grupo como eliminado. Si se reconecta, vuelve al grupo (5.3).

### 6.8 Fin de partida y rejugabilidad

**Fin de una partida** (`Run`): cuando todos sus grupos están `COMPLETED` o `ELIMINATED`, pasa a `ENDED`. Se muestra una **pantalla de resultados**: zona alcanzada, tiempo y supervivientes por grupo. Después se devuelve a los jugadores al lobby (restaurando su estado, 5.3).

**Mini ascensor:**
- Al pasar a `ENDED`, tras los resultados y `mini_return_delay` (10 s), el grupo vuelve al lobby, la instancia se **limpia** y el ascensor queda **«Libre»**.
- **No hay espera ni reinicio global:** el grupo (o cualquier otro) puede entrar en un mini ascensor libre y volver a bajar cuando quiera. Cada bajada **guarda y vacía** el inventario del jugador (5.3) y crea una **secuencia de zonas nueva** (6.5, `avoid_repeat_last` respecto a la anterior de ese ascensor).
- Los grupos **no se mantienen** entre partidas: cada grupo es quien entra en la cabina.

**Mega ascensor (día del evento):**
- Al acabar el evento se muestra el resultado y se devuelve a todos al lobby.
- **Es rejugable** (`replayable = true`, por defecto): los jugadores pueden volver a bajar tantas veces como quieran (`max_plays_per_player`, 0 = ilimitado). Lo que cambia entre partidas son las **zonas intermedias**.
  - Tras la pantalla de resultados y un breve plazo (`lobby_reset_delay`, por defecto 30 s), el servidor **prepara una partida nueva del mega** sola (`auto_newrun`): limpia las instancias, elige **otra secuencia de zonas** con una semilla nueva (distinta de la anterior, 6.5) y **reabre el mega ascensor**.
  - Un administrador puede forzarlo con `/smileprotocol newrun [semilla]`, o desactivar `auto_newrun` para controlar cuándo empieza la siguiente.
- **Mientras hay una partida del mega en marcha**, el mega está **ocupado**: los jugadores que llegan, los eliminados y los que han terminado esperan en el lobby y forman el **siguiente grupo de espera**. En cuanto se reabre, el contador «X/Y listos» cuenta a todos ellos (6.2).

**Jugadores que se conectan con partidas en marcha:**
- **Modo libre:** pueden entrar en un mini ascensor libre. El resto de partidas no les afecta.
- **Modo evento:** no pueden bajar con el mega en marcha. Se quedan en el lobby (o pueden espectar, 6.7) hasta la siguiente partida del mega.

---

## 7. Aislamiento de otros mods (solo Simple Voice Chat y Sound Physics Remastered permitidos)

**Objetivo:** en la dimensión del evento, ningún mod de terceros puede alterar la experiencia, salvo Simple Voice Chat y Sound Physics Remastered.

### 7.1 Qué es posible y qué no (leer antes de implementar)

Forge **no permite desactivar un mod por dimensión**. Lo que sí se puede es **neutralizar sus efectos visibles y jugables** mientras `inSP` sea true, con una **lista blanca**. Además, **los mods que solo existen en el cliente** (minimapas, tooltips, shaders...) no pueden controlarse al 100 % desde el servidor. Por eso el sistema tiene tres capas:

1. **Capa de distribución (la más fiable):** el evento se juega con un **modpack cerrado** que solo incluye el mod, sus librerías, Simple Voice Chat y Sound Physics Remastered.
2. **Capa de verificación al conectarse:** un paquete de handshake propio en el que el cliente informa de sus mods cargados (`ModList.get().getMods()`). Si hay alguno fuera de la lista blanca, **no cuenta como listo ni puede bajar en el ascensor** y se muestra el motivo. El mismo handshake exige que estén presentes los mods **obligatorios del evento** (Veil y Sound Physics Remastered); si falta alguno, tampoco puede bajar y se indica cuál. *Un cliente modificado puede mentir; esto cubre el caso normal.*
3. **Capa de neutralización en juego:** bloqueos por código, descritos abajo.

### 7.2 Lista blanca (configurable)

```toml
allowedMods = ["minecraft", "forge", "smileprotocol", "geckolib", "veil", "playeranimator", "voicechat", "sound_physics_remastered"]
```

Las librerías que añada el mod se incluyen aquí. Mods de rendimiento (por ejemplo Embeddium) quedan **fuera por defecto**; ver sección 24.

### 7.3 Neutralización en juego

| Qué | Cómo |
|---|---|
| **Overlays de HUD de otros mods** | En `RenderGuiOverlayEvent.Pre`, cancelar si `overlay.id().getNamespace()` no está en la lista blanca (vanilla solo permitido para los que usamos) |
| **Pantallas (GUI) de otros mods** | En `ScreenEvent.Opening`, cancelar si el paquete de la clase no pertenece a vanilla, a nuestro mod o a `de.maxhenkel.voicechat` |
| **Teclas de otros mods** | Cada tick, para cada `KeyMapping` cuya categoría no esté permitida: `setDown(false)` y consumir sus clicks. *No cubre mods que leen el teclado en bruto con `InputEvent.Key`* |
| **Entidades de otros mods** | `EntityJoinLevelEvent`: cancelar en la dimensión si el namespace del tipo no es el nuestro (los jugadores se exceptúan) |
| **Items y bloques de otros mods** | El jugador solo recibe nuestros items. Se cancela `PlayerInteractEvent` con items/bloques de otros namespaces |
| **Comandos de otros mods** | `CommandEvent`: bloquear comandos fuera de una lista permitida para jugadores sin permisos (los admins no se ven afectados) |
| **Minimapas, tooltips (Jade/WTHIT), JEI** | JEI depende de la pantalla de inventario, que ya está bloqueada. Para minimapas y tooltips, la capa 1 y 2 es la protección real; **además** algunos minimapas respetan configuraciones de servidor por dimensión (**investigar caso por caso** si se quieren admitir) |
| **Shaders de terceros** | Prohibidos (Iris, Oculus...): incompatibles con el `PostChain` del mod y con el renderizado de Veil |

### 7.4 Simple Voice Chat (permitido)

- Dependencia **opcional**. Si está instalado, funciona dentro de la dimensión sin restricciones.
- Se usa su **API de plugin** (`de.maxhenkel.voicechat.api`, punto de entrada `VoicechatPlugin`). **Verificar la versión de API.**
- **Integración con el sigilo** (ver sección 17): la voz de los jugadores genera ruido que los mobs pueden oír.
- Opciones adicionales: grupo de espectadores para los jugadores muertos, voz amortiguada durante un agarre (QTE) y radios en el mapa (futuro).

### 7.5 Sound Physics Remastered (permitido y requerido en el evento)

- Mod de audio que aplica **reverberación, atenuación y oclusión** a los sonidos según la geometría real del mapa (paredes, puertas, habitaciones, conductos). Forge 1.20.1 disponible (`soundphysics-forge-1.20.1`); **verificar la versión y el mod id** (en la lista blanca figura como `sound_physics_remastered`).
- **Solo es ambientación:** no sustituye al `NoiseSystem` (sección 17). La detección de los mobs sigue siendo de servidor y no depende de lo que oye el cliente. El `NoiseSystem` ya atenúa por paredes y puertas con su propio cálculo; este mod hace que **lo que oye el jugador sea coherente** con eso.
- **Integración suave:** el mod propio **no referencia sus clases** ni lo necesita para compilar o arrancar. Se incluye en el modpack y el handshake de la sección 7.1 exige que el cliente lo tenga. Si se prefiere obligatorio a nivel de `mods.toml`, se declara como dependencia, pero no es necesario.
- **Configuración del evento:** el modpack distribuye una configuración fija del mod (sin que el jugador la cambie, dentro de lo posible) con la reverberación suficiente para laboratorios, pasillos y conductos. Parámetros concretos: **ajustar en pruebas**.
- Los sonidos propios (`SoundEvent`) deben **ser compatibles**: si algún sonido no debe procesarse (latidos del jugador, música, sonidos de HUD y de QTE), se excluye con la lista de sonidos permitidos del mod. **Verificar cómo se configura.**
- **Con Simple Voice Chat:** comprobar si la voz de proximidad se procesa o no con la acústica del mod y decidir qué se quiere (ver 27).

---

## 8. HUD y controles de la dimensión

### 8.1 HUD

- **Solo una barra de vida personalizada.** Se cancelan todos los overlays vanilla (hotbar, vida, hambre, armadura, aire, experiencia, items sostenidos, objetivo del crosshair, etc.) y los de otros mods (sección 7).
- **Hotbar propia de 4 huecos** (ver sección 10), discreta y con estética de laboratorio.
- Elementos **contextuales** que aparecen solo cuando hacen falta:
  - Punto de mira mínimo y **prompt de interacción** ("Mantén click derecho: girar válvula").
  - Barra de aguante al esconderse (sección 15).
  - Objetivo actual de la misión (sección 19).
  - Minijuego QTE (sección 18).
  - **«X/Y jugadores listos»** dentro del ascensor del lobby (6.2).
  - Trayectoria discreta al apuntar un objeto lanzable (12.7) y barra de uso de un consumible (10.1).
  - Barra de **desangrado** y avisos cuando estás derribado o lo está un compañero (9.3).
  - **Iconos de los marcadores** de los compañeros y rueda de marcadores (8.4).
  - Indicador mínimo del **walkie** (transmitiendo o recibiendo) (17.3).
  - Cuenta atrás del **ascensor** cuando la cabina espera a todos (6.5).
- Efectos de pantalla con la vida baja (viñeta, desaturación, latidos).

```java
@SubscribeEvent
public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
    if (!ClientState.inSmileProtocol()) return;
    if (!HudWhitelist.isAllowed(e.getOverlay())) e.setCanceled(true);
}
```

### 8.2 Bloqueos de interfaz

| Bloqueo | Cómo |
|---|---|
| **Tecla E** | Cancelar `ScreenEvent.Opening` de `InventoryScreen` y `CreativeModeInventoryScreen` |
| **F3** | Cancelar `DEBUG_TEXT` y forzar `renderDebug = false` cada tick (Mixin sobre `KeyboardHandler` si hay que bloquear combinaciones F3+tecla) |
| **F5 (cambio de cámara)** | Forzar primera persona (configurable) |
| **Intercambio de mano, etc.** | Cancelar teclas no usadas |

> **F3 y todo el cliente** son bloqueos de cliente y se pueden saltar con clientes modificados. Información sensible (posición de mobs) **no debe enviarse al cliente** si no es necesaria.

### 8.3 Controles dentro de la dimensión

| Acción | Tecla por defecto |
|---|---|
| Moverse | W A S D |
| Correr | Ctrl (o doble W) |
| Agacharse / sigilo | Shift |
| **Dive** | Correr + Shift |
| Saltar / **saltar obstáculo** | Espacio |
| **Mirar atrás** | C (mantener; configurable a toggle) |
| **Asomarse por una esquina** | **Q** (izquierda) / **E** (derecha), mantener |
| Interactuar (mantener) | Click derecho |
| Linterna | F |
| **Soltar objeto** | **G** |
| **Marcador** | **R** (pulsar: marcador rápido; mantener: rueda de marcadores) |
| **Hablar por el walkie** | **B** (mantener, con el walkie en el hueco activo). *Verificar que no choque con las teclas de Simple Voice Chat.* |
| **Usar objeto en la mano** | Mantener click derecho (sin nada interactuable delante): apuntar un lanzable, consumir un consumible o encender/apagar el walkie (ver 11.1) |
| **Lanzar objeto** | Click izquierdo (solo mientras se apunta) |
| Seleccionar hueco | 1–4 o rueda |
| Click izquierdo | Desactivado, salvo para lanzar mientras se apunta un objeto |

> **Prioridad del click derecho:** si miras algo interactuable (item, puerta, válvula, escondite...), el click derecho mantenido es **interactuar**. Si no hay nada interactuable en el punto de mira, el click derecho mantenido es **usar el objeto que llevas en la mano** (apuntar un lanzable, consumir un consumible o encender/apagar el walkie). Ver 11.1. Q y E ya no sueltan objetos: el soltar va en G.

Las teclas propias se registran como `KeyMapping` normales y solo actúan dentro de la dimensión.

### 8.4 Marcadores (R)

Sistema para coordinar al equipo **sin hablar** (hablar hace ruido).

- **Pulsar R:** coloca un marcador de **atención** en el punto al que miras (hasta unos 64 bloques, configurable).
- **Mantener R:** abre una **rueda de 4 marcadores**: **Peligro**, **Ir allí**, **Necesito ayuda** y **Despejado**.
- **Visibles para los compañeros** como icono con la distancia, incluso a través de paredes, con el color del jugador que lo puso. Desaparecen solos (por defecto 20 s) o al colocar otro del mismo tipo.
- **Límites:** máximo de marcadores simultáneos por jugador (por defecto 3) y un cooldown corto, validados por el servidor (`MarkerManager`).
- **No hacen ruido** y los mobs no los perciben. Sí tienen un sonido discreto para los compañeros.
- **Restricciones:** no se pueden colocar durante un QTE ni en cinemáticas. Derribado, solo se puede usar **Necesito ayuda**, que se coloca automáticamente sobre el jugador.
- **Accesibilidad:** cada tipo se distingue por **forma e icono**, no solo por color (24.2).

---

## 9. Vida y hambre

### 9.1 Vida

- Barra única, estilo propio. Vida máxima configurable (por defecto 20).
- Los mobs **no hacen daño por golpes**: su amenaza es el agarre (QTE), el **derribo**, el **remate** y, en algunos, la **muerte instantánea** (9.3 y 18.1). Hay daños ambientales (vapor, caídas, electricidad) configurables.
- Recuperación: **no regenera sola** (por defecto). Se cura con botiquines (item de 1 hueco) y, al 100 %, en los **ascensores de zona** (6.5).
- Al llegar a 0 de vida o fallar un agarre, el jugador queda **derribado** (9.3) en vez de morir al instante, salvo muerte instantánea.

### 9.2 Sin hambre ni saturación

- Cada tick servidor, si `inSP`: `foodLevel = 20`, `saturation = 20`, `exhaustion = 0`. Así **nunca se pierde** y además se puede correr siempre.
- Cancelar `LivingEntityUseItemEvent` para comida y el evento de cambio de hambre.
- **Cancelar la regeneración natural:** `LivingHealEvent` se cancela si `inSP` y la curación no viene de nuestro sistema (se marca con un flag propio). **No usar el gamerule `naturalRegeneration`**: es global, no por dimensión.
- El HUD no muestra hambre en ningún caso.

### 9.3 Estado derribado

Es el estado intermedio entre estar vivo y morir. Da tiempo al equipo para reaccionar y crea tensión: un compañero puede salvarte, pero un mob puede **rematarte**.

**Cómo se llega:**
- Fallar un QTE de agarre (por defecto; configurable, ver 18.1).
- Que un mob tenga `on_catch: DOWN` (te derriba al alcanzarte).
- Vida a 0 por daño ambiental no letal.
- **No se llega** si el mob (o el evento) tiene muerte instantánea (ver más abajo).

**Mientras estás derribado (`DOWNED`):**
- Cámara baja, viñeta y latidos fuertes. El jugador se **arrastra** muy despacio (`PlayerActionState.CRAWL`).
- Hay un tiempo de **desangrado** (`bleed_out_time`, por defecto 60 s) con barra en el HUD. Al acabar, muere.
- **Puede:** arrastrarse, hablar por el walkie, colocar el marcador **Necesito ayuda**, usar la linterna y usar la jeringa de adrenalina (10.1).
- **No puede:** correr, hacer dive o vault, esconderse, interactuar con mecanismos, soltar objetos ni usar otros consumibles.
- Arrastrarse hace un poco de ruido y puede emitir **quejidos** periódicos (`downed_noise_interval`) que atraen a los mobs.

**Reanimar (compañero):**
- Interacción mantenida y cooperativa (`Interactable`, 11): por defecto **6 s**; con varios compañeros se acorta. Se cancela si el rescatador suelta, se mueve o lo agarran.
- Hace ruido moderado. Animación de **reanimación** para el rescatador y de **levantarse** para la víctima, sincronizadas.
- Al levantarse, la víctima recupera un porcentaje de vida (`revive_health_percent`, por defecto 30 %) y tiene una breve **protección** (por defecto 3 s) contra remates.
- La **jeringa de adrenalina** permite levantarse uno mismo, más despacio (10.1).

**Remates por los mobs:**
- Un mob con la propiedad `execution` que detecte (vista u oído) a un jugador derribado **se dirige a rematarlo**, y puede darle prioridad frente a otros jugadores (`execute_downed_priority`).
- Al alcanzarlo se inicia una **secuencia de remate** (`ExecutionSequence`), con animación propia **por mob**. Al terminar, el jugador muere.
- Mientras el mob va hacia el jugador **sí se puede distraer** al mob (objetos lanzados, ruido) o reanimar al jugador, pero una vez empezada la secuencia **ya no se puede cancelar**.

**Muerte instantánea:**
- Algunos mobs (y trampas o eventos concretos) tienen `on_catch: INSTANT_KILL`: **no hay QTE ni derribo**, el jugador muere al ser alcanzado con una **secuencia de muerte propia** (`KillSequence`) con cámara y animación específicas por mob.
- Cuáles son los mobs con muerte instantánea se decide al definir cada uno (sección 20 y 27).
- Si el dive o cualquier otra mecánica puede evitarlo, es una propiedad del mob (ver 27).

**Animaciones (todas obligatorias):** caer derribado, derribado en reposo, arrastrarse, levantarse (solo o ayudado), reanimar (rescatador), remate (una por mob, con su versión de jugador) y muerte instantánea (una por mob, con su versión de jugador). Ver 13.1.

**Cómo se sincronizan los remates y las muertes:** son **secuencias dirigidas por el servidor**. El servidor ancla al jugador y al mob a posiciones relativas, bloquea el control del jugador (invulnerable durante la secuencia) y avisa a los clientes con `StartSequence`. Cada cliente reproduce la animación del jugador (PlayerAnimator) y la del mob (GeckoLib) de forma sincronizada, con efectos de cámara. El servidor decide el resultado, por lo que el cliente no puede falsearlo.

**Muerte final:** el jugador pasa a **espectar a sus compañeros vivos** y **reaparece en la cabina del ascensor** cuando el grupo sube (6.5), curado al 100 % pero sin sus curaciones ni objetos. Si **todos** los jugadores del grupo mueren antes de que nadie llegue al ascensor, es **game over** para el grupo (6.7).

---

## 10. Inventario de 4 huecos

- `SPInventory` con **4 slots**, guardado en la capability.
- El servidor ignora cualquier otro slot del inventario real.
- Cambio de hueco con **1–4** y con la rueda (limitada a 4).
- **Un item por hueco** (sin stacks), salvo que se configure lo contrario.
- **Sin recogida automática**: los items se recogen mirándolos e interactuando (click derecho mantenido, 0,3 s).
- Si el inventario está lleno, se avisa con un prompt y un sonido.
- Items del evento:

| Item | Uso |
|---|---|
| **Linterna** | Se entrega al empezar, ocupa un hueco. Batería limitada. Proyecta un **cono de luz dinámico real** (ver 21.1) |
| **Batería** (consumible) | Recarga la linterna (10.1) |
| **Fusible** | Se inserta en cajas de fusibles (sección 14) |
| **Llave** | Abre una cerradura concreta (`lock_id`) |
| **Tarjeta de acceso** | Nivel 1–3, abre lectores de tarjeta |
| **Manivela / rueda de válvula** | Opcional: se coloca en un tubo sin rueda |
| **Botiquín** (consumible) | Cura vida (10.1) |
| **Bengala** (consumible) | Enciende una luz dinámica en el suelo durante un rato (10.1) |
| **Jeringa de adrenalina** (consumible) | Permite levantarse solo si estás derribado (10.1) |
| **Walkie** | Habla con el equipo por radio (17.3). Ocupa un hueco |
| **Objeto lanzable** (lata, botella) | Se apunta con click derecho mantenido y se lanza con click izquierdo para distraer (ver 12.7). Con G se suelta al suelo sin lanzarlo |
| **Notas** | No ocupan hueco: se guardan en un diario consultable |

- Al morir, los items del evento caen en el punto de muerte como `SPItemEntity` recuperables (sin perder progreso) o se retiran al subir al ascensor (6.5).
- Con solo 4 huecos hay **decisiones constantes**: la linterna ocupa uno, así que solo se pueden llevar 3 cosas más.

### 10.1 Consumibles

Los consumibles **se consumen con el click derecho mantenido** (ver 11.1) y se gastan al completar el uso.

- **Cómo se usa:** con el consumible en el hueco activo y sin nada interactuable delante, se mantiene el click derecho. Una barra muestra el progreso del uso (cada consumible define su `use_time`). Se **cancela** si se suelta el click, se cambia de hueco o el jugador es agarrado.
- **Definición data-driven:** `data/smileprotocol/consumables/*.json` con `id`, `use_time`, efecto, ruido, animación, sonido y si se puede usar derribado.
- Se **sincroniza** con el servidor (`C2S_UseItem`); el servidor valida y aplica el efecto. Hay animación del jugador para cada uso (13.1).

Lista inicial **propuesta** (cantidades y valores ajustables en pruebas):

| Consumible | Efecto | Tiempo de uso | Ruido | Derribado |
|---|---|---|---|---|
| **Botiquín** | Cura una parte de la vida | ~2,5 s | Bajo | No |
| **Batería** | Recarga la linterna | ~1,2 s | Muy bajo | No |
| **Bengala** | Luz dinámica (Veil) en el suelo durante ~40 s. **También atrae la atención** de los mobs | ~0,8 s | Medio | No |
| **Jeringa de adrenalina** | Levantarse solo, con poca vida, tras un tiempo | ~4 s | Bajo | **Sí** |

Los lanzables (lata, botella) **no son consumibles**: se apuntan y se lanzan (12.7). El walkie tampoco: es equipo que se queda en el hueco (17.3).

---

## 11. Interacción

Sistema común `Interactable` (interfaz) para todo lo que se pueda usar:

- Define: texto del prompt, **tiempo de mantenido** (0 = instantáneo), si hace **ruido** y cuánto, si se cancela al moverse, y si es **cooperativo** (varios jugadores a la vez).
- El cliente muestra el prompt y la barra de progreso; el servidor decide el resultado. Si el jugador suelta o se mueve, se cancela.
- Ejemplos: recoger items, abrir puertas, insertar fusibles, girar válvulas, entrar a un escondite, usar una tarjeta.
- Interacciones largas hacen jugar el ruido: **cuanto más tarda, más ruido**.
- Animación del jugador para cada una (sección 13).

### 11.1 Prioridad del click derecho

El click derecho mantenido es **un solo gesto** que significa cosas distintas según el contexto. El orden de prioridad es:

1. **Interactuable en el punto de mira** (item, puerta, válvula, fusible, escondite, rejilla, ascensor, reanimar...): **interactuar**.
2. **Si no hay nada interactuable**, se usa el **objeto de la mano**:
   - **Lanzable:** apuntar (con click izquierdo se lanza, 12.7).
   - **Consumible:** consumirlo (10.1).
   - **Walkie:** encender o apagar (17.3).
3. **Mano vacía y sin nada delante:** no hace nada.

La prioridad se resuelve **en el cliente** para el prompt y la barra, y **el servidor lo valida** (no acepta el uso de un objeto si en ese instante había un interactable válido más cercano).

---

## 12. Movimiento y sigilo

Todas las mecánicas se activan solo dentro de la dimensión. **El movimiento está predicho por el cliente (como en vanilla) y validado por el servidor**: el cliente aplica el movimiento y envía una petición; el servidor valida plausibilidad (cooldown, distancia, que exista el obstáculo) y sincroniza el estado a los demás.

### 12.1 Niveles de movimiento

| Estado | Ruido (radio base) | Notas |
|---|---|---|
| Agachado quieto | 0 | Casi indetectable |
| Agachado andando | 3 | |
| Andando | 8 | |
| Corriendo | 16 | Siempre disponible (no hay hambre) |
| Dive | 18 | |
| Saltar obstáculo | 12 | |
| Asomarse (Q / E) | 0 | No hace ruido, pero **expone la cabeza** a la vista de los mobs |
| Impacto de objeto lanzado | 14 | Se emite donde cae, no donde está el jugador |
| Soltar objeto (G) | 4 | |
| Usar un consumible | 2–8 | Según el consumible (10.1) |
| Arrastrarse (derribado) | 2 | Más quejidos periódicos (9.3) |
| Moverse por un conducto | 6 | El metal lo amplifica (12.9) |
| Retirar una rejilla | 10 | Interacción mantenida |

Valores configurables. Se emiten al `NoiseSystem` (sección 17). Moverse sobre cristales, charcos o metal puede amplificarlo.

### 12.2 Correr y estamina oculta

- Sin barra de estamina en pantalla. Opcional (`STAMINA_ENABLED`, desactivado por defecto): correr mucho provoca respiración agitada, FOV más bajo y ruido extra, sin mostrar barra.

### 12.3 Dive (deslizamiento)

**Cómo:** mientras se corre, pulsar **Shift**.

1. El cliente envía `C2S_Dive`. El servidor valida: está corriendo, en el suelo, sin cooldown, no está agarrado ni escondido.
2. Impulso hacia delante (y un pequeño salto) y luego **deslizamiento en el suelo**: `Pose.SWIMMING` forzada con `setForcedPose`, que reduce la caja de colisión a 0,6 bloques de alto.
3. Duración aproximada de 0,8 s deslizando y 0,4 s levantándose. **Cooldown ~3 s.**
4. **Usos:** pasar por huecos de 1 bloque de alto (bajo tuberías, conductos, mesas), **entrar a escondites bajos** (bajo camilla) y **esquivar** un agarre con buen *timing* (ventana corta de invulnerabilidad al agarre, configurable).
5. Genera ruido alto. Animación propia (sección 13) y efecto de cámara (inclinación y rebote).

### 12.4 Saltar obstáculos (vault)

**Cómo:** mientras se anda o se corre hacia un obstáculo de **1 bloque de altura**, pulsar **Espacio**.

1. **Detección:** raycast hacia delante. El obstáculo debe tener altura de colisión ≤ 1 bloque, tener 2 bloques libres encima y espacio libre al otro lado. Se marca con el tag `smileprotocol:vaultable` (cajas, mostradores, camillas). Se puede permitir la detección automática de cualquier obstáculo de 1 bloque.
2. El cliente hace una **trayectoria curva** (subida, apoyo de mano, bajada) de ~0,5 s bloqueando el input mientras dura. El servidor valida la petición (`C2S_Vault`) y sincroniza el estado.
3. Si no hay obstáculo vaultable, Espacio es un salto normal.
4. Velocidad conservada: si venías corriendo, sales corriendo. Genera ruido medio.
5. Animación propia y movimiento de cámara (sección 13).

### 12.5 Mirar atrás

**Cómo:** mantener **C** (o toggle, configurable) mientras se corre.

- **Solo cambia la cámara**, no la dirección de movimiento: el cliente suma +180° al yaw en `ViewportEvent.ComputeCameraAngles`, suavizado. Se sigue avanzando hacia delante.
- Se desactiva durante QTE, escondites y cinemáticas.
- Los demás jugadores ven al jugador girar el torso y la cabeza (estado sincronizado por paquete).
- Útil para ver al mob que te persigue mientras se corre.

### 12.6 Asomarse por las esquinas (Q / E)

**Cómo:** mantener **Q** para asomarse a la izquierda o **E** a la derecha (o toggle, configurable). Pensado para mirar por una esquina o una puerta sin salir al pasillo.

- **Solo se mueve la cámara y el cuerpo se inclina:** desplazamiento lateral de la cámara (~0,4 bloques) e inclinación (*roll*, ~15°), suavizados con `ViewportEvent.ComputeCameraAngles`. El jugador no se desplaza.
- **No atraviesa paredes:** el cliente lanza un raycast lateral y limita el desplazamiento si hay un bloque, de modo que la cámara nunca se mete en la geometría.
- **Disponible** de pie, agachado o andando despacio. **No** se puede mientras se corre, durante el dive o el vault, en escondites, agarrado, en QTE ni en cinemáticas. Si empieza alguna de esas acciones, se cancela.
- **Validación:** el cliente envía `C2S_Lean` (lado y estado). El servidor valida que no haya acciones incompatibles y sincroniza el estado a los demás.
- **Riesgo real:** el servidor calcula la visibilidad del jugador desde la posición **asomada** de la cabeza. Asomarse no hace ruido, pero si un mob tiene línea de visión a esa cabeza, puede verte.
- Los demás jugadores ven al personaje inclinar el torso y la cabeza hacia el lado correspondiente.
- Si se pulsan Q y E a la vez, se anulan entre sí.

### 12.7 Objetos lanzables y soltar objetos

**Soltar (G):** deja caer el objeto del hueco activo a los pies del jugador como `SPItemEntity`, recuperable con la interacción normal. Emite un ruido pequeño. Los items de misión (fusibles, llaves, tarjetas) se pueden soltar, y siguen protegidos contra pérdidas por muerte (ver 10). Se puede desactivar en la configuración.

**Lanzar para distraer (click derecho + click izquierdo):**

1. Con un objeto lanzable (lata, botella) en el hueco activo y **sin nada interactuable** en el punto de mira, **mantener click derecho** entra en modo apuntado: el jugador levanta el brazo y aparece una **trayectoria discreta** en el HUD (sección 8.1). Soltar el click cancela el apuntado sin gastar el objeto.
2. Con el click derecho mantenido, **click izquierdo** lanza el objeto. El cliente envía `C2S_Throw` con posición y dirección, y el servidor valida (que el objeto exista y sea lanzable, cooldown, fuerza máxima) y simula la trayectoria.
3. Al **impactar**, el `NoiseSystem` emite un ruido (sección 17) en el punto de caída, con sonido según la superficie (cristal, metal, hormigón). Los mobs pasan a `INVESTIGATE` hacia ese punto.
4. El objeto se **consume** al lanzarlo (o cae al suelo si el tipo lo permite). Los lanzables **no hacen daño** a nadie.
5. No se puede apuntar ni lanzar mientras se corre, se hace dive o vault, en escondites, agarrado, en QTE ni en cinemáticas.
6. El click izquierdo **sigue sin atacar, romper ni colocar** nada: solo lanza mientras se apunta un objeto.
7. Animación propia de apuntar y lanzar (sección 13), visible para los demás.

### 12.8 Otras

- **Salto normal:** permitido, con ruido. Caídas de más de 3 bloques dan daño y mucho ruido.
- **Cooperativo:** un compañero puede ser reanimado, y puede distraer a un mob.

### 12.9 Conductos de ventilación

Los conductos son una **ruta alternativa dentro de la misma zona**: conectan salas y atajos **de una misma zona**, nunca de una zona a otra (de eso se encargan los ascensores, 6.5).

- **Entrada y salida:** por **rejillas** (`VentGrilleBlock`), interactuables. Una rejilla cerrada se retira con interacción mantenida (~2 s, ruido alto). Algunas están **selladas por un circuito** y hay que abrirlas con una llave, un fusible o un evento.
- **Movimiento:** dentro del conducto el jugador va **a gatas** (pose de gateo de vanilla, hitbox de 0,6 bloques de alto, para túneles de 1 bloque de alto). Es **lento**. No se puede correr, hacer dive, vault, asomarse ni apuntar lanzables.
- **Sí se puede:** usar la linterna, los consumibles, el walkie y los marcadores. Los jugadores **no se pueden cruzar** dentro del conducto: se pasa de uno en uno.
- **Ruido:** el metal amplifica los pasos (12.1). Los sonidos dentro llegan **atenuados fuera** del conducto, salvo por las rejillas abiertas. La acústica (7.5) hace que resuenen.
- **Mobs:** cada mob define `can_enter_vents`. La mayoría **no entra**: escucha desde fuera y espera en las salidas. Los pequeños pueden seguirte.
- **Ventiladores y trampas:** un ventilador en marcha (ligado a un `circuit_id`) bloquea el paso hasta que se corta su circuito. También pueden dispararse **eventos** (14.7) al entrar.
- **Diseño:** cada zona debe tener pocas rutas (por ejemplo una o dos), con **al menos dos salidas** para que no sean una trampa mortal.
- **Escondite:** el conducto de ventilación también es un tipo de escondite (15.1), con las mismas reglas de gateo.

---

## 13. Animaciones del jugador

### 13.1 Lista de animaciones

| Grupo | Animaciones |
|---|---|
| Base | idle, andar, correr, agachado quieto, agachado andando, saltar, caer, aterrizaje duro |
| Parkour | dive (inicio, deslizamiento, levantarse), vault (subir y bajar), mirar atrás, asomarse (izquierda y derecha) |
| Escondites | entrar y salir de cada tipo (taquilla, armario, bajo camilla, conducto, cortina, contenedor), idle escondido, aguantar la respiración |
| Interacción | recoger, girar válvula, insertar fusible, abrir puerta, usar tarjeta, linterna (sacar y encender), soltar objeto, apuntar y lanzar objeto, usar un consumible (por tipo), usar el walkie, poner un marcador, gatear por un conducto, retirar una rejilla |
| Terror | miedo (temblor cuando hay un mob cerca), sobresalto, agarrado (por cada mob), forcejeo del QTE, liberación, **derribado** (caer, reposo, arrastrarse, levantarse solo o ayudado), **reanimación** (víctima y rescatador), **remate** (una por mob, con su versión de jugador), **muerte instantánea** (una por mob, con su versión de jugador), muerte |
| Cinemáticas | animaciones propias de la cinemática inicial |

### 13.2 Tecnología

- **PlayerAnimator** como librería principal: animaciones en JSON exportadas desde Blockbench, capas (`ModifierLayer`) para mezclar con las vanilla. **Verificar compatibilidad con 1.20.1 y Forge.**
- Se hace una **máquina de estados** `PlayerActionState` (enum) en el servidor: `NORMAL, SPRINT, CROUCH, DIVE, VAULT, HIDDEN, INTERACTING, GRABBED, DEAD...`. Se sincroniza a los jugadores cercanos con un paquete (`SyncPlayerState`) y cada cliente reproduce la animación correspondiente.
- **Primera persona:** se fuerza como cámara principal, así que la prioridad es lo que ve el propio jugador: *camera bobbing*, inclinación en el dive, hundimiento en el vault, rendijas al esconderse, manos y brazos con la linterna (GeckoLib item). Las animaciones completas del cuerpo las ven **los demás jugadores**.
- Los modelos de brazos en primera persona se pueden hacer como entidad/renderer propio con GeckoLib.

### 13.3 Hitbox

- Dive: `Pose.SWIMMING` forzada (0,6 de alto).
- Esconderse: el jugador queda **inmóvil y anclado** al punto del escondite; se oculta de los demás (invisible mientras está dentro) y la animación se reproduce en el bloque (la puerta de la taquilla, etc.).

### 13.4 Alternativa sin PlayerAnimator

Un `RenderPlayerEvent.Pre` que oculte el modelo vanilla y use un modelo GeckoLib con la skin del jugador, o manipular partes del `PlayerModel`. Más trabajo, pero sin dependencia extra.

---

## 14. Fusibles, llaves, puertas y circuitos

### 14.1 Circuitos y fusibles

- `CircuitManager` (`SavedData`) guarda para cada `circuit_id`: fusibles necesarios, insertados y estado (encendido/apagado).
- **Caja de fusibles** (`FuseBoxBlock`, GeckoLib): N ranuras. Se inserta un fusible con interacción mantenida (animación del jugador y de la caja). Cuando se completan, el circuito **se enciende**: se lanza `CircuitPoweredEvent`.
- Cualquier bloque con `circuit_id` reacciona: **luces** (se encienden o dejan de parpadear), **puertas eléctricas**, ascensores, pantallas, ventiladores, máquinas.
- Los fusibles pueden tener **tipos** (`fuse_a`, `fuse_b`...) para que cada ranura exija uno concreto, y haber **fusibles fundidos** (señuelo) sin efecto.
- Se pueden **retirar** y volver a poner (cooperativo: un jugador los lleva mientras otro distrae).
- Con solo 4 huecos hay que **ir y volver** por los fusibles.
- Insertar, retirar o **mover un fusible de una caja a otra** puede disparar **eventos de laboratorio y jumpscares** (14.7).

### 14.2 Llaves y tarjetas

- `KeyItem` con NBT `lock_id`. `LockedDoorBlock` con NBT `lock_id`. Si coinciden, la puerta se abre.
- La llave puede **gastarse al usarse** o conservarse (`consume` configurable por cerradura).
- `KeycardItem` con **nivel 1–3**. Lectores (`KeycardReaderBlock`) exigen un nivel mínimo.
- Comandos de construcción: `/spadmin lock set`.

### 14.3 Tipos de puerta

| Tipo | Se abre con |
|---|---|
| Normal | Interacción (puede ser lenta y sin ruido agachado) |
| Con llave | Llave con el `lock_id` correcto |
| Con tarjeta | Lector con nivel suficiente |
| Eléctrica | Circuito encendido |
| Hidráulica / presión | Válvulas giradas (sección 14.4) |
| Atascada | Dos jugadores a la vez |
| Esclusa (airlock) | Cierra detrás; un temporizador permite abrir la siguiente |
| Rejilla / conducto | Acceso para *dive* y gateo |

- Los mobs pueden **abrir** puertas normales (con tiempo y ruido), no las cerradas con llave o sin electricidad (salvo el jefe).
- Cada puerta tiene sonido, animación GeckoLib y ruido propio.

### 14.4 Válvulas

- `ValveBlock` con animación GeckoLib. Se gira con **click derecho mantenido** mientras se llena una barra.
- **Hace ruido** que atrae a los mobs.
- Modos: individual, **varios a la vez** (cooperativo), en un **orden** concreto (indicado en notas del mapa).
- Opcional: la rueda es un item que hay que encontrar y colocar.
- Efectos: abre compuertas, cierra chorros de vapor, despresuriza una sala.

### 14.5 Otros mecanismos

Generadores (varios pasos), palancas, botones, ascensores (que separan las zonas, ver 6.5) y cámaras de seguridad (que, si se activan, delatan la posición del jugador al mob).

### 14.6 Lógica data-driven

Cerraduras, circuitos y objetivos se configuran con NBT de los bloques y con JSON de misiones, no con código, para que se pueda editar el mapa sin recompilar.

### 14.7 Eventos de laboratorio y jumpscares

Acciones del jugador sobre el laboratorio (encender fusibles, moverlos de sitio, abrir una puerta...) pueden provocar **eventos** que asustan, cambian el entorno o atraen mobs. Todo es **data-driven**, en `data/smileprotocol/lab_events/*.json`, para que se pueda editar el mapa sin recompilar.

**Disparadores (`trigger`):**
- Fusibles: `fuse_inserted`, `fuse_removed` y **`fuse_moved`** (un fusible retirado de una caja y colocado en otra).
- Circuitos: `circuit_powered` y `circuit_unpowered`.
- Mecanismos: `door_opened`, `door_closed`, `door_forced`, `valve_turned`, `lever_pulled`, `elevator_called`.
- Jugador: `item_picked`, `item_dropped`, `hide_enter`, `hide_exit`, `zone_entered`, `vent_entered`, `flashlight_on` (en una zona oscura), `player_downed`, `player_revived`.
- Otros: `timer` y `mission_objective_completed`.

**Condiciones:** `once` (una sola vez) o repetible con `cooldown`, `chance` (probabilidad), un mínimo de jugadores en la zona y, si se quiere, que se cumpla otra condición (por ejemplo, que un circuito esté encendido).

**Alcance (`scope`):** `trigger_player` (solo quien lo provocó), `players_in_zone` o `all`.

**Acciones (`actions`)** (las mismas que usan las misiones, sección 19, para que haya un único catálogo): `jumpscare`, `light_flicker`, `blackout`, `alarm`, `sound` (posicional o ambiente), `lock_door` / `unlock_door`, `set_circuit`, `noise_burst` (emite un `NoiseEvent` para atraer mobs), `spawn_mob` (con los ids de la sección 20), `radio_message` (mensaje por el walkie), `camera_effect`, `start_sequence` y `ambient_damage`.

**Tipos de jumpscare (`jumpscare.type`):**
- `visual`: aparece una `ScareEntity` decorativa (GeckoLib, sin colisión, inofensiva) cerca del jugador durante un instante.
- `audio`: golpe de sonido, a menudo precedido de un silencio.
- `flash`: destello o parpadeo de pantalla.
- `peek`: un mob se asoma por una esquina o conducto y desaparece. No ataca.
- `environment`: puerta que se cierra de golpe, algo que cae, luces que se apagan.

**Reglas de diseño:**
- **Presupuesto de sustos** (`JumpscareBudget`): un cooldown mínimo por jugador entre jumpscares (`jumpscare_cooldown`, por defecto 20 s) para no saturar.
- Cada evento marca si es **inofensivo** o **real** (si hace `spawn_mob` o `noise_burst`, es una amenaza de verdad).
- Fusibles falsos (los **fundidos**, 14.1) también pueden disparar eventos al insertarse.
- Se ejecuta en el **servidor**, que evalúa disparadores y condiciones. Los efectos locales (sustos visuales y sonoros) se envían solo a quien corresponda con `PlayJumpscare`, y respetan la **accesibilidad** (24.2).
- Los eventos `once` se guardan en un `EventStateManager` (`SavedData`) **por instancia**: cada grupo tiene su propio estado de eventos (6.3).

Ejemplo:

```json
{
  "id": "storage_fuse_surge",
  "trigger": { "type": "fuse_moved", "from": "storage_a", "to": "storage_b" },
  "conditions": { "once": true, "chance": 1.0 },
  "delay_ticks": 20,
  "scope": "players_in_zone",
  "actions": [
    { "type": "light_flicker", "circuit": "storage_b", "ticks": 80 },
    { "type": "sound", "id": "smileprotocol:power_surge" },
    { "type": "jumpscare", "id": "window_smile", "target": "trigger_player" }
  ]
}
```

---

## 15. Escondites

### 15.1 Tipos

Todos definidos en JSON (`data/smileprotocol/hiding_spots/*.json`) con sus propiedades.

| Tipo | Capacidad | Notas |
|---|---|---|
| **Taquilla** | 1 | Rendijas, el mob la revisa a menudo, ruido medio al entrar |
| **Armario grande** | 2 | Más espacio pero más ruido al compartirlo |
| **Bajo camilla / mesa** | 1 | Se entra a ras de suelo (ideal con *dive*). Vista baja, se ve desde el lateral si el mob mira agachado |
| **Conducto de ventilación** | 1 | También sirve de **ruta alternativa** dentro de la zona (gateo, ver 12.9). Ruido al moverse por él |
| **Cortina de laboratorio** | 1 | Ocultación **parcial**: no hay aguante, pero se ve la silueta |
| **Contenedor / barril** | 1 | Poco ruido, pero el mob puede golpearlo |
| **Zona de oscuridad** | — | Escondite *blando*: agachado y con la linterna apagada reduce mucho la detección |

### 15.2 Propiedades por tipo (JSON)

`capacity`, `enterTime`, `exitTime`, `noiseOnEnter`, `viewCone` (ángulo de visión desde dentro), `breathDrain`, `searchPriority`, `canBeLocked`, `animationId`, `soundSet`.

### 15.3 Máquina de estados

`OUTSIDE → ENTERING → HIDDEN → EXITING`

Reglas:
- No se puede entrar si el mob **tiene línea de visión directa** y está cerca: si lo ve entrar, el escondite queda **comprometido** y el mob irá directo.
- Mientras el mob **está revisando** el escondite no se puede salir.
- Al entrar se genera ruido (según el tipo).

### 15.4 Vista y aguante

- Cámara anclada al escondite, con **giro limitado** (por ejemplo ±35°) y **superposición de rendijas**.
- Sonido amortiguado y latidos que aumentan con la cercanía del mob.
- **Aguante de la respiración:** barra que aparece solo mientras se está escondido. Se puede **aguantar la respiración** (tecla Shift) para reducir ruido a casi cero, pero se agota. Si se agota, el jugador exhala y emite ruido.
- Otros jugadores ven el escondite cerrado y animado.

### 15.5 IA del mob frente a los escondites

- `HidingSearchGoal`: al perder al jugador, el mob visita los escondites cercanos a la **última posición conocida** (más probable cuanto más cerca, más ruido y más `searchPriority`).
- Animación de revisión (abre la puerta, mira dentro).
- Si el jugador está dentro y **no** está comprometido y **aguanta la respiración**, el mob pasa de largo. Si falla algo, **lo saca y lo agarra** (QTE).
- Un jugador puede **distraer** al mob con un ruido para que otro se escape.

### 15.6 Construcción

`/spadmin hide set <tipo>` convierte un bloque en escondite. Modelos y animaciones con GeckoLib.

---

## 16. Reservado

(Sección intencionalmente libre para futuras mecánicas.)

---

## 17. Ruido y sigilo

### 17.1 NoiseSystem

- Fuente central: cada acción emite un `NoiseEvent(pos, radio, tipo)`.
- Los mobs se suscriben y reaccionan según su **sentido de oído** (radio, atenuación por paredes y puertas).
- Fuentes: pasos, *dive*, vault, puertas, válvulas, fusibles, objetos lanzados, caídas, escondites, consumibles, arrastrarse derribado, rejillas y conductos, eventos de laboratorio, **walkies** y **la voz**.
- Se pueden visualizar en depuración (`/spadmin noise test`).

### 17.2 Integración con Simple Voice Chat (opcional)

- Con el plugin de la API de voz, cada paquete de micrófono se traduce a ruido: **hablar** emite un radio medio, **gritar** un radio mayor (según volumen).
- Dentro de un escondite, hablar es peligroso (o impide aguantar la respiración).
- Si Simple Voice Chat no está instalado, el sistema de ruido funciona igual sin la parte de voz.

### 17.3 Walkies

Permiten hablar con el equipo a distancia y sin perder el control del grupo. Requieren **Simple Voice Chat**: sin él, la función está desactivada y el item no se entrega.

- **Item `walkie`:** ocupa un hueco (10). Se **recibe** mientras esté en cualquier hueco y se **transmite** con la tecla **B** (mantener) solo si está en el **hueco activo**, con su animación.
- **Canal de equipo:** por defecto, un canal propio de **cada grupo** (los de grupos distintos nunca se mezclan; `walkie_channels` configurable). La voz se envía con un **canal estático** de la API (**verificar** su soporte y la posibilidad de aplicar un filtro de radio; si no se puede, el mod reproduce un clic y un ruido de fondo).
- **Encender y apagar:** con el walkie en la mano y sin nada interactuable delante, click derecho mantenido (11.1). Apagado, ni recibe ni transmite y **no hace ruido**.
- **Riesgo:** transmitir **sí hace ruido** (la voz del emisor genera el ruido de 17.2) y al **recibir** el walkie del compañero emite un crujido que se oye a su alrededor (radio pequeño, configurable). Estar escondido con el walkie encendido es peligroso.
- **Derribados:** pueden hablar por el walkie para pedir ayuda.
- **Jugadores muertos:** no usan el walkie, pero pueden escuchar al equipo como espectadores.
- **Interferencias:** un mob puede tener `causes_interference`: cerca de él, el audio del walkie se distorsiona (`walkie_interference_radius`).
- **Mensajes de radio:** los eventos (14.7) pueden emitir `radio_message` por el walkie: voces de historia o avisos.

---

## 18. Agarre y minijuego de escape (QTE)

1. Un mob (`SPMonster`) alcanza al jugador y llama a `QTEManager.startGrab(mob, player)`.
2. El servidor pone al jugador en `GRABBED`: movimiento bloqueado, anclado al mob, animación de agarre sincronizada.
3. `S2C_StartQTE` avisa al cliente con tipo, dificultad y tiempo. **El HUD muestra el minijuego.**
4. El cliente envía `C2S_QTEInput`. **El servidor valida** (tiempos y pulsaciones plausibles).
5. Resultado:
   - **Éxito:** el jugador se libera, el mob queda aturdido y hay un pequeño empujón de huida.
   - **Fallo:** según el mob y la configuración (18.1): por defecto el jugador queda **derribado** (9.3). Puede configurarse como muerte directa.

**Tipos:** MASH (machacar), TIMING (barra), SEQUENCE (secuencia de teclas), HOLD (mantener en rango). La dificultad escala por mob.

Interacciones con otras mecánicas:
- **Dive** puede esquivar el agarre con buen *timing*.
- Sacar a un jugador de un escondite activa el agarre.
- Un compañero cercano puede **ayudar** (interacción mantenida) a reducir la dificultad.

### 18.1 Qué pasa al ser alcanzado (por mob)

Cada mob define en su ficha (`data/smileprotocol/mobs/*.json`) qué ocurre cuando alcanza a un jugador:

| `on_catch` | Resultado |
|---|---|
| `GRAB_QTE` (por defecto) | Agarre y minijuego. Si se falla: **derribo** (`qte_fail_result: DOWN`) o muerte (`DEATH`) |
| `DOWN` | Derribo directo, sin QTE |
| `INSTANT_KILL` | **Muerte instantánea** con su secuencia (`KillSequence`, 9.3) |

Además, cada mob puede tener `execution` (si **remata** a jugadores derribados, 9.3), `can_enter_vents` (12.9) y `causes_interference` (17.3). Todas las secuencias (agarre, remate, muerte) tienen **animación propia del mob y del jugador**, sincronizadas por el servidor.

---

## 19. Misiones

- `MissionManager` (`SavedData`), definición **data-driven** en `data/smileprotocol/missions/*.json`.
- Tipos de objetivo: encontrar item, **insertar fusibles**, **abrir puerta (llave/tarjeta)**, **girar válvulas**, llegar a una zona, sobrevivir X tiempo, escapar.
- Por jugador o globales (configurable), con objetivos cooperativos.
- Eventos al completar: spawnear mobs, abrir puertas, apagar luces, reproducir sonidos o cinemáticas.
- HUD: solo el objetivo actual, de forma discreta. Sincronización por paquetes.

---

## 20. Mobs (propuesta inicial)

> **Esta tabla es solo un borrador**, salvo el **Berserker**, que ya tiene ficha (20.2). Los demás tipos de smilers se definirán **uno a uno** con detalle más adelante. Cada ficha incluirá: comportamiento, sentidos (vista y oído), estados de IA, QTE, `on_catch`, `execution`, `can_enter_vents`, `causes_interference`, animaciones, sonidos y zonas donde aparece.
>
> **Regla de familia: todos los smilers están basados en Smiley** (el jefe, 20.1). Cada ficha nueva debe indicar **en qué se parece a Smiley y en qué se diferencia**.

Todos con **GeckoLib 4**: animaciones `idle, walk, run, grab, stunned, execute, kill`. Clase base `SPMonster`: sin daño por golpes (su amenaza es el agarre, el remate y la muerte instantánea), inmune a los jugadores, con sentidos de **vista** y **oído** (NoiseSystem). Los smilers heredan de `SmilerBase` (sobre `SPMonster`), que aporta los **rasgos visuales de Smiley** (20.1).

| Mob | Comportamiento | QTE sugerido |
|---|---|---|
| **Berserker** | **Ciego.** Solo oye y golpea el punto donde oyó el ruido (ficha en 20.2) | Ninguno (`DOWN` directo) |
| **El Paciente** *(nombre provisional)* | Te sigue y solo avanza cuando no lo miras | TIMING |
| **Acechador de pasillo** | Persigue por sonido **y vista**. Revisa escondites | MASH |
| **Muñeco colgante** | Cuelga del techo, ataque por sorpresa | SEQUENCE |
| **Entidad de la oscuridad** | Aparece en zonas oscuras, retrocede ante la linterna | HOLD |
| **Smiley** (jefe final) | Varias fases y cinemáticas. **Sin definir todavía** (20.1) | Mixto |

Estados de IA típicos: `PATROL → INVESTIGATE (ruido) → CHASE → SEARCH (escondites) → PATROL`. Cada mob usa los que le encajan: el Berserker no tiene `CHASE` (20.2).

### 20.1 Smiley y la familia de los smilers

**Smiley es el jefe final** (6.6). Se **define más adelante**; de momento solo se fija su **aspecto**, porque es la **base de diseño de todos los smilers**.

**Aspecto de Smiley (canónico):**

| Rasgo | Descripción |
|---|---|
| **Tamaño** | Grande: unos **3 o 4 bloques** de alto, **delgado** |
| **Silueta** | **Completamente negra**, sin detalles de superficie: se reconoce por el contorno |
| **Brazos y manos** | Brazos **largos**, manos **grandes** con **dedos afilados** |
| **Piernas** | **Largas** |
| **Pelo** | **Largo**, con aspecto **de chico**, visible en la silueta |
| **Ojo** | **Un solo ojo rojo**, en el lado del **ojo derecho**. Al otro lado no hay ojo |
| **Boca** | **Sonrisa roja** |

**Reglas para todos los smilers:**
- Mantienen **la silueta negra y la sonrisa roja**. Es lo que los hace de la familia.
- Cada smiler **varía** a partir de Smiley: más bajo o encorvado, otras proporciones, el pelo, las manos, y sobre todo **el ojo** (apagado, cerrado, ausente...). La variación debe tener sentido con su comportamiento.
- **El rojo es lo único que brilla.** El ojo y la sonrisa usan una **capa emisiva** (GeckoLib, **verificar** cómo se hace en 4.x) y no una luz dinámica de Veil, para no gastar el presupuesto de luces (21.1).
- Los modelos comparten un **esqueleto base** (mismos nombres de huesos para cabeza, torso, brazos, dedos y piernas) para poder **reutilizar animaciones** entre smilers. *Propuesta.*
- **Altura y mapa:** un smiler de más de 2 bloques no cabe por una puerta vanilla. Las zonas donde aparezcan deben tener **pasillos y puertas más altos** (ver 27).

### 20.2 Berserker

El **primer smiler** que se implementa. Un mob **ciego** que solo se guía por el **oído** y **ataca el lugar donde oyó el ruido**, no al jugador. *Todas las cifras son propuestas iniciales a ajustar en pruebas.*

**Aspecto** (variación de Smiley, 20.1):
- Más bajo que Smiley: unos **2,5 bloques**, **encorvado**, con brazos largos que casi arrastran. Mantiene la **silueta negra**, el pelo largo, las manos grandes con dedos afilados y la **sonrisa roja**.
- **Su ojo rojo está apagado o cerrado**: es ciego. Es lo que lo distingue a simple vista de un smiler que sí ve.
- Hitbox propuesta: **0,9 de ancho × 2,6 de alto**. *Implementado (fase 11):* la caja es de **0,9 × 1,95** para que pase por las puertas del laboratorio (se agacha); el modelo mide unos 2,5 bloques encorvado. El derribo de esta fase es el mínimo (arrastrarse, ruido, desangrado y remate); la reanimación llega en la fase 12.

**Sentidos:**
- **Vista: ninguna.** No ve a los jugadores, ni la linterna, ni a nadie asomándose (12.6), por mucho que se acerque.
- **Oído: muy agudo** (`hearing_multiplier`, por ejemplo ×1,5 sobre el radio del ruido). Ignora los ruidos cuyo radio efectivo (radio del ruido × `hearing_multiplier`, en bloques, tras atenuación por paredes) quede por debajo de `min_noise` (5 bloques). Así, **andar agachado** (3 × 1,5 = 4,5) no lo alerta, pero **andar normal** (8 × 1,5 = 12) sí.
- No revisa escondites (`checks_hiding_spots: false`). Si un jugador hace ruido **dentro** de un escondite, golpea ese sitio.

**Estados de IA** (no tiene `CHASE`: no puede seguir a alguien que se mueve):

| Estado | Qué hace |
|---|---|
| `PATROL` | Merodea despacio por su zona o se queda quieto girando la cabeza (`idle_behavior`: `WANDER` o `STAND`). Es una amenaza de **zona** |
| `INVESTIGATE` | Oye un ruido, **fija el punto** donde sonó y **corre hacia él**. Mientras va, solo cambia de objetivo si oye algo **mucho más fuerte** (`retarget_loudness_ratio`, ×1,5) |
| `STRIKE` | Al llegar al punto, **golpea esa zona** con un barrido de brazos (área, no un solo jugador): aviso de unos 0,6 s, impacto y recuperación de 1,5 s |
| `SEARCH` | Se queda **escuchando** en el sitio unos segundos (`search_time`, 6 s) con animación de olfateo y giro de cabeza. Si oye otro ruido, vuelve a `INVESTIGATE`. Si no, vuelve a `PATROL` |

**Qué pasa al alcanzar:**
- `on_catch: DOWN`: el golpe **derriba** a **todos los jugadores dentro del área** (`strike_radius`, 2,5 bloques) sin QTE, porque ataca una zona y no agarra a nadie. Si el jugador **se ha movido** antes del impacto, el golpe falla.
- `execution: true`: **remata** a los derribados. Un jugador derribado que se **arrastra hace ruido** (17.1), así que el Berserker acude a rematarlo. Reanimar a un compañero a su lado es muy arriesgado.
- `can_enter_vents: false` (es demasiado grande, 12.9) y `causes_interference: false`.
- Si el *dive* esquiva el golpe durante su aviso, queda **pendiente** (ver 27).

**Cómo se juega contra él** (el diseño busca que el ruido sea el recurso principal):
- **Silencio:** caminar agachado, no correr cerca, no hablar ni usar el walkie. La voz y el crujido del walkie (17.2, 17.3) son lo más peligroso.
- **Señuelos:** **lanzar un objeto** (12.7) lo atrae a ese punto y deja el camino libre. Es su contrapunto.
- **Moverse tras hacer ruido:** como golpea el lugar y no a la persona, quien se aparta a tiempo se salva.
- **La linterna no le afecta:** puede usarse con libertad a su lado. Solo importa el sonido.
- **Ventana de huida:** tras el golpe tiene 1,5 s de recuperación y luego se queda escuchando.

**Dónde aparece:** zonas de sigilo, como el **Archivo** y el **Almacén de fusibles** (22). Su número por zona se escala con el tamaño del grupo (6.3).

**Ficha** (`data/smileprotocol/mobs/berserker.json`, propuesta):

```json
{
  "id": "smileprotocol:berserker",
  "family": "smiler",
  "based_on": "smiley",
  "size": { "width": 0.9, "height": 2.6 },
  "senses": {
    "vision": { "enabled": false },
    "hearing": { "multiplier": 1.5, "min_noise": 5.0 }
  },
  "ai": {
    "idle_behavior": "WANDER",
    "retarget_loudness_ratio": 1.5,
    "search_time": 120,
    "checks_hiding_spots": false
  },
  "strike": { "windup_ticks": 12, "radius": 2.5, "recovery_ticks": 30 },
  "on_catch": "DOWN",
  "execution": true,
  "can_enter_vents": false,
  "causes_interference": false
}
```

---

## 21. Ambientación

- **Estilo:** laboratorio abandonado. Cuarzo y hormigón desgastados, metal oxidado, verde enfermizo, luces de emergencia rojas, tuberías, cables, cristales rotos, notas e informes por el mapa.
- **Shaders (`PostChain`)**: viñeta, grano, aberración y distorsión, más intensos con un mob cerca o durante un QTE.
- **Sonido:** ambiente por zona, audio posicional, latidos, silencios deliberados. La **acústica realista** (eco, reverberación y amortiguación por paredes y puertas) la aporta **Sound Physics Remastered** (7.5): conductos, salas grandes y pasillos deben sonar distintos.
- **Cámara:** `ViewportEvent.ComputeCameraAngles` para temblor, inclinación y cinemáticas.
- **Iluminación:** luz tenue. Los circuitos controlan luces (fusibles). La linterna usa iluminación dinámica real (ver 21.1).

### 21.1 Linterna e iluminación dinámica (Veil, obligatoria)

La linterna no es un simple efecto de pantalla: es una **luz dinámica de tipo foco (cono)** que ilumina de verdad la geometría del mundo, gestionada con **Veil**.

- **Luz del propio jugador:** foco anclado a la cámara (posición de los ojos y dirección de la mirada), con ángulo del cono, alcance, color y atenuación configurables. Se actualiza cada frame en el cliente.
- **Luz de otros jugadores:** cada cliente crea y mueve el foco de las linternas ajenas a partir del estado sincronizado (`SyncFlashlight`: encendida o apagada y dirección). Solo se renderizan las que estén dentro de un radio y hasta un máximo de luces simultáneas (config), para cuidar el rendimiento.
- **Batería:** la intensidad y el alcance bajan al agotarse; parpadeo con poca batería. Apagar la linterna es una decisión de sigilo (ver 15 y 17).
- **Luces de ambiente:** los bloques de luz ligados a un `circuit_id` (fusibles, 14.1) pueden usar luces dinámicas de Veil además de la luz de bloque vanilla: encendido, parpadeo y apagón.
- **Mobs y luz:** la **Entidad de la oscuridad** reacciona a la luz de la linterna (retrocede). La detección del servidor **no depende de lo que renderiza el cliente**: usa un cálculo propio (cono y distancia, con línea de visión) para que sea consistente y no manipulable.
- **Linterna sin Veil:** no existe modo de reserva. Si Veil falta, el mod no arranca (dependencia obligatoria).
- **Compatibilidad:** Veil no debe convivir con shaders de terceros (ya prohibidos). **Verificar su compatibilidad con Embeddium** antes de admitirlo (ver 27).

---

## 22. Mapa (fase posterior)

El mapa se diseñará **después** de implementar las mecánicas. Se compone de:

**1. El lobby (overworld):** la recepción de laboratorio y hospital con el ascensor al fondo (5.2).

**2. Las instancias (una copia por grupo)**, con esta estructura:

| Tramo | Contenido |
|---|---|
| **Pasillo inicial** (fijo) | Llegada desde el ascensor del lobby, cinemática y primeras amenazas. Es el tutorial implícito |
| **Zonas intermedias** (del catálogo, cambian entre partidas) | Cada una con su ascensor de salida, sus circuitos, sus escondites, su red de conductos y sus eventos |
| **Sala final** (fija) | Sala de **criogenización y experimentos**, con la batalla final |

**Catálogo inicial de zonas intermedias (a revisar):** almacén de fusibles (escondites + fusibles), sala de máquinas (válvulas), laboratorios de contención (zona sin luz), sótano del reactor, pasillo de cuarentena y archivo.

Cada zona debe usar **bloques de interacción** (puertas con cerradura, cajas de fusibles, escondites, rejillas de ventilación), **huecos de *dive*** y **rutas por conductos** (12.9).

El **número y el contenido definitivos** del catálogo están **pendientes** (27).

Cada zona debe usar las mecánicas de arriba: escondites, puertas con llave y tarjeta, circuitos y obstáculos *vaultables*, huecos de *dive* y rutas por conductos. Se usarán **bloques de datos** para marcar spawns de mobs, items y objetivos.

---

## 23. Red (`SimpleChannel`)

| Dirección | Paquete | Contenido |
|---|---|---|
| S → C | `SyncSPState` | `inSP`, `GameState`, inventario de 4 huecos, vida |
| S → C | `SyncPlayerState` | `PlayerActionState` de jugadores cercanos |
| C → S | `C2S_Dive` / `C2S_Vault` / `C2S_LookBack` / `C2S_Lean` | Peticiones de movimiento a validar |
| C → S | `C2S_Throw` / `C2S_DropItem` | Lanzar un objeto (posición y dirección) y soltar el objeto del hueco activo |
| C → S | `C2S_Interact` | Inicio, progreso y fin de interacciones mantenidas |
| C → S | `SelectSlot` | Hueco activo (1–4) |
| C → S | `C2S_Handshake` | Lista de mods cargados (sección 7) |
| S → C | `StartQTE` / `EndQTE` y C → S `QTEInput` | Minijuego |
| S → C | `SyncMission` | Misión y objetivo actual |
| S → C | `HideState` | Estado de escondite y aguante |
| C → S / S → C | `SyncFlashlight` | Linterna encendida o apagada, dirección y batería, retransmitido a jugadores cercanos |
| S → C | `CameraEffect` / `PlaySequence` | Cinemáticas y efectos |
| S → C | `SyncDowned` | Estado derribado, desangrado y reanimación |
| S → C | `SyncReady` | Contador «X/Y jugadores listos», cuenta atrás y estado de **un ascensor del lobby** (lleva el id del ascensor) |
| S → C | `SyncLobbyBoards` | Estado de todos los ascensores («Libre», «2/4 listos», «En curso, zona N», «Cerrado») para las pantallas del lobby |
| C → S | `C2S_ReadyButton` | Botón de «listo» dentro de un mini ascensor |
| S → C | `SyncGroup` | Grupo del jugador, compañeros y estado del grupo (`GroupState`) |
| S → C | `SpectateState` | Objetivos de espectador permitidos (compañeros o grupos de cualquier partida en curso) |
| S → C | `ShowResults` | Pantalla de GAME OVER y pantalla de resultados al acabar |
| S → C | `StartSequence` / `EndSequence` | Remates, muertes instantáneas y agarres sincronizados (mob, jugador, anclaje, cámara) |
| C → S | `C2S_UseItem` | Inicio y fin del uso de un objeto con click derecho (consumible, walkie) |
| C → S / S → C | `Marker` | Marcadores de la tecla R |
| S → C | `PlayJumpscare` | Susto local (visual, sonido, destello) |
| S → C | `SyncElevator` | Estado del ascensor y cuenta atrás |
| C → S / S → C | `WalkieState` | Walkie encendido, transmisión y canal |
| S → C | `DebugData` | Solo a operadores (modo depuración, 24.1) |

---

## 24. Configuración (`smileprotocol-common.toml`)

Tiempos de dive y cooldown, ruido por acción, aguante de escondites, probabilidad de que el mob revise un escondite, vida máxima, regeneración, número de vidas, `STAMINA_ENABLED`, lista blanca de mods, modo de muerte, timeout del lobby, jugadores tardíos, forzar primera persona, permitir soltar objetos (G), ángulo y desplazamiento al asomarse (Q / E) y modo toggle o mantener, fuerza máxima y cooldown del lanzamiento, y activar/desactivar el dive esquivando un agarre. **Linterna:** ángulo del cono, alcance, intensidad, duración de la batería y máximo de luces dinámicas simultáneas. **Derribado:** `bleed_out_time`, tiempo de reanimación, vida al levantarse, protección tras levantarse, intervalo de quejidos, `qte_fail_result` y prioridad de los remates. **Consumibles y marcadores:** valores de cada consumible (JSON), duración y máximo de marcadores. **Walkies:** activar o no, número de canales y radio de interferencia. **Lobby y grupos:** `lobby_mode` (`FREE`, `EVENT`), `min_group_size` (4, mega), `group_mode` (`RANDOM`, mega), `mini_min_players` (2), `mini_max_players` (6), `mini_ready_timeout` (30 s), `mini_return_delay` (10 s), `max_mini_runs` (4), `descent_countdown`, `required_ready_ratio`, `lobby_gamemode`, `max_groups` e `instance_spacing`. **Dificultad:** tabla de escalado por tamaño de grupo (`group_scaling.json`). **Zonas y ascensores:** `intermediate_zones`, `per_group_zones`, `elevator_grace`, `elevator_ride_time`, `elevator_strip` (`HURT_OR_REVIVED`, `ALL` o `REVIVED_ONLY`) y `disconnect_grace`. **Espectadores:** si pueden ver a otros grupos, `spectate_scope` (`ALL_RUNS`, `OWN_RUN`) y el grupo de voz aparte. **Partida:** `replayable` (por defecto, `true`), `max_plays_per_player`, `auto_newrun` y `lobby_reset_delay` (solo mega), `avoid_repeat_last` y `restore_inventory`. **Sustos:** cooldown e intensidad de los jumpscares.

### 24.1 Modo depuración

Herramientas para **desarrollar y probar**. Solo para operadores (nivel 2). Los datos de depuración **solo se envían a los operadores** (`DebugData`), nunca al resto de jugadores, para no filtrar información sensible (posición de mobs, estados de IA).

- **Activación:** `/spadmin debug <on|off> [categorías]`.
- **Overlays:**
  - Radios de **ruido** y su atenuación (ya existe `/spadmin noise test`).
  - **Conos de visión** y rayos de oído de los mobs, con su estado de IA (`PATROL`, `INVESTIGATE`, `CHASE`, `SEARCH`).
  - **Estado de jugadores** (`DOWNED`, `GRABBED`, escondido...) y tiempos de desangrado.
  - **Zonas**, ascensores y estado de cada circuito (fusibles insertados).
  - **Registro de eventos de laboratorio** (disparadores, condiciones y acciones ejecutadas) y presupuesto de sustos.
  - Número de **luces dinámicas** (Veil) activas y resumen del tráfico de red.
- **Comandos de pruebas:**
  - `/spadmin event fire <id>` (forzar un evento).
  - `/spadmin mob spawn <id>`.
  - `/spadmin player <nombre> down|revive|kill`.
  - `/spadmin zone tp <id>` y `/spadmin elevator force`.
  - `/spadmin state <estado>`, `/smileprotocol newrun` y `/smileprotocol runs list` (partidas en curso, grupos y huecos usados).
  - `/spadmin god` (invulnerable e ignorado por los mobs).
- **Registro:** archivo `sp-debug.log` con eventos de servidor, útil para pruebas multijugador.

### 24.2 Accesibilidad

Opciones de **cliente** (menú propio del mod), pensadas para que el terror no excluya a nadie:

- **Subtítulos de sonido:** indicador visual y direccional de los ruidos importantes (pasos cercanos, puertas, eventos, walkie), con texto corto.
- **Daltonismo:** modos de color y, sobre todo, **forma e icono** además del color en marcadores (8.4), QTE y avisos.
- **Reducir sacudida de cámara:** control de 0 a 100 % para temblor, inclinación al asomarse y efectos de impacto (`ComputeCameraAngles`).
- **Destellos y fotosensibilidad:** los destellos de los jumpscares se limitan en frecuencia y hay un modo que los **sustituye por un fundido suave**.
- **Aviso de sustos:** pantalla de advertencia al entrar y modo de **sustos suaves** (menos volumen en los picos, sin imágenes bruscas).
- **Pulsar en vez de mantener:** alternativa de **toggle** para mirar atrás (C), asomarse (Q/E), apuntar y la interacción mantenida, además de la ayuda del QTE (**QTE asistido**: más tiempo y menos pulsaciones). Esta última se valida en el servidor (`allow_qte_assist`).
- **Tamaño de HUD y texto** ajustable.

---

## 25. Plan de trabajo por fases

1. **Base:** proyecto, GeckoLib, **Veil**, registros, dimensión de instancias, comandos.
2. **Servidor y sesión:** guardado y restauración del estado del jugador con todos los casos límite, `inSP` y `/sp lobby`.
3. **Restricciones y HUD:** barra de vida, 4 huecos, sin hambre, sin E/F3, sin ataque. **Modo depuración básico** (24.1), que se amplía con cada fase.
4. **Aislamiento de mods** (sección 7) y handshake al conectarse.
5. **Lobby:** protecciones, **mini ascensores** y **mega ascensor**, contador de listos, pantallas de estado y modo libre/evento (6.2).
6. **Partidas, grupos e instancias:** `Run` y `RunManager` (varias partidas a la vez), algoritmo de grupos del mega (6.3), `InstanceManager`, `InstanceContext`, colocación de zonas desde plantillas y **escalado de dificultad por tamaño de grupo**.
7. **Movimiento:** correr, dive, vault, mirar atrás y asomarse, con ruido.
8. **Animaciones del jugador** (PlayerAnimator y cámara en primera persona).
9. **Interacción mantenida**, fusibles, llaves, puertas, circuitos y válvulas (por instancia).
10. **Escondites:** tipos, aguante y vista.
11. **Primer mob: el Berserker** (20.2), con oído, estados de IA y golpe de área, sobre `SmilerBase`. El **QTE** y la búsqueda de escondites se prueban con un mob de pruebas (`GRAB_QTE`) hasta definir el siguiente smiler.
12. **Derribado, reanimación, remates y muertes instantáneas** (9.3 y 18.1), con sus animaciones. *Implementado:* reanimación cooperativa (6 s, el derribado no se desangra mientras tanto, se levanta con el 30 % y 3 s de protección), ayuda a un compañero agarrado, daño letal = derribo, secuencias dirigidas por el servidor (`SequenceManager`, `SequenceKind`: remate del Berserker, remate y muerte instantánea del smiler de pruebas) con cámara y HUD para la víctima, y muerte final como espectador de los compañeros vivos (estado `DEAD` del grupo). La jeringa de adrenalina queda para la fase 13 y la reaparición en la cabina para la 16.
13. **Uso de objetos y consumibles** (10.1, 11.1) y **objetos lanzables** (12.7). *Implementado:* botiquín, batería, bengala (luz Veil y ruido que atrae) y jeringa de adrenalina, definidos en JSON y usados con el click derecho mantenido (`UseManager`); lata y botella con apuntado, trayectoria, lanzamiento, ruido al caer según la superficie y la lata recuperable; animaciones de cada uso, de apuntar y de lanzar.
14. **Misiones.**
15. **Eventos de laboratorio y jumpscares** (14.7).
16. **Zonas y ascensores de zona** (revivir, curar y retirar objetos, 6.5), **game over y espectadores** (6.7), **fase final** (6.6) y **fin de partida** (6.8).
17. **Conductos de ventilación** (12.9).
18. **Simple Voice Chat** (integración de voz como ruido y grupos de voz).
19. **Walkies** (17.3) y **marcadores** (8.4).
20. **Ambientación:** shaders, sonido (con Sound Physics Remastered, 7.5), cámara y **linterna con iluminación dinámica (Veil, 21.1)**.
21. **Resto de mobs** (definidos uno a uno, todos basados en Smiley), **Smiley como jefe y diseño del mapa** (lobby, pasillo, catálogo de zonas y sala final).
22. **Accesibilidad** (24.2).
23. **Pulido:** configuración, pruebas multijugador **con varios grupos a la vez**, **prueba de carga con el máximo de mini partidas y del mega a la vez (zonas, mobs y luces de Veil)**, rendimiento, textos (es/en).
24. **Decoración y mobiliario registrable:** más bloques de decoración del laboratorio (escritorios, estanterías, archivadores, carros, monitores, material médico...) y **muebles que se abren y se registran** con la interacción mantenida (cajones, armarios, archivadores, taquillas, cajas): animación de abrir, ruido al registrar y un botín aleatorio por zona (consumibles, lanzables, a veces llaves o fusibles), sincronizado por instancia. Las zonas los colocan con marcadores.

---

## 26. Checklist de pruebas

- [ ] Bajar y salir de una partida devuelve el inventario intacto (incluso con desconexión o crash). `/sp lobby` lleva al lobby y no funciona dentro de una partida.
- [ ] E, F3, F5 y hotbar vanilla funcionan **normal fuera** y están bloqueados **dentro**.
- [ ] La barra de vida es el único elemento permanente del HUD; no hay hambre.
- [ ] El hambre y la saturación nunca bajan; la regeneración natural está cancelada.
- [ ] Solo hay 4 huecos y no se recoge nada automáticamente.
- [ ] No se puede atacar, romper ni colocar dentro. El click izquierdo solo lanza mientras se apunta un objeto lanzable.
- [ ] Q y E asoman la cámara sin atravesar paredes, se cancelan al correr o esconderse, y el mob puede ver la cabeza asomada.
- [ ] G suelta el objeto del hueco activo; click derecho mantenido apunta y click izquierdo lanza, y el impacto atrae a los mobs.
- [ ] Con algo interactuable delante, el click derecho interactúa en vez de apuntar.
- [ ] El lobby está protegido: no se rompe ni se coloca, no hay daño ni mobs, y los jugadores aparecen en aventura.
- [ ] El **mega ascensor** cuenta a todo el lobby en supervivencia o aventura, muestra «X/Y jugadores listos», exige un mínimo de 4, y se recalcula al cambiar de modo o desconectarse. Al estar todos, pulsa el botón y la pantalla se pone en negro.
- [ ] Un **mini ascensor** cuenta solo a los de dentro de la cabina, con un botón de «listo» por jugador, exige un mínimo de 2, admite un máximo de 6, y bloquea las puertas al empezar.
- [ ] Un jugador AFK dentro de un mini ascensor es expulsado tras `mini_ready_timeout`, y uno AFK fuera de la cabina no afecta a nadie.
- [ ] Cada ascensor muestra su estado en la pantalla: «Libre», «X/Y listos», «En curso, zona N» o «Cerrado».
- [ ] El interruptor de modo funciona: en **modo libre** el mega está cerrado y los mini funcionan; en **modo evento** los mini se bloquean (las partidas mini en curso terminan con normalidad) y solo funciona el mega.
- [ ] Con `max_mini_runs` o `max_groups` alcanzados, los mini muestran «Cerrado» y el mega espera a tener hueco.
- [ ] Varias partidas a la vez no mezclan estado: circuitos, misiones, eventos, ruido, marcadores, voz y walkies son independientes entre `Run`.
- [ ] La dificultad escala con el tamaño del grupo (fusibles, mobs y tiempo del QTE) y un grupo de 2 puede completar una partida con una dificultad razonable.
- [ ] El algoritmo de grupos del mega nunca crea un grupo de menos de 4 y da grupos de 5 cuando sobran jugadores (casos 4 a 30 probados).
- [ ] Cada grupo recibe su instancia propia y no ve ni oye a los demás. Circuitos, misiones, eventos, ruido y marcadores no se mezclan entre instancias.
- [ ] Solo hay dos zonas cargadas por grupo como máximo, y la zona anterior se limpia al subir.
- [ ] Todos los grupos de una partida reciben la misma secuencia de zonas, que cambia en cada partida (`/smileprotocol newrun` o automático al acabar).
- [ ] Un mod de terceros en el cliente impide entrar; los overlays y pantallas de otros mods no aparecen dentro.
- [ ] Simple Voice Chat funciona dentro y su voz genera ruido.
- [ ] Sound Physics Remastered está en el modpack, su mod id está en la lista blanca, y sin él el handshake impide entrar. Los sonidos excluidos (latidos, HUD, QTE) no se procesan.
- [ ] La acústica es coherente con la detección: un sonido muy amortiguado para el jugador también llega atenuado al `NoiseSystem`.
- [ ] Dive, vault y mirar atrás funcionan, tienen cooldown y el servidor rechaza peticiones imposibles.
- [ ] Las animaciones se ven bien para los demás jugadores (sincronización).
- [ ] Cada tipo de escondite: entrar, salir, aguante, ser encontrado y ser ignorado.
- [ ] Fusibles, llaves, tarjetas, válvulas y circuitos activan lo que deben, sin que se pierdan items con la muerte.
- [ ] El QTE no se puede saltar con un cliente falso.
- [ ] El **Berserker** no reacciona a la vista ni a la linterna, solo al ruido por encima de `min_noise`, y agacharse lo mantiene a raya.
- [ ] El Berserker corre al punto donde oyó el ruido y golpea **esa zona**: quien se ha movido antes del impacto no es alcanzado y quien está dentro del área cae derribado (`DOWN`).
- [ ] El Berserker pasa por `PATROL → INVESTIGATE → STRIKE → SEARCH`, solo cambia de objetivo con un ruido mucho más fuerte y remata a los derribados que se arrastran.
- [ ] Un objeto lanzado atrae al Berserker, y un ruido dentro de un escondite hace que golpee ese escondite.
- [ ] Los smilers comparten los rasgos de Smiley (silueta negra, sonrisa roja, ojo y emisivo solo en rojo) y su esqueleto base permite reutilizar animaciones.
- [ ] La linterna proyecta un cono de luz dinámico real, también visible en las linternas de otros jugadores, y respeta el máximo de luces.
- [ ] Sin Veil instalado, el mod se niega a cargar con un mensaje claro.
- [ ] Fallar un QTE deja al jugador **derribado** (si el mob es `GRAB_QTE`); el desangrado, el arrastre y las restricciones del estado funcionan.
- [ ] Un compañero reanima con interacción mantenida, con animación sincronizada; se cancela si lo agarran o se suelta.
- [ ] Un mob con `execution` remata a un jugador derribado con su animación y no se puede cancelar una vez empezada. La protección tras levantarse funciona.
- [ ] Un mob con `INSTANT_KILL` mata sin QTE ni derribo, con su secuencia propia sincronizada para todos.
- [ ] El click derecho respeta la prioridad de 11.1: interactuar, luego usar el objeto de la mano.
- [ ] Los consumibles se gastan al completar el uso con click derecho mantenido y se cancelan al soltar o cambiar de hueco. El servidor rechaza usos inválidos.
- [ ] R pone un marcador y mantener R abre la rueda; los compañeros lo ven con icono y distancia; respetan límites y cooldown; no atraen mobs.
- [ ] El walkie transmite con B solo en el hueco activo, se recibe desde cualquier hueco y el crujido al recibir genera ruido. Sin Simple Voice Chat se desactiva.
- [ ] Los conductos se recorren a gatas y no cruzan zonas. Las rejillas, los ventiladores y los mobs que no pueden entrar funcionan.
- [ ] Insertar, retirar y mover fusibles dispara los eventos definidos (incluido `fuse_moved`), respetando `once`, `chance`, alcance y el presupuesto de sustos. El estado de los eventos es independiente en cada instancia.
- [ ] Al entrar un jugador vivo en la cabina empieza la cuenta atrás de gracia; al partir, los muertos, derribados y los que estaban fuera aparecen dentro, todos quedan al 100 %, y los heridos o revividos pierden curaciones y objetos (no la linterna ni el walkie).
- [ ] Si todos los jugadores de un grupo mueren, es game over: pantalla de GAME OVER, sin reintento, y pasan a espectar a grupos de **cualquier partida en curso** (sin poder hablar con ellos) o salen al lobby y pueden entrar en un mini ascensor libre.
- [ ] Un jugador desconectado vuelve a su grupo y su grupo no se elimina durante `disconnect_grace`.
- [ ] Al acabar todos los grupos se muestra la pantalla de resultados y todos vuelven al lobby.
- [ ] Al acabar una partida del mega se muestra el resultado y, tras `lobby_reset_delay`, se prepara una nueva sola: instancias limpias, otra secuencia de zonas distinta de la anterior y mega reabierto.
- [ ] Al acabar una partida mini, el grupo vuelve al lobby, la instancia se limpia y el ascensor queda libre para rejugar al momento con otra secuencia de zonas.
- [ ] Mientras hay una partida del mega en marcha, el mega está ocupado y los que esperan forman el siguiente grupo. Los grupos se rehacen en cada partida.
- [ ] Rejugar varias veces seguidas no deja instancias, chunks ni estado antiguo cargados.
- [ ] El modo depuración solo envía datos a operadores y los comandos de pruebas funcionan.
- [ ] Las opciones de accesibilidad (subtítulos, daltonismo, reducir cámara, destellos, sustos suaves, toggles) funcionan y no rompen la validación del servidor.
- [ ] Funciona en servidor dedicado (sin clases solo-cliente en código común).

---

## 27. Pendiente de definir

- Si se permitirán mods de rendimiento (por ejemplo Embeddium) además de Simple Voice Chat y Sound Physics Remastered, y si son compatibles con Veil.
- Versión y mod id exactos de Sound Physics Remastered para 1.20.1, y si afecta o no a la voz de Simple Voice Chat.
- Qué sonidos del mod se excluyen de la acústica y qué configuración fija se distribuye.
- Versión exacta de Veil para 1.20.1, y si es necesaria solo en cliente o en ambos lados.
- Rendimiento de la iluminación dinámica con muchos jugadores (límite de luces por defecto).
- Si el dive puede esquivar agarres y con qué ventana.
- Cuántos fusibles, llaves y válvulas por zona, y misiones concretas.
- **Servidor aparte:** si en el futuro hay un proxy (por ejemplo Velocity) para conectar un servidor principal con este, `/sp lobby` solo lleva al lobby del mismo servidor.
- Qué hacer con un jugador AFK que bloquea el **mega** ascensor (`start force`, `required_ready_ratio` o un tiempo máximo). En los mini ya está resuelto (`mini_ready_timeout`).
- **Prueba de carga pendiente:** rendimiento de varias instancias a la vez (zonas, mobs, luces de Veil) con `max_mini_runs` y el mega en marcha, y valores definitivos de `max_mini_runs` y `max_groups`.
- Valores definitivos del **escalado de dificultad por tamaño de grupo** (6.3) y si hay que ajustar también la vida o la velocidad de los mobs.
- Si el modo evento debe poder **terminar a la fuerza** las partidas mini en curso, en vez de esperar a que acaben.
- Diseño de la **batalla final** (objetivos y **Smiley** como jefe: fases, habilidades y sentidos) y de la sala de criogenización y experimentos. El aspecto de Smiley ya está fijado (20.1).
- **Berserker:** valores definitivos (radio de oído, `min_noise`, velocidades, tiempos del golpe y radio), si el *dive* puede esquivar su golpe, si **rompe puertas** o solo abre las normales, si hay **más de uno por zona** y si **reanimar** emite ruido (afecta mucho a su dificultad).
- **Altura de los smilers:** puertas, pasillos y cabinas de ascensor deben ser lo bastante altos para el Berserker (~2,6 bloques) y para Smiley (3-4). Decidir si los demás smilers también son grandes.
- Si el **Acechador de pasillo** (oye y ve) se mantiene como mob aparte o se cambia, ahora que existe el Berserker (solo oye).
- Catálogo definitivo de zonas intermedias, dificultad creciente y cuántas se juegan por partida. Para que rejugar tenga variedad real, el catálogo debería tener **bastantes más zonas de las que se juegan** (por ejemplo el doble).
- Si, al subir, la penalización de objetos (`elevator_strip`) se aplica solo a los heridos o revividos (por defecto) o a todos.
- Si los espectadores de otros grupos pueden oír la voz de los jugadores a los que miran.
- Valores del estado derribado (desangrado, reanimación, protección, quejidos) y si hay un máximo de derribos por zona.
- Qué mobs rematan y cuáles tienen muerte instantánea (al definir cada smiler), y si se puede esquivar la muerte instantánea.
- Lista y balance definitivos de los **consumibles** (la de 10.1 es una propuesta) y cuántos se reparten por zona.
- Tecla del walkie y su compatibilidad con Simple Voice Chat; si se necesitan varios canales.
- Número de zonas, contenido de cada una y diseño de la **fase final**.
- Cómo se resuelve el viaje en ascensor (teletransporte entre cabinas idénticas) y qué eventos pasan dentro.
- Lista de eventos de laboratorio y jumpscares por zona, y cuántos sustos por minuto son aceptables.
- Si el walkie puede tener un efecto de radio con la API de Simple Voice Chat.
- Tipos finales de escondite y qué mobs revisan cuáles.
- Diseño del mapa, dimensiones y rutas (fase posterior).
- Nombre y diseño visual definitivos de cada mob.

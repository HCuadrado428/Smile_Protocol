# Cómo probar lo implementado (fases 1 a 6)

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

## Depuración

- `/spadmin debug on`: muestra el panel de estado (sesión, mods, lobby y ascensores). Solo lo ven los operadores.
- Los eventos importantes se escriben en `sp-debug.log`, en la carpeta del servidor.

## Partidas, grupos e instancias (fase 6)

Al bajar un ascensor se crea una **partida** (`R1`, `R2`...) con uno o varios **grupos** (`R1.1`, `R1.2`...). Cada grupo juega en su propio hueco de la dimensión, a 4096 bloques de los demás.

**Zonas provisionales.** Hasta que se diseñe el mapa (fase 21) hay 7 zonas de prueba:
- el pasillo inicial `start_corridor`;
- cinco intermedias: `lab_storage`, `lab_offices`, `lab_morgue`, `lab_generators` y `lab_greenhouse`;
- la sala final `final_room`.

Cada partida elige **3 intermedias** al azar (con dificultad creciente y distintas de la partida anterior de ese ascensor).

**Qué comprobar:**
1. **Mini ascensor.** Bajad 2 jugadores por `mini1`. Aparecéis en la cabina del pasillo y el chat dice «Grupo R1.1 · 2 jugadores». La pantalla del ascensor pasa a «En curso, pasillo».
2. **Varias a la vez.** A la vez, otros 2 bajan por `mini2`. Cada grupo está en su propia copia (no os veis). `/smileprotocol runs` lista las dos partidas.
3. **Avanzar de zona.** Los ascensores de zona llegan en la fase 16; de momento se avanza con `/smileprotocol zone R1.1 next`. El grupo aparece en la cabina de la zona siguiente, que ya estaba precargada. La anterior se vacía poco a poco. La pantalla del lobby pasa a «En curso, zona 1».
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

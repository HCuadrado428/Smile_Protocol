# Cómo probar lo implementado (fases 1 a 5)

## Preparar el entorno

1. Instala **Java 17** y **Gradle 8.8**.
2. En la carpeta del repositorio, ejecuta una vez `gradle wrapper`. Desde entonces usa `./gradlew` (o `gradlew.bat` en Windows).
3. Arranca un cliente con `./gradlew runClient` o un servidor con `./gradlew runServer`.
4. Cada push compila en GitHub Actions. El jar queda en la pestaña **Actions**, como artefacto `smileprotocol-jar`.

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

## Provisional hasta la fase 6

Los ascensores bajan a la **sala de pruebas**, no a una instancia real. Un ascensor vuelve a quedar «Libre» cuando ninguno de los que bajaron sigue en la partida. Para sacarlos de ella, usa `/smileprotocol leave <jugadores>`.

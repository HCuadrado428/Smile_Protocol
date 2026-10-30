# Fichas aceptadas de los smilers (fase 21)

Elegidas en el cuestionario «Fichas de los smilers».

**Regla de familia (revisada):** Smiley es el único que es una silueta negra pura. Los demás comparten solo lo rojo que brilla (la sonrisa y/o el ojo); el resto del aspecto es libre y debe ser grotesco y propio de cada uno:
- **El Paciente:** escayola pálida y agrietada como una estatua, vendas manchadas sobre las cuencas, incisión de autopsia en Y cosida en el pecho, pulsera de hospital y una vía que le cuelga del brazo.
- **El Rastreador**: sin piel, músculo morado y tendones a la vista, púas de hueso en el espinazo y la mandíbula partida; el ojo rojo en la palma.
- **El Ahorcado**: muñeco hecho de sábanas de hospital cosidas, relleno que se sale, cables enredados; ojo de botón rojo y sonrisa cosida con hilo rojo.
- **Apagón**: ceniza y humo gris que se deshacen, brazos de sombra larguísimos y muchos ojos rojos que se abren y se cierran.
- **Berserker** (rehacer): piel carbonizada y agrietada sobre un cuerpo hinchado, costuras y grapas, el ojo cosido.

## El Paciente
Inspirado en los ángeles llorones de Doctor Who. *Implementado (fase 21):* modelo `tools/mob_models.py` (`paciente`), IA en `entity/Paciente.java`, muerte `PACIENTE_KILL`, aparece en `paciente_zones` (morgue y sala final).
- **Aspecto:** muy alto y delgado (3,5 bloques), recto y rígido, brazos pegados al cuerpo. **Sin ojo**: solo la sonrisa, que se ensancha cuanto más cerca está.
- **Movimiento:** salta de posición en posición cuando nadie lo mira (sin animación de andar); instantáneo si nadie mira, quieto del todo si alguien mira.
- **Actitud:** paciente y acosador, te sigue por la planta sin prisa. **Cambia de objetivo si su víctima se aleja mucho.**
- **Mecánica:** se congela mientras **cualquier** jugador lo mire (hay que turnarse). Al tocarte, **muerte instantánea**. No remata.
- **Escondites:** no los revisa; espera fuera. **Conductos:** no entra, espera en la salida. **Por su tamaño no entra en las salas** (se queda en los pasillos).
- **Walkies:** estática que sube cuanto más cerca está. **Sonido:** silencio; crujido de huesos al cambiar de sitio.
- **Cómo se le gana:** moverse en grupo vigilándolo, encerrarlo cerrando puertas, llegar al ascensor (no sale de la planta). La linterna **no** lo congela.
- **Dónde:** pasillos largos y morgue.

## El Rastreador (acechador de pasillo)
*Implementado (fase 21):* modelo `rastreador`, IA en `entity/Rastreador.java`, aparece en `rastreador_zones` (oficinas y almacén). Pendiente: seguirte por los conductos.
- **Aspecto:** 2,2 bloques, delgado y ágil, espalda arqueada y cabeza adelantada. **Ojo en la palma de la mano**, que levanta para buscar. Garras que arañan las paredes y dejan marcas.
- **Movimiento:** de pie al patrullar, a cuatro patas al perseguir. Más rápido que el jugador corriendo, pero se cansa a los 8 s.
- **Actitud:** cazador metódico: patrulla, oye, mira, persigue y revisa.
- **Sentidos:** vista en cono de 20 bloques y oído normal. Revisa escondites y saca a quien esté dentro.
- **Mecánica:** busca donde te perdió de vista y revisa los escondites cercanos. Agarre con **QTE MASH**; **remata**. Entra en los conductos detrás de ti. Sin interferencias.
- **Sonido:** chasquidos de garras y respiración agitada al correr.
- **Cómo se le gana:** romper la línea de visión, dive bajo huecos, esconderse cuando no te ve.
- **Dónde:** oficinas y almacén.

## El Ahorcado (muñeco colgante)
*Implementado (fase 21):* modelo `ahorcado`, IA en `entity/Ahorcado.java`, colgado en `ahorcado_zones` (`ahorcado_count` por planta).
- **Aspecto:** pequeño (1,2 bloques), muñeco de trapo flácido colgado de cables. Ojo cosido con un botón rojo brillante; sonrisa cosida que se descose al atacar.
- **Movimiento:** inmóvil colgado; cae, se arrastra muy rápido 2 s y vuelve a subir.
- **Actitud:** emboscador, no persigue lejos. Nota lo que pasa justo debajo (2 bloques). No revisa escondites.
- **Mecánica:** cae sobre quien pasa debajo sin mirar arriba; **la linterna hacia arriba lo delata**. Agarre con **QTE SEQUENCE**. No remata. Vive en los conductos y en los techos. Sin interferencias.
- **Sonido:** cuerda que se tensa y una risa de juguete.
- **Cómo se le gana:** mirar arriba con la linterna, o lanzar un objeto debajo para que caiga antes.
- **Dónde:** conductos, invernadero y salas con tuberías en el techo.

## Apagón (entidad de la oscuridad)
*Implementado (fase 21):* modelo `apagon`, IA en `entity/Apagon.java`, aparece en `apagon_zones` (planta de energía y sala final); la linterna gasta `apagon_battery_drain` por tick al apuntarle.
- **Aspecto:** pequeña en la luz y enorme (4 bloques) a oscuras; humo negro con forma de Smiley que se deshace por los bordes. El ojo y la sonrisa flotan; el cuerpo apenas se ve. Apaga las luces por donde pasa.
- **Movimiento:** flota y atraviesa puertas cerradas. Lenta a la luz, muy rápida a oscuras.
- **Actitud:** hambrienta y persistente, va a por el que se separa. Siente a quien está a oscuras. **Respeta los escondites.**
- **Mecánica:** la linterna la hace retroceder, pero gasta batería más rápido al apuntarla. Agarre **HOLD**: mantener la linterna encendida apuntándole. No remata. **No entra en los conductos.** Interferencia fuerte en los walkies.
- **Sonido:** susurros desde todas partes y un zumbido que se hace grave.
- **Cómo se le gana:** luces encendidas, baterías, bengalas y no separarse.
- **Dónde:** plantas con apagones, generadores y la final.

## Smiley (jefe final)
Inspirado en Slenderman y en SCP-096 («el tímido»). Aspecto canónico de 20.1: 4 bloques, delgado, se agacha en las puertas; un ojo rojo a la derecha y la sonrisa roja.

- **Antes de la batalla:** se le ve un instante de lejos en cada planta, sin atacar.
- **Inicio:** cinemática: se apagan las luces, se enciende su ojo y la sonrisa se abre.
- **Sala:** laboratorio de varias alturas con pasarelas.
- **Cómo se gana:** no se le puede herir. Hay que encender los generadores, poner fusibles y demás mecanismos, y **esperar al ascensor**.
- **Fases (3, una más con cada generador encendido):**
  1. Lento y teatral: camina, observa y tantea al grupo; agarre con QTE largo.
  2. Más rápido; invoca a 1 o 2 smilers y apaga luces.
  3. Furioso y a oscuras: solo se ven su ojo y la sonrisa; muerte instantánea; encender el último generador y correr al ascensor.
- **Habilidades:** apaga las luces de la sala; invoca a otros smilers; grito que aturde y atrae a todos los mobs; **teletransporte por manchas**: en las paredes hay manchas con su forma, y sale de una cercana a otro jugador cuando se cansa de perseguir al actual.
- **Al alcanzarte:** agarre con un QTE que cambia de tipo cada vez; si fallas, remate.
- **Luz:** retrocede si lo apuntan dos linternas o más a la vez.
- **Escalado:** más generadores y más smilers invocados cuanto más grande es el grupo.
- **Voz:** frases cortas en voz baja y distorsionada, llamando a los jugadores por su nombre.
- **Música:** mezcla de todo: canción infantil distorsionada, música tensa que sube con cada fase y latidos que se aceleran; el ambiente se rompe en la última fase.
- **Al vencerlo:** la sala se sella con él dentro, su ojo se apaga pero la sonrisa sigue; escapáis en el ascensor.
- **Si cae el grupo:** se ve a Smiley arrastrando al último jugador, una risa y GAME OVER.

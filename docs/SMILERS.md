# Fichas aceptadas de los smilers (fase 21)

Elegidas en el cuestionario «Fichas de los smilers».

**Regla de familia (revisada):** Smiley es el único que es una silueta negra pura. Los demás comparten solo lo rojo que brilla (la sonrisa y/o el ojo); el resto del aspecto es libre y debe ser grotesco y propio de cada uno:
- **El Paciente:** escayola pálida y agrietada como una estatua, vendas manchadas sobre las cuencas, incisión de autopsia en Y cosida en el pecho, pulsera de hospital y una vía que le cuelga del brazo.
- **El Rastreador**: sin piel, músculo morado y tendones a la vista, púas de hueso en el espinazo y la mandíbula partida; el ojo rojo en la palma.
- **El Ahorcado**: muñeco hecho de sábanas de hospital cosidas, relleno que se sale, cables enredados; ojo de botón rojo y sonrisa cosida con hilo rojo.
- **Apagón**: ceniza y humo gris que se deshacen, brazos de sombra larguísimos y muchos ojos rojos que se abren y se cierran.
- **Berserker** (rehecho): piel carbonizada y agrietada sobre un cuerpo hinchado, costuras y grapas, el ojo cosido.

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
*Implementado (fase 21):* modelo `rastreador`, IA en `entity/Rastreador.java`, aparece en `rastreador_zones` (oficinas y almacén).
- **Aspecto:** 2,2 bloques, delgado y ágil, espalda arqueada y cabeza adelantada. **Ojo en la palma de la mano**, que levanta para buscar. Garras que arañan las paredes y dejan marcas.
- **Movimiento:** siempre a cuatro patas, bajo y con el hocico casi en el suelo; solo se levanta para buscar con la mano del ojo en alto. Persiguiendo, al galope: más rápido que el jugador corriendo, pero se cansa a los 8 s.
- **Actitud:** cazador metódico: patrulla, oye, mira, persigue y revisa.
- **Sentidos:** vista en cono de 20 bloques y oído normal. Revisa escondites y saca a quien esté dentro.
- **Sigue tu rastro:** por donde pasas queda un rastro durante minuto y medio (`entity/ScentTrail.java`). Si encuentra uno fresco, patrullando o al perderte, lo sigue olfateando hasta donde acaba; si estás herido dejas manchas de sangre en el suelo y lo sigue más deprisa. Donde se acaba el rastro, revisa los escondites.
- **El ojo bajo la puerta:** sin prisa, antes de cruzar una puerta cerrada se pega al suelo y mete la mano por debajo con la palma hacia arriba; si el ojo ve a alguien al otro lado, abre y va a por él. Persiguiendo, la abre sin más.
- **Arañazos:** al pasar pegado a las paredes las araña; las marcas se quedan, así sabes por dónde ha pasado.
- **Mecánica:** busca donde te perdió de vista y revisa los escondites cercanos. Agarre con **QTE MASH**; **remata**. **Te sigue por los conductos:** si te metes en uno mientras te persigue, va a la rejilla, la arranca y entra. Dentro no se le ve, solo se le oye arañar la chapa cada vez más cerca; va **más lento que tú gateando** (`rastreador_vent_speed`). Si te alcanza dentro te derriba; si sales por otra rejilla, sale detrás; tras 30 s (`rastreador_vent_seconds`) sale y te busca. Sin interferencias.
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

## El Imitador (habla con tu voz)
*Implementado:* modelo `imitador` (`tools/imitador_model.py`), IA en `entity/Imitador.java`, aparece en `imitador_zones` (almacén, morgue y energía).
- **Aspecto:** 2,4 bloques, pálido y descarnado, encorvado como si escuchara. Sin ojos: la cabeza es un huevo liso con una sonrisa enorme de dientes de personas distintas y dos orejas grandes que se mueven. Para imitar se ahueca las manos junto a la boca y la mandíbula imita el habla.
- **Movimiento:** lento y de puntillas mientras acecha; a zancadas cuando ataca o huye.
- **Actitud:** no se deja ver. Se queda a unos 14 bloques, detrás de una pared, y cada 12-20 s habla con la voz de **otro del grupo**: frases que ha oído por la voz de proximidad (solo se guardan en memoria las 6 últimas de cada uno durante la partida y se borran al salir; se puede desactivar con `imitador_voices`). Suenan algo más graves, con un tartamudeo en medio. Sin Simple Voice Chat, susurra y da golpes.
- **Mecánica:** si quien va a mirar llega **solo** a menos de 5 bloques, se ríe y se le echa encima: lo **derriba** y se escabulle. Si llega acompañado, o si alguien le mira, huye corriendo. Respeta los escondites. Se le puede aturdir.
- **Cómo se le gana:** no ir solo hacia una voz; preguntar por el walkie si de verdad era él.

## Smiley (jefe final)
*Implementado (fase 21):* modelo `tools/smiley_model.py` (silueta negra de 4 bloques; ojo y sonrisa en placas propias que la intro enciende y abre), IA en `entity/Smiley.java`, combate y apariciones en `entity/SmileyFight.java`, sala `tools/arena.py` (zona final `smiley_lab`, «Laboratorio Cero»: suelo, anillo de pasarelas con puente y pasarelas altas, escaleras que él puede subir y escaleras de mano que no; tres generadores `gen1`-`gen3`, uno por nivel, que encienden las luces de su nivel), misión `missions/smiley_lab.json`, remate `SMILEY_KILL` y arrastre del último `SMILEY_DRAG`, sonidos de grito, susurro, risa y mancha. La música (`tools/music.py`, tres bucles: nana de caja de música, coro, tensión que sube por fase y latidos) suena mientras pelea, con fundido entre fases (`boss_music_volume`); se calla al encerrarlo. Ajustes `smiley_*` en la configuración. `final_room` pasa a ser una planta intermedia más.

Inspirado en Slenderman y en SCP-096 («el tímido»). **Aspecto (rehecho):** una sombra negra pura, muy alta pero siempre encorvada (más de 4 bloques si se estirara), con los brazos rozando el suelo y el pelo largo y mojado que le tapa media cara. Solo brillan un **ojo rojo hundido en su cuenca** (la otra cuenca, vacía) y la **sonrisa tallada en la cabeza**, de lado a lado: se abre de verdad con la mandíbula y dentro hay filas de dientes desiguales y una garganta que es un pozo negro con un resplandor al fondo. **Paciente: nunca corre.**

- **Antes de la batalla:** se le ve un instante de lejos en cada planta, sin atacar.
- **Inicio:** cinemática: se apagan las luces, se enciende su ojo y la sonrisa se abre.
- **Sala:** laboratorio de varias alturas con pasarelas.
- **Cómo se gana:** no se le puede herir. Hay que encender los generadores, poner fusibles y demás mecanismos, y **esperar al ascensor**.
- **Fases (3, una más con cada generador encendido):**
  1. **Observa:** te sigue de lejos y se queda mirándote a unos bloques; de vez en cuando ataca (`smiley_attack_every`); agarre con QTE largo.
  2. **Acecha:** las lámparas de la sala parpadean y se apagan una a una alrededor de su presa y aparece debajo de la última; invoca a 1 o 2 smilers; se adelanta y **te espera al final del pasillo**, asomado a una esquina y tamborileando en la pared; ataca más a menudo.
  3. **Cerca:** se acerca sin parar, a saltos cada vez más seguidos; a oscuras solo se ven su ojo y la sonrisa; muerte instantánea; encender el último generador y correr al ascensor.
- **Se acerca sin que le veas:** anda despacio, pero cada vez que **nadie** le mira aparece unos bloques más cerca (`smiley_blink_ticks`, `smiley_blink_step`). Si le miras no se para (eso es el Paciente): sigue andando despacio.
- **Va a por el que se queda solo.** Gira la cabeza casi del todo para seguirte con la mirada mientras el cuerpo va hacia otro lado.
- **Sonidos:** respira (se oye a través de las paredes), una risita ahogada la primera vez que te ve y uñas que tamborilean cuando espera.
- **Habilidades:** apaga las luces de la sala; invoca a otros smilers; grito que aturde y atrae a todos los mobs; **teletransporte por manchas**: en las paredes hay manchas con su forma, y sale de una cercana a otro jugador cuando se cansa de perseguir al actual.
- **Al alcanzarte:** te levanta hasta dejarte de puntillas, colgando de sus manos, y te agarra con un QTE que cambia de tipo cada vez; si fallas, remate: sigue subiéndote hasta su cara, echa la cabeza atrás, la boca se le abre hasta partirle la cara y te traga.
- **Luz:** dos linternas o más a la vez (o una si solo queda uno) **le congelan**: se tapa la cara y tiembla sin moverse. En cuanto la luz se aparta, ya está más cerca. Si le tienen así mucho rato se funde en la pared y sale por otro lado.
- **Escalado:** más generadores y más smilers invocados cuanto más grande es el grupo.
- **Voz:** frases cortas en voz baja y distorsionada, llamando a los jugadores por su nombre.
- **Música:** mezcla de todo: canción infantil distorsionada, música tensa que sube con cada fase y latidos que se aceleran; el ambiente se rompe en la última fase.
- **Al vencerlo:** la sala se sella con él dentro, su ojo se apaga pero la sonrisa sigue; escapáis en el ascensor.
- **Si cae el grupo:** se ve a Smiley arrastrando al último jugador, una risa y GAME OVER.

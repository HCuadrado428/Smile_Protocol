# Sesión de prueba con jugadores reales

Guía para una tarde de pruebas en multijugador (4 a 8 personas). Dura unas 2 horas. El objetivo es encontrar fallos y medir si el juego asusta y se entiende, no ganar.

## Antes de empezar (administrador)

1. Servidor con el mod, Veil, GeckoLib, PlayerAnimator, Simple Voice Chat y Sound Physics Remastered. Clientes iguales (el modpack del evento). Ajustes recomendados de Sound Physics en `docs/sound_physics/soundphysics.properties`.
2. Construye el lobby si no está: `/spadmin lobby build confirm`.
3. `/smileprotocol playtest start`. Todo lo que pase se apunta en `smileprotocol-playtest/<fecha>.log` en la carpeta del servidor. Los jugadores ven un aviso con cómo dejar notas.
4. Diles a todos: **si veis algo raro, escribid `/sp nota <qué ha pasado>`** en el momento. Se guarda con vuestro nombre, la planta y la posición.
5. Una persona hace de observadora (en espectador o mirando una pantalla) y apunta: dónde se pierde la gente, cuándo se asustan, cuándo se aburren.

## Bloque 1 — Tutorial (15 min)

- [ ] Todos entran al ascensor **TUTORIAL** (pared oeste del lobby), de uno en uno o en parejas.
- [ ] ¿Se entienden los carteles sin explicarlos? Apunta qué estación les cuesta más.
- [ ] ¿Alguien se atasca en el pasadizo agachado o en el conducto?
- [ ] Al terminar sale «Graduado» y vuelven al lobby.

## Bloque 2 — Partidas pequeñas en Normal (40 min)

- [ ] Dos grupos de 2-3 en los ascensores pequeños. Sin tocar las reglas (Normal).
- [ ] Voz: hablad normal, gritad, susurrad. ¿Los smilers reaccionan al grito? ¿Se oye ahogado quien está en una taquilla?
- [ ] Walkie: probadlo separados en salas distintas.
- [ ] Registrad muebles: ¿sale botín de vez en cuando? ¿Alguien encontró un aturdidor o un despertador?
- [ ] Usad un despertador para despistar a un berserker.
- [ ] Al menos una persona se esconde mientras pasa un smiler cerca.
- [ ] Si alguien cae: ¿lo reaniman a tiempo? ¿Se entiende el minijuego?
- [ ] Al acabar, cada grupo mira su puntuación y `/sp records`.

## Bloque 3 — Monstruos nuevos (20 min)

- [ ] **Gemelo** (oficinas, jardín o criogenia): ¿alguien se da cuenta de que no lleva linterna? ¿Cuánto tardan en descubrirlo?
- [ ] **Imitador** (almacén, morgue o energía): ¿alguien fue hacia una voz conocida? ¿Se dieron cuenta de que no era su compañero?
- [ ] Apunta si la voz imitada se entiende o suena demasiado rota.

## Bloque 4 — Evento grande (30 min)

- [ ] `/smileprotocol mode event`. Todos al ascensor de servicio. Se forman grupos de 4 o 5.
- [ ] Probad una vez con `event_rules` en `NIGHTMARE` (configuración) si el grupo es bueno.
- [ ] Llegad a la sala de Smiley si se puede: música, fases, generadores, final.
- [ ] Prueba de rendimiento: ¿alguien nota tirones con varios grupos a la vez? Apunta FPS y TPS (`/spadmin debug on` o el F3).

## Bloque 5 — Cosas que se rompen (10 min)

- [ ] Alguien se desconecta en mitad de una planta y vuelve: ¿vuelve a su sitio?
- [ ] Reinicia el servidor con una partida a medias: ¿la misión sigue en el mismo paso?
- [ ] `/smileprotocol mode closed`: solo queda abierto el TUTORIAL.

## Al terminar

1. `/smileprotocol playtest stop`. El registro acaba con un **resumen**: partidas, grupos escapados y eliminados (y en qué planta), derribos por cada smiler, muertes y número de notas.
2. Encuesta rápida (a mano o en un formulario), del 1 al 5:
   - ¿Daba miedo?
   - ¿Sabías qué tenías que hacer?
   - ¿Qué smiler te pareció más injusto? ¿Y el mejor?
   - ¿Qué cambiarías primero?
3. Pásame el `.log` y las respuestas y lo reviso.

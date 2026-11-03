-- Smiley (Smile Protocol) - avatar de Figura
--
-- Rueda de acciones (tecla B por defecto): asomarse, golpear, gritar, abrir la boca y reirse.
-- Anda, corre y se agacha solo. La cabeza sigue a donde miras (cuello y cabeza a medias).
-- Mide unos 4 bloques: para que la caja y la camara vayan a juego, en el servidor con Pehkui:
--   /scale set pehkui:hitbox_height 2.2 <jugador>
--   /scale set pehkui:eye_height 2.2 <jugador>

local A = animations.smiley
local NECK2 = @NECK2@
local HEAD = @HEAD@
local LENGTH = { @LENGTHS@ }

vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)
vanilla_model.CAPE:setVisible(false)
vanilla_model.ELYTRA:setVisible(false)
vanilla_model.HELD_ITEMS:setVisible(false)
renderer:setShadowRadius(0.8)
nameplate.ENTITY:setPos(0, 2.4, 0)

-- andar, correr y agacharse: suenan todas a la vez y se reparten el peso (fundido suave)
local LOCO = { "idle", "walk", "run", "crouch", "crouch_walk" }
local weight = { idle = 1, walk = 0, run = 0, crouch = 0, crouch_walk = 0 }
local current = "idle"
for _, n in ipairs(LOCO) do
  A[n]:play()
  A[n]:setBlend(weight[n])
end

-- gestos de cuerpo entero (asomarse, golpear, gritar) y la boca, que se suma encima
local gesture, gestureTicks, gw, fw = nil, 0, 0, 0
local jaw, jw = false, 0
A.jaw_open:play()
A.jaw_open:setBlend(0)
local pending = {}

local function sound(name, volume, pitch)
  sounds:playSound(name, player:getPos():add(0, 3, 0), volume or 1, pitch or 1)
end

local fading = nil   -- gesto que se esta apagando (se para del todo al llegar a peso 0)

local function stopGesture()
  if gesture then fading, fw = gesture, gw end
  gesture, gw = nil, 0
end

function pings.gesto(name)
  if gesture then A[gesture]:stop() end
  if fading then A[fading]:stop() end
  fading, gesture = nil, name
  gw = 0
  A[name]:restart()
  gestureTicks = name == "peek" and -1 or math.ceil(LENGTH[name] * 20)
  if name == "tap" then
    pending = { [11] = "tap" }
  elseif name == "scream" then
    sound("scream", 1.5, 1)
  end
end

function pings.parar()
  stopGesture()
end

function pings.boca(on)
  jaw = on
  if on then A.jaw_open:restart() end
end

function pings.risa()
  sound("giggle", 1, 0.9 + math.random() * 0.2)
end

-- rueda de acciones
if host:isHost() then
  local page = action_wheel:newPage("Smiley")
  action_wheel:setPage(page)
  page:newAction()
    :title("Asomarse"):toggleTitle("Dejar de asomarse")
    :item("minecraft:ender_eye")
    :onToggle(function(on) if on then pings.gesto("peek") else pings.parar() end end)
  page:newAction()
    :title("Golpear la pared")
    :item("minecraft:oak_door")
    :onLeftClick(function() pings.gesto("tap") end)
  page:newAction()
    :title("Gritar")
    :item("minecraft:goat_horn")
    :onLeftClick(function() pings.gesto("scream") end)
  page:newAction()
    :title("Abrir la boca"):toggleTitle("Cerrar la boca")
    :item("minecraft:redstone")
    :onToggle(function(on) pings.boca(on) end)
  page:newAction()
    :title("Reirse")
    :item("minecraft:poppy")
    :onLeftClick(function() pings.risa() end)
end

local breathCd = 200
local ticks = 0

function events.tick()
  ticks = ticks + 1
  local vel = player:getVelocity()
  local speed = math.sqrt(vel.x * vel.x + vel.z * vel.z)
  local crouching = player:getPose() == "CROUCHING"

  if crouching then
    current = speed > 0.015 and "crouch_walk" or "crouch"
  elseif player:isSprinting() and speed > 0.1 then
    current = "run"
  elseif speed > 0.025 then
    current = "walk"
  else
    current = "idle"
  end
  -- el paso va con la velocidad real (piernas largas: pocas zancadas)
  A.walk:setSpeed(math.clamp(speed / 0.216 * 1.6, 0.6, 2.2))
  A.run:setSpeed(math.clamp(speed / 0.28 * 1.1, 0.7, 1.6))
  A.crouch_walk:setSpeed(math.clamp(speed / 0.065 * 1.2, 0.5, 2.0))

  -- andar corta el asomarse y el golpe; el grito acaba siempre
  if gesture and gesture ~= "scream" and speed > 0.05 then
    stopGesture()
  end
  if gesture and gestureTicks > 0 then
    gestureTicks = gestureTicks - 1
    local s = pending[math.ceil(LENGTH[gesture] * 20) - gestureTicks]
    if s then sound(s, 1, 1) end
    if gestureTicks == 0 then stopGesture() end
  end

  -- respira de vez en cuando cuando esta quieto
  breathCd = breathCd - 1
  if breathCd <= 0 then
    breathCd = 240 + math.random(200)
    if speed < 0.02 and not gesture then sound("breath", 0.6, 0.95 + math.random() * 0.1) end
  end
end

local last = 0

function events.render(delta, context)
  local now = ticks + delta
  local dt = math.clamp(now - last, 0, 2) / 20
  last = now
  local k = 1 - math.exp(-dt * 7)

  gw = math.lerp(gw, gesture and 1 or 0, k)
  jw = math.lerp(jw, jaw and 1 or 0, k)
  for _, n in ipairs(LOCO) do
    weight[n] = math.lerp(weight[n], n == current and 1 or 0, k)
    A[n]:setBlend(weight[n] * (1 - math.max(gw, fading and fw or 0)))
  end
  if gesture then A[gesture]:setBlend(gw) end
  if fading then
    fw = math.lerp(fw, 0, k)
    A[fading]:setBlend(fw)
    if fw < 0.02 then A[fading]:stop(); fading = nil end
  end
  A.jaw_open:setBlend(jw)

  -- la cabeza sigue a la mirada: un tercio el cuello, el resto la cabeza
  local r = vanilla_model.HEAD:getOriginRot()
  r = vec((r.x + 180) % 360 - 180, (r.y + 180) % 360 - 180, 0)
  NECK2:setOffsetRot(r * 0.35)
  HEAD:setOffsetRot(r * 0.65)
end

class_name Sim
extends RefCounted
## Deterministic 2D table simulation in table units (ball radius 12).
## The same code drives the game, replays and the AI look-ahead, so it must
## never use randomness.

const PL := 34.0
const PT := 34.0
const PR := 486.0
const PB := 966.0
const CX := 260.0
const CY := 500.0
const BR := 12.0
const MAXV := 26.0
const STEPS := 6
const DRAG := 0.991
const DECEL := 0.013
const STOP_V := 0.04
const CUSHION_E := 0.8
const BALL_E := 0.95
const CORNER_GAP := 32.0
const SIDE_GAP := 22.0
const JAW := 18.0
const POT_DEPTH := 6.0
const HEAD_Y := 733.0
const FOOT := Vector2(CX, 330.0)
const CUE_START := Vector2(CX, 780.0)

const POCKETS := [
	Vector2(PL - 6, PT - 6), Vector2(PR + 6, PT - 6),
	Vector2(PL - 14, CY), Vector2(PR + 14, CY),
	Vector2(PL - 6, PB + 6), Vector2(PR + 6, PB + 6),
]
## Points the AI aims object balls at (just inside each mouth).
const AIM_POINTS := [
	Vector2(PL + 5, PT + 5), Vector2(PR - 5, PT + 5),
	Vector2(PL - 4, CY), Vector2(PR + 4, CY),
	Vector2(PL + 5, PB - 5), Vector2(PR - 5, PB - 5),
]

enum Ev { BALL, RAIL, POT }

static var SEGS: Array = _build_segments()

var num := PackedInt32Array()
var px := PackedFloat64Array()
var py := PackedFloat64Array()
var vx := PackedFloat64Array()
var vy := PackedFloat64Array()
var alive := PackedByteArray()
var first_hit := -1
var rail_after := false
var potted := PackedInt32Array()
var pocket_of := {}
var events := []
var record_events := true


static func _build_segments() -> Array:
	var s := []
	var cg := CORNER_GAP
	var sg := SIDE_GAP
	s.append([Vector2(PL + cg, PT), Vector2(PR - cg, PT)])
	s.append([Vector2(PL + cg, PB), Vector2(PR - cg, PB)])
	for x0 in [PL, PR]:
		s.append([Vector2(x0, PT + cg), Vector2(x0, CY - sg)])
		s.append([Vector2(x0, CY + sg), Vector2(x0, PB - cg)])
	for c in [[PL, PT, 1.0, 1.0], [PR, PT, -1.0, 1.0], [PL, PB, 1.0, -1.0], [PR, PB, -1.0, -1.0]]:
		var cx: float = c[0]
		var cy: float = c[1]
		var sx: float = c[2]
		var sy: float = c[3]
		s.append([Vector2(cx + sx * cg, cy), Vector2(cx + sx * (cg - JAW * 0.7), cy - sy * JAW)])
		s.append([Vector2(cx, cy + sy * cg), Vector2(cx - sx * JAW, cy + sy * (cg - JAW * 0.7))])
	for e in [[PL, -1.0], [PR, 1.0]]:
		var x1: float = e[0]
		var d: float = e[1]
		s.append([Vector2(x1, CY - sg), Vector2(x1 + d * JAW, CY - sg + 3)])
		s.append([Vector2(x1, CY + sg), Vector2(x1 + d * JAW, CY + sg - 3)])
	return s


## Standard triangle with the 8 in the middle and one solid and one stripe
## in the back corners. `nums` is the order of the 15 object balls.
static func rack_order(rng: RandomNumberGenerator) -> Array:
	var rest := [1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15]
	for i in range(rest.size() - 1, 0, -1):
		var j := rng.randi_range(0, i)
		var t = rest[i]
		rest[i] = rest[j]
		rest[j] = t
	rest.insert(4, 8)
	if (rest[10] < 8) == (rest[14] < 8):
		for k in range(11, 14):
			if (rest[k] < 8) != (rest[14] < 8):
				var t2 = rest[10]
				rest[10] = rest[k]
				rest[k] = t2
				break
	return rest


func setup(nums: Array) -> void:
	num.clear(); px.clear(); py.clear(); vx.clear(); vy.clear(); alive.clear()
	_add(0, CUE_START.x, CUE_START.y)
	var i := 0
	var row_h := BR * sqrt(3.0) + 0.6
	for c in 5:
		for r in c + 1:
			_add(int(nums[i]), FOOT.x + (r - c / 2.0) * (2 * BR + 0.6), FOOT.y - c * row_h)
			i += 1
	_clear_shot()


func _add(n: int, x: float, y: float) -> void:
	num.append(n); px.append(x); py.append(y); vx.append(0.0); vy.append(0.0); alive.append(1)


func copy() -> Sim:
	var s := Sim.new()
	s.num = num.duplicate(); s.px = px.duplicate(); s.py = py.duplicate()
	s.vx = vx.duplicate(); s.vy = vy.duplicate(); s.alive = alive.duplicate()
	s.pocket_of = pocket_of.duplicate()
	s.record_events = false
	return s


func index_of(n: int) -> int:
	return num.find(n)


func is_alive(n: int) -> bool:
	var i := num.find(n)
	return i >= 0 and alive[i] == 1


func count_alive(pred: Callable) -> int:
	var c := 0
	for i in num.size():
		if alive[i] == 1 and pred.call(num[i]):
			c += 1
	return c


func free_spot(x: float, y: float) -> bool:
	for i in num.size():
		if i == 0 or alive[i] == 0:
			continue
		if Vector2(px[i] - x, py[i] - y).length() <= 2 * BR + 1:
			return false
	return true


func valid_cue_pos(x: float, y: float, kitchen: bool) -> bool:
	if x < PL + BR or x > PR - BR or y < PT + BR or y > PB - BR:
		return false
	if kitchen and y < HEAD_Y:
		return false
	return free_spot(x, y)


func place_cue(x: float, y: float) -> void:
	px[0] = x; py[0] = y; vx[0] = 0.0; vy[0] = 0.0; alive[0] = 1


## Puts a potted ball back on the foot spot, sliding towards the foot rail if taken.
func respot(n: int) -> void:
	var i := num.find(n)
	var y := FOOT.y
	while y > PT + BR and not free_spot(FOOT.x, y):
		y -= 1.0
	px[i] = FOOT.x; py[i] = y; vx[i] = 0.0; vy[i] = 0.0; alive[i] = 1
	pocket_of.erase(n)


func _clear_shot() -> void:
	first_hit = -1
	rail_after = false
	potted.clear()
	events.clear()


func shoot(angle: float, power: float) -> void:
	_clear_shot()
	vx[0] = cos(angle) * power * MAXV
	vy[0] = sin(angle) * power * MAXV


func moving() -> bool:
	for i in num.size():
		if alive[i] == 1 and (vx[i] != 0.0 or vy[i] != 0.0):
			return true
	return false


func step() -> void:
	var n := num.size()
	var r2 := 4.0 * BR * BR
	for _s in STEPS:
		for i in n:
			if alive[i] == 1:
				px[i] += vx[i] / STEPS
				py[i] += vy[i] / STEPS
		for i in n:
			if alive[i] == 0:
				continue
			var x := px[i]
			var y := py[i]
			if x < PL - POT_DEPTH or x > PR + POT_DEPTH or y < PT - POT_DEPTH or y > PB + POT_DEPTH:
				_pot(i)
				continue
			if x > PL + BR + 1 and x < PR - BR - 1 and y > PT + BR + 1 and y < PB - BR - 1:
				continue
			if _over_pocket(x, y):
				_pot(i)
				continue
			_cushions(i)
		for i in n:
			if alive[i] == 0:
				continue
			for j in range(i + 1, n):
				if alive[j] == 0:
					continue
				var dx := px[j] - px[i]
				if dx >= 2 * BR or dx <= -2 * BR:
					continue
				var dy := py[j] - py[i]
				var d2 := dx * dx + dy * dy
				if d2 >= r2 or d2 == 0.0:
					continue
				var d := sqrt(d2)
				var nx := dx / d
				var ny := dy / d
				var push := (2 * BR - d) / 2
				px[i] -= nx * push; py[i] -= ny * push
				px[j] += nx * push; py[j] += ny * push
				var vn := (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny
				if vn > 0.0:
					var k := vn * (1.0 + BALL_E) / 2.0
					vx[i] -= nx * k; vy[i] -= ny * k
					vx[j] += nx * k; vy[j] += ny * k
					if record_events:
						events.append([Ev.BALL, vn])
					if first_hit < 0:
						if num[i] == 0:
							first_hit = num[j]
						elif num[j] == 0:
							first_hit = num[i]
	for i in n:
		if alive[i] == 0:
			continue
		var sp := sqrt(vx[i] * vx[i] + vy[i] * vy[i])
		if sp == 0.0:
			continue
		var ns := sp * DRAG - DECEL
		if ns < STOP_V:
			vx[i] = 0.0; vy[i] = 0.0
		else:
			vx[i] *= ns / sp; vy[i] *= ns / sp


## Liegt die Kugelmitte über dem Loch, fällt sie hinein (nichts bleibt auf den Backen liegen)
func _over_pocket(x: float, y: float) -> bool:
	for k in POCKETS.size():
		var p: Vector2 = POCKETS[k]
		var r := 16.0 if k == 2 or k == 3 else 20.0
		if (x - p.x) * (x - p.x) + (y - p.y) * (y - p.y) < r * r:
			return true
	return false


func _cushions(i: int) -> void:
	var c := Vector2(px[i], py[i])
	for s in SEGS:
		var a: Vector2 = s[0]
		var b: Vector2 = s[1]
		var ab := b - a
		var t := clampf((c - a).dot(ab) / ab.length_squared(), 0.0, 1.0)
		var q := a + ab * t
		var d := c.distance_to(q)
		if d >= BR or d == 0.0:
			continue
		var nrm := (c - q) / d
		c = q + nrm * BR
		var v := Vector2(vx[i], vy[i])
		var vn := v.dot(nrm)
		if vn < 0.0:
			v -= nrm * vn * (1.0 + CUSHION_E)
			vx[i] = v.x; vy[i] = v.y
			if first_hit >= 0:
				rail_after = true
			if record_events:
				events.append([Ev.RAIL, -vn])
	px[i] = c.x; py[i] = c.y


func _pot(i: int) -> void:
	alive[i] = 0
	vx[i] = 0.0; vy[i] = 0.0
	var best := 0
	var bd := INF
	for k in POCKETS.size():
		var d: float = (POCKETS[k] as Vector2).distance_squared_to(Vector2(px[i], py[i]))
		if d < bd:
			bd = d
			best = k
	pocket_of[num[i]] = best
	potted.append(num[i])
	if record_events:
		events.append([Ev.POT, 1.0])


## Runs a whole shot to rest. Used by the AI and the tests.
func run_to_rest(max_frames := 1200) -> int:
	var f := 0
	while moving() and f < max_frames:
		step()
		f += 1
	return f

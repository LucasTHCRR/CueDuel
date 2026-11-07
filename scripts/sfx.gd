extends Node
## Synthesised sound effects and haptics. All samples are generated at start-up,
## so the game ships without audio files.

const RATE := 22050

var streams := {}
var players: Array[AudioStreamPlayer] = []
var next := 0
var budget := 4
var quiet := false
var _last_vib := 0


func _ready() -> void:
	for i in 10:
		var p := AudioStreamPlayer.new()
		add_child(p)
		players.append(p)
	WorkerThreadPool.add_task(_build)


func _build() -> void:
	var streams := {}
	streams.ball = _render(0.08, func(t, d): return _sweep(t, 2600.0, 1300.0, d, "tri") * 0.7 + _noise() * 0.25 * (1.0 - t / d))
	streams.rail = _render(0.12, func(t, d): return _sweep(t, 190.0, 70.0, d, "sine"))
	streams.pot = _render(0.4, func(t, d): return _sweep(t, 420.0, 70.0, d, "sine") * 0.8 + _noise() * 0.3 * (1.0 - t / d))
	streams.cue = _render(0.07, func(t, d): return _sweep(t, 1600.0, 600.0, d, "sq") * 0.4 + _noise() * 0.5 * (1.0 - t / d))
	streams.win = _jingle([523.0, 659.0, 784.0, 1047.0])
	streams.lose = _jingle([392.0, 330.0, 262.0, 196.0])
	streams.click = _render(0.03, func(t, d): return _sweep(t, 900.0, 700.0, d, "sine") * 0.5)
	_install.call_deferred(streams)


func _install(s: Dictionary) -> void:
	streams = s


func _process(_d: float) -> void:
	budget = 4


func play(kind: String, strength := 1.0) -> void:
	if Store.cfg.mute or not streams.has(kind):
		return
	if kind == "ball" or kind == "rail":
		if budget <= 0:
			return
		budget -= 1
	var p := players[next]
	next = (next + 1) % players.size()
	p.stream = streams[kind]
	p.volume_db = linear_to_db(clampf(strength, 0.05, 1.0)) - 4.0
	p.pitch_scale = 1.0 + randf_range(-0.04, 0.04) if kind == "ball" else 1.0
	p.play()


func vibrate(ms: int) -> void:
	if quiet or not Store.cfg.haptic:
		return
	var now := Time.get_ticks_msec()
	if now - _last_vib < 45:
		return
	_last_vib = now
	Input.vibrate_handheld(ms)


var _seed := 12345


func _noise() -> float:
	_seed = (_seed * 1103515245 + 12345) & 0x7fffffff
	return float(_seed) / 0x3fffffff - 1.0


var _phase := 0.0


func _sweep(t: float, f0: float, f1: float, d: float, kind: String) -> float:
	var f := f0 * pow(f1 / f0, t / d)
	_phase += f / RATE
	var env := pow(0.001, t / d)
	var x := fmod(_phase, 1.0)
	match kind:
		"tri":
			return (4.0 * absf(x - 0.5) - 1.0) * env
		"sq":
			return (1.0 if x < 0.5 else -1.0) * env
	return sin(TAU * x) * env


func _render(d: float, fn: Callable) -> AudioStreamWAV:
	_phase = 0.0
	var n := int(RATE * d)
	var data := PackedByteArray()
	data.resize(n * 2)
	for i in n:
		var v := clampf(fn.call(float(i) / RATE, d), -1.0, 1.0)
		data.encode_s16(i * 2, int(v * 32000.0))
	return _wav(data)


func _jingle(notes: Array) -> AudioStreamWAV:
	var step := 0.17
	var dur := 0.3
	var n := int(RATE * (step * (notes.size() - 1) + dur))
	var buf := PackedFloat32Array()
	buf.resize(n)
	for k in notes.size():
		_phase = 0.0
		var start := int(RATE * step * k)
		for i in int(RATE * dur):
			if start + i < n:
				buf[start + i] += _sweep(float(i) / RATE, notes[k], notes[k] * 0.98, dur, "tri") * 0.6
	var data := PackedByteArray()
	data.resize(n * 2)
	for i in n:
		data.encode_s16(i * 2, int(clampf(buf[i], -1.0, 1.0) * 32000.0))
	return _wav(data)


func _wav(data: PackedByteArray) -> AudioStreamWAV:
	var w := AudioStreamWAV.new()
	w.format = AudioStreamWAV.FORMAT_16_BITS
	w.mix_rate = RATE
	w.stereo = false
	w.data = data
	return w

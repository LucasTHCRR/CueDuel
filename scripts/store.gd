extends Node
## Settings, statistics and replays, kept in one JSON file under user://.

const PATH := "user://save.json"
const MAX_REPLAYS := 12
const REPLAY_VERSION := 1

var cfg := {"level": 2, "haptic": true, "mute": false, "lang": "", "quality": -1}
var stats := {}
var replays := []


func _ready() -> void:
	stats = default_stats()
	var f := FileAccess.open(PATH, FileAccess.READ)
	if f:
		var d = JSON.parse_string(f.get_as_text())
		if d is Dictionary:
			if d.get("cfg") is Dictionary:
				cfg.merge(d.cfg, true)
			if d.get("stats") is Dictionary:
				stats.merge(d.stats, true)
			if d.get("replays") is Array:
				replays = d.replays.filter(func(r): return r is Dictionary and int(r.get("v", 0)) == REPLAY_VERSION)
	cfg.level = clampi(int(cfg.level), 1, 4)
	if int(cfg.quality) < 0:
		cfg.quality = 0 if OS.has_feature("mobile") else 1
	# JSON has no integers; turn counters back into ints.
	for k in stats:
		if stats[k] is float:
			stats[k] = int(stats[k])
	for lv in stats.levels:
		stats.levels[lv] = [int(stats.levels[lv][0]), int(stats.levels[lv][1])]


func default_stats() -> Dictionary:
	return {"games": 0, "wins": 0, "losses": 0, "streak": 0, "best_streak": 0,
		"pots": 0, "shots": 0, "fouls": 0,
		"levels": {"1": [0, 0], "2": [0, 0], "3": [0, 0], "4": [0, 0]}}


func save() -> void:
	var f := FileAccess.open(PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify({"cfg": cfg, "stats": stats, "replays": replays}))


func add_replay(r: Dictionary) -> void:
	replays.push_front(r)
	if replays.size() > MAX_REPLAYS:
		replays.resize(MAX_REPLAYS)
	save()


func reset_stats() -> void:
	stats = default_stats()
	save()

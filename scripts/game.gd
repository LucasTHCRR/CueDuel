extends Node3D
## Game controller: turn order, input, AI, replays and statistics.
## Game logic runs on the fixed 60 Hz physics tick so replays stay exact.

enum St { IDLE, PLACE, AIM, AI, STRIKE, MOVING, RP, OVER, WAIT }

var sim := Sim.new()
var view: TableView
var ui: GameUI
var state := St.IDLE
var turn := "player"
var groups := {"player": "", "ai": ""}
var hand := false
var kitchen := false
var is_break := true
var on_eight := false
var winner := ""
var duo := false
var online := false
var my_side := "player"
var in_game := false
var aim := -PI / 2
var power := 0.55
var follow_cam := true
var spd := 1
var paused := false
var msg := ""
var rec = null
var replay = null
var rp := {}
var place_rec = null
var gs := {}
var rng := RandomNumberGenerator.new()

var _ai_task := -1
var _ai_res := {}
var _ai_t := 0
var _ai_go := 0
var _strike_t := 0.0
var _strike_from := 0.0
var _drag = null
var _rack_nums := []
var _pending_snap := {}
var _wait_snap := false
var _remote_shot := false
var _fin := []
var _grab = null


func _ready() -> void:
	rng.randomize()
	L.set_lang(Store.cfg.lang if Store.cfg.lang != "" else L.system_lang())
	view = TableView.new()
	add_child(view)
	ui = GameUI.new()
	ui.game = self
	add_child(ui)
	Net.got.connect(_on_net)
	Net.lost.connect(_on_lost)
	_new_rack()
	state = St.IDLE
	ui.open_menu()
	_dev_args()


## Developer switches: -- --play[=duo] --level=N --shots=sec,sec,... (PNG to user://)
func _dev_args() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--level="):
			Store.cfg.level = int(a.get_slice("=", 1))
		elif a.begins_with("--play"):
			ui.close_all()
			start_game(a.ends_with("duo"))
		elif a.begins_with("--net-host"):
			Net.host_game("test", a.get_slice("=", 1) if "=" in a else "", false)
		elif a.begins_with("--net-join="):
			var jp := a.get_slice("=", 1).split(",")
			Net.join_game(jp[0], Net.PORT, jp[1] if jp.size() > 1 else "")
		elif a == "--net-shoot":
			var nt := Timer.new()
			nt.wait_time = 1.0
			nt.autostart = true
			add_child(nt)
			nt.timeout.connect(func():
				print("net online=%s state=%s turn=%s me=%s hash=%d" % [online, St.keys()[state], turn, my_side, hash(sim.px)])
				if online and turn == my_side:
					if state == St.AIM:
						shoot_pressed()
					elif state == St.PLACE:
						confirm_place())
		elif a == "--bake-table":
			view.bake.call_deferred()
		elif a == "--balls-open":
			ui.close_all()
			start_game(false)
			ui.open("balls")
		elif a == "--balls":
			ui.close_all()
			start_game(true)
			groups = {"player": "stripe", "ai": "solid"}
			for n in [9, 3, 6]:
				sim.alive[sim.index_of(n)] = 0
			ui.open("balls")
		elif a.begins_with("--lang="):
			L.set_lang(a.get_slice("=", 1))
		elif a.begins_with("--cam="):
			var v := a.get_slice("=", 1).split_floats(",")
			view.fixed_cam = [Vector3(v[0], v[1], v[2]), Vector3(v[3], v[4], v[5])]
		elif a == "--top":
			follow_cam = false
		elif a == "--settings":
			ui.open("settings")
		elif a == "--result":
			ui.close_all()
			start_game(false)
			ui.show_result(L.t("VICTORY!"), true, L.t("You potted the 8 cleanly"), [
				["6:42", "Duration"], ["14 : 11", "Shots (you : AI)"], ["8 : 5", "Potted"],
				["1 : 2", "Fouls"], ["4", "Best run"], ["57 %", "Pots per shot"]])
		elif a == "--menu-stats":
			ui.open("stats")
		elif a == "--aiwatch":
			ui.close_all()
			start_game(false)
			turn = "ai"
			_begin()
		elif a.begins_with("--shots="):
			var i := 0
			for t in a.get_slice("=", 1).split(","):
				i += 1
				var n := i
				get_tree().create_timer(float(t)).timeout.connect(func():
					var img := get_viewport().get_texture().get_image()
					img.save_png("user://shot_%d.png" % n)
					print("shot ", n))
		elif a == "--trace":
			var tm := Timer.new()
			tm.wait_time = 0.5
			tm.autostart = true
			add_child(tm)
			tm.timeout.connect(func(): print("t=%.1f state=%s turn=%s ai_t=%d go=%d task=%d hand=%s" % [Time.get_ticks_msec() / 1000.0, St.keys()[state], turn, _ai_t, _ai_go, _ai_task, hand]))
		elif a.begins_with("--quit="):
			get_tree().create_timer(float(a.get_slice("=", 1))).timeout.connect(get_tree().quit)


# ---------- flow ----------

func start_game(two_players: bool, net := false) -> void:
	if not net:
		_leave_online()
	_cancel_ai()
	duo = two_players
	replay = null
	Sfx.quiet = false
	spd = 1
	in_game = true
	ui.set_replay_mode(false)
	_new_rack()


func start_replay(log: Dictionary) -> void:
	_leave_online()
	_cancel_ai()
	_save_log("Aborted")
	in_game = false
	duo = bool(log.get("duo", false))
	replay = {"log": log, "i": 0}
	Sfx.quiet = true
	spd = 1
	ui.set_replay_mode(true)
	_new_rack()


func stop_replay() -> void:
	replay = null
	Sfx.quiet = false
	spd = 1
	ui.set_replay_mode(false)
	state = St.IDLE
	ui.open_menu()

func restart() -> void:
	if replay != null:
		return
	if online:
		online_again()
		return
	_cancel_ai()
	_new_rack()


func _new_rack() -> void:
	if rec != null and replay == null:
		_save_log("Aborted")
	var nums: Array
	if replay != null:
		nums = replay.log.nums.duplicate()
	elif online and not Net.host:
		nums = _rack_nums.duplicate()
	else:
		nums = Sim.rack_order(rng)
	if online and Net.host and replay == null:
		Net.send({"t": "rack", "nums": nums})
	_fin = []
	_pending_snap = {}
	_wait_snap = false
	sim.setup(nums)
	view.reset_balls(sim)
	turn = "player"
	groups = {"player": "", "ai": ""}
	# The break is played from the fixed head spot; ball in hand only after fouls.
	hand = false
	kitchen = false
	is_break = true
	winner = ""
	place_rec = null
	rec = null if replay != null else {"v": Store.REPLAY_VERSION, "nums": nums, "level": Store.cfg.level, "duo": duo, "shots": [], "date": Time.get_datetime_string_from_system(false, true).substr(0, 16)}
	gs = {"t0": Time.get_ticks_msec(), "ps": 0, "as": 0, "pp": 0, "ap": 0, "pf": 0, "af": 0, "run": 0, "best": 0}
	msg = L.t("Break!")
	_begin()


func _begin() -> void:
	if replay != null:
		_next_rp()
		return
	if online and turn != my_side:
		state = St.WAIT
		_say(msg)
	elif turn == "player" or duo:
		if hand:
			state = St.PLACE
			if sim.alive[0] == 0 or not sim.valid_cue_pos(sim.px[0], sim.py[0], kitchen):
				_default_cue()
			var hint := L.t("drag the cue ball, then OK")
			_say(msg + " – " + hint)
		else:
			state = St.AIM
			_aim_near()
			_say(msg)
	else:
		state = St.AI
		_ai_t = 0
		_ai_go = 0
		_ai_res = {}
		_say(msg)
	ui.update_hud()


func _default_cue() -> void:
	var y := Sim.CUE_START.y
	var x := Sim.CUE_START.x
	while not sim.valid_cue_pos(x, y, kitchen) and x < Sim.PR - Sim.BR:
		x += 4
	sim.place_cue(x, y)


func confirm_place() -> void:
	if state != St.PLACE or not sim.valid_cue_pos(sim.px[0], sim.py[0], kitchen):
		return
	hand = false
	place_rec = [sim.px[0], sim.py[0]]
	state = St.AIM
	_aim_near()
	_say(L.t("Aim and shoot!"))
	ui.update_hud()


func shoot_pressed() -> void:
	if state == St.PLACE:
		confirm_place()
	elif state == St.AIM:
		_strike()


func _strike() -> void:
	if online and not _remote_shot:
		Net.send({"t": "shot", "a": aim, "p": maxf(0.05, power), "pl": place_rec})
	_remote_shot = false
	_strike_t = 0.0
	_strike_from = Sim.BR + 3 + power * 95
	state = St.STRIKE


func _shoot(a: float, p: float) -> void:
	if rec != null:
		rec.shots.append({"t": turn, "pl": place_rec, "a": a, "p": p})
	place_rec = null
	var g: String = groups[turn]
	on_eight = g != "" and Rules.group_left(sim, g) == 0
	sim.shoot(a, p)
	Sfx.play("cue", 0.4 + p * 0.6)
	Sfx.vibrate(int(20 + p * 50))
	state = St.MOVING


func _resolve() -> void:
	if online and turn != my_side:
		# Der Schütze schickt das Ergebnis, wir übernehmen es nur
		_wait_snap = true
		if not _pending_snap.is_empty():
			_apply_snap(_pending_snap)
		return
	_resolve_local()
	if online:
		Net.send({"t": "snap", "px": sim.px, "py": sim.py, "al": sim.alive, "turn": turn,
			"groups": groups, "hand": hand, "kitchen": kitchen, "brk": is_break, "msg": msg,
			"fin": _fin, "gs": gs})


func _resolve_local() -> void:
	var sh := turn
	var o := _other(sh)
	var g: String = groups[sh]
	var j := Rules.judge(sim, g, on_eight, is_break)
	var was_break := is_break
	is_break = false
	kitchen = false
	var k := "p" if sh == "player" else "a"
	var pn: int = j.pots.size() + (1 if j.eight else 0)
	gs[k + "s"] += 1
	gs[k + "p"] += pn
	if j.foul:
		gs[k + "f"] += 1
	if k == "p":
		if not j.foul and pn > 0:
			gs.run += 1
			gs.best = maxi(gs.best, gs.run)
		else:
			gs.run = 0
	if j.eight and not was_break:
		var win: bool = not j.foul and on_eight
		_finish(sh if win else o, sh, win)
		return
	msg = ""
	if j.eight:
		sim.respot(8)
		msg = L.t("The 8 goes back on the spot")
	if not j.foul and g == "" and not was_break and j.pots.size() > 0:
		g = Rules.group_of(j.pots[0])
		groups[sh] = g
		groups[o] = Rules.other_group(g)
		j = Rules.judge(sim, g, on_eight, false)
	var keep: bool = not j.foul and ((g != "" and j.own > 0) or (was_break and j.pots.size() > 0))
	var line := ""
	if j.foul:
		hand = true
		turn = o
		line = L.t("Foul: {0}", [L.t(j.why)])
		Sfx.vibrate(180)
	elif keep:
		line = L.t("{0} goes again", [_name(sh)]) if duo else (L.t("Pot! Go again") if sh == "player" else L.t("The AI potted one"))
	else:
		turn = o
		line = L.t("{0}'s turn", [_name(o)]) if duo else (L.t("Your turn") if o == "player" else L.t("AI's turn"))
	msg = line if msg == "" else msg + " · " + line
	_begin()


func _finish(win_side: String, shooter: String, clean: bool) -> void:
	_fin = [win_side, shooter, clean]
	winner = win_side
	state = St.OVER
	var player_won := winner == "player"
	Sfx.play("win" if (player_won or duo) else "lose")
	Sfx.vibrate(300)
	_save_log("Won" if player_won else "Lost")
	ui.update_hud()
	if replay != null:
		_say(L.t("Replay finished"))
		return
	var k := "cleanly" if clean else "too early or with a foul"
	var reason: String
	if duo:
		reason = L.t("{0} potted the 8 " + k, [_name(shooter)])
	elif shooter == "player":
		reason = L.t("You potted the 8 " + k)
	else:
		reason = L.t("The AI potted the 8 " + k)
	if online:
		_say(L.t("You win!") if winner == my_side else L.t("Opponent wins!"))
	else:
		_say(L.t("{0} wins!", [_name(winner)]) if duo else (L.t("You win!") if player_won else L.t("AI wins!")))
	in_game = false
	if not duo:
		var s := Store.stats
		var lv := str(Store.cfg.level)
		s.games += 1
		if player_won:
			s.wins += 1
			s.levels[lv][0] += 1
			s.streak = maxi(1, s.streak + 1)
		else:
			s.losses += 1
			s.levels[lv][1] += 1
			s.streak = mini(-1, s.streak - 1)
		s.best_streak = maxi(s.best_streak, s.streak)
		s.pots += gs.pp
		s.shots += gs.ps
		s.fouls += gs.pf
		Store.save()
	var tiles := [
		[_fmt_time(Time.get_ticks_msec() - gs.t0), "Duration"],
		["%d : %d" % [gs.ps, gs["as"]], "Shots (P1 : P2)" if duo else "Shots (you : AI)"],
		["%d : %d" % [gs.pp, gs.ap], "Potted"],
		["%d : %d" % [gs.pf, gs.af], "Fouls"],
		[str(gs.best), "Best run"],
		[_pct(gs.pp, gs.ps), "Pots per shot"],
	]
	var title := L.t("{0} WINS", [_name(winner).to_upper()]) if duo else (L.t("VICTORY!") if player_won else L.t("DEFEAT"))
	var good := player_won or duo
	if online:
		good = winner == my_side
		title = L.t("VICTORY!") if good else L.t("DEFEAT")
	get_tree().create_timer(1.4).timeout.connect(func():
		if state == St.OVER and replay == null:
			ui.show_result(title, good, reason, tiles))


func _save_log(res: String) -> void:
	if rec == null or replay != null:
		return
	if rec.shots.size() >= 3:
		rec.res = res
		Store.add_replay(rec)
	rec = null


# ---------- online ----------

func online_begin() -> void:
	online = true
	my_side = "player"
	ui.close_all()
	start_game(true, true)


func rematch() -> void:
	if online:
		online_again()
	else:
		start_game(duo)


func online_again() -> void:
	if not online:
		return
	if Net.host:
		ui.close_all()
		start_game(true, true)
	else:
		Net.send({"t": "again"})
		ui.toast(L.t("Waiting for the host…"))


func _leave_online() -> void:
	if online or Net.active:
		online = false
		my_side = "player"
		Net.stop()


func _on_lost() -> void:
	if not online:
		return
	_leave_online()
	_cancel_ai()
	in_game = false
	state = St.IDLE
	ui.open_menu()
	ui.toast(L.t("Connection lost"))


func _on_net(d: Dictionary) -> void:
	match d.get("t", ""):
		"rack":
			online = true
			my_side = "ai"
			_rack_nums = d.nums
			ui.close_all()
			start_game(true, true)
		"again":
			if online and Net.host:
				online_again()
		"shot":
			if state != St.WAIT:
				return
			if d.pl != null:
				sim.place_cue(d.pl[0], d.pl[1])
				place_rec = d.pl
				hand = false
			aim = d.a
			power = d.p
			ui.set_power(power)
			_remote_shot = true
			_strike()
		"snap":
			if _wait_snap:
				_apply_snap(d)
			else:
				_pending_snap = d


func _apply_snap(d: Dictionary) -> void:
	_pending_snap = {}
	_wait_snap = false
	sim.px = d.px
	sim.py = d.py
	sim.alive = d.al
	sim.vx.fill(0.0)
	sim.vy.fill(0.0)
	turn = d.turn
	groups = d.groups
	hand = d.hand
	kitchen = d.kitchen
	is_break = d.brk
	msg = d.msg
	for k in d.gs:
		if k != "t0":
			gs[k] = d.gs[k]
	if not d.fin.is_empty():
		_finish(d.fin[0], d.fin[1], d.fin[2])
	else:
		_begin()


# ---------- replays ----------

func _next_rp() -> void:
	var shots: Array = replay.log.shots
	if replay.i >= shots.size():
		state = St.OVER
		_say(L.t("Replay finished"))
		return
	rp = {"t": 0, "s": shots[replay.i]}
	replay.i += 1
	turn = rp.s.t
	state = St.RP
	ui.update_hud()
	ui.toast("%s %d/%d" % [L.t("Replay"), replay.i, shots.size()])


# ---------- AI ----------

func _ai_start() -> void:
	var snap := sim.copy()
	var g: String = groups["ai"]
	var oe := g != "" and Rules.group_left(sim, g) == 0
	var args := [snap, g, oe, hand, kitchen, is_break, int(Store.cfg.level), rng.randi()]
	_ai_task = WorkerThreadPool.add_task(func(): _ai_res = AI.think.callv(args))


func _cancel_ai() -> void:
	if _ai_task >= 0:
		WorkerThreadPool.wait_for_task_completion(_ai_task)
		_ai_task = -1
	_ai_res = {}


# ---------- tick ----------

func _physics_process(_d: float) -> void:
	if paused or state == St.IDLE:
		return
	for k in spd:
		_tick()


func _tick() -> void:
	match state:
		St.AI:
			_ai_t += 1
			if _ai_t == 20:
				_ai_start()
			elif _ai_t > 20:
				if _ai_task >= 0:
					if not WorkerThreadPool.is_task_completed(_ai_task):
						return
					WorkerThreadPool.wait_for_task_completion(_ai_task)
					_ai_task = -1
					if _ai_res.has("place"):
						var p: Vector2 = _ai_res.place
						sim.place_cue(p.x, p.y)
						place_rec = [p.x, p.y]
						hand = false
				_ai_go += 1
				aim += angle_difference(aim, _ai_res.a) * 0.07
				power += (_ai_res.p - power) * 0.06
				ui.set_power(power)
				if _ai_go > 70:
					aim = _ai_res.a
					power = _ai_res.p
					_strike()
		St.RP:
			rp.t += 1
			var s: Dictionary = rp.s
			if rp.t == 2 and s.get("pl") != null:
				sim.place_cue(s.pl[0], s.pl[1])
			if rp.t > 2 and rp.t <= 50:
				aim += angle_difference(aim, s.a) * 0.12
				power += (s.p - power) * 0.1
				ui.set_power(power)
			elif rp.t > 50:
				aim = s.a
				power = s.p
				_strike()
		St.STRIKE:
			_strike_t += 0.12
			if _strike_t >= 1.0:
				_shoot(aim, maxf(0.05, power))
		St.MOVING:
			sim.step()
			for e in sim.events:
				match e[0]:
					Sim.Ev.BALL:
						Sfx.play("ball", e[1] / 12.0)
						Sfx.vibrate(int(6 + minf(1.0, e[1] / 12.0) * 20))
					Sim.Ev.RAIL:
						Sfx.play("rail", e[1] / 12.0)
					Sim.Ev.POT:
						Sfx.play("pot")
						Sfx.vibrate(40)
			sim.events.clear()
			if not sim.moving():
				_resolve()


func _process(delta: float) -> void:
	if state == St.AIM and ui.fine != 0:
		ui.fine_t += delta
		# kurzer Tipp = ein kleiner Schritt, danach gedrückt halten = laufend
		if ui.fine_t > 0.3:
			aim += ui.fine * delta * 0.12
	view.sync(sim, delta)
	var cue := Vector2(sim.px[0], sim.py[0])
	var show_stick := sim.alive[0] == 1 and state in [St.AIM, St.AI, St.STRIKE, St.RP]
	var pull := Sim.BR + 3 + power * 95
	if state == St.STRIKE:
		pull = _strike_from + (Sim.BR + 1 - _strike_from) * minf(1.0, _strike_t * _strike_t)
	view.show_stick(show_stick, cue, aim, pull)
	if state == St.AIM:
		view.show_guides(sim, aim)
	else:
		view.hide_guides()
	view.head_line.visible = state == St.PLACE and kitchen
	view.update_camera(follow_cam and show_stick, cue, aim, delta)


# ---------- input ----------

func _unhandled_input(e: InputEvent) -> void:
	if paused:
		return
	if e is InputEventScreenTouch:
		if e.index == 0:
			_pointer(e.pressed, e.position)
	elif e is InputEventScreenDrag:
		if e.index == 0:
			_move(e.position)
	elif e is InputEventMouse and e.device == InputEvent.DEVICE_ID_EMULATION:
		return
	elif e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT:
		_pointer(e.pressed, e.position)
	elif e is InputEventMouseMotion:
		_move(e.position)


func _pointer(pressed: bool, pos: Vector2) -> void:
	if not pressed:
		_drag = null
		_grab = null
		return
	_drag = pos
	_grab = null
	if state == St.PLACE:
		# Only a touch on (or right next to) the cue ball picks it up.
		var t = view.screen_to_table(pos)
		if t != null and (t as Vector2).distance_to(Vector2(sim.px[0], sim.py[0])) < Sim.BR * 4:
			_grab = Vector2(sim.px[0], sim.py[0]) - t


func _move(pos: Vector2) -> void:
	if _drag == null:
		return
	if state == St.AIM:
		var w := get_viewport().get_visible_rect().size.x
		aim += (pos.x - _drag.x) / w * 2.4
	elif state == St.PLACE and _grab != null:
		var t = view.screen_to_table(pos)
		if t != null:
			var p: Vector2 = t + _grab
			p.x = clampf(p.x, Sim.PL + Sim.BR, Sim.PR - Sim.BR)
			p.y = clampf(p.y, (Sim.HEAD_Y if kitchen else Sim.PT) + Sim.BR, Sim.PB - Sim.BR)
			if sim.free_spot(p.x, p.y):
				sim.place_cue(p.x, p.y)
	_drag = pos


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_GO_BACK_REQUEST and ui:
		ui.back()
	elif what == NOTIFICATION_APPLICATION_PAUSED:
		Store.save()


# ---------- helpers ----------

func _aim_near() -> void:
	var tg := AI.targets(sim, groups[turn])
	if tg.is_empty():
		return
	var cue := Vector2(sim.px[0], sim.py[0])
	var best: int = tg[0]
	for i in tg:
		if cue.distance_to(Vector2(sim.px[i], sim.py[i])) < cue.distance_to(Vector2(sim.px[best], sim.py[best])):
			best = i
	aim = (Vector2(sim.px[best], sim.py[best]) - cue).angle()


func _other(side: String) -> String:
	return "ai" if side == "player" else "player"


func _name(side: String) -> String:
	if online:
		return L.t("You") if side == my_side else L.t("Opponent")
	if duo:
		return L.t("Player 1") if side == "player" else L.t("Player 2")
	return L.t("You") if side == "player" else L.t("AI")


func _say(t: String) -> void:
	msg = t
	if t != "":
		ui.toast(t)


func _fmt_time(ms: int) -> String:
	var s := ms / 1000
	return "%d:%02d" % [s / 60, s % 60]


func _pct(a: int, b: int) -> String:
	return "%d %%" % roundi(100.0 * a / b) if b > 0 else "–"


func set_level(lv: int) -> void:
	var changed := lv != int(Store.cfg.level)
	Store.cfg.level = lv
	Store.save()
	if changed and in_game and not duo:
		restart()

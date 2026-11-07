class_name GameUI
extends CanvasLayer
## All 2D interface: HUD, menus and overlays, built in code.

const C_BG := Color("14110f")
const C_CARD := Color("2a241f")
const C_BTN := Color("42382f")
const C_GREEN := Color("2fb36f")
const C_RED := Color("e2361f")
const C_GOLD := Color("ffe678")
const C_DIM := Color("b8b0a8")

var game
var fine := 0
var fine_t := 0.0
var root: Control
var hud: Control
var toast_lb: Label
var chip_p: PanelContainer
var chip_a: PanelContainer
var power_slider: HSlider
var shoot_btn: Button
var controls: Control
var rp_bar: Control
var rp_speed: Button
var restart_btn: Button
var sound_btn: Button
var view_btn: Button
var overlays := {}
var stack: Array[String] = []
var _texts := []
var _toast_tw: Tween
var _restart_armed := false
var _tmp := {}
var _result_t := 0.0
var _result_lb: Label
var _result_bar: ProgressBar
var font_black: Font = load("res://assets/fonts/NotoSans-Black.ttf")
var font_bold: Font = load("res://assets/fonts/NotoSans-Bold.ttf")


func _ready() -> void:
	root = Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.theme = _theme()
	root.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	add_child(root)
	_build_hud()
	_build_menu()
	_build_settings()
	_build_online()
	_build_stats()
	_build_replays()
	_build_balls()
	_build_result()
	_build_loading()
	L.changed.connect(_retranslate)
	_retranslate()


func _process(delta: float) -> void:
	if overlays.result.visible and _result_t > 0:
		_result_t -= delta
		_result_bar.value = _result_t / 15.0 * 100
		_result_lb.text = L.t("Back to menu in {0} s", [ceili(maxf(0, _result_t))])
		if _result_t <= 0:
			close_all()
			open_menu()


# ---------- theme & helpers ----------

func _sb(c: Color, r := 16, shadow := 0) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = c
	s.set_corner_radius_all(r)
	s.content_margin_left = 16
	s.content_margin_right = 16
	s.content_margin_top = 10
	s.content_margin_bottom = 10
	if shadow > 0:
		s.shadow_color = Color(0, 0, 0, 0.45)
		s.shadow_size = shadow
		s.shadow_offset = Vector2(0, 3)
	return s


func _theme() -> Theme:
	var t := Theme.new()
	t.default_font = font_bold
	t.default_font_size = 24
	t.set_stylebox("normal", "Button", _sb(C_BTN, 18, 4))
	t.set_stylebox("hover", "Button", _sb(C_BTN.lightened(0.08), 18, 4))
	t.set_stylebox("pressed", "Button", _sb(C_BTN.darkened(0.2), 18, 1))
	t.set_stylebox("focus", "Button", StyleBoxEmpty.new())
	t.set_stylebox("disabled", "Button", _sb(C_BTN.darkened(0.3), 18))
	t.set_color("font_color", "Button", Color.WHITE)
	t.set_color("font_pressed_color", "Button", Color.WHITE)
	t.set_color("font_hover_color", "Button", Color.WHITE)
	t.set_color("font_color", "Label", Color("eeeeee"))
	t.set_stylebox("panel", "PanelContainer", _sb(C_CARD, 22, 12))
	var grab := StyleBoxFlat.new()
	grab.bg_color = Color.WHITE
	t.set_stylebox("slider", "HSlider", _sb(Color(1, 1, 1, 0.15), 6))
	var area := _sb(C_GOLD, 6)
	t.set_stylebox("grabber_area", "HSlider", area)
	t.set_stylebox("grabber_area_highlight", "HSlider", area)
	var knob := _circle_tex(36, Color.WHITE)
	t.set_icon("grabber", "HSlider", knob)
	t.set_icon("grabber_highlight", "HSlider", knob)
	t.set_stylebox("background", "ProgressBar", _sb(Color(1, 1, 1, 0.12), 4))
	t.set_stylebox("fill", "ProgressBar", _sb(C_GREEN, 4))
	return t


func _circle_tex(d: int, c: Color) -> ImageTexture:
	var img := Image.create(d, d, false, Image.FORMAT_RGBA8)
	var r := d / 2.0
	for y in d:
		for x in d:
			var dist := Vector2(x + 0.5 - r, y + 0.5 - r).length()
			img.set_pixel(x, y, Color(c, clampf(r - dist, 0.0, 1.0)))
	return ImageTexture.create_from_image(img)


func _tr(node: Control, key: String) -> Control:
	_texts.append([node, key])
	node.text = L.t(key)
	return node


func _retranslate() -> void:
	for p in _texts:
		if is_instance_valid(p[0]):
			p[0].text = L.t(p[1])
	if game:
		update_hud()
	_refresh_open()


func _btn(key: String, cb: Callable, color := C_BTN, translate := true) -> Button:
	var b := Button.new()
	if translate:
		_tr(b, key)
	else:
		b.text = key
	if color != C_BTN:
		b.add_theme_stylebox_override("normal", _sb(color, 18, 4))
		b.add_theme_stylebox_override("hover", _sb(color.lightened(0.08), 18, 4))
		b.add_theme_stylebox_override("pressed", _sb(color.darkened(0.2), 18, 1))
	b.pressed.connect(func():
		Sfx.play("click")
		cb.call())
	b.custom_minimum_size = Vector2(0, 64)
	return b


func _label(key: String, size := 24, color := Color("eeeeee"), translate := true) -> Label:
	var l := Label.new()
	if translate:
		_tr(l, key)
	else:
		l.text = key
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return l


## Labels inside rows have no width of their own; wrapping would break them per letter.
func _nowrap(l: Label) -> Label:
	l.autowrap_mode = TextServer.AUTOWRAP_OFF
	l.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	return l


func _overlay(name: String) -> VBoxContainer:
	var o := Control.new()
	o.set_anchors_preset(Control.PRESET_FULL_RECT)
	o.visible = false
	root.add_child(o)
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.7)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	o.add_child(dim)
	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	o.add_child(center)
	var card := PanelContainer.new()
	card.custom_minimum_size = Vector2(600, 0)
	center.add_child(card)
	var m := MarginContainer.new()
	for side in ["left", "right", "top", "bottom"]:
		m.add_theme_constant_override("margin_" + side, 18)
	card.add_child(m)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 12)
	m.add_child(v)
	overlays[name] = o
	return v


func _row(parent: Node, sep := 10) -> HBoxContainer:
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", sep)
	parent.add_child(h)
	return h


func _grow(c: Control, ratio := 1.0) -> Control:
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	c.size_flags_stretch_ratio = ratio
	return c


# ---------- overlay stack ----------

func open(name: String) -> void:
	for k in overlays:
		overlays[k].visible = false
	if not stack.has(name):
		stack.append(name)
	overlays[name].visible = true
	_refresh(name)
	_update_pause()


func close_top() -> void:
	if stack.is_empty():
		return
	overlays[stack.pop_back()].visible = false
	if not stack.is_empty():
		overlays[stack.back()].visible = true
		_refresh(stack.back())
	_update_pause()


func close_all() -> void:
	for k in overlays:
		overlays[k].visible = false
	stack.clear()
	_update_pause()


func back() -> void:
	if not stack.is_empty():
		if stack.back() == "menu":
			if game.in_game:
				close_all()
			else:
				get_tree().quit()
		elif stack.back() == "result":
			close_all()
			open_menu()
		else:
			close_top()
	elif game.replay != null:
		game.stop_replay()
	else:
		open_menu()


func _go(action: Callable) -> void:
	close_all()
	action.call()


func _set_tmp(k: String, v) -> void:
	_tmp[k] = v
	_fill_settings()


func open_menu() -> void:
	close_all()
	open("menu")


func _update_pause() -> void:
	if game:
		game.paused = not stack.is_empty()
	hud.visible = stack.is_empty() or stack.back() == "balls"


func _refresh(name: String) -> void:
	match name:
		"menu": _fill_menu()
		"settings": _fill_settings()
		"online": _fill_online()
		"host": _fill_host()
		"lobbies": _load_lobbies()
		"stats": _fill_stats()
		"replays": _fill_replays()
		"balls": _fill_balls()


func _refresh_open() -> void:
	if not stack.is_empty():
		_refresh(stack.back())


# ---------- HUD ----------

func _build_hud() -> void:
	hud = Control.new()
	hud.set_anchors_preset(Control.PRESET_FULL_RECT)
	hud.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(hud)
	var safe := MarginContainer.new()
	safe.set_anchors_preset(Control.PRESET_FULL_RECT)
	safe.mouse_filter = Control.MOUSE_FILTER_IGNORE
	for side in ["left", "right"]:
		safe.add_theme_constant_override("margin_" + side, 14)
	safe.add_theme_constant_override("margin_top", 40)
	safe.add_theme_constant_override("margin_bottom", 24)
	hud.add_child(safe)
	var col := VBoxContainer.new()
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	safe.add_child(col)
	var top := _row(col)
	top.add_child(_btn("Menu", open_menu))
	top.add_child(_btn("Balls", func(): open("balls")))
	top.add_child(_grow(Control.new()))
	top.get_child(2).mouse_filter = Control.MOUSE_FILTER_IGNORE
	restart_btn = _btn("Restart", _restart)
	top.add_child(restart_btn)
	var chips := _row(col, 12)
	chips.alignment = BoxContainer.ALIGNMENT_CENTER
	chip_p = _chip()
	chip_a = _chip()
	chips.add_child(chip_p)
	chips.add_child(chip_a)
	toast_lb = Label.new()
	toast_lb.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	toast_lb.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	toast_lb.add_theme_font_size_override("font_size", 26)
	toast_lb.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.8))
	toast_lb.add_theme_constant_override("outline_size", 8)
	toast_lb.modulate.a = 0
	col.add_child(toast_lb)
	var spacer := Control.new()
	spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	col.add_child(spacer)
	controls = VBoxContainer.new()
	controls.add_theme_constant_override("separation", 10)
	col.add_child(controls)
	var r1 := _row(controls)
	r1.add_child(_hold_btn("‹", -1))
	r1.add_child(_hold_btn("›", 1))
	var gap := _grow(Control.new())
	gap.mouse_filter = Control.MOUSE_FILTER_IGNORE
	r1.add_child(gap)
	view_btn = _btn("View", func(): game.follow_cam = not game.follow_cam)
	r1.add_child(view_btn)
	sound_btn = _btn("", _toggle_sound, C_BTN, false)
	r1.add_child(sound_btn)
	var r2 := _row(controls, 14)
	power_slider = HSlider.new()
	power_slider.min_value = 5
	power_slider.max_value = 100
	power_slider.value = 55
	power_slider.custom_minimum_size = Vector2(0, 64)
	power_slider.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	power_slider.value_changed.connect(func(v):
		if game.state == game.St.AIM:
			game.power = v / 100.0)
	r2.add_child(_grow(power_slider))
	shoot_btn = _btn("Shoot", func(): game.shoot_pressed(), C_RED)
	shoot_btn.custom_minimum_size = Vector2(200, 80)
	shoot_btn.add_theme_font_override("font", font_black)
	shoot_btn.add_theme_font_size_override("font_size", 30)
	r2.add_child(shoot_btn)
	rp_bar = _row(col)
	rp_bar.visible = false
	rp_bar.add_child(_btn("Stop", func(): game.stop_replay()))
	rp_speed = _btn("x1", _cycle_speed, C_BTN, false)
	rp_bar.add_child(rp_speed)
	_update_sound_btn()


func _chip() -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", _sb(Color(1, 1, 1, 0.08), 22))
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", 8)
	p.add_child(h)
	var name := Label.new()
	name.add_theme_font_override("font", font_black)
	h.add_child(name)
	var ball := TextureRect.new()
	ball.custom_minimum_size = Vector2(28, 28)
	ball.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	ball.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	ball.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	h.add_child(ball)
	var n := Label.new()
	h.add_child(n)
	return p


func _fill_chip(chip: PanelContainer, side: String) -> void:
	var h := chip.get_child(0)
	h.get_child(0).text = game._name(side)
	var g: String = game.groups[side]
	var ball: TextureRect = h.get_child(1)
	var n: Label = h.get_child(2)
	if g == "":
		ball.texture = null
		n.text = "–"
	else:
		var left := Rules.group_left(game.sim, g)
		ball.texture = _ball_icon(1 if g == "solid" else 9, 28) if left > 0 else _ball_icon(8, 28)
		n.text = str(left) if left > 0 else "8"
	var active: bool = game.turn == side
	chip.add_theme_stylebox_override("panel", _sb(Color(C_GREEN, 0.25) if active else Color(1, 1, 1, 0.08), 22))
	var sb: StyleBoxFlat = chip.get_theme_stylebox("panel")
	sb.border_color = C_GREEN if active else Color(0, 0, 0, 0)
	sb.set_border_width_all(3)


var _icon_cache := {}


func _ball_icon(n: int, d: int) -> ImageTexture:
	var key := "%d_%d" % [n, d]
	if _icon_cache.has(key):
		return _icon_cache[key]
	d *= 3  # drawn large and scaled down, so it stays sharp on high-density screens
	var col := Color(TableView.BALL_COLORS[(n - 1) % 8 + 1]) if n > 0 else Color("f2f2ec")
	var white := Color("f2f2ec")
	var img := Image.create(d, d, false, Image.FORMAT_RGBA8)
	var r := d / 2.0
	for y in d:
		for x in d:
			var p := Vector2(x + 0.5 - r, y + 0.5 - r)
			var a := clampf(r - p.length(), 0.0, 1.0)
			var c := col
			if n > 8 and absf(p.y) > r * 0.45:
				c = white
			if n > 0 and p.length() < r * 0.38:
				c = white
			var light := 1.0 - clampf((p + Vector2(r, r) * 0.35).length() / (r * 2.2), 0.0, 1.0) * 0.45
			img.set_pixel(x, y, Color(c.r * light, c.g * light, c.b * light, a))
	img.generate_mipmaps()
	var t := ImageTexture.create_from_image(img)
	_icon_cache[key] = t
	return t


func _hold_btn(t: String, dir: int) -> Button:
	var b := Button.new()
	b.text = t
	b.custom_minimum_size = Vector2(84, 64)
	b.add_theme_font_size_override("font_size", 40)
	b.focus_mode = Control.FOCUS_NONE
	b.button_down.connect(func():
		fine = dir
		fine_t = 0.0
		if game and game.state == game.St.AIM:
			game.aim += dir * 0.012)
	b.button_up.connect(func(): fine = 0)
	return b


func update_hud() -> void:
	if not game:
		return
	_fill_chip(chip_p, "player")
	_fill_chip(chip_a, "ai")
	var my_turn: bool = game.state == game.St.AIM or game.state == game.St.PLACE
	shoot_btn.disabled = not my_turn
	controls.modulate.a = 1.0 if my_turn else 0.45
	if game.state == game.St.PLACE:
		shoot_btn.text = "OK"
	else:
		shoot_btn.text = L.t("Shoot")
	if not overlays.is_empty() and overlays.balls.visible:
		_fill_balls()


func set_power(p: float) -> void:
	power_slider.set_value_no_signal(p * 100)


func set_replay_mode(on: bool) -> void:
	controls.visible = not on
	rp_bar.visible = on
	restart_btn.visible = not on
	rp_speed.text = "x1"


func toast(t: String) -> void:
	toast_lb.text = t
	if _toast_tw:
		_toast_tw.kill()
	_toast_tw = create_tween()
	toast_lb.modulate.a = 0.0
	_toast_tw.tween_property(toast_lb, "modulate:a", 1.0, 0.2)
	_toast_tw.tween_interval(2.6)
	_toast_tw.tween_property(toast_lb, "modulate:a", 0.0, 0.4)


func _restart() -> void:
	if _restart_armed:
		_restart_armed = false
		restart_btn.text = L.t("Restart")
		game.restart()
		return
	_restart_armed = true
	restart_btn.text = L.t("Sure?")
	get_tree().create_timer(2.5).timeout.connect(func():
		_restart_armed = false
		restart_btn.text = L.t("Restart"))


func _toggle_sound() -> void:
	Store.cfg.mute = not Store.cfg.mute
	Store.save()
	_update_sound_btn()


func _update_sound_btn() -> void:
	sound_btn.text = L.t("Sound") + (" " + L.t("Off") if Store.cfg.mute else "")


func _cycle_speed() -> void:
	game.spd = 2 if game.spd == 1 else (4 if game.spd == 2 else 1)
	rp_speed.text = "x%d" % game.spd


# ---------- main menu ----------

var _menu_sub: Label
var _menu_continue: Button


func _build_menu() -> void:
	var v := _overlay("menu")
	var logo := TextureRect.new()
	logo.texture = load("res://icon.svg")
	logo.custom_minimum_size = Vector2(170, 170)
	logo.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	logo.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	v.add_child(logo)
	var title := _label("CUE DUEL", 64, C_GOLD, false)
	title.add_theme_font_override("font", font_black)
	v.add_child(title)
	_menu_sub = _label("", 22, C_DIM, false)
	v.add_child(_menu_sub)
	_menu_continue = _btn("Continue", func(): close_all())
	v.add_child(_menu_continue)
	var play := _btn("Play", func(): _go(func(): game.start_game(false)), C_GREEN)
	play.custom_minimum_size.y = 84
	play.add_theme_font_override("font", font_black)
	play.add_theme_font_size_override("font_size", 32)
	v.add_child(play)
	v.add_child(_btn("2 players", func(): _go(func(): game.start_game(true))))
	v.add_child(_btn("Online", func(): open("online")))
	var r := _row(v)
	r.add_child(_grow(_btn("Replays", func(): open("replays"))))
	r.add_child(_grow(_btn("Statistics", func(): open("stats"))))
	v.add_child(_btn("Settings", func(): open("settings")))


func _fill_menu() -> void:
	var s := Store.stats
	_menu_sub.text = L.t("{0} games · {1} wins", [s.games, _pct(s.wins, s.games)]) if s.games > 0 else L.t("8-ball against the computer")
	_menu_continue.visible = game.in_game and game.replay == null


func _pct(a, b) -> String:
	return "%d %%" % roundi(100.0 * a / b) if b > 0 else "–"


# ---------- loading ----------

var _loading: Control


func _build_loading() -> void:
	_loading = ColorRect.new()
	_loading.color = Color("14110f")
	_loading.set_anchors_preset(Control.PRESET_FULL_RECT)
	_loading.mouse_filter = Control.MOUSE_FILTER_STOP
	root.add_child(_loading)
	var c := CenterContainer.new()
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	_loading.add_child(c)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 14)
	c.add_child(v)
	var logo := TextureRect.new()
	logo.texture = load("res://icon.svg")
	logo.custom_minimum_size = Vector2(140, 140)
	logo.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	logo.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	v.add_child(logo)
	v.add_child(_label("Loading…", 28, C_DIM))
	if game.view._apply_mats:
		_loading.visible = false
	else:
		game.view.ready_done.connect(_hide_loading)


func _hide_loading() -> void:
	var tw := create_tween()
	tw.tween_property(_loading, "modulate:a", 0.0, 0.25)
	tw.tween_callback(func(): _loading.visible = false)


# ---------- online ----------

var _on_status: Label
var _on_ip: LineEdit
var _on_pw: LineEdit
var _host_name: LineEdit
var _host_pw: LineEdit
var _host_pub := true
var _host_pub_btn: Button
var _host_status: Label
var _lob_list: VBoxContainer
var _lob_status: Label
var _pw_in: LineEdit
var _pw_title: Label
var _pick := {}


func _build_online() -> void:
	var v := _overlay("online")
	var h := _label("Online game", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	v.add_child(_btn("Find games", func(): open("lobbies"), C_GREEN))
	v.add_child(_btn("Host a game", func(): open("host")))
	v.add_child(_label("Join with address", 22, C_DIM))
	_on_ip = LineEdit.new()
	_on_ip.placeholder_text = L.t("Address or room code")
	_on_ip.custom_minimum_size.y = 64
	_on_ip.add_theme_font_size_override("font_size", 28)
	v.add_child(_on_ip)
	_on_pw = _field(v, "Password (if any)")
	v.add_child(_btn("Join", _on_join))
	_on_status = _label("", 20, C_DIM, false)
	_on_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(_on_status)
	v.add_child(_btn("Back", _on_back, C_GREEN))
	_build_host()
	_build_lobbies()
	_build_pw()
	Net.status.connect(_on_net_status)
	Net.connected.connect(_on_net_connected)
	Net.failed.connect(_on_net_failed)
	Net.denied.connect(_on_net_denied)
	Net.lobbies_loaded.connect(_on_lobbies)
	Net.lobbies_failed.connect(_on_lobbies_failed)


func _field(parent: Node, hint: String) -> LineEdit:
	var e := LineEdit.new()
	e.placeholder_text = L.t(hint)
	e.custom_minimum_size.y = 64
	e.add_theme_font_size_override("font_size", 28)
	parent.add_child(e)
	return e


func _build_host() -> void:
	var v := _overlay("host")
	var h := _label("Host a game", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	_host_name = _field(v, "Game name")
	_host_name.max_length = 24
	_host_pw = _field(v, "Password (optional)")
	_host_pw.max_length = 24
	_host_pub_btn = _btn("Show in the list", func():
		_host_pub = not _host_pub
		_fill_host())
	v.add_child(_host_pub_btn)
	v.add_child(_btn("Create game", _on_create, C_GREEN))
	_host_status = _label("", 20, C_DIM, false)
	_host_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(_host_status)
	v.add_child(_btn("Back", _on_back, C_BTN))


func _fill_host() -> void:
	_mark(_host_pub_btn, _host_pub)
	if not Net.active:
		_host_status.text = ""


func _on_create() -> void:
	var nm := _host_name.text.strip_edges()
	if nm == "":
		nm = "Cue Duel"
	if Net.host_game(nm, _host_pw.text, _host_pub):
		_host_status.text = Net.host_text()
	else:
		_host_status.text = L.t("Could not connect")


func _build_lobbies() -> void:
	var v := _overlay("lobbies")
	var h := _label("Find games", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	var sc := ScrollContainer.new()
	sc.custom_minimum_size = Vector2(560, 520)
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	v.add_child(sc)
	_lob_list = VBoxContainer.new()
	_lob_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_lob_list.add_theme_constant_override("separation", 8)
	sc.add_child(_lob_list)
	_lob_status = _label("", 20, C_DIM, false)
	v.add_child(_lob_status)
	var r := _row(v)
	r.add_child(_grow(_btn("Refresh", _load_lobbies)))
	r.add_child(_grow(_btn("Back", _on_back, C_GREEN)))


func _load_lobbies() -> void:
	for c in _lob_list.get_children():
		c.queue_free()
	_lob_status.text = L.t("Loading…")
	Net.fetch_lobbies()


func _on_lobbies(list: Array) -> void:
	for c in _lob_list.get_children():
		c.queue_free()
	var shown := 0
	for l in list:
		if not l is Dictionary or int(l.get("players", 1)) >= 2:
			continue
		shown += 1
		var row := _row(_lob_list, 8)
		var locked: bool = bool(l.get("locked", false))
		var ver := str(l.get("version", "")).replace("-BETA", "")
		var nm := _label(str(l.get("name", "")).left(24) + ("  " + ver if ver != "" else ""), 24, Color.WHITE, false)
		nm.autowrap_mode = TextServer.AUTOWRAP_OFF
		nm.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		nm.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		nm.custom_minimum_size.x = 160
		nm.clip_text = true
		row.add_child(nm)
		var tag := _label("Password" if locked else "Open", 18, Color("f5b041") if locked else C_DIM)
		tag.autowrap_mode = TextServer.AUTOWRAP_OFF
		tag.custom_minimum_size.x = 100
		row.add_child(tag)
		var item: Dictionary = l
		var jb := _btn("Join", func(): _pick_lobby(item))
		jb.custom_minimum_size = Vector2(130, 64)
		row.add_child(jb)
	_lob_status.text = "" if shown > 0 else L.t("No games found")


func _on_lobbies_failed() -> void:
	for c in _lob_list.get_children():
		c.queue_free()
	_lob_status.text = L.t("The list could not be reached")


func _pick_lobby(l: Dictionary) -> void:
	_pick = l
	if bool(l.get("locked", false)):
		_pw_title.text = str(l.get("name", ""))
		_pw_in.text = ""
		open("password")
	else:
		_join_pick("")


func _join_pick(pw: String) -> void:
	var port := int(_pick.get("port", Net.PORT))
	var ips := []
	if bool(_pick.get("same_net", false)) and _pick.get("lans") is Array:
		ips = _pick.lans.duplicate()
	if bool(_pick.get("open", false)) or ips.is_empty():
		ips.append(str(_pick.get("ip", "")))
	if Net.join_any(ips, port, pw, str(_pick.get("id", ""))):
		_lob_status.text = L.t("Connecting…")
	else:
		_lob_status.text = L.t("Could not connect")


func _build_pw() -> void:
	var v := _overlay("password")
	var h := _label("Password", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	_pw_title = _label("", 24, C_DIM, false)
	v.add_child(_pw_title)
	_pw_in = _field(v, "Password")
	var r := _row(v)
	r.add_child(_grow(_btn("Back", close_top)))
	r.add_child(_grow(_btn("Join", func():
		var pw := _pw_in.text
		close_top()
		_join_pick(pw), C_GREEN)))


func _on_join() -> void:
	var ip := _on_ip.text.strip_edges()
	if ip == "":
		return
	var ok: bool
	if ip.length() == 8 and ip.to_lower().is_valid_hex_number():
		ok = Net.join_any([], Net.PORT, _on_pw.text, ip.to_lower())
	else:
		ok = Net.join_any([ip], Net.PORT, _on_pw.text)
	_on_status.text = L.t("Connecting…") if ok else L.t("Could not connect")


func _on_back() -> void:
	Net.stop()
	close_top()


func _set_net_status(t: String) -> void:
	_on_status.text = t
	_host_status.text = t
	_lob_status.text = t


func _on_net_status(t: String) -> void:
	_host_status.text = t


func _on_net_connected() -> void:
	if Net.host:
		game.online_begin()
	else:
		_set_net_status(L.t("Connected, waiting for the host…"))


func _on_net_failed() -> void:
	_set_net_status(L.t("Could not connect"))


func _on_net_denied() -> void:
	_set_net_status(L.t("Wrong password"))


func _fill_online() -> void:
	if not Net.active:
		_on_status.text = ""


# ---------- settings ----------

var _lv_btns := []
var _lv_desc: Label
var _lv_warn: Label
var _vib_btns := []
var _lang_btns := []
var _gfx_btns := []
const DESC := ["Aims poorly, often picks hard shots and slips up a lot.",
	"Aims decently and picks good shots, but slips up now and then.",
	"Very accurate and thinks about where the cue ball ends up.",
	"Almost flawless and plays position. Good luck!"]


func _build_settings() -> void:
	var v := _overlay("settings")
	var h := _label("Settings", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	v.add_child(_label("AI level", 22, C_DIM))
	var r := _row(v, 8)
	for i in 4:
		var b := _btn(["Easy", "Medium", "Hard", "Pro"][i], func(): _set_tmp("level", i + 1))
		r.add_child(_grow(b))
		_lv_btns.append(b)
	_lv_desc = _label("", 20, C_DIM, false)
	v.add_child(_lv_desc)
	_lv_warn = _label("A new AI level starts a new game.", 20, Color("f5b041"))
	v.add_child(_lv_warn)
	v.add_child(_label("Vibration", 22, C_DIM))
	var r2 := _row(v, 8)
	for i in 2:
		var b2 := _btn(["Off", "On"][i], func():
			_tmp.haptic = i == 1
			_fill_settings()
			if i == 1:
				Input.vibrate_handheld(40))
		r2.add_child(_grow(b2))
		_vib_btns.append(b2)
	v.add_child(_label("Graphics", 22, C_DIM))
	var rg := _row(v, 8)
	for i in 2:
		var bg := _btn(["Low", "High"][i], func():
			_tmp.quality = i
			_fill_settings())
		rg.add_child(_grow(bg))
		_gfx_btns.append(bg)
	v.add_child(_label("Language", 22, C_DIM))
	var r3 := _row(v, 8)
	for l in L.LANGS:
		var b3 := _btn(L.NAMES[l], func():
			Store.cfg.lang = l
			Store.save()
			L.set_lang(l)
			_update_sound_btn(), C_BTN, false)
		r3.add_child(_grow(b3))
		_lang_btns.append(b3)
	var r4 := _row(v)
	r4.add_child(_grow(_btn("Cancel", close_top)))
	r4.add_child(_grow(_btn("Apply", _apply_settings, C_GREEN)))


func _fill_settings() -> void:
	if not _tmp.has("level"):
		_tmp = {"level": int(Store.cfg.level), "haptic": bool(Store.cfg.haptic), "quality": int(Store.cfg.quality)}
	for i in 2:
		_mark(_gfx_btns[i], _tmp.quality == i)
	for i in 4:
		_mark(_lv_btns[i], _tmp.level == i + 1)
	for i in 2:
		_mark(_vib_btns[i], _tmp.haptic == (i == 1))
	for i in L.LANGS.size():
		_mark(_lang_btns[i], L.lang == L.LANGS[i])
	_lv_desc.text = L.t(DESC[_tmp.level - 1])
	_lv_warn.visible = _tmp.level != int(Store.cfg.level) and game.in_game and not game.duo


func _mark(b: Button, on: bool) -> void:
	var c := C_GREEN if on else C_BTN
	b.add_theme_stylebox_override("normal", _sb(c, 18, 4))
	b.add_theme_stylebox_override("hover", _sb(c.lightened(0.08), 18, 4))


func _apply_settings() -> void:
	Store.cfg.haptic = _tmp.haptic
	Store.cfg.quality = _tmp.quality
	game.view.apply_quality()
	var lv: int = _tmp.level
	_tmp = {}
	close_top()
	game.set_level(lv)
	Store.save()


# ---------- statistics ----------

var _st_grid: GridContainer
var _st_levels: VBoxContainer
var _st_reset: Button
var _st_armed := false


func _build_stats() -> void:
	var v := _overlay("stats")
	var h := _label("Statistics", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	_st_grid = GridContainer.new()
	_st_grid.columns = 3
	_st_grid.add_theme_constant_override("h_separation", 10)
	_st_grid.add_theme_constant_override("v_separation", 10)
	v.add_child(_st_grid)
	v.add_child(_label("Win rate per level", 22, C_DIM))
	_st_levels = VBoxContainer.new()
	v.add_child(_st_levels)
	var r := _row(v)
	_st_reset = _btn("Reset", func():
		if not _st_armed:
			_st_armed = true
			_st_reset.text = L.t("Sure?")
			return
		_st_armed = false
		Store.reset_stats()
		_fill_stats())
	r.add_child(_grow(_st_reset))
	r.add_child(_grow(_btn("Back", close_top, C_GREEN)))


func _tile(parent: Node, value: String, key: String) -> void:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", _sb(Color(1, 1, 1, 0.07), 14))
	p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var v := VBoxContainer.new()
	p.add_child(v)
	var a := _label(value, 30, Color.WHITE, false)
	a.add_theme_font_override("font", font_black)
	v.add_child(a)
	v.add_child(_label(L.t(key), 17, C_DIM, false))
	parent.add_child(p)


func _fill_stats() -> void:
	_st_armed = false
	_st_reset.text = L.t("Reset")
	for c in _st_grid.get_children():
		c.queue_free()
	for c in _st_levels.get_children():
		c.queue_free()
	var s := Store.stats
	for t in [[str(s.games), "Games"], [str(s.wins), "Wins"], [str(s.losses), "Losses"],
			[_pct(s.wins, s.games), "Win rate"], [str(s.best_streak), "Best win streak"], [_pct(s.pots, s.shots), "Pots per shot"]]:
		_tile(_st_grid, t[0], t[1])
	for i in 4:
		var wl: Array = s.levels[str(i + 1)]
		var total: int = int(wl[0]) + int(wl[1])
		var r := _row(_st_levels)
		var name := _label(L.t(["Easy", "Medium", "Hard", "Pro"][i]), 22, Color.WHITE, false)
		name.custom_minimum_size.x = 130
		name.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		r.add_child(_nowrap(name))
		var bar := ProgressBar.new()
		bar.show_percentage = false
		bar.value = 100.0 * wl[0] / total if total > 0 else 0.0
		bar.custom_minimum_size.y = 14
		bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		r.add_child(_grow(bar))
		var pc := _label(L.t("{0} W · {1} L", [int(wl[0]), int(wl[1])]), 20, C_DIM, false)
		pc.custom_minimum_size.x = 150
		r.add_child(_nowrap(pc))


# ---------- replays ----------

var _rp_list: VBoxContainer


func _build_replays() -> void:
	var v := _overlay("replays")
	var h := _label("Replays", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	var sc := ScrollContainer.new()
	sc.custom_minimum_size = Vector2(0, 620)
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	v.add_child(sc)
	_rp_list = VBoxContainer.new()
	_rp_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_rp_list.add_theme_constant_override("separation", 10)
	sc.add_child(_rp_list)
	v.add_child(_btn("Back", close_top, C_GREEN))


func _fill_replays() -> void:
	for c in _rp_list.get_children():
		c.queue_free()
	if Store.replays.is_empty():
		_rp_list.add_child(_label(L.t("No replays yet. Every game is saved automatically."), 22, C_DIM, false))
		return
	for i in Store.replays.size():
		var r: Dictionary = Store.replays[i]
		var row := _row(_rp_list)
		var who := L.t("2 players") if r.get("duo", false) else L.t(["Easy", "Medium", "Hard", "Pro"][clampi(int(r.get("level", 2)), 1, 4) - 1])
		var info := _label("%s\n%s · %s · %s" % [str(r.get("date", "")).replace("T", " "), who, L.t(r.get("res", "Aborted")), L.t("{0} shots", [r.shots.size()])], 19, Color.WHITE, false)
		info.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		row.add_child(_grow(info))
		var play := _btn(L.t("Play"), func(): _go(func(): game.start_replay(r)), C_GREEN, false)
		play.custom_minimum_size.x = 130
		row.add_child(play)
		var del := _btn("×", func():
			Store.replays.remove_at(Store.replays.find(r))
			Store.save()
			_fill_replays(), C_BTN, false)
		del.custom_minimum_size.x = 76
		row.add_child(del)


# ---------- balls ----------

var _balls_box: VBoxContainer


func _build_balls() -> void:
	var v := _overlay("balls")
	var h := _label("Balls", 34)
	h.add_theme_font_override("font", font_black)
	v.add_child(h)
	_balls_box = VBoxContainer.new()
	_balls_box.add_theme_constant_override("separation", 8)
	v.add_child(_balls_box)
	v.add_child(_btn("Close", close_top, C_GREEN))


func _fill_balls() -> void:
	for c in _balls_box.get_children():
		c.queue_free()
	for side in ["player", "ai"]:
		var g: String = game.groups[side]
		var head: String = game._name(side) + (" – " + L.t("Solids" if g == "solid" else "Stripes") if g != "" else "")
		var hl := _label(head, 24, Color.WHITE, false)
		hl.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		_balls_box.add_child(hl)
		var r := _row(_balls_box, 6)
		if g == "":
			r.add_child(_nowrap(_label(L.t("Open table"), 20, C_DIM, false)))
			continue
		var nums := range(1, 8) if g == "solid" else range(9, 16)
		var left := 0
		for n in nums:
			var t := TextureRect.new()
			t.texture = _ball_icon(n, 44)
			t.custom_minimum_size = Vector2(44, 44)
			t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
			t.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
			t.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			var num := Label.new()
			num.text = str(n)
			num.set_anchors_preset(Control.PRESET_FULL_RECT)
			num.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
			num.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
			num.add_theme_font_override("font", font_black)
			num.add_theme_font_size_override("font_size", 14)
			num.add_theme_color_override("font_color", Color.BLACK)
			t.add_child(num)
			var on: bool = game.sim.is_alive(n)
			t.modulate.a = 1.0 if on else 0.2
			left += 1 if on else 0
			r.add_child(t)
		var hint := L.t("{0} left", [left]) if left > 0 else L.t("only the 8 left!")
		var hl2 := _label(hint, 20, C_DIM, false)
		hl2.autowrap_mode = TextServer.AUTOWRAP_OFF
		hl2.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		r.add_child(hl2)


# ---------- result ----------

var _res_title: Label
var _res_reason: Label
var _res_grid: GridContainer


func _build_result() -> void:
	var v := _overlay("result")
	_res_title = _label("", 52, C_GOLD, false)
	_res_title.add_theme_font_override("font", font_black)
	v.add_child(_res_title)
	_res_reason = _label("", 21, C_DIM, false)
	v.add_child(_res_reason)
	_res_grid = GridContainer.new()
	_res_grid.columns = 3
	_res_grid.add_theme_constant_override("h_separation", 10)
	_res_grid.add_theme_constant_override("v_separation", 10)
	v.add_child(_res_grid)
	_result_bar = ProgressBar.new()
	_result_bar.show_percentage = false
	_result_bar.custom_minimum_size.y = 10
	v.add_child(_result_bar)
	_result_lb = _label("", 18, C_DIM, false)
	v.add_child(_result_lb)
	var r := _row(v)
	r.add_child(_grow(_btn("Menu", open_menu)))
	r.add_child(_grow(_btn("Replay", func():
		close_all()
		if not Store.replays.is_empty():
			game.start_replay(Store.replays[0])
		else:
			open_menu())))
	r.add_child(_grow(_btn("Again", func(): _go(func(): game.rematch()), C_GREEN), 1.6))


func show_result(title: String, good: bool, reason: String, tiles: Array) -> void:
	_res_title.text = title
	_res_title.add_theme_color_override("font_color", C_GOLD if good else Color("ff7b6b"))
	_res_reason.text = reason
	for c in _res_grid.get_children():
		c.queue_free()
	for t in tiles:
		_tile(_res_grid, t[0], t[1])
	_result_t = 15.0
	open("result")
	if good:
		_confetti()


func _confetti() -> void:
	var size := root.get_viewport_rect().size
	for i in 40:
		var c := ColorRect.new()
		c.color = Color.from_hsv(randf(), 0.7, 1.0)
		c.size = Vector2(12, 18)
		c.position = Vector2(randf() * size.x, -30)
		c.rotation = randf() * TAU
		c.mouse_filter = Control.MOUSE_FILTER_IGNORE
		root.add_child(c)
		var tw := create_tween()
		tw.set_parallel()
		var dur := randf_range(2.0, 4.0)
		tw.tween_property(c, "position:y", size.y + 40, dur).set_delay(randf() * 0.8)
		tw.tween_property(c, "rotation", c.rotation + randf_range(-8, 8), dur)
		tw.chain().tween_callback(c.queue_free)

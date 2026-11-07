class_name TableView
extends Node3D
## Builds the 3D room, table, balls and cue, and mirrors the simulation.
## Table units are scaled by S into world units (ball radius 0.12).

const S := 0.01
const BALL_COLORS := {1: "f0c800", 2: "0046aa", 3: "c81e1e", 4: "5a1e82", 5: "eb7800", 6: "007832", 7: "781e1e", 8: "141414"}
const FELT := Color("14784a")
const CLOTH := Color("117040")

var cam: Camera3D
var balls: Array[MeshInstance3D] = []
var ball_mats := {}
var stick: Node3D
var guides: Array[MeshInstance3D] = []
var ghost: MeshInstance3D
var head_line: MeshInstance3D
var _last := []
var _fall := {}
var _look := Vector3.ZERO
var fixed_cam = null
var lamp: Node3D


static func w(x: float, y: float, h := 0.0) -> Vector3:
	return Vector3((x - Sim.CX) * S, h, (y - Sim.CY) * S)


func _ready() -> void:
	_environment()
	_room()
	_table()
	_stick()
	_guides()
	cam = Camera3D.new()
	cam.fov = 50
	cam.near = 0.05
	cam.far = 80
	add_child(cam)
	cam.position = Vector3(0, 13, 0.6)
	cam.look_at(Vector3.ZERO)
	var sphere := SphereMesh.new()
	sphere.radius = Sim.BR * S
	sphere.height = Sim.BR * S * 2
	sphere.radial_segments = 32
	sphere.rings = 16
	for i in 16:
		var m := MeshInstance3D.new()
		m.mesh = sphere
		add_child(m)
		balls.append(m)
		_last.append(Vector2.ZERO)
	await _make_ball_materials()


func _mat(color: Color, rough := 0.7, metal := 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.roughness = rough
	m.metallic = metal
	return m


func _noise_tex(colors: PackedColorArray, freq: float, w_px := 256, h_px := 256) -> NoiseTexture2D:
	var n := FastNoiseLite.new()
	n.frequency = freq
	n.fractal_octaves = 4
	var t := NoiseTexture2D.new()
	t.noise = n
	t.width = w_px
	t.height = h_px
	t.seamless = true
	var g := Gradient.new()
	g.colors = colors
	g.offsets = PackedFloat32Array(range(colors.size()).map(func(i): return float(i) / (colors.size() - 1)))
	t.color_ramp = g
	return t


func _box(size: Vector3, pos: Vector3, mat: Material, parent: Node = self, shadow := true) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var b := BoxMesh.new()
	b.size = size
	mi.mesh = b
	mi.material_override = mat
	mi.position = pos
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if shadow else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(mi)
	return mi


func _csg_box(size: Vector3, pos: Vector3) -> CSGBox3D:
	var b := CSGBox3D.new()
	b.size = size
	b.position = pos
	return b


## CSG combiner with one subtracting cylinder per pocket (moved last after filling).
func _csg_cut(mat: Material, radius: float) -> CSGCombiner3D:
	var c := CSGCombiner3D.new()
	c.material_override = mat
	var cutters := []
	for p in Sim.POCKETS:
		var cyl := CSGCylinder3D.new()
		cyl.radius = radius
		cyl.height = 1.0
		cyl.sides = 40
		cyl.operation = CSGShape3D.OPERATION_SUBTRACTION
		cyl.position = w(p.x, p.y, 0)
		c.add_child(cyl)
		cutters.append(cyl)
	c.set_meta("cutters", cutters)
	return c


func _environment() -> void:
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color("2a2118")
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color("8a7560")
	env.ambient_light_energy = 1.0
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_exposure = 1.05
	env.glow_enabled = true
	env.glow_intensity = 0.35
	env.glow_bloom = 0.05
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	var sun := DirectionalLight3D.new()
	sun.light_energy = 0.55
	sun.light_color = Color("fff4e6")
	sun.shadow_enabled = true
	sun.directional_shadow_max_distance = 22.0
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_ORTHOGONAL
	sun.rotation_degrees = Vector3(-62, 28, 0)
	add_child(sun)
	var spot := SpotLight3D.new()
	spot.position = Vector3(0, 3.2, 0)
	spot.rotation_degrees = Vector3(-90, 0, 0)
	spot.spot_range = 8.0
	spot.spot_angle = 70.0
	spot.spot_attenuation = 0.6
	spot.light_energy = 1.8
	spot.light_color = Color("fff0d0")
	spot.shadow_enabled = true
	add_child(spot)


func _room() -> void:
	var floor_mat := StandardMaterial3D.new()
	floor_mat.albedo_texture = _noise_tex(PackedColorArray([Color("3a2414"), Color("5b3920"), Color("6e4527")]), 0.02, 512, 512)
	floor_mat.uv1_scale = Vector3(6, 30, 1)
	floor_mat.roughness = 0.55
	_box(Vector3(30, 0.1, 30), Vector3(0, -3.25, 0), floor_mat, self, false)
	var wall := _mat(Color("2f5e4a"), 0.9)
	var panel := _mat(Color("3b2010"), 0.6)
	for side in [[Vector3(0, 1.5, -13), Vector3(26, 9.5, 0.2)], [Vector3(0, 1.5, 13), Vector3(26, 9.5, 0.2)],
			[Vector3(-13, 1.5, 0), Vector3(0.2, 9.5, 26)], [Vector3(13, 1.5, 0), Vector3(0.2, 9.5, 26)]]:
		_box(side[1], side[0], wall, self, false)
		var p: Vector3 = side[0]
		var sz: Vector3 = side[1]
		_box(Vector3(maxf(sz.x - 0.05, 0.3), 1.6, maxf(sz.z - 0.05, 0.3)) , Vector3(p.x * 0.995, -2.4, p.z * 0.995), panel, self, false)
	# Decke, sonst schaut die Kamera ins schwarze Nichts
	_box(Vector3(26.4, 0.2, 26.4), Vector3(0, 6.35, 0), _mat(Color("4a3a2c"), 0.9), self, false)
	var fill := OmniLight3D.new()
	fill.position = Vector3(0, 5.0, 0)
	fill.omni_range = 24.0
	fill.light_energy = 0.9
	fill.light_color = Color("ffe9c9")
	fill.shadow_enabled = false
	add_child(fill)
	# lamp over the table; hidden in the top view where it would block the camera
	lamp = Node3D.new()
	add_child(lamp)
	var shade := _mat(Color("0f4d34"), 0.3, 0.1)
	_box(Vector3(1.0, 0.26, 4.3), Vector3(0, 3.3, 0), shade, lamp, false)
	var glow := StandardMaterial3D.new()
	glow.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	glow.albedo_color = Color("fff2c8")
	_box(Vector3(0.88, 0.02, 4.1), Vector3(0, 3.16, 0), glow, lamp, false)
	for z in [-1.5, 1.5]:
		_box(Vector3(0.024, 3.0, 0.024), Vector3(0, 4.9, z), _mat(Color("222222")), lamp, false)


func _table() -> void:
	var wood := StandardMaterial3D.new()
	wood.albedo_texture = _noise_tex(PackedColorArray([Color("3d1d0b"), Color("6e3b1b"), Color("8a4c25"), Color("5a2d12")]), 0.03, 512, 128)
	wood.uv1_scale = Vector3(1, 8, 1)
	wood.roughness = 0.32
	wood.clearcoat_enabled = true
	wood.clearcoat = 0.6
	var felt := StandardMaterial3D.new()
	felt.albedo_texture = _noise_tex(PackedColorArray([FELT.darkened(0.12), FELT, FELT.lightened(0.06)]), 0.6)
	felt.uv1_scale = Vector3(6, 12, 1)
	felt.roughness = 1.0
	var cushion := _mat(CLOTH, 0.9)
	var tw := (Sim.PR - Sim.PL) * S
	var td := (Sim.PB - Sim.PT) * S
	var rw := 0.42
	var rh := 0.2
	var pr := 0.25
	# Felt bed, rails and cushions are CSG shapes with the pockets cut out, so the
	# pockets are real holes instead of objects standing on the table.
	var bed := _csg_cut(felt, pr)
	bed.add_child(_csg_box(Vector3(tw + 0.5, 0.06, td + 0.5), Vector3(0, -0.03, 0)))
	var frame := _csg_cut(wood, pr)
	var outer := _csg_box(Vector3(tw + 0.2 + 2 * rw, rh, td + 0.2 + 2 * rw), Vector3(0, rh / 2, 0))
	frame.add_child(outer)
	var inner := _csg_box(Vector3(tw + 0.2, rh + 0.2, td + 0.2), Vector3(0, rh / 2, 0))
	inner.operation = CSGShape3D.OPERATION_SUBTRACTION
	frame.add_child(inner)
	var cush := _csg_cut(cushion, pr)
	for sg in Sim.SEGS:
		var a2: Vector2 = sg[0]
		var b2: Vector2 = sg[1]
		var d := b2 - a2
		var out := Vector2(-d.y, d.x).normalized()
		var mid := (a2 + b2) / 2
		if Vector2(Sim.CX, Sim.CY).distance_to(mid + out) < Vector2(Sim.CX, Sim.CY).distance_to(mid):
			out = -out
		# etwas länger und dicker, damit an den Knicken und zum Rahmen keine Lücke bleibt
		var c := mid + out * 6.0
		var cb := _csg_box(Vector3(d.length() * S + 0.12, 0.15, 0.12), w(c.x, c.y, 0.075))
		cb.rotation.y = -d.angle()
		cush.add_child(cb)
	for node in [frame, bed, cush]:
		var cutters: Array = node.get_meta("cutters")
		for cyl in cutters:
			node.move_child(cyl, -1)
		add_child(node)
	_box(Vector3(tw + 1.2, 0.36, td + 1.2), Vector3(0, -0.3, 0), wood)
	for sx in [-1, 1]:
		for sz in [-1, 1]:
			_box(Vector3(0.42, 2.9, 0.42), Vector3(sx * (tw / 2 + 0.2), -1.75, sz * (td / 2 + 0.2)), wood)
	# pocket shafts: inside-out black cylinders seen through the holes
	var hole := StandardMaterial3D.new()
	hole.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	hole.albedo_color = Color("040404")
	for p in Sim.POCKETS:
		var cyl := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = pr - 0.002
		cm.bottom_radius = pr - 0.002
		cm.height = rh + 0.11
		cm.radial_segments = 40
		cm.cap_top = false
		cm.flip_faces = true
		cyl.mesh = cm
		cyl.material_override = hole
		cyl.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		cyl.position = w(p.x, p.y, (rh - 0.11) / 2)
		add_child(cyl)
	# diamonds
	var pearl := _mat(Color("f3e9c9"), 0.3, 0.4)
	for k in range(1, 8):
		if k == 4:
			continue
		var z := Sim.PT + k * (Sim.PB - Sim.PT) / 8.0
		for x in [Sim.PL - 30, Sim.PR + 30]:
			_box(Vector3(0.05, 0.01, 0.05), w(x, z, rh + 0.005), pearl, self, false)
	for k in range(1, 4):
		var x2 := Sim.PL + k * (Sim.PR - Sim.PL) / 4.0
		for z2 in [Sim.PT - 30, Sim.PB + 30]:
			_box(Vector3(0.05, 0.01, 0.05), w(x2, z2, rh + 0.005), pearl, self, false)
	# head string, shown while placing for the break
	var line := StandardMaterial3D.new()
	line.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	line.albedo_color = Color(1, 1, 1, 0.35)
	line.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	head_line = _box(Vector3(tw, 0.004, 0.012), w(Sim.CX, Sim.HEAD_Y, 0.002), line, self, false)
	head_line.visible = false
	# chalk cube on the rail
	_box(Vector3(0.14, 0.09, 0.14), w(Sim.PR + 30, Sim.PT + 170, rh + 0.045), _mat(Color("3b6fd4"), 0.9)).rotation.y = 0.6


func _stick() -> void:
	stick = Node3D.new()
	add_child(stick)
	var shaft := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.017
	cm.bottom_radius = 0.042
	cm.height = 3.2
	cm.radial_segments = 16
	shaft.mesh = cm
	shaft.material_override = _mat(Color("deb887"), 0.45)
	shaft.rotation.z = PI / 2
	shaft.position.x = 1.66
	stick.add_child(shaft)
	var butt := MeshInstance3D.new()
	var bm := CylinderMesh.new()
	bm.top_radius = 0.034
	bm.bottom_radius = 0.043
	bm.height = 1.2
	bm.radial_segments = 16
	butt.mesh = bm
	butt.material_override = _mat(Color("2a160b"), 0.35)
	butt.rotation.z = PI / 2
	butt.position.x = 2.66
	stick.add_child(butt)
	var tip := MeshInstance3D.new()
	var tmesh := CylinderMesh.new()
	tmesh.top_radius = 0.017
	tmesh.bottom_radius = 0.017
	tmesh.height = 0.06
	tip.mesh = tmesh
	tip.material_override = _mat(Color("4a90d9"), 0.9)
	tip.rotation.z = PI / 2
	tip.position.x = 0.03
	stick.add_child(tip)


func _guides() -> void:
	for c in [Color(1, 1, 1, 0.85), Color("ffe678"), Color("8fd3ff"), Color(1, 1, 1, 0.5)]:
		var m := StandardMaterial3D.new()
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		m.albedo_color = c
		var mi := _box(Vector3(1, 0.004, 0.016), Vector3.ZERO, m, self, false)
		mi.visible = false
		guides.append(mi)
	ghost = MeshInstance3D.new()
	var tm := TorusMesh.new()
	tm.inner_radius = Sim.BR * S - 0.012
	tm.outer_radius = Sim.BR * S
	ghost.mesh = tm
	var gm := StandardMaterial3D.new()
	gm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	gm.albedo_color = Color.WHITE
	ghost.material_override = gm
	ghost.visible = false
	add_child(ghost)


func _make_ball_materials() -> void:
	var vp := SubViewport.new()
	vp.size = Vector2i(512, 256 * 16)
	vp.transparent_bg = false
	vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	add_child(vp)
	var font: Font = load("res://assets/fonts/NotoSans-Black.ttf")
	for n in 16:
		var y0 := n * 256
		var white := Color("f2f2ec")
		var col: Color = white if n == 0 else Color(BALL_COLORS[(n - 1) % 8 + 1])
		var base := ColorRect.new()
		base.color = white if n > 8 else col
		base.position = Vector2(0, y0)
		base.size = Vector2(512, 256)
		vp.add_child(base)
		if n > 8:
			var band := ColorRect.new()
			band.color = col
			band.position = Vector2(0, y0 + 60)
			band.size = Vector2(512, 136)
			vp.add_child(band)
		for cx in [128, 384]:
			var r := 44 if n > 0 else 10
			var dot := Panel.new()
			var sb := StyleBoxFlat.new()
			sb.bg_color = white if n > 0 else Color("cc3333")
			sb.set_corner_radius_all(r)
			sb.anti_aliasing = true
			dot.add_theme_stylebox_override("panel", sb)
			dot.position = Vector2(cx - r, y0 + 128 - r)
			dot.size = Vector2(r * 2, r * 2)
			vp.add_child(dot)
			if n > 0:
				var lb := Label.new()
				lb.text = str(n)
				lb.add_theme_font_override("font", font)
				lb.add_theme_font_size_override("font_size", 52 if n < 10 else 44)
				lb.add_theme_color_override("font_color", Color.BLACK)
				lb.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
				lb.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
				lb.position = Vector2(cx - 44, y0 + 84)
				lb.size = Vector2(88, 88)
				vp.add_child(lb)
	await RenderingServer.frame_post_draw
	await RenderingServer.frame_post_draw
	var img := vp.get_texture().get_image()
	vp.queue_free()
	for n in 16:
		var part := img.get_region(Rect2i(0, n * 256, 512, 256))
		part.generate_mipmaps()
		var m := StandardMaterial3D.new()
		m.albedo_texture = ImageTexture.create_from_image(part)
		m.roughness = 0.12
		m.metallic_specular = 0.7
		m.clearcoat_enabled = true
		m.clearcoat = 1.0
		m.clearcoat_roughness = 0.05
		ball_mats[n] = m
	_apply_mats = true


var _apply_mats := false
var _num_cache := PackedInt32Array()


## Called after every rack; resets all ball meshes instead of creating new ones.
func reset_balls(sim: Sim) -> void:
	_num_cache = sim.num.duplicate()
	_fall.clear()
	for i in balls.size():
		var m := balls[i]
		m.visible = i < sim.num.size()
		m.scale = Vector3.ONE
		m.basis = Basis.from_euler(Vector3(randf() * TAU, randf() * TAU, randf() * TAU))
		if i < sim.num.size():
			_last[i] = Vector2(sim.px[i], sim.py[i])
	_apply_mats = true


func sync(sim: Sim, delta: float) -> void:
	if _apply_mats and ball_mats.size() == 16:
		_apply_mats = false
		for i in _num_cache.size():
			balls[i].material_override = ball_mats[_num_cache[i]]
	for i in sim.num.size():
		var m := balls[i]
		var p := Vector2(sim.px[i], sim.py[i])
		if sim.alive[i] == 1:
			if _fall.has(i):
				_fall.erase(i)
				m.scale = Vector3.ONE
				_last[i] = p
			m.visible = true
			var d: Vector2 = p - _last[i]
			var l := d.length()
			if l > 0.001:
				var axis := Vector3(d.y / l, 0, -d.x / l)
				m.basis = (Basis(axis, l / Sim.BR) * m.basis).orthonormalized()
			m.position = w(p.x, p.y, Sim.BR * S)
			_last[i] = p
		elif m.visible:
			if not _fall.has(i):
				_fall[i] = 0.0
			var f: float = minf(1.0, _fall[i] + delta * 2.6)
			_fall[i] = f
			var pk: Vector2 = Sim.POCKETS[sim.pocket_of.get(sim.num[i], 0)]
			var from := _last[i] as Vector2
			var q := from.lerp(pk, minf(1.0, f * 1.6))
			m.position = w(q.x, q.y, Sim.BR * S - f * 0.45)
			m.scale = Vector3.ONE * (1.0 - f * 0.2)
			if f >= 1.0:
				m.visible = false


func show_stick(on: bool, cue: Vector2, angle: float, pull: float) -> void:
	stick.visible = on
	if not on:
		return
	var back := Vector2(cos(angle), sin(angle)) * -pull
	stick.position = w(cue.x + back.x, cue.y + back.y, Sim.BR * S + 0.012)
	stick.rotation = Vector3(0, PI - angle, deg_to_rad(4))


func _seg(i: int, a: Vector2, b: Vector2) -> void:
	var g := guides[i]
	var d := b - a
	var l := d.length()
	g.visible = l > 1.0
	g.scale = Vector3(l * S, 1, 1)
	var m := (a + b) / 2
	g.position = w(m.x, m.y, 0.014)
	g.rotation.y = -d.angle()


func hide_guides() -> void:
	for g in guides:
		g.visible = false
	ghost.visible = false


## Aiming aid: path to the first ball or rail, the object ball line and the
## cue ball deflection.
func show_guides(sim: Sim, angle: float) -> void:
	var cue := Vector2(sim.px[0], sim.py[0])
	var dir := Vector2(cos(angle), sin(angle))
	var tm := 3000.0
	var hit := -1
	var br := Sim.BR
	for i in range(1, sim.num.size()):
		if sim.alive[i] == 0:
			continue
		var f := cue - Vector2(sim.px[i], sim.py[i])
		var bq := f.dot(dir)
		var disc := bq * bq - (f.length_squared() - 4 * br * br)
		if disc >= 0:
			var t := -bq - sqrt(disc)
			if t > 0 and t < tm:
				tm = t
				hit = i
	var wt := 3000.0
	if dir.x > 1e-6:
		wt = minf(wt, (Sim.PR - br - cue.x) / dir.x)
	elif dir.x < -1e-6:
		wt = minf(wt, (Sim.PL + br - cue.x) / dir.x)
	if dir.y > 1e-6:
		wt = minf(wt, (Sim.PB - br - cue.y) / dir.y)
	elif dir.y < -1e-6:
		wt = minf(wt, (Sim.PT + br - cue.y) / dir.y)
	var start := cue + dir * br
	ghost.visible = true
	if hit >= 0 and tm < wt:
		var e := cue + dir * tm
		_seg(0, start, e)
		ghost.position = w(e.x, e.y, 0.016)
		var hb := Vector2(sim.px[hit], sim.py[hit])
		var nrm := (hb - e).normalized()
		_seg(1, hb, hb + nrm * 100)
		var tang := dir - nrm * dir.dot(nrm)
		if tang.length() > 0.02:
			_seg(2, e, e + tang.normalized() * 70 * tang.length())
		else:
			guides[2].visible = false
		guides[3].visible = false
	else:
		var e2 := cue + dir * wt
		_seg(0, start, e2)
		ghost.position = w(e2.x, e2.y, 0.016)
		var r := dir
		if e2.x >= Sim.PR - br - 0.1 or e2.x <= Sim.PL + br + 0.1:
			r.x = -r.x
		else:
			r.y = -r.y
		_seg(3, e2, e2 + r * 180)
		guides[1].visible = false
		guides[2].visible = false


func update_camera(follow: bool, cue: Vector2, angle: float, delta: float) -> void:
	if fixed_cam != null:
		cam.position = fixed_cam[0]
		cam.look_at(fixed_cam[1])
		return
	var vp := get_viewport().get_visible_rect().size
	var aspect := vp.x / maxf(vp.y, 1.0)
	var p: Vector3
	var l: Vector3
	if follow:
		var dir := Vector3(cos(angle), 0, sin(angle))
		var c := w(cue.x, cue.y)
		p = c - dir * 2.5 + Vector3(0, 1.75, 0)
		l = c + dir * 2.0
	else:
		var h := maxf(14.0, 7.3 / (0.93 * aspect))
		p = Vector3(0, h, 0.7)
		l = Vector3(0, 0, 0.15)
	lamp.visible = follow
	var k := 1.0 - exp(-delta * 6.0)
	cam.position = cam.position.lerp(p, k)
	_look = _look.lerp(l, 1.0 - exp(-delta * 7.0))
	cam.look_at(_look)


## Screen point to table coordinates on the ball-centre plane, or null.
func screen_to_table(pos: Vector2):
	var o := cam.project_ray_origin(pos)
	var d := cam.project_ray_normal(pos)
	if d.y >= 0:
		return null
	var t := (Sim.BR * S - o.y) / d.y
	var hit := o + d * t
	return Vector2(hit.x / S + Sim.CX, hit.z / S + Sim.CY)

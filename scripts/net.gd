extends Node
## Online play: one phone hosts (ENet), the other joins by address. No server involved.

signal got(d: Dictionary)
signal connected
signal lost
signal failed
signal status(text: String)
signal lobbies_loaded(list: Array)
signal lobbies_failed
signal denied

const PORT := 24680
const LOBBY_URL := "https://paperstream.dev/cueduel/api/v1"

var host := false
var active := false

var _peer: ENetMultiplayerPeer
var _upnp: UPNP
var _ext := ""
var _upnp_done := false
var _timer: Timer
var _hb: Timer
var password := ""
var join_password := ""
var lobby_name := ""
var _lobby_id := ""
var _lobby_token := ""
var _authed := false
var _players := 1
var _public := false
var _cands := []
var _cand_port := PORT


func _ready() -> void:
	multiplayer.peer_disconnected.connect(func(_id): if host and active: lost.emit())
	multiplayer.connected_to_server.connect(func():
		_timer.stop()
		_authed = true
		_rx.rpc({"t": "hello", "pw": _hash(join_password)})
		connected.emit())
	multiplayer.connection_failed.connect(_join_failed)
	multiplayer.server_disconnected.connect(func():
		stop()
		lost.emit())
	_timer = Timer.new()
	_timer.one_shot = true
	_timer.wait_time = 8.0
	_timer.timeout.connect(_join_failed)
	add_child(_timer)
	_hb = Timer.new()
	_hb.wait_time = 15.0
	_hb.timeout.connect(_ping)
	add_child(_hb)


func host_game(name := "", pw := "", public := false) -> bool:
	stop()
	password = pw
	lobby_name = name
	_public = public
	_players = 1
	_authed = false
	_peer = ENetMultiplayerPeer.new()
	if _peer.create_server(PORT, 1) != OK:
		_peer = null
		return false
	multiplayer.multiplayer_peer = _peer
	host = true
	active = true
	_ext = ""
	_upnp_done = false
	status.emit(host_text())
	WorkerThreadPool.add_task(_try_upnp)
	return true


func join_game(ip: String, port := PORT, pw := "") -> bool:
	return join_any([ip], port, pw)


## Probiert die Adressen nacheinander durch (erst lokale, dann die öffentliche)
func join_any(ips: Array, port := PORT, pw := "") -> bool:
	stop()
	join_password = pw
	_cands = ips.duplicate()
	_cand_port = port
	return _try_next()


func _try_next() -> bool:
	while not _cands.is_empty():
		var ip: String = _cands.pop_front()
		_peer = ENetMultiplayerPeer.new()
		if _peer.create_client(ip, _cand_port) != OK:
			_peer = null
			continue
		multiplayer.multiplayer_peer = _peer
		host = false
		active = true
		_timer.start(3.0 if not _cands.is_empty() else 8.0)
		return true
	return false


func _join_failed() -> void:
	stop()
	if not _try_next():
		failed.emit()


func stop() -> void:
	_timer.stop()
	_unregister()
	_authed = false
	if _upnp != null:
		_upnp.delete_port_mapping(PORT, "UDP")
		_upnp = null
	if _peer != null:
		_peer.close()
	multiplayer.multiplayer_peer = null
	_peer = null
	active = false
	host = false


func send(d: Dictionary) -> void:
	if active and _peer != null and not multiplayer.get_peers().is_empty():
		_rx.rpc(d)


@rpc("any_peer", "reliable")
func _rx(d: Dictionary) -> void:
	match d.get("t", ""):
		"hello":
			if not host or _authed:
				return
			if str(d.get("pw", "")) == _hash(password):
				_authed = true
				_set_players(2)
				connected.emit()
			else:
				var id := multiplayer.get_remote_sender_id()
				_rx.rpc_id(id, {"t": "deny"})
				get_tree().create_timer(0.5).timeout.connect(func():
					if _peer != null:
						_peer.disconnect_peer(id))
		"deny":
			if not host:
				stop()
				denied.emit()
		_:
			if _authed:
				got.emit(d)


func _hash(pw: String) -> String:
	return ("cueduel" + pw).sha256_text()


# ---------- lobby list ----------

func fetch_lobbies() -> void:
	_req(HTTPClient.METHOD_GET, "/lobbies", {}, func(data):
		if data is Array:
			lobbies_loaded.emit(data)
		else:
			lobbies_failed.emit())


func _req(method: int, path: String, body: Dictionary, cb: Callable) -> void:
	var r := HTTPRequest.new()
	r.timeout = 8.0
	add_child(r)
	r.request_completed.connect(func(res, code, _h, b):
		r.queue_free()
		var data = null
		if res == HTTPRequest.RESULT_SUCCESS and code >= 200 and code < 300:
			data = JSON.parse_string(b.get_string_from_utf8())
		cb.call(data))
	var json := JSON.stringify(body) if method != HTTPClient.METHOD_GET else ""
	if r.request(LOBBY_URL + path, ["Content-Type: application/json"], method, json) != OK:
		r.queue_free()
		cb.call(null)


func _register() -> void:
	var body := {"name": lobby_name, "port": PORT, "locked": password != "", "open": _ext != "",
		"lans": local_ips(),
		"version": str(ProjectSettings.get_setting("application/config/version", ""))}
	_req(HTTPClient.METHOD_POST, "/lobbies", body, func(data):
		if data is Dictionary and data.has("id") and active and host:
			_lobby_id = str(data.id)
			_lobby_token = str(data.token)
			_hb.start()
		elif active and host:
			status.emit(host_text() + "\n" + L.t("The list could not be reached")))


func _set_players(n: int) -> void:
	_players = n
	_ping()


func _ping() -> void:
	if _lobby_id == "":
		return
	_req(HTTPClient.METHOD_POST, "/lobbies/%s/ping" % _lobby_id, {"token": _lobby_token, "players": _players}, func(_d): pass)


func _unregister() -> void:
	_hb.stop()
	if _lobby_id != "":
		_req(HTTPClient.METHOD_POST, "/lobbies/%s/close" % _lobby_id, {"token": _lobby_token}, func(_d): pass)
		_lobby_id = ""
		_lobby_token = ""


func local_ips() -> Array:
	var out := []
	for a in IP.get_local_addresses():
		var p: PackedStringArray = a.split(".")
		if p.size() != 4:
			continue
		var b := int(p[1])
		if a.begins_with("192.168.") or a.begins_with("10.") or (p[0] == "172" and b >= 16 and b <= 31):
			out.append(a)
	out.sort_custom(func(x, y): return _rank(x) < _rank(y))
	return out


func _rank(a: String) -> int:
	if a.begins_with("192.168."):
		return 0
	if a.begins_with("10."):
		return 1
	return 2


func host_text() -> String:
	var s := L.t("Waiting for a friend…")
	for a in local_ips():
		s += "\n" + L.t("Same Wi-Fi or hotspot: {0}", [a])
	if _ext != "":
		s += "\n" + L.t("Internet: {0}", [_ext])
	elif _upnp_done:
		s += "\n" + L.t("Internet: forward UDP port {0} on your router", [PORT])
	else:
		s += "\n" + L.t("Looking for an internet address…")
	return s


func _try_upnp() -> void:
	var u := UPNP.new()
	var ok := false
	if u.discover(2000) == UPNP.UPNP_RESULT_SUCCESS:
		var gw := u.get_gateway()
		if gw != null and gw.is_valid_gateway() and u.add_port_mapping(PORT, PORT, "CueDuel", "UDP") == UPNP.UPNP_RESULT_SUCCESS:
			ok = true
			_ext = u.query_external_address()
	_upnp_done = true
	if ok:
		_upnp = u
	_upnp_finished.call_deferred()


func _upnp_finished() -> void:
	if active and host:
		if _public and _lobby_id == "":
			_register()
		var t := host_text()
		if _public and _ext == "":
			t += "\n" + L.t("Only players in your network see your game in the list")
		status.emit(t)

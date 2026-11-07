extends Node
## Online play: one phone hosts (ENet), the other joins by address. No server involved.

signal got(d: Dictionary)
signal connected
signal lost
signal failed
signal status(text: String)

const PORT := 24680

var host := false
var active := false

var _peer: ENetMultiplayerPeer
var _upnp: UPNP
var _ext := ""
var _upnp_done := false
var _timer: Timer


func _ready() -> void:
	multiplayer.peer_connected.connect(func(_id): if host: connected.emit())
	multiplayer.peer_disconnected.connect(func(_id): if host and active: lost.emit())
	multiplayer.connected_to_server.connect(func():
		_timer.stop()
		connected.emit())
	multiplayer.connection_failed.connect(func():
		stop()
		failed.emit())
	multiplayer.server_disconnected.connect(func():
		stop()
		lost.emit())
	_timer = Timer.new()
	_timer.one_shot = true
	_timer.wait_time = 8.0
	_timer.timeout.connect(func():
		stop()
		failed.emit())
	add_child(_timer)


func host_game() -> bool:
	stop()
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


func join_game(ip: String) -> bool:
	stop()
	_peer = ENetMultiplayerPeer.new()
	if _peer.create_client(ip, PORT) != OK:
		_peer = null
		return false
	multiplayer.multiplayer_peer = _peer
	host = false
	active = true
	_timer.start()
	return true


func stop() -> void:
	_timer.stop()
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
	got.emit(d)


func local_ips() -> Array:
	var out := []
	for a in IP.get_local_addresses():
		var p: PackedStringArray = a.split(".")
		if p.size() != 4:
			continue
		var b := int(p[1])
		if a.begins_with("192.168.") or a.begins_with("10.") or (p[0] == "172" and b >= 16 and b <= 31):
			out.append(a)
	return out


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
		status.emit(host_text())

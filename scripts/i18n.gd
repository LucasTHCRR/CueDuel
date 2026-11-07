extends Node
## Tiny translation table. Keys are the English texts; {0}, {1} are placeholders.

signal changed

const LANGS := ["en", "de", "es", "tr"]
const NAMES := {"en": "English", "de": "Deutsch", "es": "Español", "tr": "Türkçe"}

# [en, de, es, tr]
const ROWS := [
	["Settings", "Einstellungen", "Ajustes", "Ayarlar"],
	["AI level", "KI-Stärke", "Nivel de la IA", "YZ seviyesi"],
	["Easy", "Leicht", "Fácil", "Kolay"],
	["Medium", "Mittel", "Medio", "Orta"],
	["Hard", "Schwer", "Difícil", "Zor"],
	["Pro", "Profi", "Pro", "Uzman"],
	["Vibration", "Vibration", "Vibración", "Titreşim"],
	["Sound", "Sound", "Sonido", "Ses"],
	["Off", "Aus", "No", "Kapalı"],
	["On", "An", "Sí", "Açık"],
	["Language", "Sprache", "Idioma", "Dil"],
	["A new AI level starts a new game.", "Eine neue KI-Stärke startet eine neue Runde.", "Un nuevo nivel de IA inicia una nueva partida.", "Yeni YZ seviyesi yeni bir oyun başlatır."],
	["Cancel", "Abbrechen", "Cancelar", "İptal"],
	["Apply", "Übernehmen", "Aplicar", "Uygula"],
	["8-ball against the computer", "8-Ball gegen die KI", "8-Ball contra la IA", "Bilgisayara karşı 8-Ball"],
	["Continue", "Weiter", "Continuar", "Devam"],
	["Play", "Spielen", "Jugar", "Oyna"],
	["2 players", "2 Spieler", "2 jugadores", "2 oyuncu"],
	["Replays", "Replays", "Repeticiones", "Tekrarlar"],
	["Statistics", "Statistik", "Estadísticas", "İstatistik"],
	["Back", "Zurück", "Volver", "Geri"],
	["Restart", "Neustart", "Reiniciar", "Yeniden"],
	["Sure?", "Sicher?", "¿Seguro?", "Emin misin?"],
	["Menu", "Menü", "Menú", "Menü"],
	["View", "Ansicht", "Vista", "Görünüm"],
	["Balls", "Kugeln", "Bolas", "Toplar"],
	["Close", "Schließen", "Cerrar", "Kapat"],
	["Shoot", "Stoß", "Tirar", "Vur"],
	["Reset", "Zurücksetzen", "Restablecer", "Sıfırla"],
	["Player 1", "Spieler 1", "Jugador 1", "Oyuncu 1"],
	["Player 2", "Spieler 2", "Jugador 2", "Oyuncu 2"],
	["You", "Du", "Tú", "Sen"],
	["AI", "KI", "IA", "YZ"],
	["Place the cue ball behind the line", "Weiße hinter die Linie setzen", "Coloca la blanca detrás de la línea", "Beyazı çizginin arkasına koy"],
	["Break!", "Anstoß!", "¡Saque!", "Açılış!"],
	["Foul: {0}", "Foul: {0}", "Falta: {0}", "Faul: {0}"],
	["Cue ball potted", "Weiße versenkt", "Blanca embocada", "Beyaz top girdi"],
	["Hit nothing", "Nichts getroffen", "No tocó nada", "Hiçbir şeye vurmadı"],
	["Hit the 8 too early", "8 zu früh getroffen", "Tocó la 8 demasiado pronto", "8'e erken vurdu"],
	["Wrong ball first", "Falsche Kugel zuerst", "Bola equivocada primero", "Önce yanlış top"],
	["No rail after contact", "Keine Bande nach dem Treffer", "Ninguna banda tras el contacto", "Temastan sonra bant yok"],
	["drag the cue ball, then OK", "Weiße ziehen, dann OK", "arrastra la blanca y pulsa OK", "beyazı sürükle, sonra OK"],
	["place the cue ball", "Weiße setzen", "coloca la blanca", "beyazı yerleştir"],
	["The 8 goes back on the spot", "Die 8 kommt zurück auf den Punkt", "La 8 vuelve a su punto", "8 yerine geri kondu"],
	["{0} goes again", "{0} bleibt dran", "{0} sigue", "{0} devam ediyor"],
	["{0}'s turn", "{0} ist dran", "Turno de {0}", "Sıra {0}'da"],
	["Pot! Go again", "Treffer! Nochmal dran", "¡Dentro! Sigues tú", "Girdi! Tekrar sende"],
	["The AI potted one", "Die KI hat eine versenkt", "La IA embocó una", "YZ bir top soktu"],
	["Your turn", "Du bist dran", "Tu turno", "Sıra sende"],
	["AI's turn", "KI ist dran", "Turno de la IA", "Sıra YZ'de"],
	["You win!", "Du gewinnst!", "¡Ganas!", "Kazandın!"],
	["AI wins!", "KI gewinnt!", "¡Gana la IA!", "YZ kazandı!"],
	["{0} wins!", "{0} gewinnt!", "¡Gana {0}!", "{0} kazandı!"],
	["Replay finished", "Replay beendet", "Repetición terminada", "Tekrar bitti"],
	["Aim and shoot!", "Zielen und stoßen!", "¡Apunta y tira!", "Nişan al ve vur!"],
	["You potted the 8 cleanly", "Du hast die 8 sauber versenkt", "Embocaste la 8 limpiamente", "8'i temiz soktun"],
	["You potted the 8 too early or with a foul", "Du hast die 8 zu früh oder mit Foul versenkt", "Embocaste la 8 demasiado pronto o con falta", "8'i erken veya faulle soktun"],
	["The AI potted the 8 cleanly", "Die KI hat die 8 sauber versenkt", "La IA embocó la 8 limpiamente", "YZ 8'i temiz soktu"],
	["The AI potted the 8 too early or with a foul", "Die KI hat die 8 zu früh oder mit Foul versenkt", "La IA embocó la 8 demasiado pronto o con falta", "YZ 8'i erken veya faulle soktu"],
	["{0} potted the 8 cleanly", "{0} hat die 8 sauber versenkt", "{0} embocó la 8 limpiamente", "{0} 8'i temiz soktu"],
	["{0} potted the 8 too early or with a foul", "{0} hat die 8 zu früh oder mit Foul versenkt", "{0} embocó la 8 demasiado pronto o con falta", "{0} 8'i erken veya faulle soktu"],
	["VICTORY!", "SIEG!", "¡VICTORIA!", "ZAFER!"],
	["DEFEAT", "NIEDERLAGE", "DERROTA", "YENİLGİ"],
	["{0} WINS", "{0} GEWINNT", "¡GANA {0}!", "{0} KAZANDI"],
	["Duration", "Dauer", "Duración", "Süre"],
	["Shots (you : AI)", "Stöße (Du : KI)", "Tiros (tú : IA)", "Vuruş (sen : YZ)"],
	["Shots (P1 : P2)", "Stöße (S1 : S2)", "Tiros (J1 : J2)", "Vuruş (O1 : O2)"],
	["Potted", "Versenkt", "Embocadas", "Giren"],
	["Fouls", "Fouls", "Faltas", "Fauller"],
	["Best run", "Beste Serie", "Mejor racha", "En iyi seri"],
	["Pots per shot", "Treffer pro Stoß", "Bolas por tiro", "Vuruş başına top"],
	["Back to menu in {0} s", "Zurück ins Menü in {0} s", "Volver al menú en {0} s", "{0} sn içinde menüye dön"],
	["Again", "Nochmal", "Otra vez", "Tekrar"],
	["Games", "Spiele", "Partidas", "Oyunlar"],
	["Wins", "Siege", "Victorias", "Galibiyet"],
	["Losses", "Niederlagen", "Derrotas", "Yenilgi"],
	["Win rate", "Gewinnquote", "Victorias %", "Kazanma oranı"],
	["Best win streak", "Beste Siegesserie", "Mejor racha de victorias", "En iyi galibiyet serisi"],
	["Win rate per level", "Gewinnquote je Stufe", "Victorias por nivel", "Seviyeye göre kazanma"],
	["{0} W · {1} L", "{0} S · {1} N", "{0} V · {1} D", "{0} G · {1} M"],
	["{0} games · {1} wins", "{0} Spiele · {1} Siege", "{0} partidas · {1} victorias", "{0} oyun · {1} galibiyet"],
	["Not assigned yet", "Noch nicht verteilt", "Aún sin asignar", "Henüz belirlenmedi"],
	["only the 8 left!", "nur noch die 8!", "¡solo queda la 8!", "sadece 8 kaldı!"],
	["{0} left", "noch {0}", "quedan {0}", "{0} kaldı"],
	["Solids", "Volle", "Lisas", "Düz"],
	["Stripes", "Halbe", "Rayadas", "Çizgili"],
	["Won", "Gewonnen", "Ganada", "Kazanıldı"],
	["Lost", "Verloren", "Perdida", "Kaybedildi"],
	["Aborted", "Abgebrochen", "Abandonada", "Yarım kaldı"],
	["{0} shots", "{0} Stöße", "{0} tiros", "{0} vuruş"],
	["No replays yet. Every game is saved automatically.", "Noch keine Replays. Jede Runde wird automatisch gespeichert.", "Aún no hay repeticiones. Cada partida se guarda automáticamente.", "Henüz tekrar yok. Her oyun otomatik kaydedilir."],
	["Aims poorly, often picks hard shots and slips up a lot.", "Zielt ungenau, wählt oft schwierige Stöße und patzt häufig.", "Apunta mal, elige tiros difíciles y falla a menudo.", "Kötü nişan alır, zor vuruşlar seçer ve çok hata yapar."],
	["Aims decently and picks good shots, but slips up now and then.", "Trifft ordentlich und wählt gute Stöße, patzt aber ab und zu.", "Apunta bien y elige buenos tiros, pero falla a veces.", "İyi nişan alır, iyi vuruşlar seçer ama ara sıra hata yapar."],
	["Very accurate and thinks about where the cue ball ends up.", "Sehr genau und denkt mit, wo die Weiße liegen bleibt.", "Muy preciso y piensa dónde acaba la blanca.", "Çok isabetli ve beyazın nerede duracağını düşünür."],
	["Almost flawless and plays position. Good luck!", "Fast fehlerfrei und spielt auf Position. Viel Glück!", "Casi sin errores y juega a posición. ¡Suerte!", "Neredeyse hatasız ve pozisyon oynar. Bol şans!"],
	["Drag on the table to aim", "Zum Zielen über den Tisch wischen", "Desliza sobre la mesa para apuntar", "Nişan almak için masada kaydır"],
	["Replay", "Replay", "Repetición", "Tekrar"],
	["Speed", "Tempo", "Velocidad", "Hız"],
	["Stop", "Stopp", "Parar", "Dur"],
	["Open table", "Offener Tisch", "Mesa abierta", "Açık masa"],
	["Graphics", "Grafik", "Gráficos", "Grafik"],
	["Low", "Niedrig", "Baja", "Düşük"],
	["High", "Hoch", "Alta", "Yüksek"],
	["Find games", "Spiele suchen", "Buscar partidas", "Oyun bul"],
	["Join with address", "Mit Adresse beitreten", "Unirse con dirección", "Adresle katıl"],
	["Game name", "Spielname", "Nombre de la partida", "Oyun adı"],
	["Password (optional)", "Passwort (optional)", "Contraseña (opcional)", "Şifre (isteğe bağlı)"],
	["Password", "Passwort", "Contraseña", "Şifre"],
	["Open", "Offen", "Abierta", "Açık"],
	["Show in the list", "In der Liste zeigen", "Mostrar en la lista", "Listede göster"],
	["Create game", "Spiel erstellen", "Crear partida", "Oyun oluştur"],
	["Refresh", "Aktualisieren", "Actualizar", "Yenile"],
	["Loading…", "Lade…", "Cargando…", "Yükleniyor…"],
	["No games found", "Keine Spiele gefunden", "No hay partidas", "Oyun bulunamadı"],
	["The list could not be reached", "Die Liste ist nicht erreichbar", "No se puede acceder a la lista", "Listeye ulaşılamadı"],
	["Wrong password", "Falsches Passwort", "Contraseña incorrecta", "Yanlış şifre"],
	["Online", "Online", "En línea", "Çevrimiçi"],
	["Online game", "Online-Spiel", "Partida en línea", "Çevrimiçi oyun"],
	["Host a game", "Spiel hosten", "Crear partida", "Oyun kur"],
	["Join a game", "Spiel beitreten", "Unirse a una partida", "Oyuna katıl"],
	["Join", "Beitreten", "Unirse", "Katıl"],
	["Waiting for a friend…", "Warte auf einen Freund…", "Esperando a un amigo…", "Bir arkadaş bekleniyor…"],
	["Same Wi-Fi or hotspot: {0}", "Gleiches WLAN oder Hotspot: {0}", "Mismo Wi-Fi o punto de acceso: {0}", "Aynı Wi-Fi veya hotspot: {0}"],
	["Internet: {0}", "Internet: {0}", "Internet: {0}", "İnternet: {0}"],
	["Internet: forward UDP port {0} on your router", "Internet: UDP-Port {0} im Router freigeben", "Internet: abre el puerto UDP {0} en tu router", "İnternet: yönlendiricide UDP {0} portunu aç"],
	["Looking for an internet address…", "Suche Internet-Adresse…", "Buscando dirección de internet…", "İnternet adresi aranıyor…"],
	["Connecting…", "Verbinde…", "Conectando…", "Bağlanıyor…"],
	["Connected, waiting for the host…", "Verbunden, warte auf den Host…", "Conectado, esperando al anfitrión…", "Bağlandı, kurucu bekleniyor…"],
	["Could not connect", "Verbindung fehlgeschlagen", "No se pudo conectar", "Bağlanılamadı"],
	["Connection lost", "Verbindung verloren", "Conexión perdida", "Bağlantı koptu"],
	["Waiting for the host…", "Warte auf den Host…", "Esperando al anfitrión…", "Kurucu bekleniyor…"],
	["Opponent", "Gegner", "Rival", "Rakip"],
	["Opponent wins!", "Gegner gewinnt!", "¡Gana el rival!", "Rakip kazandı!"],
]

var lang := "en"
var _tab := {}


func _ready() -> void:
	for l in LANGS:
		_tab[l] = {}
	for r in ROWS:
		for k in LANGS.size():
			_tab[LANGS[k]][r[0]] = r[k]


func system_lang() -> String:
	var l := OS.get_locale_language()
	return l if l in LANGS else "en"


func set_lang(l: String) -> void:
	lang = l if l in LANGS else "en"
	TranslationServer.set_locale(lang)
	changed.emit()


func t(key: String, args: Array = []) -> String:
	var s: String = _tab.get(lang, {}).get(key, key)
	for i in args.size():
		s = s.replace("{%d}" % i, str(args[i]))
	return s

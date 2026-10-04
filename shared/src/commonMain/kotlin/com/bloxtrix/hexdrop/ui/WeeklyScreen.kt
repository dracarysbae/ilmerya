package com.bloxtrix.hexdrop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.bloxtrix.hexdrop.competition.*
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

@Composable fun WeeklyScreen(starting: Boolean, onPlay: () -> Unit, onBack: () -> Unit) {
    val league = LocalLeague.current
    val state by league.state.collectAsState(); val scope = rememberCoroutineScope()
    var previous by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val board = if (previous) state.previous else state.current
    var remaining by remember(board) { mutableStateOf(board?.let { it.endsAt - it.serverNow } ?: 0L) }
    val gameCenter = league.provider == "game_center"
    LaunchedEffect(Unit) { if (league.configured) league.refresh() }
    LaunchedEffect(board, previous) {
        val b = board ?: return@LaunchedEffect
        val clock = TimeSource.Monotonic.markNow()
        while (!previous) {
            remaining = (b.endsAt - b.serverNow - clock.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)
            if (remaining == 0L) { league.refresh(); break }
            delay(1000)
        }
    }
    Atmosphere(LocalReducedMotion.current) {
        Column(Modifier.safeDrawingPadding().fillMaxSize().widthIn(max = 540.dp).align(Alignment.Center)
            .verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(words("‹ Geri", "‹ Back"), color = C.Accent) }
                TextButton(onClick = { scope.launch { league.refresh() } }, modifier = Modifier.heightIn(min = 48.dp),
                    enabled = league.configured && !state.loading && !state.loginRequired) { Text(words("Yenile", "Refresh")) }
            }
            SmallLabel(words("AYNI KURALLAR. GERÇEK OYUNCULAR.", "SAME RULES. REAL PLAYERS."))
            Text(words("Haftalık lig", "Weekly league"), color = C.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(words("Haftanın en iyi Dingin lig oyunun sıralamaya girer. Skorlar, hamlelerin sunucuda yeniden oynatılmasıyla doğrulanır.",
                "Your best Calm ranked run of the week enters the standings. The server verifies every score by replaying your moves."), color = C.TextDim, fontSize = 14.sp)
            if (league.configured && !state.loginRequired) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(!previous, { previous = false }, label = { Text(words("Bu hafta", "This week")) })
                FilterChip(previous, { previous = true }, label = { Text(words("Geçen hafta", "Last week")) })
            }
            if (state.loading || starting || state.signingIn) LinearProgressIndicator(Modifier.fillMaxWidth(), color = C.Accent)
            Column(Modifier.fillMaxWidth().neuSurface(22.dp, recessed = true, color = C.Board).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!league.configured) {
                    Text(words("Lig bu sürümde kapalı", "League is off in this build"), color = C.Accent, fontWeight = FontWeight.Bold)
                    Text(words("Bu kurulum lig sunucusuna bağlı değil. Sıralama gösterilmez; serbest oyun her zamanki gibi oynanır.",
                        "This build is not connected to the league server. No standings are shown; free play works as usual."), color = C.TextDim)
                } else if (state.loginRequired) {
                    Text(words("Lige katıl", "Join the league"), color = C.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(if (gameCenter) words("Game Center hesabınla katıl. Sıralamada Game Center adın görünür; e-posta adresin paylaşılmaz.",
                            "Join with your Game Center account. Your Game Center name appears in the standings; your email is never shared.")
                        else words("Google Play Oyunlar profilinle katıl. Sıralamada oyuncu adın görünür; e-posta adresin paylaşılmaz.",
                            "Join with your Google Play Games profile. Your player name appears in the standings; your email is never shared."), color = C.TextDim)
                    Action(if (gameCenter) words("Game Center ile katıl", "Join with Game Center") else words("Google Play Oyunlar ile katıl", "Join with Google Play Games"),
                        { scope.launch { league.signIn() } }, Modifier.fillMaxWidth(), primary = true, enabled = !state.signingIn)
                } else {
                    SmallLabel(state.profile.name.uppercase())
                    Text(board?.me?.let { "#${it.rank}  ·  ${it.score} " + words("puan", "points") }
                        ?: words("Bu hafta henüz skorun yok", "No score this week yet"), color = C.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (!previous) {
                        val minutes = remaining / 60000
                        if (board != null) Text(words("Kapanışa ${minutes / 1440} gün ${(minutes / 60) % 24} sa ${minutes % 60} dk",
                            "Closes in ${minutes / 1440}d ${(minutes / 60) % 24}h ${minutes % 60}m"), color = C.TextDim)
                        Action(words("Lig oyununa başla", "Start a ranked run"), onPlay, Modifier.fillMaxWidth(), primary = true, enabled = !starting && !state.loading)
                    }
                }
            }
            if (state.error.isNotEmpty()) Text(when (state.error) {
                "pending" -> words("Gönderilmeyi bekleyen bir lig sonucun var. Bağlantı geldiğinde Yenile'ye dokun; ardından yeni lig oyunu başlatabilirsin.",
                    "A ranked result is waiting to be sent. Tap Refresh when you are online, then start a new ranked run.")
                "update" -> words("Lig kuralları güncellendi. Lige devam etmek için uygulamayı güncelle.", "League rules changed. Update the app to keep playing ranked runs.")
                "delete_failed" -> words("Hesap silinemedi. Bağlantını kontrol edip tekrar dene.", "The account could not be deleted. Check your connection and try again.")
                "busy" -> words("Sunucu yoğun. Biraz sonra tekrar dene.", "The server is busy. Try again shortly.")
                else -> words("Bağlantı kurulamadı veya oturumun yenilenmeli. Tekrar deneyebilirsin; serbest oyun etkilenmez.",
                    "Connection failed or your session needs renewal. Try again; free play is unaffected.")
            }, color = C.Danger)
            board?.takeIf { league.configured && !state.loginRequired }?.let { b ->
                SmallLabel((if (previous) words("KESİNLEŞEN SONUÇLAR", "FINAL RESULTS") else words("HAFTANIN SIRALAMASI", "WEEKLY STANDINGS")) + " · ${b.week}")
                if (!state.connected) Text(words("Son alınan sıralama · bağlantı yok", "Last received standings · offline"), color = C.TextDim)
                if (b.entries.isEmpty()) Text(if (previous) words("Geçen hafta tamamlanmış lig oyunu yok.", "No ranked runs were completed last week.")
                    else words("Bu hafta henüz tamamlanmış lig oyunu yok. İlk skoru sen yaz.", "No ranked runs completed this week yet. Set the first score."), color = C.TextDim)
                b.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth().neuSurface(14.dp, color = if (entry.player == state.profile.id) C.Board else C.Surface).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${entry.rank}", color = if (entry.rank <= 3) C.Accent else C.TextDim, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(entry.name, Modifier.weight(1f), color = C.TextPrimary, maxLines = 1)
                        Text("${entry.score}", color = C.Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (league.configured && !state.loginRequired && state.profile.history.isNotEmpty()) {
                SmallLabel(words("GEÇMİŞ HAFTALARIN", "YOUR PAST WEEKS"))
                state.profile.history.take(6).forEach { finish ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(finish.week, color = C.TextDim)
                        Text("#${finish.rank} · ${finish.score}", color = C.TextPrimary)
                    }
                }
            }
            Text(words("Her pazartesi 00.00'da (Türkiye saati) hafta kapanır.\nEşit puanda o puana önce ulaşan öndedir. Lig oyunu internet bağlantısıyla başlar; hafta kapandıktan sonra ulaşan sonuçlar sayılmaz.",
                "Each week closes on Monday at 00:00 Türkiye time.\nOn equal scores, whoever reached it first ranks higher. Ranked runs start online; results arriving after the week closes do not count."), color = C.TextDim, fontSize = 12.sp, lineHeight = 19.sp)
            if (league.configured && !state.loginRequired) TextButton(onClick = { delete = true }, enabled = !starting && !state.loading,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(words("Lig hesabımı sil", "Delete my league account"), color = C.TextDim) }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, containerColor = C.Surface,
        title = { Text(words("Lig hesabın silinsin mi?", "Delete your league account?"), color = C.TextPrimary) },
        text = { Text(words("Sunucudaki lig profilin, skorların, lig oyunu kayıtların ve geçmiş derecelerin kalıcı olarak silinir. Cihazdaki kişisel rekorun ve ayarların korunur.",
            "Your league profile, scores, ranked run records and past ranks are permanently deleted from the server. Your personal best and settings on this device are kept."), color = C.TextDim) },
        confirmButton = { TextButton(onClick = { delete = false; scope.launch { league.deleteAccount() } }) { Text(words("Kalıcı olarak sil", "Delete permanently"), color = C.Danger) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(words("Vazgeç", "Cancel")) } })
}

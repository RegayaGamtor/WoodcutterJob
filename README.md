# WoodcutterJob 1.2

Plugin pekerjaan **penebang pohon** untuk **Paper 1.21.11** (Java 21), oleh **RegayaGamtor**.

```
Pemain pegang kapak → arahkan ke UDARA/AIR di dalam region → klik
→ minigame GUI (klik slot HIJAU) → GUI menutup
→ pemain MEMUKUL SENDIRI, tiap pukulan menambah progress (boss bar) sampai 100%
→ reward + XP → cooldown region (persisten)
```

## Yang baru di 1.2

| Fitur | Keterangan |
|---|---|
| **Sistem region** | Pohon tidak lagi didaftarkan per log. Admin membuat region (kotak dari koordinat A sampai B). Interaksi hanya di **udara / air** dalam region saat memakai **kapak**. Klik pada block (log, tanah, dll) tidak memulai apa pun. |
| **Auto-pukul** | Progress tidak ada di GUI/inventory. Setelah minigame selesai, GUI menutup dan pemain memukul otomatis; tiap pukulan menambah progress (boss bar + action bar) sampai selesai. |
| **Cooldown via command** | `/woodcutter cooldown ...` (disimpan ke `config.yml`). |
| **Reward via command** | `/woodcutter reward ...`: tambah / hapus item, jumlah, chance, uang. Item apa saja, termasuk item custom dari tangan. |
| **Give** | `/woodcutter give <player> <TIPE|item> [jumlah]`. |

## Upgrade dari 1.0

- Pohon lama (titik log) **tidak dipakai lagi**. Buat region baru. Tabel lama di database dibiarkan, tidak dihapus.
- Hapus `messages.yml` lama lalu restart agar teks GUI/pesan baru dibuat (teks GUI lama masih menampilkan progress).
- `config.yml` lama tetap terbaca; section baru `region` dan `chop` memakai nilai default jika belum ada. Section `registration` dan `gui.progress-meter` diabaikan.

## 1. Requirements

Java 21, Paper 1.21.11, Maven 3.9+ untuk build. Opsional: Vault + plugin economy (reward uang), PlaceholderAPI.

## 2. Build

```bash
mvn clean package
```
Hasil: `target/WoodcutterJob-1.2.0.jar`. Salin ke `plugins/` lalu **restart** server.

## 3. Membuat region

Cara 1, pakai pos1 / pos2 (berdiri di sudut lalu jalankan):
```
/woodcutter pos1
/woodcutter pos2
/woodcutter region create hutan1 OAK
```
Cara 2, tulis koordinat langsung (`~` = relatif posisi pemain):
```
/woodcutter region create hutan1 OAK 100 64 200 110 75 210
/woodcutter region create hutan1 OAK 100 64 200 110 75 210 world_nether   (world opsional, wajib dari console)
```
`/woodcutter pos1 <x> <y> <z>` juga bisa. Nama region: huruf, angka, `_`, `-` (maks 32).

Cara pakai (pemain): pegang kapak, **arahkan ke udara / air di dalam area region**, klik kanan (ubah di `region.click`).
Klik ke block (termasuk log) tidak dihitung, sesuai desain.

| Command | Fungsi | Permission |
|---|---|---|
| `/woodcutter pos1\|pos2 [x y z]` | Pilih sudut region | `woodcutter.region` |
| `/woodcutter region create <nama> <tipe> [koordinat]` | Buat region | `woodcutter.region` |
| `/woodcutter region remove <nama>` | Hapus region (sesi aktif dibatalkan) | `woodcutter.region` |
| `/woodcutter region settype <nama> <tipe>` | Ganti tipe pohon region | `woodcutter.region` |
| `/woodcutter region info <nama>` / `/woodcutter list` | Info / daftar region | `woodcutter.info` |

Tipe: OAK, BIRCH, SPRUCE, JUNGLE, ACACIA, DARK_OAK, MANGROVE, CHERRY (menentukan reward, XP, cooldown, partikel).

## 4. Cooldown (via command)

```
/woodcutter cooldown                      lihat nilai saat ini
/woodcutter cooldown OAK 5m               cooldown tipe OAK = 5 menit
/woodcutter cooldown all 1h30m            semua tipe + default
/woodcutter cooldown default 300          default (untuk tipe tanpa override)
/woodcutter cooldown fail 60              cooldown setelah gagal
/woodcutter cooldown reset <region|all>   hapus cooldown region
```
Format waktu: `300` (detik), `45s`, `5m`, `1h30m`, `2d`. Maksimal 1 tahun. `0` = tanpa cooldown.
Nilai `per-type` mengalahkan `default`, jadi gunakan `all` atau tipe spesifik. Cooldown melekat pada **region** dan bertahan saat restart.

## 5. Reward (via command)

```
/woodcutter reward list OAK
/woodcutter reward add OAK DIAMOND 1 5            1 diamond, chance 5%
/woodcutter reward add OAK APPLE 1-3 40           1-3 apple, chance 40%
/woodcutter reward add OAK hand 1 10              salin item di tangan (nama, lore, enchant, dll) apa adanya
/woodcutter reward remove OAK 3                   hapus reward nomor 3
/woodcutter reward money OAK 50                   uang untuk OAK (butuh Vault)
/woodcutter reward money enable|disable
```
- `<item>` = nama item Minecraft apa saja (tab-complete tersedia), atau `hand` untuk item custom.
- Jumlah `5` atau rentang `2-5`; chance 0-100 (default 100).
- Item terakhir tidak bisa dihapus (minimal satu reward per tipe).
- Semua perubahan langsung aktif dan ditulis ke `config.yml`. Inventory penuh → item dijatuhkan.

### Give

```
/woodcutter give Steve OAK            beri reward OAK (hasil roll chance & jumlah, tanpa XP/cooldown)
/woodcutter give Steve DIAMOND 16     beri 16 diamond
```

## 6. Alur minigame dan auto-pukul

1. **Minigame**: GUI baris tengah berisi slot MERAH dan satu HIJAU. Klik HIJAU sebanyak `minigame.required-successes`. GUI **tidak** menampilkan progress.
2. **Auto-pukul**: setelah minigame selesai GUI menutup. Pemain memukul sendiri tiap `chop.interval-ticks` tick; tiap pukulan menambah `100 / chop.hits-required` persen, ditampilkan di boss bar dan action bar.
3. Selesai di 100% → reward, XP, cooldown region.

Penebangan dibatalkan jika pemain terlalu jauh dari region (`minigame.max-distance`), melepas kapak dari main hand, mati, atau keluar. Salah klik di GUI mengikuti `minigame.miss.mode` (NO_PROGRESS / LOSE_PROGRESS / FAIL_MINIGAME). Region dikunci untuk satu pemain pada satu waktu.

```yaml
region:
  interact-range: 4.5
  click: RIGHT            # RIGHT | LEFT | BOTH
chop:
  hits-required: 8
  interval-ticks: 10
  show-bossbar: true
  bossbar-color: GREEN
  show-actionbar: true
```

## 7. Permissions

| Permission | Default | Keterangan |
|---|---|---|
| `woodcutter.use` | true | Memakai region + `/woodcutter stats` |
| `woodcutter.region` | op | pos1/pos2, buat/hapus/ubah region |
| `woodcutter.cooldown` | op | `/woodcutter cooldown` |
| `woodcutter.reward` | op | `/woodcutter reward` |
| `woodcutter.give` | op | `/woodcutter give` |
| `woodcutter.info` | op | `region info`, `list` |
| `woodcutter.reload` | op | `/woodcutter reload` |
| `woodcutter.admin` | op | Semua di atas |

Alias: `/wcjob`.

## 8. XP, database, Vault, PlaceholderAPI

Tidak berubah dari 1.0: XP/level di `config.yml`, penyimpanan SQLite (fallback YAML; region di tabel `regions` atau `data/regions.yml`), Vault opsional lewat reflection, placeholder `%woodcutter_level%`, `%woodcutter_xp%`, `%woodcutter_trees_cut%`, `%woodcutter_successful_hits%`, `%woodcutter_total_rewards%`, `%woodcutter_total_earnings%`, `%woodcutter_xp_next%`.

## 9. Testing cepat

1. `/woodcutter cooldown all 10s`, buat region kecil di udara dekat pohon.
2. Pegang kapak, arahkan ke udara dalam region, klik kanan → GUI muncul.
3. Klik slot hijau 5× → GUI menutup, pemain memukul sendiri, boss bar naik sampai 100%, reward masuk.
4. Klik lagi saat cooldown → pesan cooldown. Klik pada block log → tidak terjadi apa-apa.
5. `/woodcutter reward add OAK hand 1 100` dengan item custom di tangan, selesaikan lagi, cek item.
6. Restart server → region dan cooldown tetap ada.

## 10. Troubleshooting

| Masalah | Solusi |
|---|---|
| Tidak ada respons saat klik | Kapak di main hand? Klik ke **udara/air**, bukan block. Titik bidik masuk area region (`/woodcutter region info <nama>`)? World aktif di `worlds`? `region.click` sesuai tombol? |
| "Tipe pohon dinonaktifkan" | Aktifkan di `tree-types` |
| Pesan GUI masih menampilkan progress | Hapus `messages.yml` lama, restart |
| Reward uang tidak masuk | `reward money enable` dan pastikan Vault + economy terpasang |
| "SQLite could not be initialised" | Plugin fallback ke YAML; pakai Paper resmi atau set `storage.type: YAML` |

## 11. Struktur

```
WoodcutterJob.java
command/   WoodcutterCommand
config/    Settings
cooldown/  CooldownManager
database/  DatabaseManager, SQLiteManager, Player/Region repository (SQLite + YAML)
gui/       WoodcuttingGui
hook/      VaultHook, PlaceholderHook, WoodcutterExpansion
job/       JobManager
listener/  RegionInteractListener, GuiListener, PlayerConnectionListener
manager/   RegionManager, SelectionManager, WoodcuttingManager, GuiManager, MessageManager, PlayerDataManager
model/     RegionData, WoodcuttingSession, RewardData, PlayerJobData, CooldownData, enums
reward/    RewardManager, RewardResult
util/      TextUtil, ItemBuilder, SoundEffect, ParticleEffect
```

# WoodcutterJob

Plugin pekerjaan **penebang pohon** dengan minigame GUI untuk **Paper 1.21.11** (Java 21).
Dibuat oleh **RegayaGamtor**.

```
Pemain memegang kapak → klik pohon terdaftar → GUI Woodcutting → klik slot HIJAU
→ progress 100% → reward + XP → POHON TETAP ADA → cooldown (persisten) → bisa dipakai lagi
```

Pohon **tidak pernah dihancurkan**. Plugin hanya memakai block log sebagai "job point".

---

## 1. Requirements

| Wajib | Opsional |
|---|---|
| Java 21 (JDK untuk build) | Vault + plugin economy (reward uang) |
| Paper 1.21.11 | PlaceholderAPI (placeholder) |
| Maven 3.9+ (untuk build) | |

Vault diakses lewat reflection dan PlaceholderAPI hanya dimuat jika terpasang, jadi plugin tetap berjalan tanpa keduanya.

## 2. Build

```bash
mvn clean package
```
Hasil: `target/WoodcutterJob-1.0.0.jar`

Build pertama butuh internet untuk mengunduh `paper-api` dari `repo.papermc.io` dan `placeholderapi` dari `repo.extendedclip.com`.

## 3. Instalasi

1. Install Java 21 dan download Paper 1.21.11 dari papermc.io.
2. Build plugin (langkah 2) atau pakai JAR yang sudah jadi.
3. Salin `WoodcutterJob-1.0.0.jar` ke folder `plugins/`.
4. Restart server (jangan hanya `/reload`).
5. File `config.yml` dan `messages.yml` dibuat otomatis di `plugins/WoodcutterJob/`.

## 4. Command

| Command | Fungsi | Permission |
|---|---|---|
| `/woodcutter set` | Daftarkan pohon: jalankan lalu klik block log | `woodcutter.set` |
| `/woodcutter remove` | Hapus pohon terdaftar: jalankan lalu klik | `woodcutter.remove` |
| `/woodcutter info` | Info pohon yang dilihat (≤6 block), atau klik pohon | `woodcutter.info` |
| `/woodcutter list` | Jumlah dan daftar pohon terdaftar + status | `woodcutter.info` |
| `/woodcutter reload` | Reload config + messages | `woodcutter.reload` |
| `/woodcutter stats` | Statistik woodcutter sendiri | `woodcutter.use` |
| `/woodcutter cancel` | Batalkan mode admin | `woodcutter.set` |

Alias: `/wcjob`. Tab completion tersedia dan hanya menampilkan subcommand yang boleh dipakai pemain.

## 5. Permissions

| Permission | Default | Keterangan |
|---|---|---|
| `woodcutter.use` | true | Memakai pohon + `/woodcutter stats` |
| `woodcutter.set` | op | Mendaftarkan pohon |
| `woodcutter.remove` | op | Menghapus pohon |
| `woodcutter.reload` | op | Reload |
| `woodcutter.info` | op | Info dan list |
| `woodcutter.admin` | op | Semua permission admin di atas |

## 6. Mendaftarkan pohon

1. Jadilah admin (OP atau `woodcutter.admin`).
2. Jalankan `/woodcutter set`.
3. Dalam 30 detik, klik kanan atau kiri sebuah block log (mis. Oak Log di batang pohon).
4. Pesan: *"Pohon berhasil didaftarkan sebagai titik penebangan."*

Catatan:
- Hanya block log yang sudah didaftarkan yang menjadi job point. Log lain di dunia tidak terpengaruh.
- Banyak pohon sekaligus: set `registration.keep-mode-after-use: true`, lalu akhiri dengan `/woodcutter cancel`.
- Pohon terdaftar dilindungi dari break, ledakan, api, piston, dan strip kapak (`registration.protect-trees`).
- Jika block pohon berubah menjadi block lain, statusnya `INVALID` (lihat `/woodcutter info` / `list`) dan tidak crash.
- Tipe pohon: OAK, BIRCH, SPRUCE, JUNGLE, ACACIA, DARK_OAK, MANGROVE, CHERRY (log, wood, dan versi stripped). Atur di `tree-types`.

## 7. Minigame

Klik kanan pohon terdaftar dengan **kapak di main hand** (kayu/batu/besi/emas/diamond/netherite, dapat dikonfigurasi di `axes`).

- Baris tengah GUI berisi slot **MERAH** dan satu slot **HIJAU** (posisi acak setiap ronde).
- Klik **HIJAU** = hit: progress naik, swing arm, sound kayu, particle, GUI diperbarui, target pindah.
- Klik **MERAH** = miss, sesuai `minigame.miss.mode`:
  - `NO_PROGRESS` – tidak ada perubahan (default)
  - `LOSE_PROGRESS` – progress berkurang `penalty-percent`
  - `FAIL_MINIGAME` – langsung gagal, tree cooldown singkat (`cooldown.fail-seconds`)
- `required-successes: 5` → tiap hit 20%. Nilai 10 → tiap hit 10%.
- `moving-target.enabled: true` → target hijau bergerak bolak-balik (`speed-ticks`). Jika `false`, target diacak per ronde.
- Menutup GUI (`close-behavior`): `CANCEL` (default, pesan "Woodcutting dibatalkan."), `FAIL`, atau `RESUME` (lanjut dalam `resume-timeout-seconds`).
- Disconnect: sesi dibersihkan, tanpa reward, tanpa sisa state.
- Pemain terlalu jauh dari pohon (`max-distance`) → sesi dibatalkan.

Keamanan GUI: semua klik dibatalkan (ambil, pindah, shift-click, drag, double click, hotkey angka, swap offhand, memasukkan item). Hanya klik kiri/kanan pada slot target yang diproses. Item GUI diberi marker PDC dan dihapus otomatis jika bocor ke inventory.

## 8. Reward

Per tipe pohon di `rewards.<TIPE>.items`:
```yaml
- { material: OAK_LOG, amount: 8, chance: 100 }
- { material: STICK, min: 1, max: 3, chance: 30 }
- { material: APPLE, amount: 1, chance: 10 }
```
- Reward diberikan **tepat satu kali** per sesi (transisi state atomik `ACTIVE → COMPLETED`).
- Inventory penuh → item **dijatuhkan** di sekitar pemain (tidak pernah hilang) + pesan.
- Item tidak valid di config dilewati dengan warning, reward lain tetap diberikan.

## 9. Cooldown

- Cooldown melekat pada **pohon** (`world + x + y + z`), bukan pemain. Pemain lain melihat cooldown yang sama.
- Disimpan sebagai **timestamp** (`cooldown_until`) di database → bertahan saat disconnect, restart, dan reload. Tidak ada scheduler per pohon.
- Selama cooldown: pesan "Pohon ini sedang dalam masa pemulihan. Tersedia dalam: 4m 32s" (`cooldown.show-message`, `cooldown.show-time`).
- Saat satu pemain bermain di sebuah pohon, pohon itu dikunci untuk pemain lain.
- Durasi: `cooldown.default-seconds` (300) dan override per tipe di `cooldown.per-type`.

## 10. XP dan level

- XP per tipe: `job.xp.OAK: 25`, dst.
- Level: `levels` (level → total XP): 1:0, 2:100, 3:250, 4:500, 5:1000, ...
- Naik level: *"LEVEL UP! Woodcutter Level: 5"* + sound.

## 11. Database

Default **SQLite** (`plugins/WoodcutterJob/database.db`), tabel `players` dan `trees`, memakai `PreparedStatement` dan try-with-resources.

- Semua I/O berjalan di **satu thread khusus** (tidak ada query sinkron per klik). Data pemain dimuat saat pre-login (async) dan disimpan async saat quit, autosave, dan shutdown.
- `storage.type: YAML` → `data/<uuid>.yml` + `data/trees.yml`.
- Jika SQLite tidak bisa dibuka, plugin otomatis fallback ke YAML (ada log severe).
- Abstraksi: `PlayerRepository` / `TreeRepository` (implementasi SQLite dan YAML).
- Mengganti `storage.type` membutuhkan restart. Data tidak dimigrasi otomatis antar backend.

## 12. Vault (opsional)

Pasang Vault + plugin economy, lalu di `config.yml`:
```yaml
reward-money:
  enabled: true
  OAK: 25.0
```
Tanpa Vault, plugin berjalan normal dan reward uang dilewati.

## 13. PlaceholderAPI (opsional)

`%woodcutter_level%`, `%woodcutter_xp%`, `%woodcutter_trees_cut%`, `%woodcutter_successful_hits%`,
tambahan: `%woodcutter_total_rewards%`, `%woodcutter_total_earnings%`, `%woodcutter_xp_next%`.

## 14. Konfigurasi dan pesan

- `config.yml` – semua mekanik, GUI, cooldown, XP, reward, sound, particle (dikomentari lengkap).
- `messages.yml` – semua teks (MiniMessage). `<prefix>` otomatis diganti. Teks item GUI juga di sini.
- Nama sound memakai gaya enum (`BLOCK_WOOD_HIT`) atau key (`minecraft:block.wood.hit`).
- Nilai config yang salah → memakai default + warning di console.

## 15. Testing

1. **Minigame**: `/woodcutter set`, klik log. Pegang kapak, klik kanan log itu. Klik slot hijau 5×. Cek reward, XP (`/woodcutter stats`), dan pohon masih ada.
2. **Miss / close**: klik slot merah (progress tetap). Tutup GUI (pesan batal, tanpa reward).
3. **Cooldown**: klik pohon lagi → pesan cooldown dengan sisa waktu. Pohon lain tetap READY. Coba dengan akun kedua.
4. **Persistence restart**: selesaikan satu pohon, lalu `stop`, nyalakan lagi. Klik pohon yang sama → cooldown masih berjalan dan `/woodcutter stats` tetap.
5. **Reload**: `/woodcutter reload` → cooldown tidak reset.
6. **Inventory penuh**: penuhi inventory lalu selesaikan → reward dijatuhkan.
7. **Proteksi**: coba hancurkan atau strip pohon terdaftar → diblok.
8. **Cepat**: set `cooldown.default-seconds: 10` untuk uji cepat.

## 16. Troubleshooting

| Masalah | Solusi |
|---|---|
| Plugin tidak load | Pastikan Paper 1.21.x dan Java 21; cek `logs/latest.log` |
| "SQLite could not be initialised" | Plugin fallback ke YAML. Pakai Paper resmi (membawa driver SQLite) atau set `storage.type: YAML` |
| Pohon tidak merespons | Terdaftar? (`/woodcutter info`), kapak di main hand?, world diaktifkan di `worlds`?, tipe pohon aktif? |
| Status INVALID | Block berubah. Kembalikan log atau `/woodcutter remove` |
| Sound/particle tidak berbunyi | Cek warning `[config]` di console; gunakan nama yang valid |
| Pohon tidak bisa dihancurkan admin | Itu disengaja; `/woodcutter remove` dulu |
| Pesan tampil mentah (`<green>`) | Pastikan Paper (MiniMessage bawaan), bukan Spigot |
| Build gagal mengunduh dependency | Pastikan akses ke `repo.papermc.io` dan `repo.extendedclip.com` |

## 17. Dependency

- **Wajib**: Paper 1.21.11 (provided).
- **Opsional (softdepend)**: Vault, PlaceholderAPI.

## 18. Struktur

```
src/main/java/com/regayagamtor/woodcutterjob/
  WoodcutterJob.java
  command/   WoodcutterCommand
  config/    Settings
  cooldown/  CooldownManager
  database/  DatabaseManager, SQLiteManager, Player/TreeRepository (+ SQLite & YAML impl)
  gui/       WoodcuttingGui
  hook/      VaultHook, PlaceholderHook, WoodcutterExpansion
  job/       JobManager
  listener/  TreeInteract, TreeProtection, Gui, PlayerConnection
  manager/   Tree, WoodcuttingManager, Gui, Message, PlayerData, AdminMode
  model/     TreeLocation, TreeData, WoodcuttingSession, PlayerJobData, RewardData, CooldownData, enums
  reward/    RewardManager, RewardResult
  util/      TextUtil, ItemBuilder, SoundEffect, ParticleEffect
```

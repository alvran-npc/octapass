# Advanced OctaPass

**Advanced OctaPass** adalah fondasi plugin seasonal progression / Battle Pass untuk Paper dengan Java 21. Kode menggunakan package `net.octa.octapass`, Apache Maven, Adventure/MiniMessage, SQLite sebagai penyimpanan default, serta opsi MariaDB/MySQL melalui HikariCP.

> **Status implementasi:** ini adalah source release awal (`1.0.0-SNAPSHOT`), bukan klaim bahwa seluruh daftar fitur dalam spesifikasi awal sudah selesai. Fokus rilis ini adalah progression pass non-combat yang aman, quest harian/mingguan/event, Free/Premium track, reward terbatas, cache profile, penyimpanan async, placeholder, command, API, dan menu inventory dasar. Lihat bagian *Batasan yang diketahui*.

## Fitur yang tersedia

- Java 21 dan Paper API; `api-version: 1.20`.
- SQLite default dan MariaDB/MySQL dengan HikariCP. Operasi JDBC dijalankan pada executor database terpisah. Ada tabel versi schema awal (`octapass_schema_version`) untuk evolusi migrasi berikutnya.
- Cache profile pemain, autosave untuk profile dirty, leaderboard level/XP/quest selesai.
- Season dengan tanggal mulai/akhir, max level, kurva XP linear/eksponensial/custom.
- Daily, weekly, season, dan event quest; pemilihan quest daily/weekly berbobot; assignment per profile; reroll gratis harian.
- Action registry untuk trigger built-in dan custom; anti-farm untuk block yang sebelumnya dipasang player pada sesi aktif.
- Free/Premium reward tracks, claim all, claim lock per player, pending delivery untuk item saat inventory penuh.
- Reward yang diaktifkan: item vanilla yang lolos allow-list, message, title, sound, uang Vault, XP booster, dan random-choice.
- Personal/global XP booster service.
- GUI inventory dasar untuk main menu, quest list, reward track, leaderboard, premium info/purchase confirmation, dan settings.
- PlaceholderAPI expansion `%octapass_...%` dan objective quest berbasis polling placeholder.
- Vault Economy hook, OctaGens soft-detection, custom API service, dan custom Bukkit events.
- Startup banner dan dependency summary.

## Build

Persyaratan lokal:

- JDK 21
- Apache Maven 3.9.x atau lebih baru
- Koneksi internet untuk mengunduh dependency dari Maven repositories

```bash
mvn clean test package
```

GitHub Actions tersedia di `.github/workflows/build.yml`. Workflow berjalan pada push, pull request ke `main`/`master`, tag `v*`, atau saat dijalankan manual dari tab **Actions**. Workflow memakai JDK 21, menjalankan `mvn clean verify`, lalu mengunggah JAR sebagai artifact bernama `AdvancedOctaPass-<commit>`.

JAR final (sudah termasuk runtime dependency yang di-shade) akan dihasilkan di:

```text
target/AdvancedOctaPass-1.0.0-SNAPSHOT.jar
```

`maven-shade-plugin` membungkus HikariCP, SQLite JDBC, MariaDB JDBC, dan Gson ke dalam JAR; HikariCP dan Gson di-relocate ke namespace plugin. Paper, Vault, dan PlaceholderAPI memakai scope `provided` dan tetap menjadi soft dependency saat runtime.

## Instalasi

1. Build project dengan JDK 21 dan Maven 3.9.x+.
2. Salin `target/AdvancedOctaPass-<version>.jar` ke folder `plugins/`.
3. Pastikan server menjalankan Paper yang sesuai dengan versi API compile target.
4. Jalankan server sekali untuk membuat direktori `plugins/AdvancedOctaPass/` dan file konfigurasi default.
5. Edit `config.yml`, `season.yml`, `quests/`, dan `rewards/` sesuai kebutuhan.
6. Restart server setelah mengubah backend database atau mengganti struktur konfigurasi.

SQLite aktif secara default. Untuk MariaDB/MySQL, ubah `storage.type` ke `mysql` atau `mariadb`, kemudian isi host, port, database, username, dan password. Jangan membagikan kredensial database di log atau repository publik.

## Struktur konfigurasi

```text
plugins/AdvancedOctaPass/
├── config.yml
├── messages.yml
├── season.yml
├── rewards/
│   ├── free.yml
│   └── premium.yml
├── quests/
│   ├── daily.yml
│   ├── weekly.yml
│   ├── season.yml
│   └── events/
│       ├── christmas.yml
│       └── ramadan.yml
├── gui/
│   ├── main.yml
│   ├── rewards.yml
│   └── quests.yml
└── lang/
    ├── id.yml
    └── en.yml
```

File `gui/*.yml`, `messages.yml`, dan `lang/*.yml` disediakan sebagai template/dokumentasi awal; menu dasar dan beberapa pesan saat ini masih dibangun langsung oleh GUI/service Java. Layout belum sepenuhnya data-driven.

### Season dan XP curve

`season.yml` memiliki `season.id`, `season.name`, `season.start`, `season.end`, `max-level`, dan `xp-curve`.

- `linear`: kebutuhan XP level `n` dihitung dari `base + (n - 1) * step`.
- `exponential`: kebutuhan XP level `n` mengikuti `base * factor^(n - 1)`.
- `custom`: nilai level dapat diisi pada `xp-curve.custom`; jika level belum ditulis, fallback ke formula linear.

`start` dan `end` memakai ISO-8601. Reset dan penjadwalan quest membaca `timezone` dari `config.yml`.

### Membuat quest

Contoh:

```yaml
quests:
  gather_oak:
    id: gather_oak
    display-name: "<green>Gather Oak Logs</green>"
    description:
      - "<gray>Collect oak logs from the wild.</gray>"
    icon: OAK_LOG
    type: block-break
    variable: OAK_LOG
    amount: 64
    xp: 80
    worlds: []
    permission: ""
    weight: 5
    premium-only: false
```

Quest keys:

- `id` (menggunakan key YAML), `display-name`, `description`, `icon`, `type`, `variable`, `amount`, `xp`
- `worlds`, `permission`, `weight`, `premium-only`
- `start-date` dan `end-date` pada event quest

`variable` memfilter material/entity/action sesuai listener. Pool daily dan weekly memakai key yang unik; jumlah assignment dikendalikan `quest-reset.daily-count` dan `quest-reset.weekly-count`.

Tipe action yang diimplementasikan pada rilis ini: `block-break`, `block-place`, `bucket-place`, `harvest-crops`, `fish`, `craft`, `smelt`, `consume`, `breed`, `tame`, `shear`, `milk`, `gain-experience`, `move`, `sprint`, `sneak`, `swim`, `fly`, `glide`, `login`, `playtime`, `chat`, `execute-command`, `right-click`, `left-click`, `right-click-block`, `left-click-block`, `honey-extract`, dan `honey-comb-extract`.

Beberapa trigger membutuhkan kondisi yang tepat: `harvest-crops` menghitung tanaman yang sudah matang, `smelt` menghitung hasil yang diambil dari furnace, `playtime` dicatat per interval, dan `placeholderapi_*` dibaca secara polling. Cooldown per quest, regex/list filters lain selain equality/regex sederhana, dan permission/world filters diproses sebagian sesuai fields yang terdaftar pada definisi quest.

### Objective PlaceholderAPI

Contoh objective generator OctaGens dari `quests/weekly.yml`:

```yaml
  generator_builder:
    id: generator_builder
    display-name: "<aqua>Generator Builder</aqua>"
    description: ["<gray>Place generators tracked by OctaGens.</gray>"]
    icon: DISPENSER
    type: placeholderapi_integer_%octagens_generators_placed%
    variable: ">=10"
    amount: 10
    xp: 250
```

Format yang didukung:

- `placeholderapi_integer_<placeholder>` dengan `variable: ">=N"` untuk nilai numerik kumulatif.
- `placeholderapi_match_<placeholder>` dengan `variable` berisi nilai yang diharapkan.

Polling placeholder diatur oleh `anti-exploit.placeholder-poll-ticks`. Placeholder eksternal harus tersedia di server. Contoh di atas bergantung pada expansion OctaGens yang menyediakan `%octagens_generators_placed%`.

### Reward

Reward ditulis di `rewards/free.yml` dan `rewards/premium.yml` sebagai daftar berdasarkan level. Contoh:

```yaml
rewards:
  1:
    - type: item
      material: BREAD
      amount: 8
      name: "<gold>Explorer Rations</gold>"
    - type: message
      text: "<green>Level reward unlocked!</green>"
```

Tipe yang aktif pada source awal:

- `item`: vanilla item dengan name, lore, dan CustomModelData; material harus lolos `SafeItemPolicy`.
- `message`, `title`, `sound`
- `money`: memakai provider Vault yang tersedia.
- `xp-boost`: `multiplier` dan `duration-seconds`.
- `random-choice`: pilihan berbobot dengan nested `reward`.

Reward item sengaja dibatasi untuk blok, makanan, dan item kosmetik/utilitas yang diizinkan. Item senjata, armor, perlengkapan tempur, bahan berbahaya, dan item sejenis ditolak oleh allow-list. Reward `command`, `currency` spesifik ExcellentEconomy, permission, MMOItems dan AdvancedEnchantments belum diaktifkan.

## Commands

| Command | Keterangan |
|---|---|
| `/octapass` atau `/octapass open` | Buka menu utama |
| `/octapass quests` | Buka quest journal |
| `/octapass rewards` | Buka reward track |
| `/octapass claim` | Claim seluruh track yang tersedia |
| `/octapass buylevel [amount]` | Beli level jika diaktifkan |
| `/octapass reroll` | Reroll daily quest dengan kuota gratis |
| `/octapass top [xp\|quests]` | Leaderboard |
| `/octapass premium` | Info Premium Pass |
| `/octapass premium buy` | Dialog konfirmasi pembelian |
| `/octapass settings` | Pengaturan notifikasi |
| `/octapass reload` | Reload file konfigurasi utama |
| `/octapass give xp\|level\|premium <player> [amount]` | Admin grant |
| `/octapass set level <player> <level>` | Atur level |
| `/octapass reset <player\|all>` | Reset profile |
| `/octapass season start\|end\|status` | Kontrol season (runtime sementara) |
| `/octapass event start\|stop <id>` | Kontrol event quest |
| `/octapass booster give <player> <multiplier> <seconds>` | Beri personal booster |
| `/octapass debug` | Status diagnostik |

Alias: `/bp` dan `/battlepass`. Alias `/op` tidak dipakai karena bentrok dengan command operator bawaan Bukkit.

## Permissions

- `octapass.use`: akses menu/quest.
- `octapass.premium`: akses Premium track.
- `octapass.admin`: induk permission admin.
- `octapass.admin.reload`, `.give`, `.set`, `.reset`, `.premium`, `.season`, `.event`, `.booster`, `.debug`: permission granular.

## Placeholders

PlaceholderAPI expansion identifier: `octapass`.

- `%octapass_level%`
- `%octapass_xp%`
- `%octapass_xp_total%` atau alias `%octapass_total_xp%`
- `%octapass_xp_needed%`
- `%octapass_xp_progress_bar%`
- `%octapass_premium%`
- `%octapass_season_name%`
- `%octapass_season_time_left%`
- `%octapass_daily_reset_in%`
- `%octapass_weekly_reset_in%`
- `%octapass_quests_completed%`
- `%octapass_booster_active%`
- `%octapass_booster_time_left%`
- `%octapass_booster_multiplier%`
- `%octapass_top_level_<n>_name%`
- `%octapass_top_level_<n>_value%`

## API untuk developer

Plugin mendaftarkan `OctaPassAPI` melalui Bukkit ServicesManager:

```java
RegisteredServiceProvider<OctaPassAPI> registration =
    Bukkit.getServicesManager().getRegistration(OctaPassAPI.class);
if (registration != null) {
    OctaPassAPI api = registration.getProvider();
    api.addXp(player.getUniqueId(), 100);
    boolean premium = api.hasPremium(player.getUniqueId());
}
```

Method penting:

- `addXp(UUID, long)`
- `getLevel(UUID)` -> `OptionalInt`
- `hasPremium(UUID)`
- `setPremium(UUID, boolean)`
- `registerQuestAction(String, QuestAction)`
- `triggerCustom(Player, String, String, int)`

Registrasi action custom:

```java
api.registerQuestAction("my_custom_action", (player, variable, amount) -> {
    // Optional observer callback, dipanggil setelah progress objective bertambah.
});
// Panggil ini dari listener event plugin kamu:
api.triggerCustom(player, "my_custom_action", "example", 1);
```

Custom Bukkit events: `OctaPassLevelUpEvent`, `OctaPassQuestCompleteEvent`, `OctaPassRewardClaimEvent` (cancellable), dan `OctaPassSeasonChangeEvent`.

## Integrasi eksternal dan hasil analisis repository

### Vault

Hook memakai `ServicesManager` untuk mengambil provider `Economy` Vault. Jika Vault atau provider economy tidak tersedia, pembelian dan reward uang gagal dengan pesan/log yang sesuai.

### OctaGens

Dokumentasi repository OctaGens menyebut event generator/energy, `McuMaxUpgradeEvent`, `DropSellEvent`, `OreGeneratorFormEvent`, `OctaGensAPI.get()`, serta beberapa placeholder generator dan magicule. Namun README publik yang diperiksa tidak memberikan package dan signature Java event/API secara lengkap. Karena itu source release ini tidak mengimpor nama kelas atau method yang tidak dapat diverifikasi. Hook mendeteksi plugin OctaGens dan mengarahkan objective ke PlaceholderAPI polling; contoh quest `%octagens_generators_placed%` adalah fallback yang hanya bekerja jika placeholder tersebut benar-benar tersedia pada versi terpasang.

### OctaMMO

Hook deteksi berada di `hook/octammo/OctaMMOHook.java`. Dokumentasi repository OctaMMO menyebut modul `octammo-api` dan API version 2, tetapi halaman dokumentasi yang bisa diakses tidak mendefinisikan event publik atau signature method yang cukup untuk implementasi listener yang aman. Source release ini tidak mengarang nama event atau method. Integrasi spesifik altar/upgrade OctaMMO belum tersedia.

### MMOItems dan AdvancedEnchantments

Keduanya dicantumkan sebagai soft dependency agar tidak menghambat startup server. Hook reward/quest khusus belum diaktifkan. Semua objective/reward yang berkaitan dengan penggunaan/upgrade/enchant perlengkapan sengaja tidak diaktifkan dalam build non-combat ini.

## Batasan yang diketahui

Source release ini **belum memenuhi seluruh spesifikasi 13 bagian secara penuh**. Batasan yang perlu diketahui sebelum dipakai di server produksi:

1. Tidak semua jenis action/quest yang diminta diimplementasikan. Quest tempur/serangan, projectile, dan equipment progression tidak disertakan.
2. GUI adalah menu dasar yang dibangun Java, belum memiliki seluruh animasi bertahap, hover-equivalent penuh, live refresh semua tombol, resource-pack font layout, atau layout engine berbasis semua YAML.
3. `gui/*.yml`, `messages.yml`, dan `lang/*.yml` masih template dan belum menjadi sumber tunggal seluruh teks/layout.
4. Event quest start/stop manual tersedia, tetapi pengelolaan jadwal, persistensi state event lintas restart, dan kalender season lanjutan masih minimal.
5. Reroll saat ini memakai kuota gratis; pembelian reroll dengan currency belum diimplementasikan.
6. Provider ExcellentEconomy langsung belum diimplementasikan. Economy saat ini melalui Vault; dukungan multi-currency belum ada.
7. MMOItems dan AdvancedEnchantments belum memiliki hook langsung.
8. OctaGens memakai fallback placeholder, bukan listener event Java, karena signature package/API tidak terlihat dalam dokumentasi yang dapat diverifikasi.
9. OctaMMO belum memiliki hook event langsung. API module/version disebut dalam dokumentasi, tetapi tidak cukup informasi publik yang berhasil diverifikasi untuk membuat integrasi compile-safe.
10. Premium purchase tersedia melalui Vault; season archive/XP inheritance dan migrasi schema masih fondasi awal.
11. Profil baru diproses saat online dan leaderboard diperbarui terjadwal; untuk deployment besar perlu profiling dan test integrasi/database lebih lanjut.
12. Folia scheduler abstraction dan Floodgate form UI belum disediakan pada source release ini.
13. Hot reload saat ini reload konfigurasi dan data runtime yang didukung, tetapi tidak mengganti konfigurasi koneksi database tanpa restart.

Jangan memasang source release awal ini sebagai pengganti plugin Battle Pass production tanpa pengujian di staging, backup database, dan pemeriksaan kompatibilitas plugin lain.

## Lisensi

MIT. Lihat [`LICENSE`](LICENSE).

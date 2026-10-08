package com.jadwal.motor

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class Kartu(val id: String, val ikon: String, val judul: String, val bulan: Int,
            val pakaiKm: Boolean, val pakaiKet: Boolean, val item: List<String>)

val KARTU = listOf(
    Kartu("service", "🔧", "Jadwal Service Motor", 3, true, true, listOf("PCX", "SOULT GT")),
    Kartu("oli", "🛢️", "Jadwal Ganti Oli", 1, true, false, listOf("PCX", "SOULT GT")),
    Kartu("ac", "❄️", "Jadwal Cuci AC", 3, false, false, listOf("AC 1", "AC 2"))
)
val FMT = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

fun prefs(c: Context) = c.getSharedPreferences("jadwal", Context.MODE_PRIVATE)

fun tanggalNext(last: Long, bulan: Int): Calendar = Calendar.getInstance().apply {
    timeInMillis = last; add(Calendar.MONTH, bulan)
    set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
}

fun jadwalkan(c: Context, k: Int, i: Int) {
    val kt = KARTU[k]
    val last = prefs(c).getLong("${kt.id}_${i}_tgl", 0L)
    val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(c, k * 10 + i,
        Intent(c, AlarmReceiver::class.java).putExtra("judul", "${kt.judul} - ${kt.item[i]}")
            .putExtra("id", k * 10 + i),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    am.cancel(pi)
    if (last == 0L) return
    val t = tanggalNext(last, kt.bulan).timeInMillis
    if (t <= System.currentTimeMillis()) return
    if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val simpan = prefs(c).getString("nada", null)
        val nada: Uri = if (simpan != null) Uri.parse(simpan)
            else (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        val chId = "alarm_" + nada.toString().hashCode()
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(chId, "Pengingat Jadwal", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(nada, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val buka = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, chId) else Notification.Builder(c).setSound(nada)
        nm.notify(i.getIntExtra("id", 0), b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Waktunya jadwal!").setContentText(i.getStringExtra("judul"))
            .setContentIntent(buka).setAutoCancel(true).build())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        KARTU.forEachIndexed { k, kt -> kt.item.indices.forEach { jadwalkan(c, k, it) } }
    }
}

class MainActivity : Activity() {
    lateinit var root: LinearLayout
    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val sv = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F1F5F9")) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(32), dp(16), dp(16)) }
        sv.addView(root); setContentView(sv)
        tampil()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 99 && res == RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val e = prefs(this).edit()
            if (uri == null) e.remove("nada") else e.putString("nada", uri.toString())
            e.apply()
            Toast.makeText(this, "Nada disimpan", Toast.LENGTH_SHORT).show()
            tampil()
        }
    }

    fun tampil() {
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = "Jadwal Motor & AC"; textSize = 24f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A")); setPadding(0, 0, 0, dp(12))
        })
        val p = prefs(this)
        val u = p.getString("nada", null)
        val namaNada = if (u == null) "Nada alarm bawaan HP"
            else RingtoneManager.getRingtone(this, Uri.parse(u))?.getTitle(this) ?: "Nada pilihan"
        root.addView(Button(this).apply {
            text = "🔔 Nada Pengingat: $namaNada"; isAllCaps = false
            setOnClickListener {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pilih nada pengingat")
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (u != null) Uri.parse(u) else null as Uri?)
                startActivityForResult(i, 99)
            }
        })
        root.addView(Button(this).apply {
            text = "▶ Tes Bunyi Alarm Sekarang"; isAllCaps = false
            setOnClickListener {
                sendBroadcast(Intent(this@MainActivity, AlarmReceiver::class.java)
                    .putExtra("judul", "Tes pengingat berhasil").putExtra("id", 999))
            }
        })
        KARTU.forEachIndexed { k, kt ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            }
            card.addView(TextView(this).apply {
                text = "${kt.ikon}  ${kt.judul}"; textSize = 19f; setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1E3A8A"))
            })
            kt.item.forEachIndexed { i, nama ->
                val tgl = p.getLong("${kt.id}_${i}_tgl", 0L)
                var info = "Belum ada data"
                if (tgl != 0L) {
                    info = "Terakhir: ${FMT.format(Date(tgl))}"
                    if (kt.pakaiKm) info += "\nKilometer: ${p.getString("${kt.id}_${i}_km", "-")} km"
                    if (kt.pakaiKet) info += "\nKeterangan: ${p.getString("${kt.id}_${i}_ket", "-")}"
                    val n = tanggalNext(tgl, kt.bulan)
                    info += "\nIngatkan lagi: ${FMT.format(n.time)}" +
                        if (n.timeInMillis < System.currentTimeMillis()) " (TERLAMBAT)" else ""
                }
                card.addView(TextView(this).apply {
                    text = nama; textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(12), 0, 0)
                })
                card.addView(TextView(this).apply { text = info; textSize = 14f; setTextColor(Color.DKGRAY) })
                card.addView(Button(this).apply {
                    text = "Edit Data"; setOnClickListener { edit(k, i) }
                })
            }
            root.addView(card)
        }
    }

    fun edit(k: Int, i: Int) {
        val kt = KARTU[k]; val p = prefs(this)
        var tgl = p.getLong("${kt.id}_${i}_tgl", System.currentTimeMillis())
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val btnTgl = Button(this).apply { text = "Tanggal: ${FMT.format(Date(tgl))}" }
        btnTgl.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = tgl }
            DatePickerDialog(this, { _, y, m, d ->
                tgl = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis
                btnTgl.text = "Tanggal: ${FMT.format(Date(tgl))}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        box.addView(btnTgl)
        val km = EditText(this).apply {
            hint = "Kilometer"; inputType = InputType.TYPE_CLASS_NUMBER
            setText(p.getString("${kt.id}_${i}_km", ""))
        }
        val ket = EditText(this).apply {
            hint = "Keterangan yang diservice"; setText(p.getString("${kt.id}_${i}_ket", ""))
        }
        if (kt.pakaiKm) box.addView(km)
        if (kt.pakaiKet) box.addView(ket)
        AlertDialog.Builder(this).setTitle("${kt.judul} - ${kt.item[i]}").setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                p.edit().putLong("${kt.id}_${i}_tgl", tgl)
                    .putString("${kt.id}_${i}_km", km.text.toString())
                    .putString("${kt.id}_${i}_ket", ket.text.toString()).apply()
                jadwalkan(this, k, i); tampil()
                Toast.makeText(this, "Tersimpan, pengingat diatur", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Batal", null).show()
    }
}

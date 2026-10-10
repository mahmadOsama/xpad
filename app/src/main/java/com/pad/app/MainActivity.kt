package com.pad.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class Cfg(
    val ip: String, val slot: Int, val scale: Float, val alpha: Float,
    val dead: Float, val vib: Boolean, val lefty: Boolean, val floating: Boolean
)

class MainActivity : AppCompatActivity() {
    private var padActive = false
    private var editing = false
    private var lastBack = 0L

    private val allItems = listOf(
        "A" to "A", "B" to "B", "X" to "X", "Y" to "Y",
        "LB" to "LB", "RB" to "RB", "LT" to "LT", "RT" to "RT",
        "BACK" to "Back (<)", "START" to "Start (>)",
        "LSB" to "Left stick click (LS)", "RSB" to "Right stick click (RS)",
        "DU" to "D-pad up", "DD" to "D-pad down", "DL" to "D-pad left", "DR" to "D-pad right",
        "LST" to "Left stick", "RST" to "Right stick"
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showMenu()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && padActive) applyFullscreen(true)
    }

    private fun applyFullscreen(on: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !on)
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctl.show(WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 24, 0, 0) }

    private fun startPad(cfg: Cfg, edit: Boolean) {
        padActive = true
        editing = edit
        setContentView(PadView(this, cfg, getSharedPreferences("p", MODE_PRIVATE), edit))
        applyFullscreen(true)
    }

    private fun showMenu() {
        padActive = false
        editing = false
        applyFullscreen(false)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val ip = EditText(this).apply { hint = "auto"; setText(prefs.getString("ip", "")) }
        val slot = EditText(this).apply {
            hint = "1 / 2 / 3"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("slot", "1"))
        }
        fun bar(mx: Int, pv: Int) = SeekBar(this).apply { max = mx; progress = pv }
        val size = bar(50, prefs.getInt("size", 30))
        val opac = bar(60, prefs.getInt("opac", 60))
        val dead = bar(30, prefs.getInt("dead", 8))
        val vib = CheckBox(this).apply { text = "Vibration"; isChecked = prefs.getBoolean("vib", true) }
        val lefty = CheckBox(this).apply { text = "Left-handed (mirrors the default layout)"; isChecked = prefs.getBoolean("lefty", false) }
        val floating = CheckBox(this).apply { text = "Floating sticks"; isChecked = prefs.getBoolean("floating", false) }
        val go = Button(this).apply { text = "Connect" }
        val editBtn = Button(this).apply { text = "Edit layout (drag buttons)" }
        val resetBtn = Button(this).apply { text = "Reset layout" }
        val offBtn = Button(this).apply { text = "Enable / disable buttons" }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(label("Server IP (leave empty = auto-discovery)")); addView(ip)
            addView(label("Player number")); addView(slot)
            addView(label("Button size")); addView(size)
            addView(label("Opacity")); addView(opac)
            addView(label("Stick dead zone")); addView(dead)
            addView(vib); addView(lefty); addView(floating)
            addView(go); addView(editBtn); addView(offBtn); addView(resetBtn)
        }
        setContentView(ScrollView(this).apply { addView(col) })

        fun commit(): Cfg {
            val n = slot.text.toString().toIntOrNull() ?: 1
            val ipText = ip.text.toString().trim()
            prefs.edit()
                .putString("ip", ipText).putString("slot", n.toString())
                .putInt("size", size.progress).putInt("opac", opac.progress).putInt("dead", dead.progress)
                .putBoolean("vib", vib.isChecked).putBoolean("lefty", lefty.isChecked)
                .putBoolean("floating", floating.isChecked).apply()
            return Cfg(
                ipText, n, (70 + size.progress) / 100f, (40 + opac.progress) / 100f,
                dead.progress / 100f, vib.isChecked, lefty.isChecked, floating.isChecked
            )
        }

        go.setOnClickListener { startPad(commit(), false) }
        offBtn.setOnClickListener {
            val off = HashSet<String>(prefs.getStringSet("off", null) ?: emptySet())
            val names = allItems.map { it.second }.toTypedArray()
            val checked = BooleanArray(allItems.size) { allItems[it].first !in off }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Checked = enabled")
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    if (isChecked) off.remove(allItems[which].first) else off.add(allItems[which].first)
                }
                .setPositiveButton("Save") { _, _ -> prefs.edit().putStringSet("off", off).apply() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        editBtn.setOnClickListener { startPad(commit(), true) }
        resetBtn.setOnClickListener {
            val ed = prefs.edit()
            for (k in prefs.all.keys) if (k.startsWith("lay_")) ed.remove(k)
            ed.apply()
            Toast.makeText(this, "Layout reset", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!padActive) { super.onBackPressed(); return }
        if (editing) { showMenu(); return }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) showMenu()
        else {
            lastBack = now
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}

class PadView(c: Context, val cfg: Cfg, val prefs: SharedPreferences, val edit: Boolean) : View(c) {
    class Btn(val id: String, val t: String, var x: Float, var y: Float, val r: Float, val m: Int)

    private val ex = Executors.newSingleThreadExecutor()
    private val sock = DatagramSocket().apply { broadcast = true }
    private val vibrator = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val auto = cfg.ip.isEmpty()
    @Volatile private var target: InetAddress? = null
    @Volatile private var lastPong = 0L
    @Volatile private var alive = true
    @Volatile private var last = ""

    private var btns = listOf<Btn>()
    private var w0 = 0
    private var h0 = 0
    private var sr = 0f
    private var dlcx = 0f; private var dlcy = 0f
    private var drcx = 0f; private var drcy = 0f
    private var lcx = 0f; private var lcy = 0f
    private var rcx = 0f; private var rcy = 0f
    private var lkx = 0f; private var lky = 0f
    private var rkx = 0f; private var rky = 0f
    private var lid = -1
    private var rid = -1
    private var mask = 0
    private var dragId: String? = null
    private val off: Set<String> = HashSet<String>(prefs.getStringSet("off", null) ?: emptySet())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        if (!edit) {
            if (!auto) Thread {
                try { target = InetAddress.getByName(cfg.ip) } catch (_: Exception) {}
            }.start()

            Thread {
                val buf = ByteArray(64)
                while (alive) {
                    try {
                        val pk = DatagramPacket(buf, buf.size)
                        sock.receive(pk)
                        val msg = String(pk.data, 0, pk.length)
                        if (msg.startsWith("XPAD_HERE")) {
                            lastPong = System.currentTimeMillis()
                            if (auto) target = pk.address
                        } else if (msg.startsWith("PONG")) {
                            lastPong = System.currentTimeMillis()
                        }
                    } catch (_: Exception) {
                        if (!alive) break
                    }
                }
            }.start()

            Thread {
                val ping = "PING".toByteArray()
                val disc = "XPAD_DISCOVER".toByteArray()
                while (alive) {
                    try {
                        val now = System.currentTimeMillis()
                        if (auto && now - lastPong > 4000) target = null
                        val t = target
                        if (t == null) {
                            if (auto) sock.send(DatagramPacket(disc, disc.size, InetAddress.getByName("255.255.255.255"), 5005))
                        } else {
                            sock.send(DatagramPacket(ping, ping.size, t, 5005))
                            val lm = last
                            if (lm.isNotEmpty()) {
                                val b = lm.toByteArray()
                                sock.send(DatagramPacket(b, b.size, t, 5005))
                            }
                        }
                    } catch (_: Exception) {}
                    postInvalidate()
                    try { Thread.sleep(1000) } catch (_: Exception) {}
                }
            }.start()
        }
    }

    override fun onDetachedFromWindow() {
        alive = false
        try { sock.close() } catch (_: Exception) {}
        ex.shutdown()
        super.onDetachedFromWindow()
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        Math.hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()

    @Suppress("DEPRECATION")
    private fun buzz() {
        try {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            else vibrator?.vibrate(20)
        } catch (_: Exception) {}
    }

    override fun onSizeChanged(w: Int, h: Int, a: Int, b: Int) {
        w0 = w; h0 = h
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
        fun fx(f: Float): Float = if (cfg.lefty) w * (1f - f) else w * f
        sr = h * 0.2f * cfg.scale
        dlcx = fx(0.12f); dlcy = h * 0.68f
        drcx = fx(0.62f); drcy = h * 0.68f
        val r = h * 0.09f * cfg.scale
        val ax = fx(0.87f); val ay = h * 0.62f; val d = r * 1.7f
        val dx = fx(0.36f); val dy = h * 0.70f; val dr = r * 0.8f; val dd = dr * 1.9f
        btns = listOf(
            Btn("A", "A", ax, ay + d, r, 0x1000), Btn("B", "B", ax + d, ay, r, 0x2000),
            Btn("X", "X", ax - d, ay, r, 0x4000), Btn("Y", "Y", ax, ay - d, r, 0x8000),
            Btn("LB", "LB", fx(0.10f), h * 0.12f, r, 0x0100), Btn("RB", "RB", fx(0.90f), h * 0.12f, r, 0x0200),
            Btn("LT", "LT", fx(0.24f), h * 0.12f, r, 0x10000), Btn("RT", "RT", fx(0.76f), h * 0.12f, r, 0x20000),
            Btn("BACK", "<", fx(0.42f), h * 0.12f, r * 0.8f, 0x0020), Btn("START", ">", fx(0.58f), h * 0.12f, r * 0.8f, 0x0010),
            Btn("LSB", "LS", fx(0.12f), h * 0.30f, r * 0.8f, 0x0040),
            Btn("RSB", "RS", fx(0.62f), h * 0.30f, r * 0.8f, 0x0080),
            Btn("DU", "^", dx, dy - dd, dr, 0x0001), Btn("DD", "v", dx, dy + dd, dr, 0x0002),
            Btn("DL", "<", dx - dd, dy, dr, 0x0004), Btn("DR", ">", dx + dd, dy, dr, 0x0008)
        )
        loadLayout(w, h)
        lcx = dlcx; lcy = dlcy; rcx = drcx; rcy = drcy
        lkx = lcx; lky = lcy; rkx = rcx; rky = rcy
    }

    private fun loadLayout(w: Int, h: Int) {
        fun saved(id: String): Pair<Float, Float>? {
            val s = prefs.getString("lay_$id", null) ?: return null
            val parts = s.split(";")
            val fx = parts.getOrNull(0)?.toFloatOrNull() ?: return null
            val fy = parts.getOrNull(1)?.toFloatOrNull() ?: return null
            return Pair(fx * w, fy * h)
        }
        for (b in btns) saved(b.id)?.let { b.x = it.first; b.y = it.second }
        saved("LST")?.let { dlcx = it.first; dlcy = it.second }
        saved("RST")?.let { drcx = it.first; drcy = it.second }
    }

    private fun saveOne(id: String, x: Float, y: Float) {
        prefs.edit().putString("lay_$id", "${x / w0};${y / h0}").apply()
    }

    private fun baseColor(m: Int): Int = when (m) {
        0x1000 -> Color.rgb(107, 190, 70)
        0x2000 -> Color.rgb(222, 61, 54)
        0x4000 -> Color.rgb(40, 140, 220)
        0x8000 -> Color.rgb(247, 200, 28)
        else -> Color.rgb(70, 70, 70)
    }

    private fun lighten(c: Int): Int =
        Color.rgb((Color.red(c) + 255) / 2, (Color.green(c) + 255) / 2, (Color.blue(c) + 255) / 2)

    private fun withAlpha(c: Int, k: Float = 1f): Int =
        Color.argb((255 * cfg.alpha * k).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun drawStick(c: Canvas, cx: Float, cy: Float, kx: Float, ky: Float, disabled: Boolean) {
        if (disabled && !edit) return
        val k = if (disabled) 0.25f else 1f
        p.style = Paint.Style.FILL
        p.color = withAlpha(Color.rgb(40, 40, 40), k)
        c.drawCircle(cx, cy, sr, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = withAlpha(Color.rgb(90, 90, 90), k)
        c.drawCircle(cx, cy, sr, p)
        p.style = Paint.Style.FILL; p.color = withAlpha(Color.rgb(130, 130, 130), k)
        c.drawCircle(kx, ky, sr * 0.4f, p)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(17, 17, 17))
        drawStick(c, lcx, lcy, lkx, lky, "LST" in off)
        drawStick(c, rcx, rcy, rkx, rky, "RST" in off)
        p.style = Paint.Style.FILL
        p.textSize = 36f; p.textAlign = Paint.Align.CENTER
        for (b in btns) {
            val dis = b.id in off
            if (dis && !edit) continue
            val k = if (dis) 0.25f else 1f
            val on = (mask and b.m) != 0
            var col = baseColor(b.m)
            if (on) col = lighten(col)
            p.color = withAlpha(col, k)
            val shoulder = b.m == 0x0100 || b.m == 0x0200 || b.m == 0x10000 || b.m == 0x20000
            if (shoulder) c.drawRoundRect(b.x - b.r * 1.2f, b.y - b.r * 0.6f, b.x + b.r * 1.2f, b.y + b.r * 0.6f, 24f, 24f, p)
            else c.drawCircle(b.x, b.y, b.r, p)
            p.color = withAlpha(if (b.m == 0x8000) Color.BLACK else Color.WHITE, k)
            c.drawText(b.t, b.x, b.y + 12f, p)
        }
        if (edit) {
            p.textSize = 30f; p.color = Color.rgb(240, 200, 60)
            c.drawText("Drag to move  -  Back to save and exit", w0 * 0.5f, h0 * 0.27f, p)
            return
        }
        val ok = System.currentTimeMillis() - lastPong < 3000
        val searching = auto && target == null
        p.color = if (ok) Color.rgb(60, 200, 90) else if (searching) Color.rgb(240, 170, 40) else Color.rgb(220, 60, 50)
        c.drawCircle(w0 * 0.5f, h0 * 0.27f, h0 * 0.025f, p)
        p.textSize = 28f; p.color = Color.LTGRAY
        c.drawText(if (ok) "Connected" else if (searching) "Searching..." else "No reply", w0 * 0.5f, h0 * 0.27f + h0 * 0.08f, p)
    }

    private fun inLeftZone(x: Float, y: Float): Boolean {
        val f = if (cfg.lefty) 1f - x / w0 else x / w0
        return f < 0.25f && y > h0 * 0.42f
    }

    private fun inRightZone(x: Float, y: Float): Boolean {
        val f = if (cfg.lefty) 1f - x / w0 else x / w0
        return f > 0.48f && f < 0.74f && y > h0 * 0.42f
    }

    private fun moveDrag(x0: Float, y0: Float) {
        val id = dragId ?: return
        val x = x0.coerceIn(0f, w0.toFloat()); val y = y0.coerceIn(0f, h0.toFloat())
        if (id == "LST") {
            dlcx = x; dlcy = y; lcx = x; lcy = y; lkx = x; lky = y
        } else if (id == "RST") {
            drcx = x; drcy = y; rcx = x; rcy = y; rkx = x; rky = y
        } else {
            for (b in btns) if (b.id == id) { b.x = x; b.y = y }
        }
    }

    private fun editTouch(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragId = null
                var best = Float.MAX_VALUE
                for (b in btns) {
                    val d = dist(x, y, b.x, b.y)
                    if (b.id !in off && d < b.r * 1.5f && d < best) { best = d; dragId = b.id }
                }
                if (dragId == null) {
                    if ("LST" !in off && dist(x, y, dlcx, dlcy) < sr) dragId = "LST"
                    else if ("RST" !in off && dist(x, y, drcx, drcy) < sr) dragId = "RST"
                }
            }
            MotionEvent.ACTION_MOVE -> moveDrag(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moveDrag(x, y)
                val id = dragId
                if (id != null) {
                    when (id) {
                        "LST" -> saveOne(id, dlcx, dlcy)
                        "RST" -> saveOne(id, drcx, drcy)
                        else -> for (b in btns) if (b.id == id) saveOne(id, b.x, b.y)
                    }
                }
                dragId = null
            }
        }
        invalidate()
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (edit) return editTouch(e)
        val act = e.actionMasked
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            val x = e.getX(i); val y = e.getY(i); val id = e.getPointerId(i)
            if (cfg.floating) {
                if (lid == -1 && "LST" !in off && inLeftZone(x, y)) {
                    lid = id; lcx = x.coerceIn(sr, w0 - sr); lcy = y.coerceIn(sr, h0 - sr)
                } else if (rid == -1 && "RST" !in off && inRightZone(x, y)) {
                    rid = id; rcx = x.coerceIn(sr, w0 - sr); rcy = y.coerceIn(sr, h0 - sr)
                }
            } else {
                if (lid == -1 && "LST" !in off && dist(x, y, lcx, lcy) < sr * 1.4f) lid = id
                else if (rid == -1 && "RST" !in off && dist(x, y, rcx, rcy) < sr * 1.4f) rid = id
            }
        }
        val up = act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL
        if (up) {
            lid = -1; rid = -1
            lcx = dlcx; lcy = dlcy; rcx = drcx; rcy = drcy
        } else if (act == MotionEvent.ACTION_POINTER_UP) {
            val id = e.getPointerId(e.actionIndex)
            if (id == lid) { lid = -1; lcx = dlcx; lcy = dlcy }
            if (id == rid) { rid = -1; rcx = drcx; rcy = drcy }
        }

        var m = 0
        var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f
        lkx = lcx; lky = lcy; rkx = rcx; rky = rcy
        if (!up) for (i in 0 until e.pointerCount) {
            if (act == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
            val id = e.getPointerId(i)
            val x = e.getX(i); val y = e.getY(i)
            if (id == lid) {
                val dx = x - lcx; val dy = y - lcy; val d = dist(x, y, lcx, lcy)
                val k = if (d > sr) sr / d else 1f
                lkx = lcx + dx * k; lky = lcy + dy * k
                val nx = dx * k / sr; val ny = dy * k / sr
                val mag = Math.hypot(nx.toDouble(), ny.toDouble()).toFloat()
                if (mag > cfg.dead) {
                    val sc = (mag - cfg.dead) / (1f - cfg.dead) / mag
                    lx = nx * sc; ly = -ny * sc
                }
            } else if (id == rid) {
                val dx = x - rcx; val dy = y - rcy; val d = dist(x, y, rcx, rcy)
                val k = if (d > sr) sr / d else 1f
                rkx = rcx + dx * k; rky = rcy + dy * k
                val nx = dx * k / sr; val ny = dy * k / sr
                val mag = Math.hypot(nx.toDouble(), ny.toDouble()).toFloat()
                if (mag > cfg.dead) {
                    val sc = (mag - cfg.dead) / (1f - cfg.dead) / mag
                    rx = nx * sc; ry = -ny * sc
                }
            } else for (b in btns)
                if (b.id !in off && dist(x, y, b.x, b.y) < b.r * 1.3f) m = m or b.m
        }
        val newly = m and mask.inv()
        if (newly != 0 && cfg.vib) buzz()
        mask = m
        invalidate()

        val msg = "${cfg.slot};$m;$lx;$ly;$rx;$ry"
        if (msg != last) {
            last = msg
            val t = target
            if (t != null) ex.execute {
                try {
                    val b = msg.toByteArray()
                    sock.send(DatagramPacket(b, b.size, t, 5005))
                } catch (_: Exception) {}
            }
        }
        return true
    }
}
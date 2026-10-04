package com.pad.app

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val ip = EditText(this).apply { hint = "192.168.1.5"; setText(prefs.getString("ip", "")) }
        val slot = EditText(this).apply { hint = "1 / 2 / 3"; setText(prefs.getString("slot", "1")) }
        val go = Button(this).apply { text = "Connect" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(ip); addView(slot); addView(go)
        })
        go.setOnClickListener {
            val n = slot.text.toString().toIntOrNull() ?: 1
            prefs.edit().putString("ip", ip.text.toString()).putString("slot", n.toString()).apply()
            setContentView(PadView(this, ip.text.toString().trim(), n))
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}

class PadView(c: Context, val ip: String, val slot: Int) : View(c) {
    class Btn(val t: String, val x: Float, val y: Float, val r: Float, val m: Int)
    private val ex = Executors.newSingleThreadExecutor()
    private val sock = DatagramSocket()
    private var btns = listOf<Btn>()
    private var lcx = 0f; private var lcy = 0f
    private var rcx = 0f; private var rcy = 0f
    private var sr = 0f
    private var lkx = 0f; private var lky = 0f
    private var rkx = 0f; private var rky = 0f
    private var lid = -1; private var rid = -1
    private var mask = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var last = ""

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        Math.hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()

    override fun onSizeChanged(w: Int, h: Int, a: Int, b: Int) {
        sr = h * 0.2f
        lcx = w * 0.12f; lcy = h * 0.68f; lkx = lcx; lky = lcy
        rcx = w * 0.62f; rcy = h * 0.68f; rkx = rcx; rky = rcy
        val r = h * 0.09f
        val ax = w * 0.87f; val ay = h * 0.62f; val d = r * 1.7f
        val dx = w * 0.36f; val dy = h * 0.70f; val dr = r * 0.8f; val dd = dr * 1.9f
        btns = listOf(
            Btn("A", ax, ay + d, r, 0x1000), Btn("B", ax + d, ay, r, 0x2000),
            Btn("X", ax - d, ay, r, 0x4000), Btn("Y", ax, ay - d, r, 0x8000),
            Btn("LB", w * 0.10f, h * 0.12f, r, 0x0100), Btn("RB", w * 0.90f, h * 0.12f, r, 0x0200),
            Btn("LT", w * 0.24f, h * 0.12f, r, 0x10000), Btn("RT", w * 0.76f, h * 0.12f, r, 0x20000),
            Btn("<", w * 0.42f, h * 0.12f, r * 0.8f, 0x0020), Btn(">", w * 0.58f, h * 0.12f, r * 0.8f, 0x0010),
            Btn("LS", w * 0.12f, h * 0.30f, r * 0.8f, 0x0040),
            Btn("RS", w * 0.62f, h * 0.30f, r * 0.8f, 0x0080),
            Btn("^", dx, dy - dd, dr, 0x0001), Btn("v", dx, dy + dd, dr, 0x0002),
            Btn("<", dx - dd, dy, dr, 0x0004), Btn(">", dx + dd, dy, dr, 0x0008)
        )
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(17, 17, 17))
        p.color = Color.rgb(50, 50, 50)
        c.drawCircle(lcx, lcy, sr, p); c.drawCircle(rcx, rcy, sr, p)
        p.color = Color.GRAY
        c.drawCircle(lkx, lky, sr * 0.4f, p); c.drawCircle(rkx, rky, sr * 0.4f, p)
        p.textSize = 36f; p.textAlign = Paint.Align.CENTER
        for (b in btns) {
            p.color = if ((mask and b.m) != 0) Color.rgb(0, 150, 90) else Color.rgb(70, 70, 70)
            c.drawCircle(b.x, b.y, b.r, p)
            p.color = Color.WHITE; c.drawText(b.t, b.x, b.y + 12f, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex; val x = e.getX(i); val y = e.getY(i); val id = e.getPointerId(i)
            if (lid == -1 && dist(x, y, lcx, lcy) < sr * 1.4f) lid = id
            else if (rid == -1 && dist(x, y, rcx, rcy) < sr * 1.4f) rid = id
        }
        val up = act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL
        if (up) { lid = -1; rid = -1 }
        else if (act == MotionEvent.ACTION_POINTER_UP) {
            val id = e.getPointerId(e.actionIndex)
            if (id == lid) lid = -1
            if (id == rid) rid = -1
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
                lx = dx * k / sr; ly = -dy * k / sr
            } else if (id == rid) {
                val dx = x - rcx; val dy = y - rcy; val d = dist(x, y, rcx, rcy)
                val k = if (d > sr) sr / d else 1f
                rkx = rcx + dx * k; rky = rcy + dy * k
                rx = dx * k / sr; ry = -dy * k / sr
            } else for (b in btns)
                if (dist(x, y, b.x, b.y) < b.r * 1.3f) m = m or b.m
        }
        mask = m; invalidate()
        val msg = "$slot;$m;$lx;$ly;$rx;$ry"
        if (msg != last) {
            last = msg
            ex.execute { try {
                val b = msg.toByteArray()
                sock.send(DatagramPacket(b, b.size, InetAddress.getByName(ip), 5005))
            } catch (_: Exception) {} }
        }
        return true
    }
}
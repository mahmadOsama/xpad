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
    private var sx = 0f; private var sy = 0f; private var sr = 0f
    private var kx = 0f; private var ky = 0f
    private var mask = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var last = ""

    override fun onSizeChanged(w: Int, h: Int, a: Int, b: Int) {
        sr = h * 0.22f; sx = w * 0.17f; sy = h * 0.68f; kx = sx; ky = sy
        val r = h * 0.1f; val cx = w * 0.82f; val cy = h * 0.68f; val d = r * 1.7f
        btns = listOf(
            Btn("A", cx, cy + d, r, 0x1000), Btn("B", cx + d, cy, r, 0x2000),
            Btn("X", cx - d, cy, r, 0x4000), Btn("Y", cx, cy - d, r, 0x8000),
            Btn("LB", w * 0.1f, h * 0.15f, r, 0x0100), Btn("RB", w * 0.9f, h * 0.15f, r, 0x0200),
            Btn("LT", w * 0.25f, h * 0.15f, r, 0x10000), Btn("RT", w * 0.75f, h * 0.15f, r, 0x20000),
           Btn("<", w * 0.42f, h * 0.15f, r * 0.8f, 0x0020), Btn(">", w * 0.58f, h * 0.15f, r * 0.8f, 0x0010),
           Btn("LS", w * 0.40f, h * 0.80f, r, 0x0040)
        )
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(17, 17, 17))
        p.color = Color.rgb(50, 50, 50); c.drawCircle(sx, sy, sr, p)
        p.color = Color.GRAY; c.drawCircle(kx, ky, sr * 0.4f, p)
        p.textSize = 40f; p.textAlign = Paint.Align.CENTER
        for (b in btns) {
            p.color = if ((mask and b.m) != 0) Color.rgb(0, 150, 90) else Color.rgb(70, 70, 70)
            c.drawCircle(b.x, b.y, b.r, p)
            p.color = Color.WHITE; c.drawText(b.t, b.x, b.y + 14f, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        var m = 0; var lx = 0f; var ly = 0f
        kx = sx; ky = sy
        val up = e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL
        if (!up) for (i in 0 until e.pointerCount) {
            if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
            val x = e.getX(i); val y = e.getY(i)
            val dx = x - sx; val dy = y - sy
            val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (d < sr * 1.8f) {
                val k = if (d > sr) sr / d else 1f
                kx = sx + dx * k; ky = sy + dy * k
                lx = dx * k / sr; ly = -dy * k / sr
            } else for (b in btns)
                if (Math.hypot((x - b.x).toDouble(), (y - b.y).toDouble()) < b.r * 1.3) m = m or b.m
        }
        mask = m; invalidate()
        val msg = "$slot;$m;$lx;$ly"
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

import socket, vgamepad as vg

try:
    N = int(input("How many phones? [2]: ") or 2)
except ValueError:
    N = 2
pads = [vg.VX360Gamepad() for _ in range(N)]
for p in pads:
    p.update()

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("0.0.0.0", 5005))
print(f"Ready with {N} pads. Phones connect to this PC's IP, port 5005.")

while True:
    d, _ = s.recvfrom(256)
    try:
        slot, m, lx, ly, rx, ry = d.decode().strip().split(";")
        p = pads[int(slot) - 1]; m = int(m)
    except Exception:
        continue
    p.report.wButtons = m & 0xFFFF
    p.left_joystick_float(float(lx), float(ly))
    p.right_joystick_float(float(rx), float(ry))
    p.left_trigger_float(1.0 if m & 0x10000 else 0.0)
    p.right_trigger_float(1.0 if m & 0x20000 else 0.0)
    p.update()
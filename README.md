[Русский](README.ru.md) · English

# Monjaro Drive Mode Selector

Switching drive modes on your **Geely Monjaro** shouldn't mean poking at a slow, fiddly stock selector. This app puts a clean on-screen strip of modes right where you can tap it — and you decide exactly which modes show up and in what order.

<!-- Screenshots go here -->

---

## Why you'll like it

- **One-tap switching** — a horizontal strip of your chosen drive modes appears on screen; the current mode is enlarged and highlighted in the center.
- **Your modes, your order** — pick only the modes you actually use and arrange them how you like.
- **Two ways to switch** — tap a mode on the overlay, or turn the physical selector knob (the knob needs MConfig+, see below).
- **Always there when you need it** — pop the selector up with a steering-wheel button without changing the mode, and it tucks itself away again automatically.

---

## How it works

The app is built to **replace** the stock drive-mode screen. The intended setup uses **MConfig+** to block the stock selector's action and remap the physical knob to this app — so turning the knob brings up *only* this overlay, with the stock screen out of the way.

- **The full experience (with MConfig+)** — the stock selector is silenced and the knob (or a steering-wheel button) drives this overlay. Turn the knob to step through your modes; tap a mode to jump straight to it. This is what the app is designed for.
- **Without MConfig+** — the overlay still pops up on its own whenever the drive mode changes by another means (for example a voice command, or a steering-wheel button assigned through a third-party app), and you can tap a mode to switch.

Either way, the overlay is yours to shape: choose the modes, set the order, and let it tuck itself away when you're done.

---

## Get started in 3 steps

1. **Install** — download the APK from the [Releases page](https://github.com/DezzK/monjaro-selector/releases) and install it (enable "install from unknown sources" if the head unit asks).
2. **Open & allow** — launch the app and grant the **"Display over other apps"** permission when prompted.
3. **Set up your modes** — in settings, build your list under **Active modes** and **Available modes** (details below). The overlay now pops up whenever the drive mode changes by another means (for example a voice command), and you can tap a mode to switch.

> For the full experience — turning the physical knob (or a steering-wheel button) to drive *only* this overlay, with the stock screen out of the way — set up **MConfig+**. See [Connect the physical knob](#connect-the-physical-knob-mconfig).

---

## What you need

- A **Geely Monjaro** head unit (ECarX KX11, Android 9 or newer).
- **MConfig+** — _recommended; this is what the app is built around._ It blocks the stock selector's action and remaps the physical knob to this app, so the overlay fully replaces the stock screen. **Without MConfig+** the overlay still works — it pops up on a mode change (for example by voice) and you can tap to switch.

---

## Setting up your modes

Open the app to reach **Drive mode selector** settings. The mode list has two sections:

- **Active modes** (on top) — the modes shown on your overlay. **Drag to reorder**, tap the **×** to remove one.
- **Available modes** (below) — every other known mode. **Tap a chip** to add it to your active list.

### The fastest way to set up

1. Tap **Probe car** — the app asks your car which drive modes it reports as supported and moves those into your **Active modes** automatically.
2. Add any hidden modes by hand — some modes the manufacturer hides but that still work (for example **Sand** on the Monjaro) won't show up in the probe. Just tap them in **Available modes**.
3. Drag your active modes into the order you like.

That's the whole setup — from here the selector is ready to go.

### Make it look the way you want

- **Carousel: active in center** — turn this on and the list rotates so the current mode always sits in the middle of the overlay.
- **Day / night theme** — follows your head unit automatically.
- **Auto-hide duration** — set two separate times: one for a plain **"show"**, and one for **after a mode switch**. Touching the overlay (tap or scroll) resets the timer. Tap inside or outside the overlay to dismiss it.
- **Show overlay now** — pops the selector up without changing the mode (a steering-wheel button does the same thing).
- **Interface** — available in **Russian** and **English**.

The app also **starts automatically** after the head unit boots, so the selector is ready whenever you are.

---

## Connect the physical knob (MConfig+)

With MConfig+ the stock selector's own action is blocked and the real knob is remapped to this app, so it drives this overlay instead of the stock screen. This is a one-time setup — once it's done, the knob just works.

1. In the app, open the **MConfig+ integration** card and tap **Show instructions**. You'll see the **Intent** and **Package** values — **tap a row to copy** it to the clipboard.
2. In **MConfig+**, set the knob slot action to **"Send intent"** and paste the copied **Intent** and **Package**.

Which slots you configure depends on your MConfig+ version:

- **MConfig+ v43 and newer** — you only need to configure the **single click** (the in-app label is **Switch mode (Click 1)**). The app tells MConfig+ whether the overlay is currently visible, so one click either shows the overlay or steps to the next mode.
- **MConfig+ v42 and older** — also configure the **2× / 3× quick-click** slots.

Once it's set up, that's it: turn the knob and your overlay does the rest.

---

## About

- **Author:** Dezz — Telegram [@DezzK](https://t.me/DezzK)
- **Repository:** https://github.com/DezzK/monjaro-selector

---

## Safety & disclaimer

This is a community project and is **not affiliated with Geely or ECarX**. The app changes your car's drive mode, so do your setup while parked — then enjoy quick, glanceable switching on the road. **Use it at your own risk**, and please don't fiddle with the settings while driving.

---

## License

Released under the **GNU General Public License v3 (GPL-3.0)**.

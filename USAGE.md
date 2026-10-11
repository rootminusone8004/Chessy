# Chessy Usage Guide ♟️

## Overview
**Chessy** is a native Kotlin Android chess app strictly designed for **Single Player vs Engine** matches. It is powered exclusively by the **Stockfish** chess engine installed in **Termux**.

---

## Setting Up Stockfish in Termux

To play in Chessy, you must have Stockfish installed in Termux:

### Option A: Standard Termux Installation
1. Install Termux from [F-Droid](https://f-droid.org/packages/com.termux/).
2. Run in Termux:
   ```bash
   pkg update -y && pkg install -y stockfish
   ```
3. Enable external app execution:
   ```bash
   mkdir -p ~/.termux
   echo 'allow-external-apps = true' >> ~/.termux/termux.properties
   ```

---

### Option B: Manual Engine Location & Phone Storage Access (`termux-setup-storage`)

If Chessy does not auto-detect Stockfish or you have a custom Stockfish location/binary:

1. In Termux, run `termux-setup-storage` to allow Termux to interact with your phone's storage:
   ```bash
   termux-setup-storage
   ```
2. You can copy Stockfish to phone storage so it is easily located:
   ```bash
   cp $(which stockfish) ~/storage/shared/
   ```
   *(This places the binary directly in your phone's main shared storage `/sdcard/stockfish`)*
3. In Chessy:
   - Tap **"Locate Engine"** on the prerequisite screen (or the search icon in the top toolbar).
   - Either enter your custom path in Termux (e.g., `~/storage/shared/stockfish`, `/data/data/com.termux/files/usr/bin/stockfish`, or `stockfish`).
   - Or tap **"Pick Engine File from Phone Storage"** to select the engine file from your phone.
   - Tap **"Test Engine"** to see live diagnostic output from Termux!
   - Tap **"Save & Play"** to start your match!

4. **Manual Override ("I've Installed It")**:
   If you have already installed Stockfish in Termux and verified it works in your terminal, tap **"I've Installed It"** on the prerequisite screen to start playing immediately.

---

## Playing Against the Engine

1. **Player Color**:
   - Play as White (you move first) or Black (Stockfish moves first).
2. **Move Execution**:
   - Tap a piece to highlight legal destinations, then tap or drag to the target square.
3. **Pawn Promotion**:
   - Reach rank 8 to choose Queen, Rook, Bishop, or Knight with Lichess pieces.
4. **Controls**:
   - **Undo**: Reverts move history.
   - **Flip Board**: Flips orientation between White and Black.
   - **Resign**: Forfeits the game.
   - **Share / Copy**: Copies PGN or FEN to clipboard.

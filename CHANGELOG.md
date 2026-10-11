# Changelog

All notable changes to the **Chessy** project will be documented in this file.

## [1.0.0] - 2026-10-10

### Added
- **Manual Engine Location & Phone Storage Access**:
  - Implemented manual engine path specification in Termux with quick presets (`stockfish`, `usr/bin/stockfish`, `~/stockfish`, `~/storage/shared/stockfish`).
  - Added support for `termux-setup-storage` allowing Stockfish to reside in phone shared storage (`~/storage/shared/`).
  - Integrated system file picker to select engine files directly from phone storage.
  - Added live engine diagnostics console reporting exit codes, stdout, and stderr.
  - Added manual confirmation / override option ("I've Installed It") to avoid blocking users on false-negative auto-detection.
- **Strict Termux Stockfish Policy**:
  - Exclusively powered by Stockfish in Termux (`com.termux.RUN_COMMAND` intent and localhost socket).
  - No online engines, no built-in engines. Prerequisite gate guides setup when Termux is not installed.
- **Core Chess Rules & Engine**:
  - Full chess rule implementation in Kotlin (castling, en passant, pawn promotion, 50-move rule, check/checkmate/stalemate detection).
  - Algebraic notation (SAN) generation and PGN export.
  - FEN parser and serializer.
- **Lichess Visual Theme**:
  - Default Lichess brown board styling (`#F0D9B5` / `#B58863`).
  - Authentic Lichess **cburnett** vector pieces for all 12 piece types.
  - Check highlights, legal move dots and capture rings, and last move indicator.
- **UI & Experience**:
  - Signature Vibrant Orange (`#E64A19`) theme inspired by Bazarnote.
  - Sleek dark surface cards (`#2C2C2C` / `#363636`).
  - Single player vs Engine experience (play as White or Black).
  - Horizontal move history list with real-time scrolling.
  - Undo move, Flip board, Resign, and PGN export.

# Changelog

All notable changes to the **Chessy** project will be documented in this file.

## [1.1.0] - 2026-10-17
### Fixed
- **Alternate moves**:
  - Another button has been introduced for alternative moves
  - Manual repititive play prevention

## [1.0.2] - 2026-10-14
### Fixed
- **Pawn promtion board**:
  - Every piece now is shown.

## [1.0.1] - 2026-10-13

- **Pawn Promotion Dialog Fix**:
  - Redesigned the promotion dialog with responsive proportional weights, explicit window constraints, and piece labels, ensuring all four promotion pieces (Queen, Rook, Bishop, Knight) are fully visible and clickable without clipping across all phone screen sizes and display densities.
  - Added a Cancel button and outside-touch cancellation so promotion can be cancelled cleanly if triggered accidentally.
- **Game Over Dialog Polish**:
  - Removed the placeholder "Review Board" button from the Game Over dialog.
  - Redesigned action layout with a primary "New Game" button and a clean, full-width outlined "Copy PGN" button with instant visual feedback.
- **King-in-Check Highlight**:
  - Replaced subtle red check highlight with vivid violet (`#8E24AA`, alpha 230) for clear visual contrast on both light and dark squares.
- **Engine & Gambit Status Color Indicators**:
  - Dynamic board status coloring: Blue status background while playing predefined gambit moves from the opening list; automatically switches to Green when Stockfish takes over and begins calculating moves.
- **Performance & ANR Fixes**:
  - Moved socket engine communication and intensive move generation off the main thread to completely eliminate "App isn't responding" (ANR) warnings.
- **UI Streamlining**:
  - Silenced startup toast notifications ("Stockfish started as...") for a clean, distraction-free match start.
- **APK Size Optimization**:
  - Enabled R8 resource shrinking, ProGuard optimizations, and trimmed unused resources, reducing release APK size to ~1.6 MB.
- **Automated CI/CD Pipeline**:
  - Added GitHub Actions release workflow (`.github/workflows/release.yml`) with automated unit tests, signed APK build, artifact upload, and GitHub Releases publication.
  - Added build summary table generator and automated changelog release notes extractor.
- **Version Bump**:
  - Bumped `versionCode` to `2` and `versionName` to `1.0.1` for seamless in-place app updates.

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

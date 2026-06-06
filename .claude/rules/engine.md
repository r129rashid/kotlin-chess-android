---
paths:
  - "app/src/main/java/com/rabi/chess/engine/**"
  - "app/src/test/java/com/rabi/chess/engine/**"
---

# Chess engine rules

The engine is the single source of truth for chess logic.

## Architecture boundary (CRITICAL)
- Zero Android imports in engine/ — no android.*, no androidx.*, no Context.
- If a file in engine/ needs an Android type, it belongs in a different package.

## Correctness
- Never optimise move generation without a complete test suite covering the optimised path.
- Every new special rule (castling, en passant, promotion) needs a dedicated unit test.

## make/undo contract
- makeMove and undoMove must be perfect inverses for every possible legal move.
- The AI calls undoMove thousands of times per search — any inconsistency corrupts eval.
- After any change to Board.makeMove or undoMove, run all engine tests immediately.

## External access
- External callers may only call makeMove, undoMove, legalMoves via MoveGenerator.
- Direct mutation of squares, castling, enPassantTarget from outside engine/ is forbidden.
- The place/setSideToMove/setCastling helpers exist for test setup only.

## Testing
- All 9 tests in MoveGeneratorTest must pass after every engine change.
- Prefer writing a failing test BEFORE fixing a bug.

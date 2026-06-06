---
name: review-engine
description: Deep correctness review of the chess engine — move generation, Board make/undo, check detection, and the AI evaluation.
---

Perform a deep correctness review of the Kotlin Chess engine.

## Step 1 — Read all engine files

Read these files in full before writing anything:
- app/src/main/java/com/rabi/chess/engine/Board.kt
- app/src/main/java/com/rabi/chess/engine/MoveGenerator.kt
- app/src/test/java/com/rabi/chess/engine/MoveGeneratorTest.kt
- app/src/main/java/com/rabi/chess/ai/ChessAI.kt

## Step 2 — Run the current test suite

Run: ./gradlew :app:testDebugUnitTest — note exact pass/fail count.

## Step 3 — Review Board.makeMove / undoMove

- Does undoMove restore: piece, captured piece, capturedSquare (different from move.to for en passant), castling rights, ep target, halfmove clock?
- En passant: is the pawn removed from (move.to.file, move.from.rank) not from move.to?
- Castling: does the rook correctly relocate for king-side (g1/g8) and queen-side (c1/c8)?
- Castling rights: forfeited when a rook is captured on its home square, not just when it moves?

## Step 4 — Review MoveGenerator

- Pawn attack direction: correct per color? WHITE attacks up, BLACK attacks down.
- isSquareAttacked covers all 6 piece types including color-dependent pawn attacks?
- Castling generation: verified the rook is actually present at its home square?
- Insufficient-material draw: KNNvK is NOT a draw — only KvK, KNvK, KBvK.

## Step 5 — Review ChessAI

- bestMove returns null only when legalMoves is empty — GameActivity must handle that.
- negamax returns -MATE - depth for checkmate (shorter mates preferred)?
- Piece-square tables: indexed correctly for both colors? White reads mirrored (7-rank), Black directly.

## Step 6 — Report

Report each issue as CRITICAL / WARNING / SUGGESTION.
After the report, propose any new unit tests that would catch CRITICAL/WARNING issues.

package practice.lld.seconditeration.chess;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: CHESS (FULL GAME ORCHESTRATOR)
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - 8x8 Board. Players alternate turns.
 * - Players can only move their own pieces.
 * - Pieces move according to specific math (Knight, King).
 * - Game ends when a King is captured.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Separation of Concerns: Board handles grid state, ChessGame handles turn rules.
 * - Extensibility: Adding a 'Queen' requires zero changes to Board or ChessGame.
 * * 3. ASSUMPTIONS:
 * - Modified Win Condition: Game ends when a King is physically captured (to save
 * interviews.interview time, avoiding deep-tree 'Checkmate' prediction algorithms).
 * - Only Knight and King are implemented to demonstrate the architecture.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - The Command Pattern: "I would wrap `makeMove` in a Command object and push it to a
 * Stack. This gives us O(1) Undo/Redo functionality and a clean move history."
 * - Network Layer: "In a real multiplayer backend, this `ChessGame` object would live
 * in a Redis cache or Stateful Actor, updated via WebSocket events."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - Castling, En Passant, Pawn Promotion, and Path-Blocking logic for sliding pieces
 * (like Rooks/Bishops, which require ray-casting across the board).
 * =======================================================================================
 */

// --- 1. Enums & Models ---

enum Color { WHITE, BLACK }
enum GameStatus { ACTIVE, WHITE_WINS, BLACK_WINS, STALEMATE }

class Player {
    final String name;
    final Color color;
    public Player(String name, Color color) {
        this.name = name;
        this.color = color;
    }
}

// --- 2. The Strategy Pattern: Pieces ---

abstract class Piece {
    final Color color;
    public Piece(Color color) { this.color = color; }
    abstract boolean isValidMove(Board board, int sx, int sy, int ex, int ey);
}

class Knight extends Piece {
    public Knight(Color color) { super(color); }
    @Override
    boolean isValidMove(Board board, int sx, int sy, int ex, int ey) {
        int dx = Math.abs(sx - ex);
        int dy = Math.abs(sy - ey);
        return (dx == 2 && dy == 1) || (dx == 1 && dy == 2); // L-Shape
    }
}

class King extends Piece {
    public King(Color color) { super(color); }
    @Override
    boolean isValidMove(Board board, int sx, int sy, int ex, int ey) {
        int dx = Math.abs(sx - ex);
        int dy = Math.abs(sy - ey);
        return dx <= 1 && dy <= 1; // 1 square in any direction
    }
}

// --- 3. The Grid Manager ---

class Board {
    Piece[][] grid = new Piece[8][8];

    public Board() {
        // Mock Setup for Interview (Normally you'd loop and place 32 pieces)
        grid[0][5] = new King(Color.WHITE);
        grid[7][4] = new King(Color.BLACK);
        grid[0][6] = new Knight(Color.WHITE);
    }

    public Piece getPiece(int x, int y) { return grid[x][y]; }

    public void setPiece(int x, int y, Piece piece) { grid[x][y] = piece; }

    public boolean isOutOfBounds(int x, int y) {
        return x < 0 || x >= 8 || y < 0 || y >= 8;
    }
}

// --- 4. The Game Orchestrator (State Machine) ---

class ChessGame {
    private final Board board;
    private final Player[] players;
    private int currentPlayerIndex;
    private GameStatus status;

    public ChessGame(Player p1, Player p2) {
        this.players = new Player[]{p1, p2};
        this.board = new Board();
        this.currentPlayerIndex = 0; // White goes first (Assuming p1 is White)
        this.status = GameStatus.ACTIVE;
    }

    public boolean makeMove(Player player, int sx, int sy, int ex, int ey) {
        if (status != GameStatus.ACTIVE) {
            System.out.println("❌ Game is already over.");
            return false;
        }

        // 1. Turn Validation
        if (!players[currentPlayerIndex].equals(player)) {
            System.out.println("❌ Not " + player.name + "'s turn!");
            return false;
        }

        // 2. Bounds Validation
        if (board.isOutOfBounds(sx, sy) || board.isOutOfBounds(ex, ey)) {
            System.out.println("❌ Move out of bounds.");
            return false;
        }

        // 3. Piece Ownership Validation
        Piece sourcePiece = board.getPiece(sx, sy);
        if (sourcePiece == null) {
            System.out.println("❌ No piece at source.");
            return false;
        }
        if (sourcePiece.color != player.color) {
            System.out.println("❌ " + player.name + " cannot move opponent's piece.");
            return false;
        }

        // 4. Mathematical Move Validation
        if (!sourcePiece.isValidMove(board, sx, sy, ex, ey)) {
            System.out.println("❌ Invalid move for this piece type.");
            return false;
        }

        // 5. Friendly Fire Validation
        Piece destPiece = board.getPiece(ex, ey);
        if (destPiece != null && destPiece.color == sourcePiece.color) {
            System.out.println("❌ Cannot capture your own piece.");
            return false;
        }

        // 6. Execute Move & Check Win Condition
        if (destPiece instanceof King) {
            System.out.println("👑 KING CAPTURED!");
            this.status = (player.color == Color.WHITE) ? GameStatus.WHITE_WINS : GameStatus.BLACK_WINS;
        }

        board.setPiece(ex, ey, sourcePiece);
        board.setPiece(sx, sy, null);

        System.out.println("✅ " + player.name + " moved piece to (" + ex + "," + ey + ")");

        // 7. Advance Turn
        if (status == GameStatus.ACTIVE) {
            currentPlayerIndex = (currentPlayerIndex + 1) % 2;
        }
        return true;
    }

    public GameStatus getStatus() { return status; }
}

// --- 5. Main Execution ---

public class Main {
    public static void main(String[] args) {
        System.out.println("=== FULL CHESS ORCHESTRATOR ===\n");

        Player p1 = new Player("Alice", Color.WHITE);
        Player p2 = new Player("Bob", Color.BLACK);
        ChessGame game = new ChessGame(p1, p2);

        // Turn 1: Alice tries to move Bob's King (Fails)
        game.makeMove(p1, 7, 4, 6, 4);

        // Turn 1: Bob tries to play out of turn (Fails)
        game.makeMove(p2, 7, 4, 6, 4);

        // Turn 1: Alice moves White Knight
        game.makeMove(p1, 0, 1, 2, 2);

        // Turn 2: Bob moves Black King
        game.makeMove(p2, 7, 4, 6, 3);

        // Turn 3: Alice moves White Knight to capture Black King (Game Over)
        // From (2,2), a Knight can jump to (4,3)... let's assume it gets close enough to eventually capture.
        // For a quick test, let's just teleport Alice's King to capture Bob's King to test the end state.
        System.out.println("\n--- Simulating Endgame ---");
        game.makeMove(p1, 0, 4, 1, 4); // Alice moves White King
        game.makeMove(p2, 6, 3, 5, 3); // Bob moves Black King

        // Let's manually trigger a capture by placing the Knight near the King
        game.makeMove(p1, 2, 2, 4, 3); // Knight jumps
        game.makeMove(p2, 5, 3, 4, 3); // Bob's King commits suicide onto Alice's Knight (Friendly fire logic prevents own piece, but enemy piece is legal capture)

        System.out.println("Game Status: " + game.getStatus());
    }
}
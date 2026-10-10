package practice.lld.seconditeration.tictactoe;


/**
 * =======================================================================================
 * LLD CHEAT SHEET: TIC-TAC-TOE (O(1) VALIDATION)
 * =======================================================================================
 * 1. FUNCTIONAL: N x N grid. Players alternate turns. First to N in a row/col/diag wins.
 * 2. NON-FUNCTIONAL: O(1) time complexity to check the winner after each move.
 * 3. ASSUMPTIONS: 2 players. Valid inputs.
 * 4. HLD CALLOUTS: "For a multiplayer online version, the state would be held in Redis,
 * and moves communicated via WebSockets. We'd use a UUID for the room."
 * 5. SKIPPED FOR TIME: Complex UI rendering and rematch logic.
 * =======================================================================================
 */
class TicTacToeGame {
    private final int[] rowSum, colSum;
    private int diagSum, antiDiagSum;
    private final int n;
    private int moves;

    // 1 for Player 1, -1 for Player 2
    private int currentPlayer = 1;

    public TicTacToeGame(int n) {
        this.n = n;
        this.rowSum = new int[n];
        this.colSum = new int[n];
    }

    /**
     * CORE SIGNAL: O(1) Winner Check.
     * Player 1 adds 1. Player 2 subtracts 1. If any sum hits N or -N, they win.
     */
    public int move(int row, int col) {
        if (row < 0 || col < 0 || row >= n || col >= n) throw new IllegalArgumentException("Out of bounds");

        rowSum[row] += currentPlayer;
        colSum[col] += currentPlayer;

        if (row == col) diagSum += currentPlayer;
        if (row + col == n - 1) antiDiagSum += currentPlayer;

        moves++;

        // Check if current player won
        if (Math.abs(rowSum[row]) == n || Math.abs(colSum[col]) == n ||
                Math.abs(diagSum) == n || Math.abs(antiDiagSum) == n) {
            return currentPlayer;
        }

        if (moves == n * n) return 0; // Draw

        currentPlayer = (currentPlayer == 1) ? -1 : 1; // Switch turns
        return 2; // Game continues
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println("=== TIC-TAC-TOE O(1) ENGINE ===");
        TicTacToeGame game = new TicTacToeGame(3);
        System.out.println("Move (0,0) P1: " + game.move(0, 0));
        System.out.println("Move (1,0) P2: " + game.move(1, 0));
        System.out.println("Move (1,1) P1: " + game.move(1, 1));
        System.out.println("Move (2,0) P2: " + game.move(2, 0));
        System.out.println("Move (2,2) P1: WINNER IS " + game.move(2, 2)); // P1 completes diagonal
    }
}

package practice.lld.thirditeration.tictactoe;

import java.util.*;

enum GameStatus { IN_PROGRESS, WON, DRAW }

class Player1 {
    private final String id;
    private final char symbol;

    public Player1(String id, char symbol) {
        this.id = id;
        this.symbol = symbol;
    }

    public String getId() { return id; }
    public char getSymbol() { return symbol; }
}

class Board {
    private final int size;
    private final char[][] grid;
    private int movesMade;

    // O(1) win tracking per player index: [playerIndex][row/col]
    private final int[][] rowCounts;
    private final int[][] colCounts;
    private final int[] diagCounts;
    private final int[] antiDiagCounts;

    public Board(int size, int playerCount) {
        this.size = size;
        this.grid = new char[size][size];
        for (char[] row : grid) {
            Arrays.fill(row, '.');
        }
        this.movesMade = 0;
        this.rowCounts = new int[playerCount][size];
        this.colCounts = new int[playerCount][size];
        this.diagCounts = new int[playerCount];
        this.antiDiagCounts = new int[playerCount];
    }

    public boolean placePiece(int r, int c, int playerIdx, char symbol) {
        if (r < 0 || r >= size || c < 0 || c >= size || grid[r][c] != '.') {
            return false;
        }

        grid[r][c] = symbol;
        movesMade++;

        rowCounts[playerIdx][r]++;
        colCounts[playerIdx][c]++;
        if (r == c) diagCounts[playerIdx]++;
        if (r + c == size - 1) antiDiagCounts[playerIdx]++;

        return true;
    }

    public boolean hasWon(int r, int c, int playerIdx) {
        return rowCounts[playerIdx][r] == size
                || colCounts[playerIdx][c] == size
                || diagCounts[playerIdx] == size
                || antiDiagCounts[playerIdx] == size;
    }

    public boolean isFull() {
        return movesMade == size * size;
    }

    public void printBoard() {
        for (char[] row : grid) {
            System.out.println(new String(row));
        }
    }
}

public class MultiPlayerTicTacToe {
    private final Board board;
    private final Queue<Player1> turnQueue;
    private final Map<String, Integer> playerIndexMap;
    private GameStatus status;

    public MultiPlayerTicTacToe(List<Player1> players, int boardSize) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("At least 2 players required.");
        }
        if (boardSize < 3) {
            throw new IllegalArgumentException("Board size must be at least 3x3.");
        }

        this.board = new Board(boardSize, players.size());
        this.turnQueue = new ArrayDeque<>(players);
        this.playerIndexMap = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            playerIndexMap.put(players.get(i).getId(), i);
        }
        this.status = GameStatus.IN_PROGRESS;
    }

    public GameStatus makeMove(Player1 player, int r, int c) {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Game is already over.");
        }

        Player1 currentTurnPlayer = turnQueue.peek();
        if (!currentTurnPlayer.getId().equals(player.getId())) {
            throw new IllegalArgumentException("Not player's turn: " + player.getId());
        }

        int playerIdx = playerIndexMap.get(player.getId());
        boolean success = board.placePiece(r, c, playerIdx, player.getSymbol());
        if (!success) {
            throw new IllegalArgumentException("Invalid move at: (" + r + ", " + c + ")");
        }

        if (board.hasWon(r, c, playerIdx)) {
            status = GameStatus.WON;
            return status;
        }

        if (board.isFull()) {
            status = GameStatus.DRAW;
            return status;
        }

        // Advance to next player
        turnQueue.offer(turnQueue.poll());
        return GameStatus.IN_PROGRESS;
    }

    public GameStatus getStatus() { return status; }
}

package practice.lld.seconditeration.snakeladder;

import java.util.*;

/**
 * =======================================================================================
 * LLD CHEAT SHEET: SNAKE AND LADDER
 * =======================================================================================
 * 1. FUNCTIONAL: Roll dice, move forward, take jumps (snakes go down, ladders go up).
 * 2. NON-FUNCTIONAL: Extensible turn management (can handle 2 to N players smoothly).
 * 3. ASSUMPTIONS: 100 squares. Exact roll needed to win (or overshoot bounces back/ignored).
 * 4. HLD CALLOUTS: "If this was an app, the Dice roll would happen server-side using
 * SecureRandom to prevent client-side hacking/memory injection."
 * 5. SKIPPED FOR TIME: Bouncing back on overshoot, multiple dice support.
 * =======================================================================================
 */
class Player {
    final String name;
    int position = 0;
    public Player(String name) { this.name = name; }
}

class SnakeLadderBoard {
    final int size = 100;
    // Maps start -> end (Works for both Snakes and Ladders natively)
    private final Map<Integer, Integer> jumps = new HashMap<>();

    public void addJump(int start, int end) { jumps.put(start, end); }
    public int getNewPosition(int pos) { return jumps.getOrDefault(pos, pos); }
}

class SnakeLadderGame {
    private final SnakeLadderBoard board;
    private final Queue<Player> turnQueue = new LinkedList<>();
    private final Random dice = new Random();

    public SnakeLadderGame(SnakeLadderBoard board, List<Player> players) {
        this.board = board;
        turnQueue.addAll(players);
    }

    public void play() {
        while (true) {
            Player p = turnQueue.poll();
            int roll = dice.nextInt(6) + 1;
            int nextPos = p.position + roll;

            if (nextPos <= board.size) {
                p.position = board.getNewPosition(nextPos);
                System.out.println(p.name + " rolled " + roll + " and moved to " + p.position);

                if (p.position == board.size) {
                    System.out.println("🏆 " + p.name + " WINS THE GAME!");
                    return;
                }
            }
            turnQueue.offer(p); // Put back in line
        }
    }
}

public class Main {
    public static void main(String[] args) {
        SnakeLadderBoard board = new SnakeLadderBoard();
        board.addJump(14, 7);   // Snake
        board.addJump(3, 22);   // Ladder
        board.addJump(98, 79);  // Snake

        SnakeLadderGame game = new SnakeLadderGame(board, Arrays.asList(new Player("Alice"), new Player("Bob")));
        game.play();
    }
}
package practice.lld.firstiteration.SnakeLadder;

import java.util.ArrayList;
import java.util.Random;

public class SnakeLadderGame {
    public Board gameBoard;
    public Player player1;
    public Player player2;
    public int turn;
    public Player winner;
    private final Random dice = new Random();

    public SnakeLadderGame(){
        ArrayList<ArrayList<Position>> snakes = new ArrayList<>();
        ArrayList<ArrayList<Position>> ladders = new ArrayList<>();

        // simple default setup on 5x5 (0,0) -> (4,4)
        // Snake: (3,3) -> (1,1)
        snakes.add(new ArrayList<>() {{
            add(new Position(3,3));
            add(new Position(1,1));
        }});

        // Ladder: (0,2) -> (2,4)
        ladders.add(new ArrayList<>() {{
            add(new Position(0,2));
            add(new Position(2,4));
        }});

        gameBoard = new Board(5,5, snakes, ladders);
        turn = 0;

        Cell start = gameBoard.getStartCell();
        player1 = new Player("P1", "Player-1", start);
        player2 = new Player("P2", "Player-2", start);
    }

    private void checkMove(Player p){
        boolean p1Turn = (turn % 2 == 0);
        if (p1Turn && p != player1) throw new IllegalStateException("not a valid move");
        if (!p1Turn && p != player2) throw new IllegalStateException("not a valid move");
    }

    private Cell makeMove(Player p, int random){
        checkMove(p);
        Cell newCell = gameBoard.move(p.cell, random);
        p.cell = newCell;
        turn++;
        return newCell;
    }

    public Player startGame(){
        while(isAnyWinner() == null){
            int diceNumber = dice.nextInt(6) + 1;

            if(turn%2==0){
                makeMove(player1, diceNumber);
            }else{
                makeMove(player2, diceNumber);
            }
        }

        this.winner = isAnyWinner();
        return this.winner;
    }

    public Player isAnyWinner(){
        Cell end = gameBoard.getEndCell();
        if (player1.cell == end) return player1;
        if (player2.cell == end) return player2;
        return null;
    }

    public static void main(String[] args) {
        SnakeLadderGame game = new SnakeLadderGame();
        Player winner = game.startGame();
        System.out.println("Winner: " + (winner == null ? "none" : winner.playerName));
    }
}

package practice.lld.firstiteration.SnakeLadder;

import java.util.ArrayList;

public class Cell {
    public ArrayList<Snake> snakes;
    public ArrayList<Ladder> ladders;

    public Cell(ArrayList<Snake> snake, ArrayList<Ladder> ladder){
        this.ladders=ladder;
        this.snakes=snake;
    }

    public Cell(){
        this.snakes = new ArrayList<>();
        this.ladders = new ArrayList<>();
    }
}

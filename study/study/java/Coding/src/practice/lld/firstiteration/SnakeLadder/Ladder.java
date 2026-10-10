package practice.lld.firstiteration.SnakeLadder;

public class Ladder {
    public String ladderId;
    public Cell endCell;

    public Ladder(Cell endCell){
        double randomNumber = Math.random();
        this.ladderId = "" + randomNumber;
        this.endCell = endCell;
    }
}

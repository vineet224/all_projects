package practice.lld.firstiteration.SnakeLadder;

public class Snake {
    public String snakeId;
    public Cell endCell;

    public Snake(Cell endCell){
        double randomNumber = Math.random();
        this.snakeId = "" + randomNumber;
        this.endCell = endCell;
    }
}

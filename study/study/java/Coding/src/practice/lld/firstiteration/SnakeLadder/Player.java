package practice.lld.firstiteration.SnakeLadder;

public class Player {
    public String playerId;
    public String playerName;
    public Cell cell;

    public Player(String playerId, String playerName, Cell cell) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.cell = cell;
    }
}

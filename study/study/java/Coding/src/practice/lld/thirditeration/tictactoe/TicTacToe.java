package practice.lld.thirditeration.tictactoe;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * functional requirements
 * - A board of 3*3 grid
 * - tow users
 * - game must end when a user wins
 */

class Place{
    boolean occupied;
    String playerid;
}

class Player{
    String playerid;
    String playername;
    Player (String playerid, String playername){
        this.playerid = playerid;
        this.playername = playername;
    }
}

public class TicTacToe {

    private final ArrayList<ArrayList<Place>> board = new ArrayList<ArrayList<Place>>();
    private final HashMap<String, Player> playerHashMap =  new HashMap<String, Player>();
    private final ArrayList<String> playeridforiteration = new ArrayList<>();

    private int turn;
    private String currentPlayerid;

    public TicTacToe(Player p1, Player p2){

        playerHashMap.put(p1.playerid, p1);
        playerHashMap.put(p2.playerid, p2);
        playeridforiteration.add(p1.playerid);
        playeridforiteration.add(p2.playerid);
        this.turn = 0;
        for(int i = 0; i < 3; i++){
            board.add(new ArrayList<>());
        }
        for(int i = 0; i < 3; i++){
            for(int j = 0; j < 3; j++){
                board.get(i).add(new Place());
            }
        }
    }

    public void makeMove(Player player,int x,int y){

        if(!player.playerid.equals(currentPlayerid)){
            throw new IllegalArgumentException(" the current player is not this");
        }


        Place place = board.get(x).get(y);
        if(place.occupied){
            throw new IllegalArgumentException(" place is already occupied");
        }

        place.occupied = true;
        place.playerid = currentPlayerid;
        boolean iswin = checkWinner(currentPlayerid,x,y);
        if(iswin){
            System.out.println(" player won");
            return;
        }
        turn++;
        if(turn > board.size()){
            System.out.println("game over");
            return;
        }
        currentPlayerid = getNextPlayer();

    }

    private boolean checkWinner(String playerid,int row,int col){

        boolean iswin = true;
        for(int i=0;i< board.size();i++){
            if (!board.get(i).get(col).playerid.equals(playerid)) {
                iswin = false;
                break;
            }
        }
        if(iswin){return true;}
        iswin = true;
        for(int i=0;i< board.get(0).size();i++){
            if(!board.get(row).get(i).playerid.equals(playerid)) {
                iswin = false;
                break;
            }
        }
        if(iswin){return true;}
        iswin = true;
        for(int i=0;i< board.size();i++){
            if(!board.get(i).get(i).playerid.equals(playerid)) {
                iswin = false;
                break;
            }
        }
        if(iswin){return true;}
        iswin = true;
        for(int i=0;i< board.size();i++){
            if(!board.get(i).get(board.size()-1-i).playerid.equals(playerid)) {
                iswin = false;
                break;
            }
        }
        if(iswin){return true;}
        return false;
    }

    private String getNextPlayer(){
        int nextPlayer = turn%playeridforiteration.size();
        return playeridforiteration.get(nextPlayer);
    }

}

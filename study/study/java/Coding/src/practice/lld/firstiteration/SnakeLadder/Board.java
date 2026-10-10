package practice.lld.firstiteration.SnakeLadder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class Board {
    public int row;
    public int col;
    public HashMap<Position, Cell> cells;
    // currently the snakes and ladders are inside the cells only but they could be here in the Board class only like with the entry and exit Position

    public Board(int row, int col){
        this(row, col, new ArrayList<>(), new ArrayList<>());
    }

    public Board(int row, int col, ArrayList<ArrayList<Position>> snakes, ArrayList<ArrayList<Position>> ladders){
        this.row = row;
        this.col = col;
        this.cells = new HashMap<>();

        for (int i = 0; i < row; i++) {
            for (int j = 0; j < col; j++) {
                cells.put(new Position(i, j), new Cell());
            }
        }

        for (int i = 0; i < snakes.size(); i++) {
            Position startPos = snakes.get(i).get(0);
            Position endPos = snakes.get(i).get(1);

            Cell startCell = cells.get(startPos);
            Cell endCell = cells.get(endPos);

            Snake newSnake = new Snake(endCell);
            startCell.snakes.add(newSnake);
        }

        for (int i = 0; i < ladders.size(); i++) {
            Position startPos = ladders.get(i).get(0);
            Position endPos = ladders.get(i).get(1);

            Cell startCell = cells.get(startPos);
            Cell endCell = cells.get(endPos);

            Ladder newLadder = new Ladder(endCell);
            startCell.ladders.add(newLadder);
        }
    }

    public Cell move(Cell cell, int steps){
        if (steps <= 0) return cell;

        int currentIndex = toIndex(cell).orElseThrow(() -> new IllegalStateException("Cell not on board"));
        int lastIndex = (row * col) - 1;
        int targetIndex = currentIndex + steps;

        // common rule: if you overshoot the end, you don't move
        if (targetIndex > lastIndex) return cell;

        Cell target = getCellByIndex(targetIndex);

        // Apply ladder first, else snake. If multiple exist, take first.
        if (!target.ladders.isEmpty()) {
            target = target.ladders.get(0).endCell;
        } else if (!target.snakes.isEmpty()) {
            target = target.snakes.get(0).endCell;
        }

        return target;
    }

    public Cell getStartCell(){
        return cells.get(new Position(0, 0));
    }

    public Cell getEndCell(){
        return cells.get(new Position(row - 1, col - 1));
    }

    private Cell getCellByIndex(int index){
        int r = index / col;
        int c = index % col;
        return cells.get(new Position(r, c));
    }

    private Optional<Integer> toIndex(Cell cell){
        for (var entry : cells.entrySet()) {
            if (entry.getValue() == cell) {
                Position p = entry.getKey();
                return Optional.of(p.row * col + p.column);
            }
        }
        return Optional.empty();
    }
}

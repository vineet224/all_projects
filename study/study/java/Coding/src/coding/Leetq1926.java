package coding;


import java.util.PriorityQueue;

public class Leetq1926 {

    public static int[] dirx = {0,0,1,-1};
    public static int[] diry = {1,-1,0,0};

    public static class Pair{
        int row;
        int col;
        int dis;
        public Pair(int row, int col, int dis){this.row=row;this.col=col;this.dis=dis;}
    }

    public boolean checkBorder(int n,int m, int row,int col){
        if(row == n || col == m || row ==0 || col ==0) return true;
        else return false;
    }

    public int nearestExit(char[][] maze, int[] entrance) {

        int n = maze.length, m =  maze[0].length;
        PriorityQueue<Pair> queue = new PriorityQueue<>();
        int[][] vis = new int[n][m];

        queue.add(new Pair(entrance[0], entrance[1], 0));
        vis[entrance[0]][entrance[1]] = 1;

        while(!queue.isEmpty()){
            Pair pair = queue.poll();
            int row = pair.row;
            int col = pair.col;
            int dis = pair.dis;

            for(int i=0;i<3;i++){
                int nr = row + dirx[i];
                int nc = col + diry[i];
                if(nr>=0 && nr<maze.length && nc>=0 && nc<maze.length && vis[nr][nc]==0){
                    vis[nr][nc]=1;
                    queue.add(new Pair(nr, nc, dis+1));
                    if(checkBorder(n,m,nr,nc)){
                        return dis+1;
                    }
                }
            }
        }

        return -1;
    }

}

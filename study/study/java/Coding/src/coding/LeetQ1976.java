package coding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;

public class LeetQ1976 {

    public static class Pair{
        public int node;
        public long dis;
        public int par;
        public Pair(int node, long dis, int par){this.node=node;this.dis=dis;this.par = par;}
    }

    public int countPaths(int n, int[][] roads) {
        ArrayList<ArrayList<Pair>> adj = new ArrayList<ArrayList<Pair>>();
        for(int i=0;i<n;i++){adj.add(new ArrayList<>());}
        for(int i=0;i<roads.length;i++){
            int nodea = roads[i][0];
            int nodeb = roads[i][1];
            int dis = roads[i][2];
            adj.get(nodea).add(new Pair(nodeb, dis,1));
            adj.get(nodeb).add(new Pair(nodea, dis,1));
        }

        long[] minDist = new long[n];
        Arrays.fill(minDist, Long.MAX_VALUE);
        long[] count = new long[n];
        long mm = 1000000007;

        PriorityQueue<Pair> pq = new PriorityQueue<>((a,b) -> {return Long.compare(a.dis,b.dis);});

        pq.offer(new Pair(0,0,1));
        count[0]=1;

        while(!pq.isEmpty()){
            Pair p = pq.poll();
            int node = p.node;
            long dis = p.dis;
            int par = p.par;

            if(minDist[node]<dis){
                continue;
            }

            //System.out.println(" the edge par "+ par + " node "+ node + " total dis "+dis + " cur min "+ minDist[node] );
            if(minDist[node]==dis){
                minDist[node] = dis;
                count[node] = (count[node] + count[par])%mm;
                continue;
            }
            minDist[node] = dis;
            count[node] = (count[node] + count[par])%mm;


            //System.out.println(" the node is "+ node + " dis "+ dis);
            for(int i=0;i<adj.get(node).size();i++){
                int ch = adj.get(node).get(i).node;
                long chdis = adj.get(node).get(i).dis;

                pq.offer(new Pair(ch,dis + chdis,node));

            }
        }
//        for(int i=0;i<n;i++){
//
//            System.out.println("i " + i + " min dis "+ minDist[i] + " count "+  count[i]);
//        }
        return (int) count[n-1];
    }

    public static void main(String[] args) {
        LeetQ1976 leetQ1976 = new LeetQ1976();
        int n = 7;
        int[][] roads = {{0,6,7},{0,1,2},{1,2,3},{1,3,3},{6,3,3},{3,5,1},{6,5,1},{2,5,1},{0,4,5},{4,6,2}};
        int ans =  leetQ1976.countPaths(n, roads);
        System.out.println("ans is "+ans);
    }
}

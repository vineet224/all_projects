package coding;

import java.util.*;

public class leetcodeq1786 {

    public static class Edge{
        int node;
        int cost;
        public Edge(int node, int cost){this.node=node;this.cost=cost;}
    }

    public int countRestrictedPaths(int n, int[][] edges) {

        ArrayList<ArrayList<Edge>> graph = new ArrayList<>();
        int[] minDist = new int[n];
        long[] ways = new long[n];
        long mm = 1000000007;
        Arrays.fill(minDist, Integer.MAX_VALUE);
        for(int i=0;i<n;i++)graph.add(new ArrayList<>());
        for(int i=0;i<edges.length;i++){
            graph.get(edges[i][0]-1).add(new Edge(edges[i][1] - 1, edges[i][2]));
            graph.get(edges[i][1]-1).add(new Edge(edges[i][0] - 1, edges[i][2]));
        }

        PriorityQueue<Edge> dijkstraqueue = new PriorityQueue<>((a,b)->Integer.compare(a.cost,b.cost));

        dijkstraqueue.add(new Edge(n - 1, 0));

        while(!dijkstraqueue.isEmpty()){
            Edge edge = dijkstraqueue.poll();
            int node = edge.node;
            int cost = edge.cost;
            if(minDist[node] !=  Integer.MAX_VALUE) continue;
            minDist[node] = cost;
            for(Edge e:graph.get(node)){
                if(minDist[e.node] != Integer.MAX_VALUE) continue;
                dijkstraqueue.add(new Edge(e.node, e.cost + cost));
            }
        }

        ArrayList<Edge> topology =  new ArrayList<>();
        //System.out.println("printing the mindist");
        for(int i=0;i<n;i++){
            //System.out.print("i "+i + " mindist "+minDist[i]);
            topology.add(new Edge(i, minDist[i]));
        }

        Collections.sort(topology, (a,b) -> {return Integer.compare(a.cost,b.cost);});
        ways[n-1] = 1L;

        for(Edge e:topology){
            //System.out.print("node "+e.node+" cost"+e.cost+" ");
            int node  = e.node;
            int cost = e.cost;

            if(node == n-1) continue;

            for(Edge ch: graph.get(node)){
                ways[node] = (ways[node] + ways[ch.node]) % mm;
            }
        }
        return (int) ways[0];
    }

    public static void main(String[] args) {
        leetcodeq1786 leetcodeq1786 = new leetcodeq1786();
        int n = 5;
        int[][] edges2 = new int[][]{{1,2,3},{1,3,3},{2,3,1},{1,4,2},{5,2,2},{3,5,1},{5,4,10}};
        int ans = leetcodeq1786.countRestrictedPaths(n,edges2);
        System.out.printf("The ans is "+ ans);
    }
}

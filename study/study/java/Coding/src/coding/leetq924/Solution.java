package coding.leetq924;

import java.util.Arrays;
import java.util.HashSet;

class Pair{
    boolean otherfound;
    int size;
    public Pair(int size, boolean otherfound){
        this.otherfound = otherfound;
        this.size = size;
    }
}

public class Solution {
    int[] vis;
    HashSet<Integer> sources;
    public Pair dfs(int node,int[][] graph, int nn){

        Pair p = new Pair(1,false);
        if(sources.contains(node) && node!=nn) p.otherfound = true;

        for(int i=0;i<graph[node].length;i++){
            int ch = i;
            if(graph[node][ch] == 1 && vis[ch] == 0){
                vis[ch] = 1;
                Pair subp = dfs(ch, graph, nn);
                p.size = subp.size + p.size;
                p.otherfound = p.otherfound & subp.otherfound;
            }
        }
        return p;
    }

    public int minMalwareSpread(int[][] graph, int[] initial) {
        Arrays.sort(initial);
        int mn = initial[0], mnval = 0,n = graph.length;
        vis = new int[n];
        sources = new HashSet<>();
        for(int i=0;i<initial.length;i++) {
            sources.add(initial[i]);
        }

        for(int i=0;i<initial.length;i++){
            int node = initial[i];
            if(vis[node]==0){
                vis[node] = 1;
                Pair p = dfs(node, graph, node);
                System.out.println(" the node is "+node+" the size is "+ p.size + " the other found is "+ p.otherfound);
                if(!p.otherfound){
                    if(p.size>mnval){
                        mnval = p.size;
                        mn = node;
                    }else if(p.size == mnval){
                        if(node < mn) mn = node;
                    }
                }
            }
        }


    return mn;
    }

    public static void Main(String[] args){
        Solution s = new Solution();

    }
}

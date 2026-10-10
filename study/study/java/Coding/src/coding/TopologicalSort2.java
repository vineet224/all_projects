package coding;

import java.util.ArrayList;
import java.util.PriorityQueue;

public class TopologicalSort2 {

    public int largestPathValue(String colors, int[][] edges) {
        int i,j,n = colors.length();

        int[] incoming = new int[n];
        ArrayList<ArrayList<Integer>> adj = new ArrayList<>();
        for(i=0;i<n;i++) {adj.add(new ArrayList<>());}
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        ArrayList<Integer> sorted = new ArrayList<>();

        for(i=0;i<edges.length;i++){
            int a = edges[i][0];
            int b = edges[i][1];

            incoming[b]++;
            adj.get(a).add(b);
        }

        for(i=0;i<n;i++){
            if(incoming[i]==0) {
                pq.add(i);
            }
        }

        while(pq.size()>0){
            int node = pq.remove();
            sorted.add(node);

            for(i=0;i<adj.get(node).size();i++){
                int ch = adj.get(node).get(i);
                incoming[ch]--;
                if(incoming[ch]==0){
                    pq.add(ch);
                }
            }
        }

        if(sorted.size() <n ) return -1;

        int[][] dp = new int[n][26];

        for(i=n-1;i>=0;i--){
            int[] charmax = new int[26];

            for(j=0;j<adj.get(i).size();j++){
                int child = adj.get(i).get(j);
                int[] thischarmax = dp[child];
                for(int k=0;k<26;k++){
                    charmax[k] = Math.max(charmax[k],thischarmax[k]);
                }
            }
            int ch = colors.charAt(i) - 'a';
            dp[i][ch] = dp[i][ch] +1;
        }

        int ans = 0;

        for(i=0;i<26;i++){
            ans = Math.max(ans, dp[0][i]);
        }


        return ans;
    }
}

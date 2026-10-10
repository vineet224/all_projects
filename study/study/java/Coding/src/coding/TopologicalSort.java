package coding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.PriorityQueue;

public class TopologicalSort {

    public int getFirstCharDiffer(String s1, String s2){
        int i=0;

        while(i<s1.length() && i<s2.length()){
            if(s1.charAt(i)!= s2.charAt(i)) return i;
            i++;
        }
        return i;
    }

    public String findOrder(String[] words) {
        int i,j,n=words.length;
        StringBuilder sb = new StringBuilder();


        int[] isPresent = new int[26];

        for(i=0;i<n;i++){
            for(j=0;j<words[i].length();j++){
                int ch = words[i].charAt(j) - 'a';
                isPresent[ch] =1;
            }
        }

        ArrayList<HashSet<Integer>> adj = new ArrayList<>();
        int[] incomingCount = new int[26];
        for(i=0;i<26;i++) {adj.add(new HashSet<>());}
        for(i=0;i<n;i++){
            String s1 = words[i];
            for(j=i+1;j<n;j++){
                String s2 = words[j];
                int ind = getFirstCharDiffer(s1,s2);
                if(ind == s1.length() || ind == s2.length()) continue;

                int ch1 = s1.charAt(ind) - 'a';
                int ch2 = s2.charAt(ind) - 'a';


                HashSet<Integer> existingGreater = adj.get(ch1);
                if(!existingGreater.contains(ch2)){
                    incomingCount[ch2]++;
                    adj.get(ch1).add(ch2);
                }
            }
        }

        PriorityQueue<Integer> pq = new PriorityQueue<>();
        for(i=0;i<26;i++){
            if(incomingCount[i] ==0 ){
                pq.add(i);
            }
        }

        while(pq.size()>0){
            int ch = pq.remove();

            sb.append((char)('a' + ch));

            for(Integer key: adj.get(ch)) {
                incomingCount[key]--;
                if (incomingCount[key] == 0) {
                    pq.add(key);
                }
            }

        }

        if(sb.length() == 26){
            StringBuilder ans = new StringBuilder();
            for(i=0;i<26;i++){
                int ch = sb.charAt(i) - 'a';
                if(isPresent[ch] == 1){
                    ans.append(sb.charAt(i));
                }
            }
            return ans.toString();

        }
        return "";

    }

}

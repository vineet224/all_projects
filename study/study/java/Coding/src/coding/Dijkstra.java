package coding;

import java.util.*;

class Pair{
    String node;
    int cost;
    String parent;
    public Pair(String node,int cost, String parent){this.node=node;this.cost=cost;this.parent=parent;}
}

class Main {

    public static String GraphChallenge(String[] strArr) {
        // code goes here
        int i,j,k,n;

        ArrayList<String> nodes = new ArrayList<>();
        HashMap<String, ArrayList<String>> adj = new HashMap<>();

        i=0;
        n = Integer.parseInt(strArr[0]);

        System.out.println(" the n is "+n);

        i=1;
        for(j = i;j<=n;j++){
            String node = strArr[j];
            nodes.add(node);
        }

        for(j=n+1;j<strArr.length;j++){
            String connection = strArr[j];
            System.out.println(" the connections are "+ connection + " j is "+ j);
            String[] splits = connection.split("-");

            String a = splits[0];
            String b = splits[1];

            ArrayList<String> achild = adj.getOrDefault(a,new ArrayList<String>());
            ArrayList<String> bchild = adj.getOrDefault(b,new ArrayList<String>());

            achild.add(b);
            bchild.add(a);

            adj.put(a,achild);
            adj.put(b,bchild);

        }

        for(String key: adj.keySet()){
            ArrayList<String> childs = adj.get(key);

            System.out.println(" the child of the a key " + key);
            for(i=0;i<childs.size();i++){
                System.out.print("  "+ childs.get(i));
            }

            System.out.println(" ");

        }



        HashMap<String,String> parent = new HashMap<>();
        HashMap<String,Integer> dis = new HashMap<>();

        PriorityQueue<Pair> pq = new PriorityQueue<>((a,b) ->{
            return Integer.compare(a.cost, b.cost);
        });

        pq.add(new Pair(nodes.get(0),0,"-1"));

        while(pq.size()>0){
            Pair top = pq.poll();

            if(parent.containsKey(top.node)){
                continue;
            }


            dis.put(top.node, top.cost);
            parent.put(top.node, top.parent);

            System.out.println(" the node is " + top.node + " the dis "+ top.cost + " parent is "+top.parent);

            if(top.node.equals(nodes.get(nodes.size()-1))) break;

            for(i=0;i<adj.getOrDefault(top.node, new ArrayList<String>()).size();i++){
                // if this is already dis
                String child = adj.get(top.node).get(i);
                if(dis.containsKey(child)) continue;
                int newcost = top.cost + 1;
                String par = top.node;

                pq.add(new Pair(child, newcost, par));
            }
        }

        StringBuilder ans = new StringBuilder("");

        if(!dis.containsKey(nodes.get(nodes.size()-1))){
            ans.append("-1");
            return ans.toString();
        }
        ans.append( nodes.get(nodes.size()-1) );
        String last = nodes.get(nodes.size()-1);
        for(i=0;i<n-1;i++){
            ans.append("-");
            ans.append(parent.get(last));
            last = parent.get(last);
        }

        return ans.toString();
    }

    public static void main (String[] args) {
        String[] arr = {
                "5", "A", "B", "C", "D", "E",
                "A-B", "B-C", "C-D", "D-E"
        };

        System.out.println(GraphChallenge(arr));
    }

}
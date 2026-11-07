package coding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;


public class Leetcodew4282 {

    record Edge(String name,double amt){}


    void dfs(HashMap<String,ArrayList<Edge>> graph, String node,double currentamt,String parent,HashMap<String,Double> daycost){

        String ch;
        double childamt;

        ArrayList<Edge> childs = graph.getOrDefault(node,new ArrayList<>());
        for(int i=0;i< childs.size();i++){
            Edge child = graph.get(node).get(i);
            ch =  child.name;
            if(Objects.equals(ch, parent)) continue;
            childamt = currentamt * child.amt;
            daycost.put(ch,childamt);
            dfs(graph,child.name,childamt,node,daycost);
        }
    }

    public double maxAmount(String initialCurrency, List<List<String>> pairs1, double[] rates1, List<List<String>> pairs2, double[] rates2) {
        int n1 = pairs1.size();
        int n2 = pairs2.size();
        double maxAmt = 1.0;
        double initAmt = 1.0;
        HashMap<String, ArrayList<Edge>> graph1 =  new HashMap<>();
        HashMap<String, ArrayList<Edge>> graph2 = new HashMap<>();

        creategraph(pairs1, rates1, n1, graph1);

        creategraph(pairs2, rates2, n2, graph2);

        HashMap<String,Double> day1Cost =  new HashMap<>();
        HashMap<String,Double> day2Cost =  new HashMap<>();

        day1Cost.put(initialCurrency,initAmt);
        day2Cost.put(initialCurrency,initAmt);

        dfs(graph1,initialCurrency,initAmt,"AAAAA",day1Cost);
        dfs(graph2,initialCurrency,initAmt,"AAAAA",day2Cost);

//        System.out.println("printing the day1 cost");
//        for(String key: day1Cost.keySet()){
//            System.out.println(key+" value is "+day1Cost.get(key) );
//        }
//
//        System.out.println("printing the day2 cost");
//        for(String key: day2Cost.keySet()){
//            System.out.println(key+" value is "+day2Cost.get(key) );
//        }

        for(String mid: day1Cost.keySet()){
            double day1amt = day1Cost.get(mid);
            if(day2Cost.containsKey(mid)){
                double day2amt = day2Cost.get(mid);
                double currentAmt = day1amt/day2amt;
                maxAmt = Math.max(maxAmt,currentAmt);
            }
        }

        return maxAmt;
    }

    private void creategraph(List<List<String>> pairs2, double[] rates2, int n2, HashMap<String, ArrayList<Edge>> graph2) {
        for(int i=0;i<n2;i++){
            String node1 = pairs2.get(i).get(0);
            String node2 = pairs2.get(i).get(1);
            double cost = rates2[i];
            double cost2 = (double)1/cost;
            graph2.putIfAbsent(node1,new ArrayList<>());
            graph2.putIfAbsent(node2,new ArrayList<>());
            graph2.get(node1).add(new Edge(node2,cost));
            graph2.get(node2).add(new Edge(node1,cost2));
        }
    }

    public static void main(String[] args){
        Leetcodew4282 obj = new Leetcodew4282();

        String initialCurrency = "EUR";
        List<List<String>> pairs1 = List.of(
                List.of("EUR", "USD"),
                List.of("USD", "JPY")
        );
        double[] rates1 = {2.0, 3.0};
        List<List<String>> pairs2 = List.of(
                List.of("JPY", "USD"),
                List.of("USD", "CHF"),
                List.of("CHF", "EUR")
        );
        double[] rates2 = {4.0, 5.0, 6.0};

        System.out.println(obj.maxAmount(initialCurrency, pairs1, rates1, pairs2, rates2));
    }

}

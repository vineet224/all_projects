package coding;

import java.util.ArrayList;
import java.util.List;

public class Leetcodew4231 {

    public int maxIncreasingSubarrays(List<Integer> nums) {
        int size = nums.size();
        if(size == 2) return 1;

        int[] rsea_including_self = new int[size];
        int[] lsea_including_self = new int[size];

        int cur=0;
        for(int i=1;i<size;i++){
            if(nums.get(i)>nums.get(i-1)){
                cur++;
                lsea_including_self[i] = cur;
            }else cur=0;
        }

        cur =0;
        for(int i=size-2;i>=0;i--){
            if(nums.get(i)<nums.get(i+1)){
                cur = cur+1;
                rsea_including_self[i] = cur;
            }else cur=0;
        }
        int mx = 1;
//        System.out.println("printing the lsea");
//        for(int i=0;i<size;i++){
//            System.out.print(lsea_including_self[i]+" ");
//        }
//        System.out.println("printing the rsea");
//
//        for(int i=0;i<size;i++){
//            System.out.print(rsea_including_self[i]+" ");
//        }

        for(int i =1;i<size;i++){
           int a = rsea_including_self[i] + 1 ;
           int b = lsea_including_self[i-1] + 1;
           int c = Math.min(a,b);
           mx = Math.max(mx,c);
        }

        return mx;
    }

    public static void main(String[] args){
        System.out.println("here is the first print line");
        ArrayList<Integer> lista = new ArrayList<>(List.of(1,2,3,4,5,6,7,5,6,9));
        Leetcodew4231 obj = new Leetcodew4231();
        int ans = obj.maxIncreasingSubarrays(lista);
        System.out.printf("ans is "+ans);
    }
}

package coding;

import java.util.ArrayList;
import java.util.Collections;

public class sample22 {

    int checkIfCanReach(int mid, int[] build, int ladders, int bricks){

        ArrayList<Integer> jumpCosts = new ArrayList<>();

        int currentBuildingHeight = build[0];
        for(int i=1;i<=mid;i++){
            int dif = build[i] - build[i-1];
            if(dif>0){
                jumpCosts.add(dif);
            }
        }

        Collections.sort(jumpCosts, Collections.reverseOrder());

        for(int i =0;i<jumpCosts.size();i++){
            if(ladders>0){
                ladders--;
                continue;
            }else{
                bricks = bricks - jumpCosts.get(i);
            }
        }

        if(bricks<0) return -1;
        return 1;
    }

    int findMaxDisBuilding(int[] build, int ladders, int bricks){
        int i,j,l,r,mid,n,ans=0;
        n = build.length;
        l = 0;
        r = n-1;

        while(l<=r){
            mid = (l+r)/2; // 2 3
            if(checkIfCanReach(mid, build, ladders, bricks) == 1){
                ans = mid;
                l = mid+1;
            }else{
                r = mid-1;
            }
        }
        return ans;
    }


    public static void main(String[] args){


        System.out.println("hello world here");

        int[] arr = {4,2,7,6,9,12,20};
        int ladders = 1;
        int bricks = 5;

        sample22 obj = new sample22();

        int ans = obj.findMaxDisBuilding(arr, ladders, bricks);

        System.out.println(" max index building i can reach "+ans);

    }

}

package coding;

import java.util.ArrayList;

public class LeetCode3953 {
    public static int minEnd(int n, int x) {
        int i,j,k;

        int[] bitProfit = new int[40];

        bitProfit[1] = 1;
        bitProfit[2] = 3;
        for(i=3;i<40;i++){
            bitProfit[i] = 2 * bitProfit[i-1] + 1;
        }

        int[] bitx = new int[40];
        ArrayList<Integer> freeBits = new ArrayList<>();
        ArrayList<Integer> freeBitsRequired = new ArrayList<>();

        j=0;
        while(x>0){
            bitx[j] = x%2;
            x=x/2;
            j++;
        }
        for(i=0;i<40;i++){
            if(bitx[i] ==0 ) {
                freeBits.add(i);
            }
        }
        System.out.println(" printing the bitx");
        for(i=0;i<40;i++){
            System.out.print(" "+bitx[i]);
        }
        System.out.println(" printing the freeBits");
        for(i=0;i<freeBits.size();i++){
            System.out.print(" "+freeBits.get(i));
        }

        int sum = n-1;

        while(sum>0){
            for(i=1;i<40;i++){
                if(bitProfit[i]==sum){
                    sum = 0;
                    freeBitsRequired.add(i);
                    break;
                }
                if(bitProfit[i]>sum){
                    sum = sum - bitProfit[i-1] -1;
                    freeBitsRequired.add(i);
                    break;
                }
            }
        }

        System.out.println(" printing the freeBits required ");
        for(i=0;i<freeBitsRequired.size();i++){
            System.out.print(" "+freeBitsRequired.get(i));
        }

        for(i=0;i<freeBitsRequired.size();i++){
            int kthBitRequired =  freeBitsRequired.get(i)-1;
            // select the kth bit from the freebit there must be

            int actualbit = freeBits.get(kthBitRequired);
            System.out.println(" the actual bit is "+ actualbit);
            bitx[actualbit] = 1;
        }

        int val =0;

        System.out.println(" printing the bitx updated");
        for(i=0;i<40;i++){
            System.out.print(" "+bitx[i]);
        }
        for(i=0;i<40;i++){
            if(bitx[i]==1){
                val = val + (1<<i);
            }
        }

        return val;

    }

    public static void main(String[] args){
        int n=3;
        int x=4;

        int ans = minEnd( n, x);
        System.out.println(" the ans is "+ ans);
    }
}

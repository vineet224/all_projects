package coding;

import java.util.HashMap;
import java.util.HashSet;

public class LeetCode3954 {
    public static int noOfSubArrWithThisUni(int[] nums, int uni){
        int i,j,k=0,n=nums.length;

        HashMap<Integer,Integer> freq = new HashMap<>();
        HashSet<Integer> unique = new HashSet<>();

        i=0;
        j=0;
        while(i<n && j<n){
            if(unique.size()<uni){
                unique.add(nums[j]);
                freq.put(nums[j],freq.getOrDefault(nums[j],0)+1);
                j++;
            }else if(unique.size() == uni){
                int freqj = freq.getOrDefault(nums[j],0);
                if(freqj ==0 ){
                    k = k + j-i;
                    // now have to remove the i from the freq;
                    int freqi = freq.getOrDefault(nums[i],0);
                    freq.put(nums[i],freqi -1);
                    if(freqi==1){
                        unique.remove(nums[i]);
                    }
                    i++;
                }else{
                    freq.put(nums[j],freq.getOrDefault(nums[j],0));
                    j++;
                }
            }
        }

        //System.out.println(" the mid is "+uni + " the k is "+k);

        int remainingLength = n - i;
        //System.out.println(" the mid is "+uni + " the remaining length is "+remainingLength);
        k = k + (remainingLength * (remainingLength+1))/2;

        return k;
    }

    public static int medianOfUniquenessArray(int[] nums) {
        int n=nums.length,l,r,mid,midean,ans=0;
        l=1;
        r=n-1;

        midean = (((n*(n+1))/2))/2;

        //System.out.println(" the median length is "+ midean);

        while(l<=r){
            mid = (l+r)/2;

            int arrayWithMidUniqueOrLess = noOfSubArrWithThisUni(nums,mid);
            //System.out.println(" the no of subarrays with no of unique less then or equal to mid "+mid + " are "+ arrayWithMidUniqueOrLess);

            if(arrayWithMidUniqueOrLess>=midean) {
                ans = mid;
                r = mid-1;
            }else{
                l = mid+1;
            }
        }

        return ans;
    }

    public static void main(String[] args){
        int[] nums = {3,4,3,4,5};

        int ans = medianOfUniquenessArray(nums);

        System.out.println(" the ans is "+ans);

    }
}

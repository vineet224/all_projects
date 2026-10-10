package coding;

import java.util.Arrays;

public class LeetCode3952 {


    public static int minimumAddedInteger(int[] nums1, int[] nums2) {
        int i,j,k,n,m,l,ans = Integer.MAX_VALUE;
        n=nums1.length;
        m=nums2.length;

        for(i=0;i<n-1;i++){
            for(j=i+1;j<n;j++){
                // remove the i and j from the nums1 or not consider them
                int[] arr = new int[m];
                l=0;
                for(k=0;k<n;k++){
                    if(k==i || k==j) continue;
                    arr[l] = nums1[k];
                    l++;
                }

                Arrays.sort(nums2);
                Arrays.sort(arr);
//                System.out.println(" the sorted arr ");
//                for(k=0;k<m;k++){
//                    System.out.print(" "+arr[k]);
//                }
//                System.out.println(" the sorted nusm2 ");
//                for(k=0;k<m;k++){
//                    System.out.print(" "+nums2[k]);
//                }

                int dif = nums2[m-1] - arr[m-1];
                // now check for each element if that satisfy

                int iseq=1;
                for(k=0;k<m;k++){
                    if(arr[k]+dif != nums2[k]){
                        iseq=0;
                        break;
                    }
                }
                if(iseq ==1) ans = Math.min(ans, dif);
            }
        }
        return ans;

    }


    public static void main(String[] args){
//        int[] nums1 = {4,20,16,12,8};
//        int[] nums2 = {14,18,10};

        int[] nums1 = {3,5,5,3};
        int[] nums2 = {7,7};

        int ans = minimumAddedInteger(nums1, nums2);

        System.out.println(" the ans is "+ans);
    }

}

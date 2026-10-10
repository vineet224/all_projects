package coding;

public class sample23 {

    void printDuplicate(int[] arr){

        int n = arr.length;

        int i=0;
        int next = -1;
        int toPlaceelemt;
        while(i<n){
            // if  the index is at it's position continue;
            // else move it to corresponding postion

            if(arr[i] == i) {
                i++;
                continue;
            }
            toPlaceelemt = arr[i];
            next = arr[toPlaceelemt];
            System.out.println(" the element to place "+ toPlaceelemt + " current element at the next placment"+next);
            while(true){
                if(arr[toPlaceelemt] == toPlaceelemt){
                    System.out.println(" this is duplicate"+toPlaceelemt);
                    break;
                }
                arr[toPlaceelemt] = toPlaceelemt;
                toPlaceelemt = next;
                next = arr[toPlaceelemt];
                System.out.println(" theee element to place "+ toPlaceelemt + " current element at the next placment"+next);
                if(arr[toPlaceelemt] == toPlaceelemt){
                    System.out.println(" this is duplicate"+toPlaceelemt);
                    break;
                }
                if(toPlaceelemt == i){
                    break;
                }
            }
            i++;
        }
    }


    public static void main(String[] args){


        System.out.println("hello world here");

        int[] arr = {1, 3, 2, 6, 3, 6, 1};

        sample23 obj = new sample23();
        obj.printDuplicate(arr);

        System.out.println(" max index building i can reach ");

    }

}

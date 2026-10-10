package interviews.interviewanaira;

public class Main {
    public  static  void main(String[] args){
        Test testObject = new Test();
        String testString1 = " this is the test    string  first  ";
        String testStringNull = null;
        String testStringEmpty = "";

        String response1 = testObject.trimingAllSpaces(testString1);
        System.out.println(response1);

        String response2 = testObject.trimingAllSpaces(null);
        System.out.println(response2);

        String response3 = testObject.trimingAllSpaces(testStringEmpty);
        System.out.println(response3);
    }
}

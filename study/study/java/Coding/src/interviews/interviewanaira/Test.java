package interviews.interviewanaira;

// we have to delete the trailing and end spacees and replaces the multiple middle spaces with single space

// " space this    is the    string   "
// "space this is the string"

public class Test {

    String removeTrailingAndLeadingSpace(String str){
        try{
            if(str.isEmpty()){
                return "";
            }
            return str.trim();
        } catch(Exception e){
            System.out.println("exception occured while triming"+e.getMessage());
            throw new RuntimeException("Exception occured while triming",e);
        }
    }

    String trimingAllSpaces(String str){

        // define here things we want to log in the catch
        try{
            if(str == null){
                return null;
            }
            if(str.isEmpty()){
                return "";
            }

            // check if there exists inbuilt java functionaly for removing multiple space string or we can use replace("regex", "_")

            StringBuilder responseString = new StringBuilder();
            String[] splits = str.split(" ");

            for(int i=0;i< splits.length;i++) {
                if (!splits[i].isEmpty()) {
                    responseString.append(splits[i]);
                    if(i != splits.length-1) {
                        responseString.append(" "); // use explicitly space removal from the last of string outside for loop return responseString.toString().trim();
                    }
                }
            }

            return responseString.toString();
        }catch(Exception e){ // use specific exception instead of general exception class
            System.out.println("exception occured while trimming space"+e.getMessage());
            throw new RuntimeException("Exception occured while Middle",e); // throw specific actionable exception
        }
    }
}

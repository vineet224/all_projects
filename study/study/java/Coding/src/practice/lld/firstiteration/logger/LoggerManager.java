package practice.lld.firstiteration.logger;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/***
 *  functional requirements
 *  - should accept and save logs in the format {containerId, timestamp(dd-mm-yyyyy), log-level, log-message, traceid}
 *  - depending on log levels the logs should be printed in different sinks
 *  - there will be get query to get logs based on (basic filter, basic sorting, grouping 1 level)
 *
 *  entities:
 *  - LogMessage{logId, traceId, containerid, timestamp, level, message}
 *  - FilterQueru{key, value}
 *  - SortQuery{key}
 *  - GroupQueru{key}
 *  - Interface sinks
 *  - SystemConsolerSink
 *  - FileSink
 *  - DBSink
 *
 *  // not doing things for runnability
 *  - the sink currently process the log printing in synchournous manner but they must be asynchournous for printing  for this we can add queue where they will submit there logs to print
 *  - in production we would definately be using the filter strategy for different filters different fileters like traceId, caontainerId currently just if else
 *  - also we can use strategies for filter, grouping , sorting as well different strategies
 *  -
 */

enum LogLevel{
    INFO,
    DEBUG,
    ERROR
}

class LogMessage{
    int logId;
    String traceId;
    String containerId;
    String timestamp; // in epoch millisec
    LogLevel level;
    String message;

    static int logIdCount = 0; // this is not thread safe we can use the atomic int instead for generative id


    public LogMessage(String logline) {
        String[] split = logline.split("\\|");
        this.logId = logIdCount;
        logIdCount++;

        System.out.println(" the splits are trace "+ split[0] + " containerId " + split[1] + " timestamp "+ split[2] + " the level "+ split[3] + " the message "+ split[4]);

        this.traceId = split[0];
        this.containerId = split[1];
        this.timestamp = split[2];
        this.level = LogLevel.valueOf(split[3]);
        this.message = split[4];
    }

    @Override
    public String toString(){
        return "traceId: "+ this.traceId + "containerid: " + this.containerId + "message: "+ this.message;
    }

}

interface Sink {
    default void print(LogMessage logmessage){}
}

class SystemConsoleSink implements Sink{
    @Override
    public void print(LogMessage logMessage){
        System.out.println("printing in the system console "+ logMessage.toString());
    }
}

class FileSink implements Sink{
    @Override
    public void print(LogMessage logMessage){
        System.out.println("printing in the file "+ logMessage.toString());
    }
}

class LogFilter{
    String key;
    String value;

    public LogFilter(String key, String value){
        this.key    = key;
        this.value = value;
    }
}



public class LoggerManager {

    ArrayList<LogMessage> allLogs;

    HashMap<LogLevel, ArrayList<Sink>> sinkFactory = new HashMap<>();

    public LoggerManager(){
        allLogs = new ArrayList<>();
        sinkFactory.put(LogLevel.DEBUG,new ArrayList<>());
        sinkFactory.put(LogLevel.INFO,new ArrayList<>());
        sinkFactory.put(LogLevel.ERROR,new ArrayList<>());
        sinkFactory.get(LogLevel.INFO).add(new SystemConsoleSink());
        sinkFactory.get(LogLevel.DEBUG).add(new SystemConsoleSink());
        sinkFactory.get(LogLevel.DEBUG).add(new FileSink());
    }

    public void addLog(String logLine){
        // there could be validation for the log line if it is valid time stamp and other things
        LogMessage logMessage = new LogMessage(logLine);
        allLogs.add(logMessage);

        ArrayList<Sink> sinksToPrint = sinkFactory.get(logMessage.level);

        for(Sink sink: sinksToPrint){
            sink.print(logMessage);
        }
    }

    public HashMap<String, ArrayList<LogMessage>> getLogs(ArrayList<LogFilter> logFilters, String sortKey, String groupingKey){
        ArrayList<LogMessage> result ;

        result = allLogs;

        for(LogFilter filter: logFilters){
            result = logFilterHandler(filter, result);
        }

        Collections.sort(result, (a,b) -> {
            if(sortKey.equals("containerId")){
                return a.containerId.compareTo(b.containerId);
            } else if(sortKey.equals("traceId")){
                return a.traceId.compareTo(b.traceId);
            }else{
                System.out.println(" sort key is invalid");
                return 1;
            }
        });

        // now to make the groups we create hashMap based on the groupingKey
        HashMap<String, ArrayList<LogMessage>> answer = new HashMap<>();

        for(LogMessage logMessage: result){
            String key = "";
            if(groupingKey.equals("containerId")){
                key = logMessage.containerId;
            } else if(groupingKey.equals("traceId")){
                key = logMessage.traceId;
            } else if(groupingKey.equals("level")){
                key = logMessage.level.toString();
            }else{
                System.out.println(" the grouping key is not correct");
            }

            if(key.isEmpty()){
                continue;
            }

            answer.putIfAbsent(key,new ArrayList<>());
            answer.get(key).add(logMessage);
        }


        return answer;
    }


    public ArrayList<LogMessage> logFilterHandler(LogFilter logFilter, ArrayList<LogMessage> logMessages){
        ArrayList<LogMessage> result = new ArrayList<>();

        for(LogMessage logMessage: logMessages){
            if(logFilter.key.equals("containerId") && logMessage.containerId.equals(logFilter.value)){
                result.add(logMessage);
            } else if(logFilter.key.equals("traceId") && logMessage.traceId.equals(logFilter.value)) {
                result.add(logMessage);
            } else if(logFilter.key.equals("level") && logMessage.level.equals( LogLevel.valueOf(logFilter.value))) {
                result.add(logMessage);
            } else{
                System.out.println(" the filter not mached this log message");
            }
        }
        return result;
        // we can apply multiple levels of this filete now
    }


    public static void main(String[] args){

        LoggerManager loggerManager = new LoggerManager();

        String logLine1 = "trace1|container1|12-02-1999|INFO|reward is accuired";
        String logLine2 = "trace1|container2|12-02-1999|DEBUG|reward is rejected";

        loggerManager.addLog(logLine1);
        loggerManager.addLog(logLine2);

        LogFilter logfilter = new LogFilter("traceId","trace1");

        ArrayList<LogFilter> filters = new ArrayList<>();
        filters.add(logfilter);

        HashMap<String, ArrayList<LogMessage>> ans = loggerManager.getLogs(filters,  "containerId", "containerId");

    }

}



























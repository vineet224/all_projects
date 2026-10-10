package practice.lld.firstiteration.loggerold;

public class TerminalPrinter implements PrinterInterface{
    @Override
    public void print(LogMessage messageToPrint) {
        System.out.println(messageToPrint.toString());
    }
}

package practice.lld.firstiteration.tradingsystem;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/***
 * - Question received from interveiwer: Trading system
 *
 * - functional requirements:
 * -  user can search for any stock name and get the stock detail like it's current price
 * - user can search for any stock name and get the old prices
 * - user can place order to buy stock (number of stock, the min price it want's to buy the max price user allow)
 * - usre should be able to check status of the stock order
 *
 * non functional requirements:
 * - the should be no duplicate spend or no withdrawl of amount if the order is executed
 * - the current price should also be updated each 5 sec window or less
 * - just an hld thing that read calls will be much higher in this system, and if the user stays on the stock page might be using polling to update the price or something server side events or websockets just for intraday trading or those things
 * - assumptions : we are not handling here the intraday or other trading things, just the user places order for a like in a amazon store or something
 * - assumptions: we are not handling the IPO's as well
 *
 *
 * entities:
 * - stock {}
 * - orderRequest
 * - order
 * - transactionledger
 *
 *
 *
 *
 */


enum OrderStatus{
    Created, IN_PROGRESS, SUCCESS, FAILED
}

class OrderRequest{
    int id; // here my doubt is that does should i keep this as idempotent key or from the main function should i pass another idempotent key
    int stockId;
    int stockNumbers;
    double maxPriceToBuyPerStock;

    static AtomicInteger idCount = new AtomicInteger(0);

    public OrderRequest(int stockId, int stockNumbers, double maxPriceToBuyPerStock){
        this.id = idCount.addAndGet(1);
        this.stockId = stockId;
        this.stockNumbers = stockNumbers;
        this.maxPriceToBuyPerStock = maxPriceToBuyPerStock;
    }
}

class Stock{
    int id;
    String stockName;
    private double currentPrice;
    private long lastUpdatedTime; // added this because the system can use this for polling in production level i think so

    static AtomicInteger idCount = new AtomicInteger(0);

    public Stock(String stockName, double currentPrice){
        this.currentPrice = currentPrice;
        this.stockName = stockName;
        this.id = idCount.addAndGet(1);
        this.lastUpdatedTime = System.currentTimeMillis();
    }


    public synchronized void updatePrice(double newPrice){
        this.currentPrice = newPrice;
        this.lastUpdatedTime  = System.currentTimeMillis();
        return;
    }

    public synchronized double getPrice(){
        return this.currentPrice; // or we can use volatile instead so we don't need to synchronize the read part
    }

    public synchronized long getLastUpdatedTime() {
        return lastUpdatedTime;
    }

}

class OrderResponse{
    int id;
    int stockId;
    int requestId;
    OrderStatus status;
    private double orderConfirmedAtPrice ;

    static AtomicInteger idCount = new AtomicInteger(0);

    public OrderResponse(int stockId, int requestId, double requestedMaxPrice){
        this.id = idCount.addAndGet(1);
        this.stockId = stockId;
        this.requestId = requestId;
        this.status = OrderStatus.IN_PROGRESS;
        this.orderConfirmedAtPrice = requestedMaxPrice; // for now assuming that the third will be coosing the max price to buy
    }


    public synchronized double updateOrderConfimedAtPrice(double orderPrice){
        this.orderConfirmedAtPrice = orderPrice;
        return this.orderConfirmedAtPrice;
    }

    public synchronized double getOrderConfirmedAtPrice(){
        return this.orderConfirmedAtPrice;
    }
}

interface StockAgency{
    public void getAndUpdateStockPrice(int stockId);
    public OrderResponse placeOrder(OrderRequest orderRequest);
}

class JaneStreetStockAgency implements StockAgency{

    @Override
    public void getAndUpdateStockPrice(int stockId) {
        // this would call the third party and get the value of the stock
        double price = 5*Math.random();
        Stock stock = MockDataBase.stocksMap.get(stockId);
        stock.updatePrice(price);
        return ;
    }

    @Override
    public OrderResponse placeOrder(OrderRequest orderRequest) {

        OrderResponse orderResponse = new OrderResponse(orderRequest.stockId, orderRequest.id, orderRequest.maxPriceToBuyPerStock);
        MockDataBase.orderLedger.put(orderRequest.id, orderResponse);
        return orderResponse;
    }
}

class MockDataBase{
    public static ConcurrentHashMap<Integer, Stock> stocksMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Integer, OrderResponse> orderLedger = new ConcurrentHashMap<>(); // key is orderRequestId that is idempotent Key here
    public static ConcurrentHashMap<Integer, OrderRequest> orderRequests = new ConcurrentHashMap<>(); // same idempotent key
}


public class TradingSystem {

    public final StockAgency agency = new JaneStreetStockAgency();

    public double getStockPrice(int stockId){
        // check here if the stock is not present then return exception wrong stockId
        Stock stock = MockDataBase.stocksMap.get(stockId);
        agency.getAndUpdateStockPrice(stockId);
        return stock.getPrice();
    }

    public OrderResponse placeOrder(OrderRequest orderRequest){

        // check in the order requests and the ledger if the id already present

        if(MockDataBase.orderRequests.containsKey(orderRequest.id)){
            System.out.println(" the order is alredy present in our system with id"+ orderRequest.id);
            return null;
        }

        MockDataBase.orderRequests.put(orderRequest.id, orderRequest);
        OrderResponse orderResponse = agency.placeOrder(orderRequest);

        System.out.println("succes fully placed order for orderId : "+ orderRequest.id);
        return orderResponse;
    }

    public void updateOrderWebhookFromAgency(int orderRequestId, OrderStatus orderStatus, double acceptedPrice){
        // check if the order is present in the ledger or not if not then throw error or handle it seperately

        OrderResponse orderResponse = MockDataBase.orderLedger.get(orderRequestId);

        orderResponse.status = orderStatus;
        orderResponse.updateOrderConfimedAtPrice(acceptedPrice);

        MockDataBase.orderLedger.put(orderRequestId,orderResponse);

        System.out.println("succes fully update order statud for orderId : "+ orderRequestId + " status "+ orderStatus);

        return ;
    }

    public static void main(String[] args){

        TradingSystem tradingSystem = new TradingSystem();

        Stock stock1 = new Stock("vineetCompany", 1000.00);
        MockDataBase.stocksMap.put(stock1.id, stock1);

        OrderRequest orderRequest1 = new OrderRequest(stock1.id, 20, 1001.00);
        OrderRequest orderRequest2 = new OrderRequest(stock1.id, 10, 1002.00);

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        executorService.submit(()->{tradingSystem.placeOrder(orderRequest1);});
        executorService.submit(()->{tradingSystem.placeOrder(orderRequest1);});
        executorService.submit(()->{tradingSystem.placeOrder(orderRequest2);});

        executorService.shutdown();

        tradingSystem.updateOrderWebhookFromAgency(orderRequest1.id, OrderStatus.SUCCESS, 1001.0);

    }

}

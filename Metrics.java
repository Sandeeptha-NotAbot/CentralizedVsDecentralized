/**
 * Metrics.java
 * Accumulates the total costs and service statistics over one simulation replication.
 */
public class Metrics {

    // --- Cost Metrics (Accumulated Totals) ---
    private double holdingCost = 0.0;     // Accumulated cost for holding inventory (on_hand). 
    private double backorderCost = 0.0;   // Accumulated cost for having backorders (unmet demand). 
    private double transportCost = 0.0;   // Accumulated cost for all shipments across the entire supply chain.
    
    // --- Service and Frequency Metrics (Accumulated Totals) ---
    private int ordersCount = 0;          // Total number of *ordering events* (used to compare ordering frequency). 
    private int fillImmediate = 0;        // Total units served directly from on-hand stock (numerator for Fill Rate). 
    private int demandTotal = 0;          // Total customer demand realized (denominator for Fill Rate). 
    private int backordersCreated = 0;    // Total units of demand that became a backorder on the day of demand. 

    /**
     * Constructor (initializes all metrics to zero).
     */
    public Metrics() {
        // All fields are initialized to 0 or 0.0 by default or the initial values above.
    }

    // --- Getters (Required for final report summarization) ---

    public double getHoldingCost() {
        return holdingCost;
    }

    public double getBackorderCost() {
        return backorderCost;
    }

    public double getTransportCost() {
        return transportCost;
    }

    public int getOrdersCount() {
        return ordersCount;
    }

    public int getFillImmediate() {
        return fillImmediate;
    }

    public int getDemandTotal() {
        return demandTotal;
    }
    
    // --- Methods for Accumulation (Used by simulation functions like accrueDailyCosts and placeBaseStockOrder) ---

    public void addHoldingCost(double cost) {
        this.holdingCost += cost;
    }

    public void addBackorderCost(double cost) {
        this.backorderCost += cost;
    }
    
    public void addTransportCost(double cost) {
        this.transportCost += cost;
    }

    public void incrementOrdersCount() {
        this.ordersCount++;
    }

    public void addFillImmediate(int served) {
        this.fillImmediate += served;
    }

    public void addDemandTotal(int demand) {
        this.demandTotal += demand;
    }

    public void addBackordersCreated(int shortage) {
        this.backordersCreated += shortage;
    }
    
    /**
     * Calculates the overall Fill Rate (beta) for the replication.
     * Fill Rate is the fraction of demand met immediately from stock. 
     */
    public double calculateFillRate() {
        if (demandTotal == 0) return 0.0;
        return (double) fillImmediate / demandTotal;
    }

    /**
     * Calculates the total accumulated cost.
     */
    public double calculateTotalCost() {
        return holdingCost + backorderCost + transportCost;
    }
}
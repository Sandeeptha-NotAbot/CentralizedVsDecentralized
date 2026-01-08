import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.stream.Collectors;

public class Simulation {

    // --- Core Utility Functions ---

    /**
     * Calculates the Inventory Position (IP) for a node.
     * IP = Stock on Hand + Stock on Order - Backorder Liability
     */
    private int inventoryPosition(NodeState node) {
        // Sum of all pending orders (Stock on Order)
        int inTransitQty = node.getInTransit().stream().mapToInt(Shipment::getQty).sum();
        return node.getOnHand() + inTransitQty - node.getBackorder();
    }

    /**
     * Executes the "Arrivals" step of the daily cycle.
     * Moves arrived quantity from 'in_transit' to 'on_hand' inventory.
     */
    private void receiveShipments(NodeState node, int day) {
        Iterator<Shipment> it = node.getInTransit().iterator();
        while (it.hasNext()) {
            Shipment sh = it.next();
            if (sh.getArriveDay() == day) {
                node.setOnHand(node.getOnHand() + sh.getQty());
                it.remove(); // Remove delivered from node.inTransit 
            }
        }
    }

    /**
     * Uses new inventory to fill old customer orders first. 
     */
    private void clearBackordersWithReceipt(NodeState node) {
        if (node.getBackorder() > 0 && node.getOnHand() > 0) {
            int cleared = Math.min(node.getOnHand(), node.getBackorder());
            node.setOnHand(node.getOnHand() - cleared); // Use available stock to clear backlog. 
            node.setBackorder(node.getBackorder() - cleared); // Reduce the backorder liability.
        }
    }

    /**
     * Handles customer interaction: demand realization and service.
     */
    private void fulfillDemand(NodeState node, int demand, Metrics metrics) {
        int served = Math.min(node.getOnHand(), demand); // Serve up to the amount available or the demand. 
        node.setOnHand(node.getOnHand() - served);
        metrics.addFillImmediate(served); // Track units served immediately. 

        int shortage = 0;
        if (served < demand) {
            shortage = demand - served;
            node.setBackorder(node.getBackorder() + shortage); // Add unserved demand to the backorder queue. 
        }
        metrics.addBackordersCreated(shortage);// Track total units that became a backorder. 
        metrics.addDemandTotal(demand); // Record all demand for service metric calculations. 
    }

    /**
     * Implements the (S-1, S) or Base-Stock policy.
     */
    private void placeBaseStockOrder(NodeState node, NodeState supplier, int lead, double cPerUnit, int today, Metrics metrics) {
        int ip = inventoryPosition(node);
        int qty = node.getBaseStock() - ip; // Order quantity needed to bring IP back up to the target S. 

        if (qty > 0) {
            // Create and track the shipment
            Shipment sh = new Shipment(qty, supplier.getName(), node.getName(),
                                       today + lead, cPerUnit); // Calculate the arrival day. 
            node.getInTransit().add(sh); // Attach shipment to 'dst'. 

            // Record costs and events
            metrics.addTransportCost(qty * cPerUnit); // Immediately record the transportation cost. 
            metrics.incrementOrdersCount(); // Track how many times an order event occurs. 
            // For audit/debug: node.setOrdersPlacedToday(qty); 
        } else {
             // Only ship positive quantities. 
        }
    }

    /**
     * This is the "cost accounting" step at the end of the day. 
     */
    private void accrueDailyCosts(List<NodeState> allNodes, Metrics metrics) {
        for (NodeState n : allNodes) {
            // Holding cost is applied to *ending* inventory.
            metrics.addHoldingCost(n.getOnHand() * Params.COST_HOLDING_PER_DAY); 
            // Backorder cost is applied to the liability carried into the next day.
            metrics.addBackorderCost(n.getBackorder() * Params.COST_BACKORDER_PER_DAY);
        }
    }

    /**
     * Simulates demand based on a Normal distribution truncated at 0.
     * NOTE: This simplified version ignores correlation (rho). 
     * A full implementation requires a multivariate normal draw function.
     */
    private int drawDailyDemand(int retailerIndex) {
        // Retailer index is not used in the simplified independent case.
        // Option A: independent Normal truncated at 0 
        double mu = Params.DEMAND_MEAN_DAILY;
        double sigma = Params.DEMAND_SIGMA_DAILY;
        
        // Simple approximation of Normal distribution draw
        double demandDraw = ThreadLocalRandom.current().nextGaussian() * sigma + mu;
        
        // Demand is always non-negative and an integer.
        return Math.max(0, (int) Math.round(demandDraw)); 
    }

    /**
     * Calculates the target S for each node based on Normal distribution approximation (safety stock formula). 
     */
    public void computeBaseStocks(List<NodeState> retailers, NodeState cw, boolean centralized) {
        double mu = Params.DEMAND_MEAN_DAILY; // Average demand per day. 
        double sigma = Params.DEMAND_SIGMA_DAILY; // Demand standard deviation per day.
        double z = Params.getZScoreForServiceTarget(); // z-score chosen to achieve the target service level. 

        // --- Retailers ---
        for (NodeState r : retailers) {
            // L is the Lead Time (L) relevant to the node's supplier. 
            int L = centralized ? Params.LT_CW_TO_RETAILER : Params.LT_MFG_TO_RETAILER;

            // S_i = Expected Demand Over Lead Time + Safety Stock 
            double sI = mu * L + z * sigma * Math.sqrt(L); // The safety stock term is crucial.
            r.setBaseStock((int) Math.round(sI));
        }

        // --- Central Warehouse (CW) ---
        if (centralized && cw != null) {
            int N = Params.N_RETAILERS;
            double rho = Params.DEMAND_CORRELATION_RHO;

            // Aggregated variability: core logic of Risk Pooling. 
            // sigma_agg = sigma * sqrt(N + ρ N(N-1)) for identical retailers 
            double sigmaAggSquared = N * sigma * sigma + rho * N * (N - 1) * sigma * sigma;
            double sigmaAgg = Math.sqrt(sigmaAggSquared);// Aggregated standard deviation calculation. 
            
            int lCw = Params.LT_MFG_TO_CW; // CW's relevant lead time (from MFG). 
            double muAgg = N * mu; // Aggregated mean demand (sum of all retailers).

            // SCW = Expected Demand Over Lead Time + Safety Stock (using lower aggregated variability)
            double sCW = muAgg * lCw + z * sigmaAgg * Math.sqrt(lCw); 
            cw.setBaseStock((int) Math.round(sCW)); 
        }
    }

    /**
     * Simulates the system (MFG -> CW -> Retailers). 
     */
    public Metrics runCentralizedReplication(int seed, List<NodeState> retailers, NodeState cw, NodeState mfg) {
        // init RNG(seed) (Done implicitly by ThreadLocalRandom or explicitly with a seed)
        Metrics metrics = new Metrics(); // METRICS <- zeros 
        
        // Assume initial states are set (on_hand to base_stock, backorder=0, in_transit=[]) 
        List<NodeState> allNodes = new ArrayList<>();
        allNodes.add(cw);
        allNodes.addAll(retailers);

        for (int day = 1; day <= Params.T_DAYS; day++) {

            // 1) Arrivals then clear backorders
            receiveShipments(cw, day);
            clearBackordersWithReceipt(cw);
            for (NodeState r : retailers) {
                receiveShipments(r, day);
                clearBackordersWithReceipt(r);
            }

            // 2) Retailer demand realization and service (customer-facing)
            for (int i = 0; i < retailers.size(); i++) { // for each retailer r:
                NodeState r = retailers.get(i);
                int d = drawDailyDemand(i);
                fulfillDemand(r, d, metrics);
            }

            // 4) End-of-day base-stock review at CW (order to MFG if needed)
            placeBaseStockOrder(cw, mfg, Params.LT_MFG_TO_CW,
                                Params.COST_TRANSPORT_INBOUND, day, metrics); // CW orders to top up its own IP.

            // 5) End-of-day retailer reviews & orders to CW
            for (NodeState r : retailers) { 
                // Retailers order from the CW.
                placeBaseStockOrder(r, cw, Params.LT_CW_TO_RETAILER,
                                   Params.COST_TRANSPORT_OUTBOUND, day, metrics);
            }
            // The CW is assumed to immediately ship from stock (logic handled inside placeBaseStockOrder). 

            // 6) Costs for the day
            accrueDailyCosts(allNodes, metrics); // Costs apply to all nodes in the system. 
        }
        return metrics; 
    }

    /**
     * Simulates the single system (MFG -> Retailers). 
     */
    public Metrics runDecentralizedReplication(int seed, List<NodeState> retailers, NodeState mfg) {
        // init RNG(seed)
        Metrics metrics = new Metrics(); // METRICS <- zeros 
        
        // Assume initial states are set (on_hand=base_stock, backorder=0, in_transit=[])

        for (int day = 1; day <= Params.T_DAYS; day++) {

            // 1) Arrivals then clear backorders
            for (NodeState r : retailers) {
                receiveShipments(r, day);
                clearBackordersWithReceipt(r);
            }

            // 2) Retailer demand realization
            for (int i = 0; i < retailers.size(); i++) {
                NodeState r = retailers.get(i);
                int d = drawDailyDemand(i);
                fulfillDemand(r, d, metrics);
            }

            // 3) End-of-day retailer reviews & orders directly to MFG
            for (NodeState r : retailers) {
                // Retailers order directly from the MFG.
                placeBaseStockOrder(r, mfg, Params.LT_MFG_TO_RETAILER, 
                                    Params.COST_TRANSPORT_DIRECT, day, metrics);
            }

            // 4) Costs for the day
            accrueDailyCosts(retailers, metrics); // Costs only apply to the retailer nodes. 
        }
        return metrics; 
    }

    /**
     * The overarching function that runs both scenarios and compares results.
     */
    public void runExperiment() {
        System.out.println("--- Starting Inventory Centralization vs. Decentralization Experiment ---");

        List<Metrics> resultsCentralized = new ArrayList<>();
        List<Metrics> resultsDecentralized = new ArrayList<>();

        // Initialize nodes outside the loop to be reused and re-initialized per replication
        NodeState mfg = new NodeState("MFG", 0); // Manufacturer has infinite capacity (onHand unused)
        NodeState cw = new NodeState("CW", 0); // Central Warehouse
        List<NodeState> retailers = new ArrayList<>();
        for (int i = 0; i < Params.N_RETAILERS; i++) {
            retailers.add(new NodeState("Retailer_" + (i + 1), 0));
        }

        for (int rep = 1; rep <= Params.R_REPLICATIONS; rep++) {
            // --- CENTRALIZED RUN ---
            computeBaseStocks(retailers, cw, true); // Calculate S values for Centralized design
            // Reset and warm-up inventory states (simplification: setting initial stock to Base Stock S)
            cw.setOnHand(cw.getBaseStock()); cw.setBackorder(0); cw.getInTransit().clear();
            for (NodeState r : retailers) {
                r.setOnHand(r.getBaseStock()); r.setBackorder(0); r.getInTransit().clear();
            }
            Metrics resC = runCentralizedReplication(rep, retailers, cw, mfg);
            resultsCentralized.add(resC);
            

            // --- DECENTRALIZED RUN ---
            // Recalculate S values for Decentralized design (uses LT_MFG_TO_RETAILER)
            computeBaseStocks(retailers, null, false); 
            // Reset and warm-up inventory states
            for (NodeState r : retailers) {
                r.setOnHand(r.getBaseStock()); r.setBackorder(0); r.getInTransit().clear();
            }
            Metrics resD = runDecentralizedReplication(rep, retailers, mfg);
            resultsDecentralized.add(resD);
        }

        // 3. Summarize and Print Results
        System.out.println("\n--- Summary Results Averaged Over " + Params.R_REPLICATIONS + " Replications ---");
        
        // This function will summarize results (Phase 5 task)
        summarizeResults(resultsCentralized, resultsDecentralized);
    }

    // Add a simple Main method to run the experiment
    public static void main(String[] args) {
        new Simulation().runExperiment();
    }

    private SummaryMetrics calculateAverages(List<Metrics> results) {
        if (results == null || results.isEmpty()) {
            return new SummaryMetrics(); 
        }

        int R = results.size();
        int T_DAYS = Params.T_DAYS;

        // Sum up the raw metric totals from all replications
        double total_holding = results.stream().mapToDouble(Metrics::getHoldingCost).sum();
        double total_backorder = results.stream().mapToDouble(Metrics::getBackorderCost).sum();
        double total_transport = results.stream().mapToDouble(Metrics::getTransportCost).sum();
        int total_orders = results.stream().mapToInt(Metrics::getOrdersCount).sum();
        int total_fill_immediate = results.stream().mapToInt(Metrics::getFillImmediate).sum();
        int total_demand = results.stream().mapToInt(Metrics::getDemandTotal).sum();
        
        SummaryMetrics summary = new SummaryMetrics();

        // Total Cost / day = sum(Holding + Backorder + Transport) / (T_days * R)
        summary.totalCostPerDay = (total_holding + total_backorder + total_transport) / (T_DAYS * R);
        
        // Fill Rate = sum(fill_immediate) / sum(demand_total)
        summary.fillRate = (double) total_fill_immediate / total_demand;

        // Avg Cost / day
        summary.avgHoldingCostPerDay = total_holding / (T_DAYS * R);
        summary.avgBackorderCostPerDay = total_backorder / (T_DAYS * R);
        summary.avgTransportCostPerDay = total_transport / (T_DAYS * R);

        // Orders per day
        summary.avgOrdersPerDay = (double) total_orders / (T_DAYS * R);

        return summary;
    }


    /**
     * Generates and prints the final side-by-side comparison table (Phase 5 Deliverable).
     */
    private void summarizeResults(List<Metrics> resultsCentralized, List<Metrics> resultsDecentralized) {
        
        SummaryMetrics sumC = calculateAverages(resultsCentralized);
        SummaryMetrics sumD = calculateAverages(resultsDecentralized);

        // Define the format for printing the table
        String formatHeader = "%-20s | %-15s | %-15s%n";
        String formatRow = "%-20s | %-15.2f | %-15.2f%n";
        String formatRowPct = "%-20s | %-15.3f | %-15.3f%n";

        System.out.println("\n" + "-".repeat(55));
        System.out.printf(formatHeader, "METRIC", "CENTRALIZED", "DECENTRALIZED");
        System.out.println("-".repeat(55));

        // Total Cost (Primary KPI)
        System.out.printf(formatRow, "**Total Cost / Day**", sumC.totalCostPerDay, sumD.totalCostPerDay);
        System.out.println("-".repeat(55));

        // Service Level
        System.out.printf(formatRowPct, "Fill Rate (beta)", sumC.fillRate, sumD.fillRate);

        // Component Costs
        System.out.printf(formatRow, "Holding Cost / Day", sumC.avgHoldingCostPerDay, sumD.avgHoldingCostPerDay);
        System.out.printf(formatRow, "Backorder Cost / Day", sumC.avgBackorderCostPerDay, sumD.avgBackorderCostPerDay);
        System.out.printf(formatRow, "Transport Cost / Day", sumC.avgTransportCostPerDay, sumD.avgTransportCostPerDay);
        
        // Frequency Metric
        System.out.printf(formatRow, "Orders Per Day", sumC.avgOrdersPerDay, sumD.avgOrdersPerDay);

        System.out.println("-".repeat(55));
    }
}
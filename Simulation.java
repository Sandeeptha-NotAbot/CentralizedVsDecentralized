import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.stream.Collectors;

public class Simulation {
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
    private void accrueDailyCosts(List<NodeState> allNodes, Metrics metrics, double customHolding) {
        for (NodeState n : allNodes) {
            metrics.addHoldingCost(n.getOnHand() * customHolding);
            metrics.addBackorderCost(n.getBackorder() * Params.COST_BACKORDER_PER_DAY);
        }
    }

    /**
     * Simulates demand based on a Normal distribution truncated at 0.
     * NOTE: This simplified version ignores correlation (rho). 
     * A full implementation requires a multivariate normal draw function.
     */
    private int drawDailyDemand(double sigma) {
        // Uses the mu from Params and the sigma passed from the test scenario  
        double mu = Params.DEMAND_MEAN_DAILY;
        
        // Simple approximation of Normal distribution draw  
        double demandDraw = ThreadLocalRandom.current().nextGaussian() * sigma + mu;
        
        // Demand must be non-negative and an integer  
        return Math.max(0, (int) Math.round(demandDraw)); 
    }

    public void computeBaseStocksAdvanced(List<NodeState> retailers, NodeState cw, boolean centralized, double rho, int leadTime, double sigma) {
        double mu = Params.DEMAND_MEAN_DAILY;
        double z = Params.getZScoreForServiceTarget();

        for (NodeState r : retailers) {
            int L = centralized ? Params.LT_CW_TO_RETAILER : leadTime;
            double sI = mu * L + z * sigma * Math.sqrt(L);
            r.setBaseStock((int) Math.round(sI));
        }

        if (centralized && cw != null) {
            int N = Params.N_RETAILERS;
            // Analytical Risk Pooling Formula 
            double sigmaAgg = sigma * Math.sqrt(N + rho * N * (N - 1));
            int lCw = Params.LT_MFG_TO_CW;
            double muAgg = N * mu;
            double sCW = muAgg * lCw + z * sigmaAgg * Math.sqrt(lCw);
            cw.setBaseStock((int) Math.round(sCW));
        }
    }

    // --- Replications ---

    public Metrics runCentralizedReplication(int seed, List<NodeState> retailers, NodeState cw, NodeState mfg, double sigma, double holding) {
        Metrics metrics = new Metrics();
        List<NodeState> allNodes = new ArrayList<>();
        allNodes.add(cw);
        allNodes.addAll(retailers);

        for (int day = 1; day <= Params.T_DAYS; day++) {
            receiveShipments(cw, day);
            clearBackordersWithReceipt(cw);
            for (NodeState r : retailers) {
                receiveShipments(r, day);
                clearBackordersWithReceipt(r);
            }
            for (int i = 0; i < retailers.size(); i++) {
                fulfillDemand(retailers.get(i), drawDailyDemand(sigma), metrics);
            }
            placeBaseStockOrder(cw, mfg, Params.LT_MFG_TO_CW, Params.COST_TRANSPORT_INBOUND, day, metrics);
            for (NodeState r : retailers) {
                placeBaseStockOrder(r, cw, Params.LT_CW_TO_RETAILER, Params.COST_TRANSPORT_OUTBOUND, day, metrics);
            }
            accrueDailyCosts(allNodes, metrics, holding);
        }
        return metrics;
    }

    public Metrics runDecentralizedReplication(int seed, List<NodeState> retailers, NodeState mfg, double sigma, double holding) {
        Metrics metrics = new Metrics();
        for (int day = 1; day <= Params.T_DAYS; day++) {
            for (NodeState r : retailers) {
                receiveShipments(r, day);
                clearBackordersWithReceipt(r);
            }
            for (int i = 0; i < retailers.size(); i++) {
                fulfillDemand(retailers.get(i), drawDailyDemand(sigma), metrics);
            }
            for (NodeState r : retailers) {
                placeBaseStockOrder(r, mfg, Params.LT_MFG_TO_RETAILER, Params.COST_TRANSPORT_DIRECT, day, metrics);
            }
            accrueDailyCosts(retailers, metrics, holding);
        }
        return metrics;
    }

    /**
     * The overarching function that runs both scenarios and compares results.
     */
    public void runExperiment() {
        System.out.println("--- Phase 7: Validation and Stress Testing ---");

        System.out.println("\nTEST 1: Independent Demand (Rho = 0.0)");
        executeScenario(0.0, Params.LT_MFG_TO_RETAILER, Params.DEMAND_SIGMA_DAILY, Params.COST_HOLDING_PER_DAY);

        System.out.println("\nTEST 2: Correlated Demand (Rho = 1.0)");
        executeScenario(1.0, Params.LT_MFG_TO_RETAILER, Params.DEMAND_SIGMA_DAILY, Params.COST_HOLDING_PER_DAY);

        System.out.println("\nTEST 3: Normalized Lead Times (Path LT = 4 days)");
        executeScenario(0.0, 4, Params.DEMAND_SIGMA_DAILY, Params.COST_HOLDING_PER_DAY);

        System.out.println("\nTEST 4: High Demand Variability (Sigma = 60)");
        executeScenario(0.0, Params.LT_MFG_TO_RETAILER, 60.0, Params.COST_HOLDING_PER_DAY);

        System.out.println("\nTEST 5: High Holding Cost ($1.00)");
        executeScenario(0.0, Params.LT_MFG_TO_RETAILER, Params.DEMAND_SIGMA_DAILY, 1.00);
    }

    // Overloaded helper for 2-argument calls
    private void executeScenario(double rho, int decentralizedLT) {
        executeScenario(rho, decentralizedLT, Params.DEMAND_SIGMA_DAILY, Params.COST_HOLDING_PER_DAY);
    }

    private void executeScenario(double rho, int decentralizedLT, double sigma, double holding) {
        List<Metrics> resultsC = new ArrayList<>();
        List<Metrics> resultsD = new ArrayList<>();
        NodeState mfg = new NodeState("MFG");
        NodeState cw = new NodeState("CW");
        List<NodeState> retailers = new ArrayList<>();
        for (int i = 1; i <= Params.N_RETAILERS; i++) retailers.add(new NodeState("R" + i));

        for (int rep = 1; rep <= Params.R_REPLICATIONS; rep++) {
            computeBaseStocksAdvanced(retailers, cw, true, rho, decentralizedLT, sigma);
            resetNodes(cw, retailers);
            resultsC.add(runCentralizedReplication(rep, retailers, cw, mfg, sigma, holding));

            computeBaseStocksAdvanced(retailers, null, false, rho, decentralizedLT, sigma);
            resetNodes(null, retailers);
            resultsD.add(runDecentralizedReplication(rep, retailers, mfg, sigma, holding));
        }
        summarizeResults(resultsC, resultsD);
    }

    private void resetNodes(NodeState cw, List<NodeState> retailers) {
        if (cw != null) {
            cw.setOnHand(cw.getBaseStock()); cw.getInTransit().clear(); cw.setBackorder(0);
        }
        for (NodeState r : retailers) {
            r.setOnHand(r.getBaseStock()); r.getInTransit().clear(); r.setBackorder(0);
        }
    }

    private void summarizeResults(List<Metrics> resC, List<Metrics> resD) {
        SummaryMetrics sC = calculateAverages(resC);
        SummaryMetrics sD = calculateAverages(resD);
        String row = "%-20s | %-15.2f | %-15.2f%n";
        System.out.printf("%-20s | %-15s | %-15s%n", "METRIC", "CENTRAL", "DECENTRAL");
        System.out.printf(row, "Total Cost/Day", sC.totalCostPerDay, sD.totalCostPerDay);
        System.out.printf(row, "Fill Rate", sC.fillRate, sD.fillRate);
        System.out.printf(row, "Hold Cost/Day", sC.avgHoldingCostPerDay, sD.avgHoldingCostPerDay);
    }

    private SummaryMetrics calculateAverages(List<Metrics> res) {
        SummaryMetrics s = new SummaryMetrics();
        double h = res.stream().mapToDouble(Metrics::getHoldingCost).sum();
        double b = res.stream().mapToDouble(Metrics::getBackorderCost).sum();
        double t = res.stream().mapToDouble(Metrics::getTransportCost).sum();
        int f = res.stream().mapToInt(Metrics::getFillImmediate).sum();
        int d = res.stream().mapToInt(Metrics::getDemandTotal).sum();
        s.totalCostPerDay = (h + b + t) / (Params.T_DAYS * res.size());
        s.fillRate = (double) f / d;
        s.avgHoldingCostPerDay = h / (Params.T_DAYS * res.size());
        return s;
    }

    public static void main(String[] args) {
        new Simulation().runExperiment();
    }
}
/**
 * Params.java
 * Defines all constant input parameters (costs, lead times, demand, service goals) 
 * for the Centralization vs. Decentralization Inventory Project.
 */
public class Params {

    // --- A. General Simulation Controls ---
    public static final int T_DAYS = 365;       // Time horizon for each replication (in days).
    public static final int R_REPLICATIONS = 30; // Number of times to run the simulation for statistical averaging.
    public static final int N_RETAILERS = 3;    // Number of independent retailer nodes in the system.
    public static final double TARGET_FILL_RATE = 0.95; // Target service level (beta) for base stock calculation.
    
    // --- B. Inventory Costs (Per Unit Per Day) ---
    // Note: Use a consistent time unit (day).
    public static final double COST_HOLDING_PER_DAY = 0.10;
    public static final double COST_BACKORDER_PER_DAY = 5.00; 

    // --- C. Transportation Costs (Per Unit) ---
    // These costs differ based on the design/leg.
    public static final double COST_TRANSPORT_INBOUND = 0.50;  // MFG -> CW (Centralized inbound).
    public static final double COST_TRANSPORT_OUTBOUND = 0.25; // CW -> Retailer (Centralized outbound).
    public static final double COST_TRANSPORT_DIRECT = 0.75;   

    // --- D. Lead Times (In Days) ---
    // These values are crucial for base-stock calculation.
    public static final int LT_MFG_TO_CW = 2;
    public static final int LT_CW_TO_RETAILER = 1;
    public static final int LT_MFG_TO_RETAILER = 4;
                                                     
    // --- E. Demand Parameters (Assumed Identical for all N Retailers) ---
    public static final double DEMAND_MEAN_DAILY = 100.0;
    public static final double DEMAND_SIGMA_DAILY = 30.0;
    public static final double DEMAND_CORRELATION_RHO = 0.0;
    

    // public static final double COST_FIXED_CW_OPERATING = 1000.0; 
    
    /**
     * Helper function to determine the z-score (standard normal deviate)
     * corresponding to the target service level (fill rate).
     * NOTE: In a full Java implementation, you would use a statistics library.
     * For a simplified project, a hardcoded value based on Normal approximation is common.
     * For a 95% Cycle Service Level (used for the Base Stock formula) in a Normal distribution:
     * P(Z <= z) = 0.95 -> z ≈ 1.645
     */
    public static double getZScoreForServiceTarget() {
        // This is a placeholder value for a 95% target, assuming the target refers 
        return 1.645; 
    }
}
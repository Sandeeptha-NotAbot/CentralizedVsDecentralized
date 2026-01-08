/**
 * Shipment.java
 * Defines the structure for inventory currently in transport between nodes.
 */
public class Shipment {

    // --- Fields (Attributes) ---
    private int qty;                      // How many units are in this shipment
    private String src;                   // Origin location (e.g., "MFG" or "CW")
    private String dst;                   // Destination location (e.g., "Retailer_1")
    private int arriveDay;                // The specific simulation day the shipment will arrive (current_day + lead_time)
    private double transportCostPerUnit;  // Cost to move one unit for this specific leg

    /**
     * Constructor for creating a new Shipment.
     */
    public Shipment(int qty, String src, String dst, int arriveDay, double transportCostPerUnit) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Shipment quantity must be positive.");
        }
        this.qty = qty;
        this.src = src;
        this.dst = dst;
        this.arriveDay = arriveDay;
        this.transportCostPerUnit = transportCostPerUnit;
    }

    // --- Getters 

    public int getQty() {
        return qty;
    }

    public String getSrc() {
        return src;
    }

    public String getDst() {
        return dst;
    }

    public int getArriveDay() {
        return arriveDay;
    }

    public double getTransportCostPerUnit() {
        return transportCostPerUnit;
    }
}
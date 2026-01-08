import java.util.ArrayList;
import java.util.List;

/**
 * NodeState.java
 * Defines an inventory-holding location (Retailer, CW, or MFG) and its current state.
 */
public class NodeState {

    private String name;
    private int onHand;
    private int backorder;
    private List<Shipment> inTransit; 
    private int baseStock;          
    private int ordersPlacedToday;  

    /**
     * Constructor for a new NodeState.
     */
    public NodeState(String name) {
        this.name = name;
        this.onHand = 0;
        this.backorder = 0;
        this.inTransit = new ArrayList<>();
        this.baseStock = 0;
        this.ordersPlacedToday = 0;
    }

    /**
     * Constructor for initialization with a starting stock level.
     */
    public NodeState(String name, int initialStock) {
        this(name);
        this.onHand = initialStock;
    }

    // --- Getters and Setters (Necessary for simulation logic and metric accumulation) ---

    public String getName() {
        return name;
    }

    public int getOnHand() {
        return onHand;
    }

    public void setOnHand(int onHand) {
        this.onHand = onHand;
    }

    public int getBackorder() {
        return backorder;
    }

    public void setBackorder(int backorder) {
        this.backorder = backorder;
    }

    public List<Shipment> getInTransit() {
        return inTransit;
    }

    public int getBaseStock() {
        return baseStock;
    }

    public void setBaseStock(int baseStock) {
        this.baseStock = baseStock;
    }

    public int getOrdersPlacedToday() {
        return ordersPlacedToday;
    }

    public void setOrdersPlacedToday(int ordersPlacedToday) {
        this.ordersPlacedToday = ordersPlacedToday;
    }

    // Utility for debugging
    @Override
    public String toString() {
        int inTransitQty = inTransit.stream().mapToInt(Shipment::getQty).sum();
        int ip = onHand + inTransitQty - backorder;
        return String.format("%s: OH=%d, BO=%d, IT=%d, IP=%d, S=%d",
            name, onHand, backorder, inTransitQty, ip, baseStock);
    }
}
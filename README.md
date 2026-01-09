This project is a discrete-event simulation built in Java to analyze the strategic trade-offs between Centralized and Decentralized warehouse architectures. It evaluates how inventory positioning affects total supply chain costs and customer service levels (Fill Rate).
| Test Case | Scenario Description | Centralized Total Cost | Decentralized Total Cost | Advantage |
| :--- | :--- | :--- | :--- | :--- |
| **Test 1** | Baseline (Independent) | **$171.27** | $272.33 | Centralized |
| **Test 2** | Correlated Demand ($\rho=1.0$) | $180.61 | $270.93 | Advantage Shrinks |
| **Test 3** | Normalized Lead Times | **$172.09** | $273.49 | Centralized |
| **Test 4** | High Volatility ($\sigma=60$) | **$209.51** | $324.93 | Centralized |
| **Test 5** | High Holding Cost ($1.00/unit) | $955.16 | **$548.62** | **Decentralized** |



### Key Findings
* **Risk Pooling Proof:** When correlation ($\rho$) was increased to 1.0 (Test 2), centralized holding costs rose. This validates that consolidation benefits depend on independent regional markets.
* **The Tipping Point (Test 5):** This is the "break-even" scenario where the Decentralized model wins. It proves that for high-value items, a **Lean Decentralized** model is superior because capital costs of safety stock outweigh shipping savings.
* **Service Level Stability:** Under extreme volatility (Test 4), the Centralized system maintained a higher **Fill Rate**, proving a central hub acts as a superior buffer for customer service.

## Static Taint Analysis
On this page we provide the results of the taintflow analysis of ConfigCrusher of our subject systems.

### H2
The analysis of H2 is running out of memory in the middle of the taint analysis. The call graph could be created successfilly. We provide the full [LogFile](TestResults_AnalysisTest_h2TestWBPIM.xml).

### Density-Converter
The analysis of Density-Converter produces reasonable taints. See [LogFile](staticAnalysisComparison.txt) section ```Density Converter```.

### Sunflow, Batik, Prevayler
The analysis of Sunflow, Batik and Prevayler produces taints of size 1. This taints are basically useless, because this configuration options are written to different data structures (Sunflow: ```HashMap```; Batik: ```Array```; Prevayler: ```Hashtable```). Have a look at the corresponding sections here: [LogFile](staticAnalysisComparison.txt).

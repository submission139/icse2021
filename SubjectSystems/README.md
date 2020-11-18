## Subject Systems

On this page we list the subject systems that we use to evaluate our approach. Each folder contains a submodule to the subject system's source repository and the feature model we used for sampling the configurations.

| System | Domain | LoC | &#124;F&#124; | &#124;C&#124; | &#124;C<sup>FW</sup>&#124; | &#124;C<sup>PW</sup>&#124; |
| :---      | :--- | ---: | ---: | ---: | ---: | ---: |
| Batik     | SVG rasterizer | 290,176 |  31 | 9.6&sdot;10<sup>4</sup>| 28 | 337 |
| Catena    | Password hashing | 4,149 |   12 |  1.0&sdot;10<sup>9</sup>| 875 | 2625 |
| cpd       | Copy-paste detector | 250,597 |     7 |  1.1&sdot;10<sup>4</sup>| 40 | 115 |
| dc        | Image density converter | 6,633 |    24 |  3.4&sdot;10<sup>6</sup>| 1600 | 9700 |
| H2        | Database | 297,623 |    16 | 6.5&sdot;10<sup>11</sup>| 375 | 2275 |
| Kanzi     | Data compression | 20,620 |  40 | 4.3&sdot;10<sup>3</sup>| 34 | 458 |
| pmd       | Source-code analyzer | 304,048 |    11 |  5.1&sdot;10<sup>2</sup>| 36 | 104 |
| Prevayler | Database | 10,400 |    12 |  1.3&sdot;10<sup>5</sup>| 250 | 400 |
| Sunflow   | Rendering engine | 201,125 |     6 |  5.4&sdot;10<sup>6</sup>| 125 | n/a |

### Profiled vs. Unprofiled
We identified different patter for the influence of the profiler on the subject systems.

#### Catena

![Profiler Influence Catena](profiled-vs-unprofiled/profiler_corellation_catena_t_2_pbd_49_7.png)

#### CPD

![Profiler Influence CPD](profiled-vs-unprofiled/profiler_corellation_cpd_t_2_pbd_49_7.png)

#### Density-Converter

![Profiler Influence Density-Converter](profiled-vs-unprofiled/profiler_corellation_density-converter_t_2_pbd_49_7.png)

#### Prevayler

![Profiler Influence Prevayler](profiled-vs-unprofiled/profiler_corellation_prevayler_t_2_pbd_49_7.png)

#### Sunflow

![Profiler Influence Sunflow](profiled-vs-unprofiled/profiler_corellation_sunflow_feature_pbd_125_5.png)

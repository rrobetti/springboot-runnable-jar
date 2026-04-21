# springboot-runnable-jar
Simple spring boot app that reads a table and writes to a file.

## Architecture

```mermaid
flowchart LR
    subgraph EXT["External Flow"]
        E1([1. Trigger])
    end

    subgraph SH["Shell Script"]
        S1[2. Start Java job\nwith parameter]
        S2[3. Wait for job\nto complete]
        S3[7. Move output file\nto archive]
    end

    subgraph JAR["springboot-runnable-jar"]
        J1[4. Load batch size\nfrom DB]
        J2[5. Create output file\nand write CSV header]
        J3[6. Stream rows from DB\nand append to file]
    end

    subgraph DB["Database"]
        D1[(batch_config)]
        D2[(person)]
    end

    subgraph FS["File System"]
        F1[/export_{param}.csv/]
        F2[/archive/export_{param}.csv/]
    end

    E1 --> S1
    S1 --> S2
    S2 --> J1
    J1 --> D1
    D1 --> J2
    J2 --> F1
    J2 --> J3
    J3 --> D2
    D2 --> J3
    J3 --> F1
    J3 --> S3
    S3 --> F2
```

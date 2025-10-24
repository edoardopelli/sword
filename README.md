# 🗡️ S.W.O.R.D. — Schema-Wide Object Reverse Designer

**S.W.O.R.D.** is a standalone Java tool for reverse-engineering relational databases into fully annotated **JPA entities** with **Lombok** support.

The tool connects to an existing relational schema, reads metadata (tables, columns, constraints, keys, etc.), and generates clean, consistent entity classes in Java.

---

## 🧩 Acronym Meaning

**S.W.O.R.D.** = **Schema-Wide Object Reverse Designer**  
Because it cuts through complex schemas and forges structured JPA entities from raw database metadata.

---

## 🚀 Features

- Reverse-engineers entire relational schemas into annotated JPA entities.
- Generates Lombok annotations (`@Data`, `@Builder`, etc.) for clean, boilerplate-free code.
- Handles:
  - `@EmbeddedId` for composite primary keys.
  - `@GeneratedValue` for identity, serial, and sequence columns.
  - Optional `@OneToOne` / `@ManyToOne` relationships.
  - `json` / `jsonb` columns → `Map<String,Object>`.
- Supports PostgreSQL, MariaDB, MySQL, SQL Server, H2, and IBM DB2.
- Allows custom naming configuration through a YAML file.
- Fully interactive console wizard for setup and generation.

---

## 🏗️ Generation Flow

When you start S.W.O.R.D., the wizard runs interactively:

1. Choose the JDBC driver.
2. Provide connection details (host, port, username, password, database).
3. Select the schema or catalog.
4. Choose:
   - Base package (e.g., `com.example.entities`)
   - Output path (default: current working directory)
5. Choose FK mode:
   - SCALAR → generates `Long companyId`
   - RELATION → generates `Company company`
6. Generation begins.

---

## 🧠 Default Naming Strategy

### Table → Entity examples
```
users -> User
incident_types -> IncidentType
PBS_CODE -> PbsCode
```

### Column → Property examples
```
problem_id -> problemId
ASSET_CODE -> assetCode
PBSCode -> pBSCode
```

---

## ⚙️ Optional YAML Naming Overrides

You can override generated names using a YAML configuration file.  
Pass the file path when starting the JAR:

```
java -jar target/sword-0.1.0-SNAPSHOT.jar --naming-file=/path/to/naming-overrides.yml
```
or
```
java -jar target/sword-0.1.0-SNAPSHOT.jar --namingFile=/path/to/naming-overrides.yml
```

If omitted, default naming rules are applied.

### Example `naming-overrides.yml`

```yaml
tables:
  problems:
    entityName: Problem
    columns:
      problem_id: id
      problem_type: type
  incidents:
    entityName: Incident
    columns:
      incident_id: id
      INCIDENT_SEVERITY: severityLevel
  alarm_events:
    entityName: AlarmEvent
    columns:
      event_code: code
      event_timestamp: timestamp
```

All unmapped tables and columns follow the default naming logic.

---

## 🧩 Supported Databases

PostgreSQL (port 5432)  
MariaDB (port 3306)  
MySQL (port 3306)  
SQL Server (port 1433)  
H2 (port 9092)  
IBM DB2 (port 50000)

---

## 🧰 Example Generated Class

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "incidents")
@Generated(value = "S.W.O.R.D.", date = "2025-10-24T21:00:00Z")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    @Column(name = "incident_id")
    private Long id;

    @Column(name = "incident_description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;
}
```

---

## 📦 Output Example

```
/Users/edoardo/Documents/workspaces/sword-output/
└── com/example/entities/
    ├── Problem.java
    ├── Incident.java
    ├── AlarmEvent.java
    └── IncidentId.java
```

---

## 🧾 License

S.W.O.R.D. is released under the Apache 2.0 License.  
© 2025 Cheetah Software Labs. All rights reserved.

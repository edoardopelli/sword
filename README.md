# 🗡️ S.W.O.R.D. — Schema-Wide Object Reverse Designer

**S.W.O.R.D.** (Schema‑Wide Object Reverse Designer) is a standalone Java tool for reverse‑engineering relational databases into annotated **JPA entities** using **Lombok**.

The tool connects to a relational database, reads schema metadata (tables, columns, keys, constraints), and generates consistent, clean entity classes.

---

## 🧩 Acronym Meaning

**S.W.O.R.D.** = **Schema‑Wide Object Reverse Designer**  
Because it cuts through complex schemas and forges structured JPA entities from raw database metadata.

---

## 🚀 Features

- Reverse‑engineers entire relational schemas into annotated JPA entities.
- Uses Lombok (`@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`) for concise code.
- Handles:
  - `@EmbeddedId` for composite primary keys.
  - `@GeneratedValue` for identity, serial, or sequence columns.
  - `@OneToOne`, `@ManyToOne`, `@OneToMany`, `@ManyToMany` relationships.
  - `json` / `jsonb` columns → `Map<String,Object>`.
- Supports both **scalar foreign keys** (default) and **entity relationships** (optional).
- Allows choosing **relation fetch mode** (`LAZY` or `EAGER`).
- Fully interactive console wizard.
- Customizable naming rules via YAML file.

---

## 🏗️ Generation Flow

When S.W.O.R.D. starts, it launches an interactive wizard:

1. Select database type (PostgreSQL, MariaDB, MySQL, SQL Server, H2, DB2).
2. Enter host, port, username, password, and database name.
3. Choose schema and/or catalog.
4. Provide base package (e.g. `org.cheetah.entities`) and output path (default = current directory).
5. Choose foreign key mode:
   - `SCALAR` → generates `Long companyId`
   - `RELATION` → generates `Company company`
6. Choose relation fetch mode (for `@ManyToOne` / `@OneToOne`):
   - `LAZY` (default, recommended)
   - `EAGER`
7. Entity generation starts automatically.

---

## ⚙️ Relation Fetch Mode

The **Relation Fetch Mode** controls how JPA loads related entities:

```
[1] LAZY   → fetch on demand (recommended, prevents session overhead)
[2] EAGER  → always load joined entity immediately
```

Only affects `@ManyToOne` and `@OneToOne` relations.  
`@OneToMany` and `@ManyToMany` are always generated as `LAZY`.

This setting can be changed interactively during generation.

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

You can override table and column names using a YAML configuration file.

### Example

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

Pass the file path via CLI argument:

```bash
java -jar target/sword-0.1.0-SNAPSHOT.jar --namingFile=/path/to/naming-overrides.yml
```

If omitted, S.W.O.R.D. uses automatic naming based on the database schema.

---

## 🧩 Supported Databases

| Vendor | Default Port |
|--------|---------------|
| PostgreSQL | 5432 |
| MariaDB | 3306 |
| MySQL | 3306 |
| SQL Server | 1433 |
| H2 | 9092 |
| IBM DB2 | 50000 |

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

## 🧾 Example Output Structure

```
/Users/edoardo/Documents/workspaces/sword-output/
└── org/cheetah/entities/
    ├── Problem.java
    ├── Incident.java
    ├── AlarmEvent.java
    └── IncidentId.java
```

---

## 🧩 FK and Relation Configuration

- **FK Mode**  
  `SCALAR` = only FK field (Long, Integer, etc.)  
  `RELATION` = full relation field (`@ManyToOne`, `@OneToOne`, etc.)

- **Relation Fetch Mode**  
  `LAZY` = recommended (on-demand load)  
  `EAGER` = full join load

---

## 🧾 License

S.W.O.R.D. is released under the Apache 2.0 License.  
© 2025 Cheetah Software Labs. All rights reserved.

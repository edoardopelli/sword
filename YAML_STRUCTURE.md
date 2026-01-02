# YAML Specification Structure (S.W.O.R.D.)

This document describes **all sections** supported by the S.W.O.R.D. YAML specification, including optional and advanced fields.

> The YAML file **never** contains DB connection settings (host/port/user/password/URL).  
> Connection details are always provided interactively when introspecting from a database.

---

## Top-level structure

A YAML spec has these top-level keys:

- `model` (**required**)  
- `generation` (optional but recommended)
- `naming` (optional)
- `resourceOverrides` (optional)
- `tables` (**required**, can be empty but normally contains all discovered tables)

Minimal skeleton:

```yaml
model:
  basePackage: org.example.generated
  schema: public
  catalog: ""

generation:
  outputDir: ./generated-src

naming:
  tables: {}

resourceOverrides:
  tables: {}

tables: []
```

---

## 1) `model` (required)

Defines global model metadata used by generators.

```yaml
model:
  basePackage: org.example.generated
  schema: ecommerce
  catalog: ""
```

### Fields

- `basePackage` (**required**)  
  Root Java package for generated code (entities, dtos, resources, repositories, services, controllers, mappers).

- `schema` (optional)  
  Database schema name used in JPA `@Table(schema = "...")`.  
  If empty or omitted, schema is not emitted in `@Table`.

- `catalog` (optional)  
  Database catalog name (DB-dependent).  
  Typically used only for introspection and in some DBs it corresponds to “database”.

---

## 2) `generation` (optional)

Generation-time defaults. These values may be overridden by wizard prompts.

```yaml
generation:
  outputDir: ./generated-src
```

### Fields

- `outputDir` (optional)  
  Default output directory for generated sources.

---

## 3) `naming` (optional)

Allows overriding the **names** of generated classes and/or the **property names** derived from DB columns.

```yaml
naming:
  tables:
    reviews:
      entityName: ReviewsEntity
      dtoName: ReviewsDTO
      resourceName: ReviewsResource
      columns:
        customer_id: customerId
        created_at: createdAt
```

### Fields

- `tables` (map: `<tableName>` → `TableNaming`)

#### `TableNaming` object

```yaml
naming:
  tables:
    <tableName>:
      entityName: <EntityClassName>
      dtoName: <DtoClassName>
      resourceName: <ResourceClassName>
      columns:
        <db_column_name>: <javaPropertyName>
```

- `entityName` (optional)  
  Overrides entity class name for that table.

- `dtoName` (optional)  
  Overrides DTO class name for that table.

- `resourceName` (optional)  
  Overrides Resource class name for that table.

- `columns` (optional map)  
  Overrides column-to-property naming.  
  Key is the **DB column name** (e.g. `customer_id`)  
  Value is the **Java property name** (e.g. `customerId`)

> If not specified, S.W.O.R.D. derives the property name using snake_case → lowerCamel.

---

## 4) `resourceOverrides` (optional)

Allows customizing **Resource** fields independently from DTO/Entity:
- rename fields
- change Java type
- map from a specific DTO field

This is useful when the external API representation differs from the internal DTO.

```yaml
resourceOverrides:
  tables:
    reviews:
      resourceName: ReviewsResource
      fields:
        customerIdStr:
          sourceDtoField: customerId
          javaType: java.lang.String
        createdAtIso:
          sourceDtoField: createdAt
          javaType: java.lang.String
```

### Structure

- `tables` (map: `<tableName>` → `ResourceTableOverride`)

#### `ResourceTableOverride`

```yaml
resourceOverrides:
  tables:
    <tableName>:
      resourceName: <OptionalResourceClassName>
      fields:
        <resourceFieldName>:
          sourceDtoField: <dtoFieldName>
          javaType: <fullyQualifiedTypeOrPrimitive>
```

### Fields

- `resourceName` (optional)  
  Overrides resource class name for the table (same purpose as `naming.tables.<table>.resourceName` but scoped to overrides).

- `fields` (optional map)  
  Key: **resource field name** that will exist in the generated Resource class.  
  Value: `ResourceFieldOverride`.

#### `ResourceFieldOverride`

- `sourceDtoField` (**required when defining a field**)  
  Which DTO field is the source for mapping.

- `javaType` (optional)  
  Java type override for the resource field. Examples:
  - `java.lang.String`
  - `int`
  - `java.time.LocalDateTime`
  - `java.util.UUID`

> When `javaType` differs from the DTO field type, the mapper must define explicit `@Mapping(...)` for all fields.
> The generator emits `@Mapping` for every field in DTO↔Resource mappers for safety.

---

## 5) `tables` (required)

Contains the full schema description used for YAML → Code generation.

```yaml
tables:
  - name: reviews
    primaryKeyColumns: [ id ]
    columns:
      - name: id
        propertyName: id
        jdbcType: 4
        jdbcTypeName: int4
        nullable: false
        autoIncrement: true
        idGeneration: IDENTITY
      - name: metadata
        propertyName: metadata
        jdbcType: 1111
        jdbcTypeName: jsonb
        nullable: true
    foreignKeys:
      - name: fk_reviews_customer
        fromColumns: [ customer_id ]
        toTable: customers
        toColumns: [ id ]
        cardinality: MANY_TO_ONE
```

### `Table` object fields

- `name` (**required**)  
  DB table name.

- `columns` (**required**)  
  Array of `Column` objects.

- `primaryKeyColumns` (optional but strongly recommended)  
  Array of column names that compose the PK.  
  If it has more than one element, the generator uses `@EmbeddedId`.

- `foreignKeys` (optional)  
  Array of `ForeignKey` objects.

---

## 5.1) `Column` object

```yaml
columns:
  - name: metadata
    propertyName: metadata
    jdbcType: 1111
    jdbcTypeName: jsonb
    nullable: true
    size: null
    scale: null
    autoIncrement: false
    idGeneration: NONE
    sequenceName: ""
```

### Fields

- `name` (**required**)  
  DB column name.

- `propertyName` (optional but recommended)  
  Java property name derived from the column.  
  If omitted, the generator derives it from `name` using snake_case → lowerCamel.

- `jdbcType` (**required**)  
  Integer value of `java.sql.Types` for the column type.

- `jdbcTypeName` (optional but recommended)  
  DB/vendor-specific type name (e.g. `varchar`, `int4`, `jsonb`).  
  Used for special cases (notably PostgreSQL `json/jsonb` detection).

- `nullable` (**required**)  
  Boolean indicating whether the column is nullable.

- `size` (optional)  
  Column size (e.g., VARCHAR length). Can be null.

- `scale` (optional)  
  Decimal digits / scale. Can be null.

#### ID generation hints (optional)

These fields are used when generating `@GeneratedValue` for **single PK** tables and are persisted so YAML → Code remains deterministic.

- `autoIncrement` (optional, default false)  
  True if the column is an identity/auto-increment column.

- `idGeneration` (optional, default `NONE`)  
  Supported values:
  - `NONE`
  - `IDENTITY`
  - `SEQUENCE`

- `sequenceName` (optional)  
  Only meaningful when `idGeneration: SEQUENCE`  
  Example: `public.orders_id_seq`

---

## 5.2) `ForeignKey` object

```yaml
foreignKeys:
  - name: fk_reviews_customer
    fromColumns: [ customer_id ]
    toTable: customers
    toColumns: [ id ]
    cardinality: MANY_TO_ONE
```

### Fields

- `name` (optional but recommended)  
  FK constraint name.

- `fromColumns` (**required**)  
  List of column names in the source table.

- `toTable` (**required**)  
  Target table name.

- `toColumns` (**required**)  
  List of referenced column names in the target table.

- `cardinality` (**required**)  
  Supported values:
  - `MANY_TO_ONE`
  - `ONE_TO_ONE`

> Cardinality is detected best-effort during DB introspection; in YAML you can manually override it.

---

## Notes on DB differences: schema vs catalog

Different DBs expose schema/catalog differently:
- PostgreSQL: commonly uses schema (`public`, `ecommerce`), catalog may correspond to database name.
- MySQL/MariaDB: “schema” is often the database; catalog listing can be more relevant.
- SQL Server: catalogs map to databases; schemas map to `dbo`, etc.

The wizard lists both using `DatabaseMetaData`:
- schemas: `getSchemas()`
- catalogs: `getCatalogs()`

If listing is unsupported/empty for a DB/driver, manual input is still possible.

---

## Complete example

```yaml
model:
  basePackage: org.cheetah.sword.poc
  schema: ecommerce
  catalog: ""

generation:
  outputDir: ./src/main/java

naming:
  tables:
    reviews:
      entityName: ReviewsEntity
      dtoName: ReviewsDTO
      resourceName: ReviewsResource
      columns:
        customer_id: customerId
        created_at: createdAt

resourceOverrides:
  tables:
    reviews:
      resourceName: ReviewsResource
      fields:
        createdAtIso:
          sourceDtoField: createdAt
          javaType: java.lang.String

tables:
  - name: reviews
    primaryKeyColumns: [ id ]
    columns:
      - name: id
        propertyName: id
        jdbcType: 4
        jdbcTypeName: int4
        nullable: false
        autoIncrement: true
        idGeneration: IDENTITY
      - name: customer_id
        propertyName: customerId
        jdbcType: 4
        jdbcTypeName: int4
        nullable: false
        autoIncrement: false
        idGeneration: NONE
      - name: metadata
        propertyName: metadata
        jdbcType: 1111
        jdbcTypeName: jsonb
        nullable: true
        autoIncrement: false
        idGeneration: NONE
    foreignKeys:
      - name: fk_reviews_customer
        fromColumns: [ customer_id ]
        toTable: customers
        toColumns: [ id ]
        cardinality: MANY_TO_ONE
```

---

## What happens when generating code from YAML

- Class names:
  - if `naming.tables.<table>.entityName` is present, it wins for Entities.
  - same for `dtoName` and `resourceName`.
- Column property names:
  - if `naming.tables.<table>.columns.<column>` is present, it wins.
  - otherwise `propertyName` is used.
  - if `propertyName` is missing, it is derived from the DB name.
- Resources:
  - if `resourceOverrides` exists, resource can define additional/renamed fields and type overrides.
- PK handling:
  - if `primaryKeyColumns` contains multiple columns, generator uses `@EmbeddedId`.
- json/jsonb:
  - if `jdbcTypeName` is `json` or `jsonb`, Entity/DTO/Resource use `Map<String,Object>`
  - Entity uses Hibernate 6 JSON binding annotations.


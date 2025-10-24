package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.Long;
import java.lang.Object;
import java.lang.String;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "incidentsHistory"
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(
    onlyExplicitlyIncluded = true
)
@EqualsAndHashCode(
    onlyExplicitlyIncluded = true
)
@Generated(
    value = "S.W.O.R.D.",
    date = "2025-10-23T23:12:08.539919+02:00"
)
public class Incidentshistory {
  @Id
  @EqualsAndHashCode.Include
  @ToString.Include
  @GeneratedValue(
      strategy = GenerationType.IDENTITY
  )
  @Column(
      name = "id"
  )
  private Long id;

  @ToString.Include
  @Column(
      name = "oldValue"
  )
  private String oldvalue;

  @ToString.Include
  @Column(
      name = "newValue"
  )
  private String newvalue;

  @ToString.Include
  @Column(
      name = "tableName"
  )
  private String tablename;

  @ToString.Include
  @Column(
      name = "createdAt"
  )
  private OffsetDateTime createdat;

  @ToString.Include
  @Column(
      name = "user"
  )
  private String user;

  @ToString.Include
  @Column(
      name = "operation"
  )
  private String operation;

  @ToString.Include
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(
      name = "diffJson",
      columnDefinition = "jsonb"
  )
  private Map<String, Object> diffjson;
}

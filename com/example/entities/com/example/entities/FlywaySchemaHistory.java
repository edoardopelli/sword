package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.Boolean;
import java.lang.Integer;
import java.lang.String;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "flyway_schema_history"
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
    date = "2025-10-23T23:12:08.528789+02:00"
)
public class FlywaySchemaHistory {
  @Id
  @EqualsAndHashCode.Include
  @ToString.Include
  @Column(
      name = "installed_rank"
  )
  private Integer installedRank;

  @ToString.Include
  @Column(
      name = "version"
  )
  private String version;

  @ToString.Include
  @Column(
      name = "description"
  )
  private String description;

  @ToString.Include
  @Column(
      name = "type"
  )
  private String type;

  @ToString.Include
  @Column(
      name = "script"
  )
  private String script;

  @ToString.Include
  @Column(
      name = "checksum"
  )
  private Integer checksum;

  @ToString.Include
  @Column(
      name = "installed_by"
  )
  private String installedBy;

  @ToString.Include
  @Column(
      name = "installed_on"
  )
  private OffsetDateTime installedOn;

  @ToString.Include
  @Column(
      name = "execution_time"
  )
  private Integer executionTime;

  @ToString.Include
  @Column(
      name = "success"
  )
  private Boolean success;
}

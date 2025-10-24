package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.Boolean;
import java.lang.Long;
import java.lang.String;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "problemsRootCauseAnalysis"
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
    date = "2025-10-23T23:12:08.618307+02:00"
)
public class Problemsrootcauseanalysis {
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
      name = "problemsId"
  )
  private Long problemsid;

  @ToString.Include
  @Column(
      name = "rootCauseClassificationsId"
  )
  private Long rootcauseclassificationsid;

  @ToString.Include
  @Column(
      name = "rootCauseDescription"
  )
  private String rootcausedescription;

  @ToString.Include
  @Column(
      name = "supplierIssue"
  )
  private Boolean supplierissue;

  @ToString.Include
  @Column(
      name = "suppliersId"
  )
  private Long suppliersid;

  @ToString.Include
  @Column(
      name = "dueDateNextStep"
  )
  private LocalDate duedatenextstep;
}

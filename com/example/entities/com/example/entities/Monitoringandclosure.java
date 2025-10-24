package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    name = "monitoringAndClosure"
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
    date = "2025-10-23T23:12:08.574305+02:00"
)
public class Monitoringandclosure {
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
      name = "monitoringStartDate"
  )
  private LocalDate monitoringstartdate;

  @ToString.Include
  @Column(
      name = "monitoringPlanninEndDate"
  )
  private LocalDate monitoringplanninenddate;

  @ToString.Include
  @Column(
      name = "caPlansConclusion"
  )
  private String caplansconclusion;

  @ToString.Include
  @Column(
      name = "csEffectivenessCriterion"
  )
  private String cseffectivenesscriterion;

  @ToString.Include
  @Column(
      name = "caEffectivenessSummary"
  )
  private String caeffectivenesssummary;

  @ToString.Include
  @Column(
      name = "problemWasCorrectedSince"
  )
  private String problemwascorrectedsince;

  @ToString.Include
  @Column(
      name = "dateOfClosure"
  )
  private LocalDate dateofclosure;

  @ToString.Include
  @Column(
      name = "closureNotes"
  )
  private String closurenotes;
}

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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "problemsEvaluation"
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
    date = "2025-10-23T23:12:08.612384+02:00"
)
public class Problemsevaluation {
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
      name = "problemCriticalitiesId"
  )
  private Long problemcriticalitiesid;

  @ToString.Include
  @Column(
      name = "impactSafety"
  )
  private Boolean impactsafety;

  @ToString.Include
  @Column(
      name = "preliminaryCARequired"
  )
  private Boolean preliminarycarequired;

  @ToString.Include
  @Column(
      name = "dueDateNextStep"
  )
  private LocalDate duedatenextstep;
}

package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.lang.Long;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Embeddable
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
    date = "2025-10-23T23:12:08.614994+02:00"
)
public class ProblemsincidentsId implements Serializable {
  @Column(
      name = "problemsId"
  )
  @ToString.Include
  @EqualsAndHashCode.Include
  private Long problemsid;

  @Column(
      name = "incidentsId"
  )
  @ToString.Include
  @EqualsAndHashCode.Include
  private Long incidentsid;
}

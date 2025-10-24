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
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "incidentsRAM"
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
    date = "2025-10-23T23:12:08.555115+02:00"
)
public class Incidentsram {
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
      name = "incidentId"
  )
  private Long incidentid;

  @ToString.Include
  @Column(
      name = "functionalFailure"
  )
  private Boolean functionalfailure;

  @ToString.Include
  @Column(
      name = "serviceAffectingFailure"
  )
  private Boolean serviceaffectingfailure;

  @ToString.Include
  @Column(
      name = "failureCategoriesId"
  )
  private Long failurecategoriesid;

  @ToString.Include
  @Column(
      name = "hwsw"
  )
  private String hwsw;

  @ToString.Include
  @Column(
      name = "rootCausesId"
  )
  private Long rootcausesid;

  @ToString.Include
  @Column(
      name = "chargeableToRAM"
  )
  private Boolean chargeabletoram;

  @ToString.Include
  @Column(
      name = "responsabilitiesId"
  )
  private Long responsabilitiesid;

  @ToString.Include
  @Column(
      name = "remarks"
  )
  private String remarks;

  @ToString.Include
  @Column(
      name = "userId"
  )
  private Long userid;

  @ToString.Include
  @Column(
      name = "status"
  )
  private String status;

  @ToString.Include
  @Column(
      name = "lastUpdate"
  )
  private OffsetDateTime lastupdate;

  @ToString.Include
  @Column(
      name = "specifiedRootCause"
  )
  private String specifiedrootcause;
}

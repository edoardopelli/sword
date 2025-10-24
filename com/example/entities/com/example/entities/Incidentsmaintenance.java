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
    name = "incidentsMaintenance"
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
    date = "2025-10-23T23:12:08.546221+02:00"
)
public class Incidentsmaintenance {
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
      name = "workOrderNumber"
  )
  private Long workordernumber;

  @ToString.Include
  @Column(
      name = "statusOfIntervention"
  )
  private String statusofintervention;

  @ToString.Include
  @Column(
      name = "purposeOfInterventionsId"
  )
  private Long purposeofinterventionsid;

  @ToString.Include
  @Column(
      name = "interventionSubcategoriesId"
  )
  private Long interventionsubcategoriesid;

  @ToString.Include
  @Column(
      name = "interventionDescription"
  )
  private String interventiondescription;

  @ToString.Include
  @Column(
      name = "repairDetails"
  )
  private String repairdetails;

  @ToString.Include
  @Column(
      name = "underWarranty"
  )
  private Boolean underwarranty;

  @ToString.Include
  @Column(
      name = "startOfIntervention"
  )
  private OffsetDateTime startofintervention;

  @ToString.Include
  @Column(
      name = "endOfIntervention"
  )
  private OffsetDateTime endofintervention;

  @ToString.Include
  @Column(
      name = "activeRepairTime"
  )
  private OffsetDateTime activerepairtime;

  @ToString.Include
  @Column(
      name = "failedLRUDescription"
  )
  private String failedlrudescription;

  @ToString.Include
  @Column(
      name = "causeId"
  )
  private Long causeid;

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
      name = "specifiedPreliminaryRootCause"
  )
  private String specifiedpreliminaryrootcause;

  @ToString.Include
  @Column(
      name = "failedLRU_PN"
  )
  private String failedlruPn;

  @ToString.Include
  @Column(
      name = "failedLRU_SN"
  )
  private String failedlruSn;

  @ToString.Include
  @Column(
      name = "failedLRU_SW_Version"
  )
  private String failedlruSwVersion;

  @ToString.Include
  @Column(
      name = "newLRU_PN"
  )
  private String newlruPn;

  @ToString.Include
  @Column(
      name = "newLRU_SN"
  )
  private String newlruSn;

  @ToString.Include
  @Column(
      name = "newLRU_SW_Version"
  )
  private String newlruSwVersion;
}

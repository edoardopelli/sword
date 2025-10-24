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
    name = "incidents"
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
    date = "2025-10-23T23:12:08.534544+02:00"
)
public class Incidents {
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
      name = "incidentCode"
  )
  private String incidentcode;

  @ToString.Include
  @Column(
      name = "IncidentDateTime"
  )
  private OffsetDateTime incidentdatetime;

  @ToString.Include
  @Column(
      name = "projectPhasesId"
  )
  private Long projectphasesid;

  @ToString.Include
  @Column(
      name = "incidentDescription"
  )
  private String incidentdescription;

  @ToString.Include
  @Column(
      name = "impactOnService"
  )
  private Boolean impactonservice;

  @ToString.Include
  @Column(
      name = "deviceId"
  )
  private Long deviceid;

  @ToString.Include
  @Column(
      name = "placeId"
  )
  private Long placeid;

  @ToString.Include
  @Column(
      name = "pbsId"
  )
  private Long pbsid;

  @ToString.Include
  @Column(
      name = "phaseId"
  )
  private Long phaseid;

  @ToString.Include
  @Column(
      name = "failureModeId"
  )
  private Long failuremodeid;

  @ToString.Include
  @Column(
      name = "failureMode"
  )
  private String failuremode;

  @ToString.Include
  @Column(
      name = "detectionMeanId"
  )
  private Long detectionmeanid;

  @ToString.Include
  @Column(
      name = "lineId"
  )
  private Long lineid;

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
}

package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.Boolean;
import java.lang.Integer;
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
    name = "incidentsOperational"
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
    date = "2025-10-23T23:12:08.551241+02:00"
)
public class Incidentsoperational {
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
      name = "incidentConsequencesId"
  )
  private Long incidentconsequencesid;

  @ToString.Include
  @Column(
      name = "delay"
  )
  private Integer delay;

  @ToString.Include
  @Column(
      name = "safetyImpactsId"
  )
  private Long safetyimpactsid;

  @ToString.Include
  @Column(
      name = "reaction"
  )
  private String reaction;

  @ToString.Include
  @Column(
      name = "interventionRequired"
  )
  private Boolean interventionrequired;

  @ToString.Include
  @Column(
      name = "resolutionMethod"
  )
  private String resolutionmethod;

  @ToString.Include
  @Column(
      name = "serviceRequestNumber"
  )
  private String servicerequestnumber;

  @ToString.Include
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(
      name = "alarmCodeIds",
      columnDefinition = "jsonb"
  )
  private Map<String, Object> alarmcodeids;

  @ToString.Include
  @Column(
      name = "alarmPriority"
  )
  private Long alarmpriority;

  @ToString.Include
  @Column(
      name = "vehicleId"
  )
  private String vehicleid;

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
}

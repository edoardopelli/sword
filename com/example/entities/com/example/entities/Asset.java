package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.Integer;
import java.lang.Long;
import java.lang.String;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "asset"
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
    date = "2025-10-23T23:12:08.476196+02:00"
)
public class Asset {
  @ToString.Include
  @Column(
      name = "metrolinxType"
  )
  private String metrolinxtype;

  @ToString.Include
  @Column(
      name = "isRotating"
  )
  private String isrotating;

  @ToString.Include
  @Column(
      name = "assetParent"
  )
  private String assetparent;

  @ToString.Include
  @Column(
      name = "assetCode"
  )
  private String assetcode;

  @ToString.Include
  @Column(
      name = "pbsCode"
  )
  private String pbscode;

  @ToString.Include
  @Column(
      name = "instance1"
  )
  private String instance1;

  @ToString.Include
  @Column(
      name = "instance2"
  )
  private String instance2;

  @ToString.Include
  @Column(
      name = "locCode"
  )
  private String loccode;

  @ToString.Include
  @Column(
      name = "itemCode"
  )
  private String itemcode;

  @ToString.Include
  @Column(
      name = "isTransportationAsset"
  )
  private String istransportationasset;

  @ToString.Include
  @Column(
      name = "contractCode"
  )
  private String contractcode;

  @ToString.Include
  @Column(
      name = "description"
  )
  private String description;

  @ToString.Include
  @Column(
      name = "serialNumber"
  )
  private String serialnumber;

  @ToString.Include
  @Column(
      name = "installationDate"
  )
  private LocalDate installationdate;

  @ToString.Include
  @Column(
      name = "year"
  )
  private Integer year;

  @ToString.Include
  @Column(
      name = "expectedServiceLife"
  )
  private BigDecimal expectedservicelife;

  @ToString.Include
  @Column(
      name = "expectedServiceLifeUnit"
  )
  private String expectedservicelifeunit;

  @ToString.Include
  @Column(
      name = "condition"
  )
  private String condition;

  @ToString.Include
  @Column(
      name = "failureClass"
  )
  private String failureclass;

  @ToString.Include
  @Column(
      name = "assetDirection"
  )
  private String assetdirection;

  @ToString.Include
  @Column(
      name = "assetStartPoint"
  )
  private String assetstartpoint;

  @ToString.Include
  @Column(
      name = "assetEndPoint"
  )
  private String assetendpoint;

  @ToString.Include
  @Column(
      name = "assetLrm"
  )
  private String assetlrm;

  @ToString.Include
  @Column(
      name = "oicCode"
  )
  private String oiccode;

  @ToString.Include
  @Column(
      name = "note"
  )
  private String note;

  @ToString.Include
  @Column(
      name = "producerValidationCode"
  )
  private String producervalidationcode;

  @ToString.Include
  @Column(
      name = "classificationCode"
  )
  private String classificationcode;

  @ToString.Include
  @Column(
      name = "customField01"
  )
  private String customfield01;

  @ToString.Include
  @Column(
      name = "customField01List"
  )
  private String customfield01list;

  @ToString.Include
  @Column(
      name = "customField01Value"
  )
  private String customfield01value;

  @ToString.Include
  @Column(
      name = "customField02"
  )
  private String customfield02;

  @ToString.Include
  @Column(
      name = "customField02List"
  )
  private String customfield02list;

  @ToString.Include
  @Column(
      name = "customField02Value"
  )
  private String customfield02value;

  @ToString.Include
  @Column(
      name = "customField03"
  )
  private String customfield03;

  @ToString.Include
  @Column(
      name = "customField03List"
  )
  private String customfield03list;

  @ToString.Include
  @Column(
      name = "customField03Value"
  )
  private String customfield03value;

  @ToString.Include
  @Column(
      name = "customField04"
  )
  private String customfield04;

  @ToString.Include
  @Column(
      name = "customField04List"
  )
  private String customfield04list;

  @ToString.Include
  @Column(
      name = "customField04Value"
  )
  private String customfield04value;

  @ToString.Include
  @Column(
      name = "customField05"
  )
  private String customfield05;

  @ToString.Include
  @Column(
      name = "customField05List"
  )
  private String customfield05list;

  @ToString.Include
  @Column(
      name = "customField05Value"
  )
  private String customfield05value;

  @ToString.Include
  @Column(
      name = "customField06"
  )
  private String customfield06;

  @ToString.Include
  @Column(
      name = "customField06List"
  )
  private String customfield06list;

  @ToString.Include
  @Column(
      name = "customField06Value"
  )
  private String customfield06value;

  @ToString.Include
  @Column(
      name = "customField07"
  )
  private String customfield07;

  @ToString.Include
  @Column(
      name = "customField07List"
  )
  private String customfield07list;

  @ToString.Include
  @Column(
      name = "customField07Value"
  )
  private String customfield07value;

  @ToString.Include
  @Column(
      name = "customField08"
  )
  private String customfield08;

  @ToString.Include
  @Column(
      name = "customField08List"
  )
  private String customfield08list;

  @ToString.Include
  @Column(
      name = "customField08Value"
  )
  private String customfield08value;

  @ToString.Include
  @Column(
      name = "customField09"
  )
  private String customfield09;

  @ToString.Include
  @Column(
      name = "customField09List"
  )
  private String customfield09list;

  @ToString.Include
  @Column(
      name = "customField09Value"
  )
  private String customfield09value;

  @ToString.Include
  @Column(
      name = "customField10"
  )
  private String customfield10;

  @ToString.Include
  @Column(
      name = "customField10List"
  )
  private String customfield10list;

  @ToString.Include
  @Column(
      name = "customField10Value"
  )
  private String customfield10value;

  @ToString.Include
  @Column(
      name = "customField11"
  )
  private String customfield11;

  @ToString.Include
  @Column(
      name = "customField11List"
  )
  private String customfield11list;

  @ToString.Include
  @Column(
      name = "customField11Value"
  )
  private String customfield11value;

  @ToString.Include
  @Column(
      name = "customField12"
  )
  private String customfield12;

  @ToString.Include
  @Column(
      name = "customField12List"
  )
  private String customfield12list;

  @ToString.Include
  @Column(
      name = "customField12Value"
  )
  private String customfield12value;

  @ToString.Include
  @Column(
      name = "customField13"
  )
  private String customfield13;

  @ToString.Include
  @Column(
      name = "customField13List"
  )
  private String customfield13list;

  @ToString.Include
  @Column(
      name = "customField13Value"
  )
  private String customfield13value;

  @ToString.Include
  @Column(
      name = "customField14"
  )
  private String customfield14;

  @ToString.Include
  @Column(
      name = "customField14List"
  )
  private String customfield14list;

  @ToString.Include
  @Column(
      name = "customField14Value"
  )
  private String customfield14value;

  @ToString.Include
  @Column(
      name = "customField15"
  )
  private String customfield15;

  @ToString.Include
  @Column(
      name = "customField15List"
  )
  private String customfield15list;

  @ToString.Include
  @Column(
      name = "customField15Value"
  )
  private String customfield15value;

  @ToString.Include
  @Column(
      name = "customField16"
  )
  private String customfield16;

  @ToString.Include
  @Column(
      name = "customField16List"
  )
  private String customfield16list;

  @ToString.Include
  @Column(
      name = "customField16Value"
  )
  private String customfield16value;

  @ToString.Include
  @Column(
      name = "customField17"
  )
  private String customfield17;

  @ToString.Include
  @Column(
      name = "customField17List"
  )
  private String customfield17list;

  @ToString.Include
  @Column(
      name = "customField17Value"
  )
  private String customfield17value;

  @ToString.Include
  @Column(
      name = "customField18"
  )
  private String customfield18;

  @ToString.Include
  @Column(
      name = "customField18List"
  )
  private String customfield18list;

  @ToString.Include
  @Column(
      name = "customField18Value"
  )
  private String customfield18value;

  @ToString.Include
  @Column(
      name = "customField19"
  )
  private String customfield19;

  @ToString.Include
  @Column(
      name = "customField19List"
  )
  private String customfield19list;

  @ToString.Include
  @Column(
      name = "customField19Value"
  )
  private String customfield19value;

  @ToString.Include
  @Column(
      name = "customField20"
  )
  private String customfield20;

  @ToString.Include
  @Column(
      name = "customField20List"
  )
  private String customfield20list;

  @ToString.Include
  @Column(
      name = "customField20Value"
  )
  private String customfield20value;

  @ToString.Include
  @Column(
      name = "customField21"
  )
  private String customfield21;

  @ToString.Include
  @Column(
      name = "customField21List"
  )
  private String customfield21list;

  @ToString.Include
  @Column(
      name = "customField21Value"
  )
  private String customfield21value;

  @ToString.Include
  @Column(
      name = "customField22"
  )
  private String customfield22;

  @ToString.Include
  @Column(
      name = "customField22List"
  )
  private String customfield22list;

  @ToString.Include
  @Column(
      name = "customField22Value"
  )
  private String customfield22value;

  @ToString.Include
  @Column(
      name = "customField23"
  )
  private String customfield23;

  @ToString.Include
  @Column(
      name = "customField23List"
  )
  private String customfield23list;

  @ToString.Include
  @Column(
      name = "customField23Value"
  )
  private String customfield23value;

  @ToString.Include
  @Column(
      name = "customField24"
  )
  private String customfield24;

  @ToString.Include
  @Column(
      name = "customField24List"
  )
  private String customfield24list;

  @ToString.Include
  @Column(
      name = "customField24Value"
  )
  private String customfield24value;

  @ToString.Include
  @Column(
      name = "customField25"
  )
  private String customfield25;

  @ToString.Include
  @Column(
      name = "customField25List"
  )
  private String customfield25list;

  @ToString.Include
  @Column(
      name = "customField25Value"
  )
  private String customfield25value;

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
      name = "userId"
  )
  private Integer userid;

  @ToString.Include
  @Column(
      name = "isFunctionalAsset"
  )
  private String isfunctionalasset;

  @ToString.Include
  @Column(
      name = "versionId"
  )
  private Integer versionid;

  @ToString.Include
  @Column(
      name = "dataStart"
  )
  private OffsetDateTime datastart;

  @ToString.Include
  @Column(
      name = "dataEnd"
  )
  private OffsetDateTime dataend;

  @ToString.Include
  @Column(
      name = "userLastMod"
  )
  private Integer userlastmod;

  @ToString.Include
  @Column(
      name = "bimModel"
  )
  private String bimmodel;

  @ToString.Include
  @Column(
      name = "customField26"
  )
  private String customfield26;

  @ToString.Include
  @Column(
      name = "customField26List"
  )
  private String customfield26list;

  @ToString.Include
  @Column(
      name = "customField26Value"
  )
  private String customfield26value;

  @ToString.Include
  @Column(
      name = "customField27"
  )
  private String customfield27;

  @ToString.Include
  @Column(
      name = "customField27List"
  )
  private String customfield27list;

  @ToString.Include
  @Column(
      name = "customField27Value"
  )
  private String customfield27value;

  @ToString.Include
  @Column(
      name = "customField28"
  )
  private String customfield28;

  @ToString.Include
  @Column(
      name = "customField28List"
  )
  private String customfield28list;

  @ToString.Include
  @Column(
      name = "customField28Value"
  )
  private String customfield28value;

  @ToString.Include
  @Column(
      name = "customField29"
  )
  private String customfield29;

  @ToString.Include
  @Column(
      name = "customField29List"
  )
  private String customfield29list;

  @ToString.Include
  @Column(
      name = "customField29Value"
  )
  private String customfield29value;

  @ToString.Include
  @Column(
      name = "customField30"
  )
  private String customfield30;

  @ToString.Include
  @Column(
      name = "customField30List"
  )
  private String customfield30list;

  @ToString.Include
  @Column(
      name = "customField30Value"
  )
  private String customfield30value;

  @ToString.Include
  @Column(
      name = "customField31"
  )
  private String customfield31;

  @ToString.Include
  @Column(
      name = "customField31List"
  )
  private String customfield31list;

  @ToString.Include
  @Column(
      name = "customField31Value"
  )
  private String customfield31value;

  @ToString.Include
  @Column(
      name = "customField32"
  )
  private String customfield32;

  @ToString.Include
  @Column(
      name = "customField32List"
  )
  private String customfield32list;

  @ToString.Include
  @Column(
      name = "customField32Value"
  )
  private String customfield32value;

  @ToString.Include
  @Column(
      name = "customField33"
  )
  private String customfield33;

  @ToString.Include
  @Column(
      name = "customField33List"
  )
  private String customfield33list;

  @ToString.Include
  @Column(
      name = "customField33Value"
  )
  private String customfield33value;

  @ToString.Include
  @Column(
      name = "customField34"
  )
  private String customfield34;

  @ToString.Include
  @Column(
      name = "customField34List"
  )
  private String customfield34list;

  @ToString.Include
  @Column(
      name = "customField34Value"
  )
  private String customfield34value;

  @ToString.Include
  @Column(
      name = "customField35"
  )
  private String customfield35;

  @ToString.Include
  @Column(
      name = "customField35List"
  )
  private String customfield35list;

  @ToString.Include
  @Column(
      name = "customField35Value"
  )
  private String customfield35value;

  @ToString.Include
  @Column(
      name = "customField36"
  )
  private String customfield36;

  @ToString.Include
  @Column(
      name = "customField36List"
  )
  private String customfield36list;

  @ToString.Include
  @Column(
      name = "customField36Value"
  )
  private String customfield36value;

  @ToString.Include
  @Column(
      name = "customField37"
  )
  private String customfield37;

  @ToString.Include
  @Column(
      name = "customField37List"
  )
  private String customfield37list;

  @ToString.Include
  @Column(
      name = "customField37Value"
  )
  private String customfield37value;

  @ToString.Include
  @Column(
      name = "customField38"
  )
  private String customfield38;

  @ToString.Include
  @Column(
      name = "customField38List"
  )
  private String customfield38list;

  @ToString.Include
  @Column(
      name = "customField38Value"
  )
  private String customfield38value;

  @ToString.Include
  @Column(
      name = "customField39"
  )
  private String customfield39;

  @ToString.Include
  @Column(
      name = "customField39List"
  )
  private String customfield39list;

  @ToString.Include
  @Column(
      name = "customField39Value"
  )
  private String customfield39value;

  @ToString.Include
  @Column(
      name = "customField40"
  )
  private String customfield40;

  @ToString.Include
  @Column(
      name = "customField40List"
  )
  private String customfield40list;

  @ToString.Include
  @Column(
      name = "customField40Value"
  )
  private String customfield40value;
}

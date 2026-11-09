package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Actions of the joints a movement uses (joint layer of the catalog). */
@Getter
@RequiredArgsConstructor
public enum JointAction {
  HIP_EXT(Joint.HIP),
  HIP_FLEX(Joint.HIP),
  HIP_ABD(Joint.HIP),
  HIP_ADD(Joint.HIP),
  HIP_ROTL(Joint.HIP),
  KNEE_EXT(Joint.KNEE),
  KNEE_FLEX(Joint.KNEE),
  ANK_PF(Joint.ANKLE),
  ANK_DF(Joint.ANKLE),
  SPINE_EXT(Joint.SPINE),
  SPINE_FLEX(Joint.SPINE),
  SPINE_LAT(Joint.SPINE),
  SPINE_ROT(Joint.SPINE),
  SH_FLEX(Joint.SHOULDER),
  SH_EXT(Joint.SHOULDER),
  SH_ABD(Joint.SHOULDER),
  SH_ADD(Joint.SHOULDER),
  SH_HADD(Joint.SHOULDER),
  SH_HABD(Joint.SHOULDER),
  SH_ROTL(Joint.SHOULDER),
  SH_ROTM(Joint.SHOULDER),
  SC_ELEV(Joint.SCAPULA),
  SC_DEPR(Joint.SCAPULA),
  SC_UP(Joint.SCAPULA),
  SC_DOWN(Joint.SCAPULA),
  SC_RETR(Joint.SCAPULA),
  SC_PROT(Joint.SCAPULA),
  ELB_FLEX(Joint.ELBOW),
  ELB_EXT(Joint.ELBOW),
  WR_FLEX(Joint.WRIST),
  WR_EXT(Joint.WRIST);

  private final Joint joint;

  /** Joints of the catalog. */
  public enum Joint {
    HIP,
    KNEE,
    ANKLE,
    SPINE,
    SHOULDER,
    SCAPULA,
    ELBOW,
    WRIST
  }

  /** How a joint works in a movement. */
  public enum Regime {
    /** The joint moves under load. */
    DYNAMIC,
    /** The joint is held. */
    HELD
  }
}

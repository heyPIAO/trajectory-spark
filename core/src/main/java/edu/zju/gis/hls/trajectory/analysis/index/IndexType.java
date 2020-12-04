package edu.zju.gis.hls.trajectory.analysis.index;

import lombok.Getter;

/**
 * @author Hu
 * @date 2019/12/16
 **/
@Getter
public enum IndexType {

  UNIFORM_GRID("uniform_grid", 0), RTREE("rtree", 1), EQUAL_GRID("equal_grid", 5);

  IndexType(String name, int type) {
    this.name = name;
    this.type = type;
  }

  private String name;
  private int type;

  public static IndexType Default() {
    return UNIFORM_GRID;
  }

}

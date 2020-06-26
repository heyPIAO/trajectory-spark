package edu.zju.gis.hls.trajectory.analysis.rddLayer;

import edu.zju.gis.hls.trajectory.analysis.model.FeatureType;
import lombok.Getter;

/**
 * @author Hu
 * @date 2019/9/20
 * 图层类型
 **/
public enum LayerType {

  POINT_LAYER(0, FeatureType.POINT, PointLayer.class),
  POLYLINE_LAYER(1, FeatureType.POLYLINE, PolylineLayer.class),
  POLYGON_LAYER(2, FeatureType.POLYGON, PolygonLayer.class),
  TRAJECTORY_POINT_LAYER(3, FeatureType.TRAJECTORY_POINT, TrajectoryPointLayer.class),
  TRAJECTORY_POLYLINE_LAYER(4, FeatureType.TRAJECTORY_POLYLINE, TrajectoryPolylineLayer.class),
  MULTI_POINT_LAYER(5, FeatureType.MULTI_POINT, MultiPointLayer.class),
  MULTI_POLYLINE_LAYER(6, FeatureType.MULTI_POLYLINE, MultiPolylineLayer.class),
  MULTI_POLYGON_LAYER(7, FeatureType.MULTI_POLYGON, MultiPolygonLayer.class);

  @Getter
  private int type;

  @Getter
  private FeatureType featureType;

  @Getter
  private Class<? extends Layer> layerClass;

  LayerType(int type, FeatureType featureType, Class<? extends Layer> layerClass) {
    this.type = type;
    this.featureType = featureType;
    this.layerClass = layerClass;
  }

  public static LayerType findLayerType(FeatureType featureType) {
    for (LayerType lt: values()) {
      if (lt.featureType.equals(featureType)) {
        return lt;
      }
    }
    return null;
  }

}

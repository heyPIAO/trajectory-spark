package edu.zju.gis.hls.trajectory.analysis.index.rectGrid;

import edu.zju.gis.hls.trajectory.analysis.index.IndexType;
import edu.zju.gis.hls.trajectory.analysis.model.Feature;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.KeyIndexedLayer;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.Layer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.api.java.function.Function;
import org.locationtech.jts.geom.Geometry;
import scala.Tuple2;

import java.util.List;

/**
 * @author Hu
 * @date 2019/12/18
 * 均匀格网索引的 RDD Layer
 **/
@Getter
@Setter
@Slf4j
public class RectGridIndexLayer<L extends Layer> extends KeyIndexedLayer<L> {

  public RectGridIndexLayer() {
    super();
    this.indexType = IndexType.RECT_GRID;
  }

  @Override
  public RectGridIndexLayer<L> query(Geometry geometry) {

    List<String> tiles = this.queryPartitionsKeys(geometry);
    this.layer = (L) this.layer.filterToLayer(new Function<Tuple2, Boolean>() {
      @Override
      public Boolean call(Tuple2 in) throws Exception {
        Object k = in._1;
        if (k instanceof String && tiles.contains(k)) {
          Feature f = (Feature) in._2;
          return f.getGeometry().intersects(geometry);
        }
        return false;
      }
    });
    return this;
  }

}
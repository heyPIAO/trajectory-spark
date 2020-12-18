package edu.zju.gis.hls.gisspark.model.stats;

import edu.zju.gis.hls.gisspark.model.BaseModel;
import edu.zju.gis.hls.gisspark.model.util.SparkSessionType;
import edu.zju.gis.hls.trajectory.analysis.index.DistributeSpatialIndex;
import edu.zju.gis.hls.trajectory.analysis.index.IndexType;
import edu.zju.gis.hls.trajectory.analysis.index.SpatialIndexFactory;
import edu.zju.gis.hls.trajectory.analysis.index.unifromGrid.UniformGridIndexConfig;
import edu.zju.gis.hls.trajectory.analysis.model.Feature;
import edu.zju.gis.hls.trajectory.analysis.model.Field;
import edu.zju.gis.hls.trajectory.analysis.model.MultiPoint;
import edu.zju.gis.hls.trajectory.analysis.model.MultiPolygon;
import edu.zju.gis.hls.trajectory.analysis.model.MultiPolyline;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.KeyIndexedLayer;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.MultiPointLayer;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.MultiPolygonLayer;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.MultiPolylineLayer;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.StatLayer;
import edu.zju.gis.hls.trajectory.datastore.exception.GISSparkException;
import edu.zju.gis.hls.trajectory.datastore.storage.LayerFactory;
import edu.zju.gis.hls.trajectory.datastore.storage.config.PgConfig;
import edu.zju.gis.hls.trajectory.datastore.storage.reader.LayerReader;
import edu.zju.gis.hls.trajectory.datastore.storage.reader.LayerReaderConfig;
import edu.zju.gis.hls.trajectory.datastore.storage.writer.LayerWriter;
import edu.zju.gis.hls.trajectory.datastore.storage.writer.LayerWriterConfig;
import edu.zju.gis.hls.trajectory.datastore.storage.writer.pg.PgLayerWriterConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.Optional;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.apache.spark.api.java.function.PairFunction;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import scala.Tuple2;
import scala.Tuple4;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Getter
@Setter
@Slf4j
public class LandFlowAnalysisNew extends BaseModel<LandFlowAnalysisNewArgs> implements Serializable {

    private static Integer DEFAULT_INDEX_LEVEL = 10;
    private static Double DEFAULT_MU = 666.6666667;
    private static Double DEFAULT_GQ = 10000.00;//0921改为公顷
    private PgConfig pgConfig = new PgConfig();
    private static String EMPTY_STRING = "_";

    public LandFlowAnalysisNew(SparkSessionType type, String[] args) {
        super(type, args);
    }

    @Override
    protected void prepare() {
        super.prepare();
        this.initPgConfig();
    }

    private void initPgConfig() {
        // TODO 用正则取出来
        InputStream in = this.getClass().getResourceAsStream("/mysqlConfig.properties");
        Properties props = new Properties();
        try {
            InputStreamReader inputStreamReader = new InputStreamReader(in, "UTF-8");
            props.load(inputStreamReader);
//            this.pgConfig.setUrl((String) (props.get("pg.url")));
//            this.pgConfig.setPort(Integer.valueOf(props.get("pg.port").toString()));
//            this.pgConfig.setDatabase((String) props.get("pg.database"));
//            this.pgConfig.setSchema((String) props.get("pg.schema"));
//            this.pgConfig.setUsername((String) props.get("pg.username"));
//            this.pgConfig.setPassword((String) props.get("pg.password"));
        } catch (IOException e) {
            throw new GISSparkException("read mysql configuration failed: " + e.getMessage());
        }
    }

    @Override
    public void run() throws Exception {
        StringBuilder logBuilder = new StringBuilder();
//        final PgHelper pgHelper = new PgHelper(this.pgConfig);
        try {

            LayerReaderConfig tb3dReaderConfig = LayerFactory.getReaderConfig(this.arg.getTb3dReaderConfig());
            LayerReader<MultiPolygonLayer> tb3dLayerReader = LayerFactory.getReader(ss, tb3dReaderConfig);
            LayerReaderConfig tb2dReaderConfig = LayerFactory.getReaderConfig(this.arg.getTb2dReaderConfig());
            LayerReader<MultiPolygonLayer> tb2dLayerReader = LayerFactory.getReader(ss, tb2dReaderConfig);
            LayerReaderConfig xz2dReaderConfig = LayerFactory.getReaderConfig(this.arg.getXz2dReaderConfig());
            LayerReader<MultiPolylineLayer> xz2dLayerReader = LayerFactory.getReader(ss, xz2dReaderConfig);
            LayerReaderConfig lx2dReaderConfig = LayerFactory.getReaderConfig(this.arg.getLx2dReaderConfig());
            LayerReader<MultiPointLayer> lx2dLayerReader = LayerFactory.getReader(ss, lx2dReaderConfig);

            LayerReaderConfig xzq3dReaderConfig = LayerFactory.getReaderConfig(this.arg.getXzq3dReaderConfig());
            LayerReader<MultiPolygonLayer> xzq3dLayerReader = LayerFactory.getReader(ss, xzq3dReaderConfig);
            LayerReaderConfig xzq2dReaderConfig = LayerFactory.getReaderConfig(this.arg.getXzq2dReaderConfig());
            LayerReader<MultiPolygonLayer> xzq2dLayerReader = LayerFactory.getReader(ss, xzq2dReaderConfig);


            LayerWriterConfig statsWriterConfig = LayerFactory.getWriterConfig(this.arg.getStatsWriterConfig());
            JSONObject writeConfig = new JSONObject(this.arg.getStatsWriterConfig());
//            //todo 暂时默认为pg输出
//            String taskName = this.arg.getTaskName();
//            String tbName = writeConfig.getString("tablename");
            logBuilder.append("数据读取准备就绪...");
//            pgHelper.runSQL(this.insertAnalysisInfo(), UUID.randomUUID()
//                    , taskName
//                    , "RUNNING"
//                    , tbName
//                    , this.arg.getStatsWriterConfig()
//                    , logBuilder.toString()
//                    , java.sql.Timestamp.from(Instant.now()));


            MultiPolygonLayer tb3dLayer = tb3dLayerReader.read();
            MultiPolygonLayer tb2dLayer = tb2dLayerReader.read();
            MultiPolylineLayer xz2dLayer = xz2dLayerReader.read();
            MultiPointLayer lx2dLayer = lx2dLayerReader.read();

            MultiPolygonLayer xzq3dLayer = xzq3dLayerReader.read();
            MultiPolygonLayer xzq2dLayer = xzq2dLayerReader.read();

            DistributeSpatialIndex si = SpatialIndexFactory.getDistributedSpatialIndex(IndexType.UNIFORM_GRID, new UniformGridIndexConfig(DEFAULT_INDEX_LEVEL, false));

            KeyIndexedLayer<MultiPolygonLayer> l1 = si.index(tb2dLayer, false);
            KeyIndexedLayer<MultiPolygonLayer> l2 = si.index(tb3dLayer, false);
            KeyIndexedLayer<MultiPolylineLayer> l3 = si.index(xz2dLayer, false);
            KeyIndexedLayer<MultiPointLayer> l4 = si.index(lx2dLayer, false);
            KeyIndexedLayer<MultiPolygonLayer> lxzq3 = si.index(xzq3dLayer, false);
            KeyIndexedLayer<MultiPolygonLayer> lxzq2 = si.index(xzq2dLayer, false);

            MultiPolygonLayer tb2dLayerPair = l1.getLayer(); // key 为 TileID
            MultiPolygonLayer tb3dLayerPair = l2.getLayer(); // key 为 TileID
            MultiPolylineLayer xz2dLayerPair = l3.getLayer();
            MultiPointLayer lx2dLayerPair = l4.getLayer();
            MultiPolygonLayer xzq2dLayerPair = lxzq2.getLayer();
            MultiPolygonLayer xzq3dLayerPair = lxzq3.getLayer();
            //JavaPairRDD<String,Geometry> flow_lmx = null;
            JavaPairRDD<String,Geometry> flow_lmx = lmx(this.arg.getXzqdm(),xzq3dLayerPair,xzq2dLayerPair,tb3dLayerPair,tb2dLayerPair,xz2dLayerPair,lx2dLayerPair);

            logBuilder.append("数据读取完毕！计算中...");
            //pgHelper.runSQL(this.updateAnalysisLog(taskName), logBuilder.toString());

            JavaPairRDD<String, MultiPolygon> temp = tb3dLayerPair.cogroup(tb2dLayerPair).flatMapToPair(new PairFlatMapFunction<Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPolygon>>>, String, MultiPolygon>() {
                @Override
                public Iterator<Tuple2<String, MultiPolygon>> call(Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPolygon>>> in) throws Exception {
                    List<Tuple2<String, MultiPolygon>> result = new ArrayList<>();
                    for (MultiPolygon tb3d : in._2._1) {
                        for (MultiPolygon tb2d : in._2._2) {
                            if (tb3d.getGeometry().intersects(tb2d.getGeometry())) {

                                Double sdtbmj = Double.valueOf(tb3d.getAttribute("TBMJ").toString());
                                Double sdmj = tb3d.getGeometry().getArea();

                                //三调tkxs、二调tkxs、二调dlbz、三调zzsxdm、zldwdm、三调dlbm、二调dlbm
                                Double tkxs_3 = Double.valueOf(tb3d.getAttribute("KCXS").toString());
                                Double tkxs_2 = Double.valueOf(tb2d.getAttribute("TKXS").toString());
                                String dlbm_3 = String.valueOf(tb3d.getAttribute("DLBM").toString());
                                String dlbm_2 = String.valueOf(tb2d.getAttribute("DLBM").toString());
                                String bsm_3 = String.valueOf(tb3d.getAttribute("BSM").toString());
                                String bsm_2 = String.valueOf(tb2d.getAttribute("BSM").toString());
                                String zldwdm_3 = String.valueOf(tb3d.getAttribute("ZLDWDM").toString()).substring(0, 6);
                                String dlbz_2 = String.valueOf(tb2d.getAttribute("DLBZ").toString());
                                String zzsxdm_3 = String.valueOf(tb3d.getAttribute("ZZSXDM").toString());
                                String zldwdm_9 = String.valueOf(tb2d.getAttribute("ZLDWDM").toString().substring(0, 12));

                                if (dlbz_2 == null || dlbz_2.equals("") || dlbz_2.equals("null")) {
                                    dlbz_2 = EMPTY_STRING;
                                }
                                if (zzsxdm_3 == null || zzsxdm_3.equals("") || zzsxdm_3.equals("null")) {
                                    zzsxdm_3 = EMPTY_STRING;
                                }
                                String bsm_23 = bsm_3 + "_" + bsm_2;
                                String tbbh_2 = String.valueOf(tb2d.getAttribute("TBBH").toString());
                                LinkedHashMap<Field, Object> attrs = new LinkedHashMap();
                                Field field1 = new Field("TBMJ");
                                field1.setType(Double.TYPE);
                                attrs.put(field1, 0.0);
                                Field field2 = new Field("TKXS_3");
                                field2.setType(Double.TYPE);
                                attrs.put(field2, tkxs_3);
                                Field field3 = new Field("TKXS_2");
                                field3.setType(Double.TYPE);
                                attrs.put(field3, tkxs_2);
                                Field field4 = new Field("DLBM_3");
                                attrs.put(field4, dlbm_3);
                                Field field5 = new Field("DLBM_2");
                                attrs.put(field5, dlbm_2);
                                Field field6 = new Field("DLBM_3");
                                attrs.put(field6, dlbm_3);
                                Field field7 = new Field("ZLDWDM_3");
                                attrs.put(field7, zldwdm_3);
                                Field field8 = new Field("DLBZ_2");
                                attrs.put(field8, dlbz_2);
                                Field field9 = new Field("ZZSXDM_3");
                                attrs.put(field9, zzsxdm_3);
                                Field field10 = new Field("XWMJ_LIST");
                                field10.setType(ArrayList.class);
                                attrs.put(field10, new ArrayList<>());
                                Field field11 = new Field("LWMJ_LIST");
                                field11.setType(ArrayList.class);
                                attrs.put(field11, new ArrayList<>());
                                Field field12 = new Field("BSM_23");
                                attrs.put(field12, bsm_23);
                                Field field13 = new Field("TBBH_2");
                                attrs.put(field13, tbbh_2);
                                Field field14 = new Field("XWMJ");
                                field14.setType(Double.class);
                                attrs.put(field14, 0.0);
                                Field field15 = new Field("LWMJ");
                                field15.setType(Double.class);
                                attrs.put(field15, 0.0);
                                Field field16 = new Field("TBDLMJ");
                                field16.setType(Double.TYPE);
                                attrs.put(field16, 0.0);
                                Field field17 = new Field("ZLDWDM_9");
                                attrs.put(field17, zldwdm_9);

                                Feature feature = new Feature();
                                Geometry tb3dg = feature.validedGeom((org.locationtech.jts.geom.MultiPolygon) tb3d.getGeometry());
                                Geometry tb2dg = feature.validedGeom((org.locationtech.jts.geom.MultiPolygon) tb2d.getGeometry());
                                tb3d.setGeometry((org.locationtech.jts.geom.MultiPolygon) tb3dg);
                                tb2d.setGeometry((org.locationtech.jts.geom.MultiPolygon) tb2dg);
                                Geometry geo = tb3d.getGeometry().intersection(tb2d.getGeometry());
                                for (int i = 0; i < geo.getNumGeometries(); i++) {
                                    MultiPolygon inter = new MultiPolygon();
                                    Geometry geometry = geo.getGeometryN(i);
                                    if (geometry.getDimension() < 2) {
                                        continue;
                                    }
//                                    if(geometry.getArea()<0.0005){
//                                        continue;
//                                    }
//                                    GeometrySnapper geometrySnapper = new GeometrySnapper(geometry);
//                                    geometry = geometrySnapper.snapToSelf(0.000001,true);
                                    if (geometry instanceof org.locationtech.jts.geom.Polygon) {
                                        org.locationtech.jts.geom.Polygon[] pl = new org.locationtech.jts.geom.Polygon[1];
                                        pl[0] = (org.locationtech.jts.geom.Polygon) geometry;
                                        GeometryFactory gf = new GeometryFactory();
                                        org.locationtech.jts.geom.MultiPolygon multiPolygon = new org.locationtech.jts.geom.MultiPolygon(pl, gf);
//                        ResultSet rs = pgHelper.select("SELECT spikeremover( " + geometry + ") as geo");
//                        geometry = (Geometry)rs.getObject("geo");
                                        inter.setGeometry((org.locationtech.jts.geom.MultiPolygon) multiPolygon);
                                    } else if (geometry instanceof org.locationtech.jts.geom.MultiPolygon) {
                                        inter.setGeometry((org.locationtech.jts.geom.MultiPolygon) geometry);
                                    }


                                    Double intermj = inter.getGeometry().getArea();
                                    Double intertbmj = sdtbmj / sdmj * intermj;
                                    LinkedHashMap<Field, Object> attrs1 = new LinkedHashMap<>(attrs);
                                    attrs1.put(field1, intertbmj);
                                    attrs1.put(field16, intertbmj);
                                    attrs1.put(field12, bsm_23 + "_" + i);
                                    inter.setAttributes(attrs1);
                                    result.add(new Tuple2<>(bsm_23 + "_" + i, inter));
                                }
                                //result.add(new Tuple2<>(tb3d.getAttributes().get(field3d) + "##" + tb2d.getAttributes().get(field2d), new Tuple2<>(tb3d, tb2d)));
                            }
                        }
                    }
                    return result.iterator();
                }
            });

            MultiPolygonLayer layer = new MultiPolygonLayer(temp.rdd());
            KeyIndexedLayer<MultiPolygonLayer> l5 = si.index(layer, false);
            MultiPolygonLayer interLayer = l5.getLayer();
            JavaPairRDD<String, MultiPolygon> inter2 = interLayer.leftOuterJoin(lx2dLayerPair).mapToPair(new PairFunction<Tuple2<String, Tuple2<MultiPolygon, Optional<MultiPoint>>>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, Tuple2<MultiPolygon, Optional<MultiPoint>>> in) throws Exception {
                    MultiPolygon mp = in._2._1;
                    String bsm_23 = mp.getAttribute("BSM_23").toString();
                    MultiPolygon mpp = new MultiPolygon(mp);
                    if (in._2._2.isPresent()) {
                        MultiPoint pt = in._2._2.get();
                        if (mpp.getGeometry().contains(pt.getGeometry())) {
                            List<Tuple4<String, Double, Geometry, String>> list = new ArrayList<>();
                            //Map<String, Double> total =(HashMap<String, Double>)(mp.getAttribute("LWMJ_MAP"));
                            Double toarea = Double.valueOf(mpp.getAttribute("LWMJ").toString());
                            Double tbdlmj = Double.valueOf(mpp.getAttribute("TBDLMJ").toString());
                            Double area = Double.valueOf(pt.getAttribute("MJ").toString());
                            toarea += area;
                            tbdlmj -= area;
                            String dlbm = pt.getAttribute("DLBM").toString();
                            String bsm = pt.getAttribute("BSM").toString();

                            Tuple4<String, Double, Geometry, String> tu = new Tuple4<>(dlbm, area, pt.getGeometry(), bsm);
                            list.add(tu);

                            Field field1 = mpp.getField("LWMJ_LIST");
                            Field field2 = mpp.getField("LWMJ");
                            Field field3 = mpp.getField("TBDLMJ");
                            LinkedHashMap<Field, Object> attrs = mpp.getAttributes();
                            attrs.put(field1, list);
                            attrs.put(field2, toarea);
                            attrs.put(field3, tbdlmj);
                            mpp.setAttributes(attrs);
                            return new Tuple2<>(bsm_23 + "##" + bsm, mpp);
                        }
                    }
                    return new Tuple2<>(bsm_23, mpp);
                }
            }).reduceByKey(new Function2<MultiPolygon, MultiPolygon, MultiPolygon>() {
                @Override
                public MultiPolygon call(MultiPolygon multiPolygon, MultiPolygon multiPolygon2) throws Exception {
                    return multiPolygon;
                }
            }).mapToPair(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    String bsm_23 = in._2.getAttribute("BSM_23").toString();
                    return new Tuple2<>(bsm_23, in._2);
                }
            }).groupByKey().mapToPair(new PairFunction<Tuple2<String, Iterable<MultiPolygon>>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, Iterable<MultiPolygon>> in) throws Exception {
                    MultiPolygon mp = new MultiPolygon();
                    int flag = 0;

                    for (MultiPolygon multiPolygon : in._2) {
                        if (flag == 0) {
                            mp = new MultiPolygon(multiPolygon);
                            flag = 1;
                            continue;
                        }
                        Double toarea = Double.valueOf(mp.getAttribute("LWMJ").toString());
                        Double toarea2 = Double.valueOf(multiPolygon.getAttribute("LWMJ").toString());
                        Double tbdlmj = Double.valueOf(mp.getAttribute("TBDLMJ").toString());
                        List<Tuple4<String, Double, Geometry, String>> list = (List<Tuple4<String, Double, Geometry, String>>) mp.getAttribute("LWMJ_LIST");
                        List<Tuple4<String, Double, Geometry, String>> list2 = (List<Tuple4<String, Double, Geometry, String>>) multiPolygon.getAttribute("LWMJ_LIST");
                        if (list2 != null) {
                            for (int j = 0; j < list2.size(); j++) {
                                int flag1 = 0;
                                for (int i = 0; i < list.size(); i++) {
                                    if (list.get(i)._4().equals(list2.get(j)._4())) {
                                        flag1 = 1;
                                        continue;
                                    }
                                }
                                if (flag1 == 0) {
                                    list.add(list2.get(j));
                                    toarea += list2.get(j)._2();
                                    tbdlmj -= list2.get(j)._2();
                                }
                            }
                        }
                        Field field1 = mp.getField("LWMJ_LIST");
                        Field field2 = mp.getField("LWMJ");
                        Field field3 = mp.getField("TBDLMJ");
                        LinkedHashMap<Field, Object> attrs = mp.getAttributes();
                        attrs.put(field1, list);
                        attrs.put(field2, toarea);
                        attrs.put(field3, tbdlmj);
                        mp.setAttributes(attrs);
                    }
                    return new Tuple2<>(in._1, mp);
                }
            });

            inter2.cache();
            MultiPolygonLayer layer2 = new MultiPolygonLayer(inter2.rdd());
            KeyIndexedLayer<MultiPolygonLayer> l6 = si.index(layer2, false);
            MultiPolygonLayer inter2Layer = l6.getLayer();


//            FileWriter writer1 = new FileWriter(this.arg.getGeomWriterConfig()+"mz");
//            List<Tuple2<String, MultiPolygon>> temp_list = inter2.collect();
//            for (int i = 0; i < temp_list.size(); i++) {
//                writer1.write(temp_list.get(i)._1 + "#" + temp_list.get(i)._2.getGeometry().toText()  + "#" + Double.valueOf(temp_list.get(i)._2.getAttribute("TBMJ").toString())+ "\n");
//            }
//            writer1.close();

            //生成最小线单元
            JavaPairRDD<String, MultiPolyline> xz = inter2Layer.cogroup(xz2dLayerPair).flatMapToPair(new PairFlatMapFunction<Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPolyline>>>, String, MultiPolyline>() {
                @Override
                public Iterator<Tuple2<String, MultiPolyline>> call(Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPolyline>>> in) throws Exception {
                    List<Tuple2<String, MultiPolyline>> result = new ArrayList<>();
                    for (MultiPolyline ml : in._2._2) {
                        Geometry source = ml.getGeometry();
                        //将栅格内所有多边形的外环，按0.001的容差，向xz线吸附
                        for (MultiPolygon mp : in._2._1) {
                            GeometrySnapper polygonSnapper = new GeometrySnapper(mp.getGeometry().getBoundary());
                            Geometry snappedBoundary = polygonSnapper.snapTo(source, 0.001);
                            GeometrySnapper snapper = new GeometrySnapper(source);
                            source = snapper.snapTo(snappedBoundary, 0.001);
                            if(mp.getGeometry().intersects(source)){
                                Geometry geometry = snappedBoundary.intersection(source);
                                source = snapper.snapTo(geometry,0.001);
                            }
                        }
                        MultiPolyline mls = new MultiPolyline();
                        if (source instanceof MultiLineString)
                            mls.setGeometry((MultiLineString) source);
                        else if (source instanceof LineString) {
                            mls.setGeometry(new MultiLineString(new LineString[]{(LineString) source}, new GeometryFactory()));
                        } else
                            throw new Exception("线装地物数据不支持当前类型");
                        LinkedHashMap<Field, Object> attrs = new LinkedHashMap<>(ml.getAttributes());
                        String bsm_xz = ml.getAttribute("BSM").toString();
                        Double sourceCd = Double.valueOf(ml.getAttribute("CD").toString());
                        Field xzBSM = new Field("YBSM");
                        attrs.put(xzBSM, bsm_xz);
                        Field xzCD = ml.getField("CD");
                        attrs.put(xzCD, sourceCd);

                        Field kcbsm1 = new Field("KCBSM1");
                        attrs.put(kcbsm1, "");
                        Field kcbsm2 = new Field("KCBSM2");
                        attrs.put(kcbsm2, "");

                        mls.setAttributes(attrs);
                        result.add(new Tuple2<>(bsm_xz, mls));
                    }
                    return result.iterator();
                }
            }).reduceByKey(new Function2<MultiPolyline, MultiPolyline, MultiPolyline>() {
                @Override
                public MultiPolyline call(MultiPolyline line1, MultiPolyline line2) throws Exception {
                    //合并跨格网对同一条xz线要素的切割结果
                    GeometrySnapper geometrySnapper = new GeometrySnapper(line1.getGeometry());
                    Geometry snappedLine = geometrySnapper.snapTo(line2.getGeometry(), 0.001);
                    if (snappedLine instanceof MultiLineString)
                        line1.setGeometry((MultiLineString) snappedLine);
                    else if (snappedLine instanceof LineString) {
                        line1.setGeometry(new MultiLineString(new LineString[]{(LineString) snappedLine}, new GeometryFactory()));
                    } else
                        throw new Exception("线装地物数据不支持当前类型");
                    return line1;
                }
            }).flatMapToPair(new PairFlatMapFunction<Tuple2<String, MultiPolyline>, String, MultiPolyline>() {
                @Override
                public Iterator<Tuple2<String, MultiPolyline>> call(Tuple2<String, MultiPolyline> snappedLine) throws Exception {
                    //将增加吸附点后的xz线，逐断分解
                    GeometryFactory gf = new GeometryFactory();
                    List<Tuple2<String, MultiPolyline>> result = new ArrayList<>();
                    List<LineString> partLineCollection = new ArrayList<>();
                    MultiLineString source = snappedLine._2.getGeometry();
                    Double sourceCD = Double.valueOf(snappedLine._2.getAttribute("CD").toString());
                    for (int geomIndex = 0; geomIndex < source.getNumGeometries(); geomIndex++) {
                        Geometry partGeom = source.getGeometryN(geomIndex);
                        for (int coordinateIndex = 0; coordinateIndex < partGeom.getCoordinates().length - 1; coordinateIndex++) {
                            LineString partLine = gf.createLineString(
                                    new Coordinate[]{
                                            partGeom.getCoordinates()[coordinateIndex],
                                            partGeom.getCoordinates()[coordinateIndex + 1]}
                            );
                            //检查最小线单元是否重复，插入结果集合
                            if (!partLine.isEmpty()) {
                                boolean isExist = false;
                                for (LineString ls : partLineCollection) {
                                    //忽略节点顺序的equals方法
                                    if (partLine.equalsNorm(ls)) {
                                        isExist = true;
                                        break;
                                    }
                                }
                                if (!isExist) {
                                    partLineCollection.add(partLine);
                                    //创建MultiLineString对象
                                    MultiPolyline mls = new MultiPolyline();
                                    Double ratio = partLine.getLength() / source.getLength();
                                    org.locationtech.jts.geom.MultiLineString multiLineString = gf.createMultiLineString(new LineString[]{partLine});
                                    mls.setGeometry(multiLineString);
                                    LinkedHashMap<Field, Object> attrs = new LinkedHashMap<>(snappedLine._2.getAttributes());
                                    String bsm_xz = snappedLine._2.getAttribute("BSM").toString();
                                    Field field = snappedLine._2.getField("CD");
                                    Double partLength = sourceCD * ratio;
                                    attrs.put(field, partLength);
                                    Field field1 = snappedLine._2.getField("BSM");
                                    attrs.put(field1, bsm_xz + "_" + UUID.randomUUID().toString());
                                    Field field2 = new Field("YBSM");
                                    attrs.put(field2, bsm_xz);
                                    mls.setAttributes(attrs);
                                    result.add(new Tuple2<>(bsm_xz, mls));
                                }
                            }
                        }
                    }
                    return result.iterator();
                }
            });
//            MultiPolylineLayer layer3 = new MultiPolylineLayer(xz.rdd());
//            KeyIndexedLayer<MultiPolylineLayer> ll = si.index(layer3, false);
//            MultiPolylineLayer xzlayer = ll.getLayer();

//            JavaPairRDD<String,MultiPolyline> xzx = xzlayer.cogroup(inter2Layer).flatMapToPair(new PairFlatMapFunction<Tuple2<String, Tuple2<Iterable<MultiPolyline>, Iterable<MultiPolygon>>>, String, MultiPolyline>() {
//                @Override
//                public Iterator<Tuple2<String, MultiPolyline>> call(Tuple2<String, Tuple2<Iterable<MultiPolyline>, Iterable<MultiPolygon>>> in) throws Exception {
//                    List<Tuple2<String,MultiPolyline>> result = new ArrayList<>();
//                    for(MultiPolyline ml:in._2._1) {
//                        int flag = 0;
//                        MultiPolyline mll = new MultiPolyline(ml);
//                        String ybsm = ml.getAttribute("YBSM").toString();
//                        String bsm = ml.getAttribute("BSM").toString();
//                        Double cd = Double.valueOf(ml.getAttribute("CD").toString());
//                        Geometry xw = mll.getGeometry();
//                        GeometryFactory gf = new GeometryFactory();
//                        for (MultiPolygon mp : in._2._2) {
//                            String bsm_23 = mp.getAttribute("BSM_23").toString();
//
//                            if(mp.getGeometry().intersects(xw)&&!xw.within(mp.getGeometry())){
//                                Double ylength = xw.getLength();
//                                Geometry temp = mp.getGeometry().intersection(xw);
//                                for(int i=0;i<temp.getNumGeometries();i++){
//                                    Geometry geo = temp.getGeometryN(i);
//                                    if(geo.getLength()>0){
//                                        flag = 1;
//                                        Double xlength = geo.getLength();
//                                        MultiPolyline mlx = new MultiPolyline(mll);
//                                        if (geo instanceof org.locationtech.jts.geom.LineString) {
//                                            mlx.setGeometry(new org.locationtech.jts.geom.MultiLineString(new LineString[]{(LineString) geo}, gf));
//                                        } else if (geo instanceof org.locationtech.jts.geom.MultiLineString) {
//                                            mlx.setGeometry((org.locationtech.jts.geom.MultiLineString) geo);
//                                        }
//
//                                        LinkedHashMap<Field,Object> attrs = new LinkedHashMap<>(mlx.getAttributes());
//                                        String xbsm = ybsm+UUID.randomUUID().toString();
//                                        Field field = mlx.getField("CD");
//                                        Field field1 = mlx.getField("BSM");
//                                        attrs.put(field,xlength/ylength*cd);
//                                        attrs.put(field1,xbsm);
//                                        mlx.setAttributes(attrs);
//                                        result.add(new Tuple2<>(xbsm+bsm_23,mlx));
//                                    }
//                                }
//                            }
//                        }
//                        if(flag == 0){
//                            result.add(new Tuple2<>(bsm,mll));
//                        }
//
//                    }
//                    return result.iterator();
//                }
//            }).reduceByKey(new Function2<MultiPolyline, MultiPolyline, MultiPolyline>() {
//                @Override
//                public MultiPolyline call(MultiPolyline multiPolyline, MultiPolyline multiPolyline2) throws Exception {
//                    return multiPolyline;
//                }
//            });


            JavaPairRDD<String,MultiPolyline> kctbbh = xz.flatMapToPair(new PairFlatMapFunction<Tuple2<String, MultiPolyline>, String, MultiPolyline>() {
                @Override
                public Iterator<Tuple2<String, MultiPolyline>> call(Tuple2<String, MultiPolyline> in) throws Exception {
                    List<Tuple2<String,MultiPolyline>> result = new ArrayList<>();
                    MultiPolyline ml = in._2;
                    String kctbbh1 = ml.getAttribute("KCTBBH1").toString();
                    String kctbbh2 = ml.getAttribute("KCTBBH2").toString();
                    String kcdwdm1 = ml.getAttribute("KCTBDWDM1").toString().substring(0, 12);
                    String kcdwdm2 = ml.getAttribute("KCTBDWDM2").toString();
                    //KCTBDWDM2 可能为null，不能直接取9位
                    result.add(new Tuple2<>(kcdwdm1+"_"+kctbbh1,ml));
                    int flag = 1;
                    if (kctbbh2 == null || kctbbh2.equals("") || kctbbh2.equals(" ")) {
                        kctbbh2 = "";
                        flag =0;
                    }
                    if (kcdwdm2 == null || kcdwdm2.equals("") || kcdwdm2.equals(" ")) {
                        kcdwdm2 = "";
                        flag =0;
                    } else {
                        kcdwdm2 = kcdwdm2.substring(0, 12);
                    }
                    if(flag==1){
                        result.add(new Tuple2<>(kcdwdm2+"_"+kctbbh2,ml));
                    }
                    return result.iterator();
                }
            });

            JavaPairRDD<String,MultiPolygon> mztbbh = inter2.mapToPair(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    MultiPolygon mp = in._2;
                    String zldwdm_9 = mp.getAttribute("ZLDWDM_9").toString();
                    String tbbh = mp.getAttribute("TBBH_2").toString();
                    return new Tuple2<>(zldwdm_9+"_"+tbbh,mp);
                }
            });



//            FileWriter writer2 = new FileWriter(this.arg.getGeomWriterConfig()+"xz");
//            List<Tuple2<String, MultiPolyline>> temp_list2 = xz.collect();
//            for (int i = 0; i < temp_list2.size(); i++) {
//                writer2.write(temp_list2.get(i)._1 + "#" + Double.valueOf(temp_list2.get(i)._2.getAttribute("CD").toString())*Double.valueOf(temp_list2.get(i)._2.getAttribute("KD").toString()) + "#" +temp_list2.get(i)._2.getAttribute("BSM").toString()+ "#" + temp_list2.get(i)._2.getGeometry().toText() + "\n");
//            }
//            writer2.close();

            JavaPairRDD<String,MultiPolyline> rebh = kctbbh.cogroup(mztbbh).flatMapToPair(new PairFlatMapFunction<Tuple2<String, Tuple2<Iterable<MultiPolyline>, Iterable<MultiPolygon>>>, String, MultiPolyline>() {
                @Override
                public Iterator<Tuple2<String, MultiPolyline>> call(Tuple2<String, Tuple2<Iterable<MultiPolyline>, Iterable<MultiPolygon>>> in) throws Exception {
                    List<Tuple2<String,MultiPolyline>> result = new ArrayList<>();
                    for(MultiPolyline ml:in._2._1) {
                        MultiPolyline mll = new MultiPolyline(ml);
                        int flag = 0;
                        String kctbbh1 = ml.getAttribute("KCTBBH1").toString();
                        String kcdwdm1 = ml.getAttribute("KCTBDWDM1").toString().substring(0,12);
                        String kctbbh2 = ml.getAttribute("KCTBBH2").toString();
                        String kcdwdm2 = ml.getAttribute("KCTBDWDM2").toString();
                        String ybsm = ml.getAttribute("YBSM").toString();
                        String bsm = ml.getAttribute("BSM").toString();
                        Double cd = Double.valueOf(ml.getAttribute("CD").toString());
                        Double kd = Double.valueOf(ml.getAttribute("KD").toString());
                        if(kcdwdm2.length()>12){
                            kcdwdm2 = kcdwdm2.substring(0,12);
                        }
                        Geometry xw = mll.getGeometry();

                        for (MultiPolygon mp : in._2._2) {

//                            if(mp.getGeometry().intersects(xw)&&!xw.within(mp.getGeometry())){
//                                Double ylength = xw.getLength();
//                                Geometry temp = mp.getGeometry().intersection(xw);
//                                for(int i=0;i<temp.getNumGeometries();i++){
//                                    Geometry geo = temp.getGeometryN(i);
//                                    if(geo.getLength()>0){
//
//                                    }
//                                }
//                                if(temp.getLength()>0){
//                                    xw = temp;
//                                    Double xlength = xw.getLength();
//                                    ratio = xlength/ylength;
//                                }
//                            }


                            String bsm_23 = mp.getAttribute("BSM_23").toString();
                            org.locationtech.jts.geom.Point point = ml.getGeometry().getCentroid();
                            if (mp.getGeometry().intersects(point.buffer(0.01))) {
                                LinkedHashMap<Field,Object> attrs = new LinkedHashMap<>(ml.getAttributes());
                                if(in._1.equals(kcdwdm1+"_"+kctbbh1)){
                                    Field field = ml.getField("KCBSM1");
                                    attrs.put(field,bsm_23);
                                    if(ybsm.equals("37357")){
                                        System.out.println(bsm_23+bsm+"  kc1 线物面积" +cd*kd  + point.toText());
                                    }
                                }else if(in._1.equals(kcdwdm2+"_"+kctbbh2)){
                                    Field field = ml.getField("KCBSM2");
                                    attrs.put(field,bsm_23);
                                    if(ybsm.equals("37357")){
                                        System.out.println(bsm_23+"  kc2线物面积" +cd*kd);
                                    }
                                }
                                mll.setAttributes(attrs);

                            }
                        }
                        result.add(new Tuple2<>(in._1,mll));
                    }
                    return result.iterator();
                }
            });




            MultiPolylineLayer mpl = new MultiPolylineLayer(rebh.rdd());
            KeyIndexedLayer<MultiPolylineLayer> mpl_index = si.index(mpl);
            MultiPolylineLayer layer1 = mpl_index.getLayer();





            JavaPairRDD<String, MultiPolygon> inter3 = inter2Layer.leftOuterJoin(layer1).mapToPair(new PairFunction<Tuple2<String, Tuple2<MultiPolygon, Optional<MultiPolyline>>>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, Tuple2<MultiPolygon, Optional<MultiPolyline>>> in) throws Exception {
                    MultiPolygon mpp = new MultiPolygon(in._2._1);
                    String bsm_23 = mpp.getAttribute("BSM_23").toString();
                    String tbbh2 = mpp.getAttribute("TBBH_2").toString();
                    String zldwdm_9 = mpp.getAttribute("ZLDWDM_9").toString();
                    if (in._2._2.isPresent()) {
                        //面积过滤全狭长
                        Geometry geometry = mpp.getGeometry();

//                        Envelope envelope = geometry.getEnvelopeInternal();
//                        Double width = Math.max(envelope.getWidth(),envelope.getHeight());
//                        Double height = Math.min(envelope.getWidth(),envelope.getHeight());

//                        if (geometry.getArea() < 0.01 && height/width<0.01 ) {
//                            log.error("面积阈值 Intersection 面: " + bsm_23 + "面积大小："+ geometry.getArea());
//                            log.error("狭长面 Intersection 面: " + bsm_23 + "长宽比："+ height/width);
//                            return new Tuple2<>(bsm_23, mpp);
//                        }
//
//                        if (geometry.getArea() < 0.01 && height/width<0.01 ) {
//                            log.error("面积阈值 Intersection 面: " + bsm_23 + "面积大小："+ geometry.getArea());
//                            log.error("狭长面 Intersection 面: " + bsm_23 + "长宽比："+ height/width);
//                            return new Tuple2<>(bsm_23, mpp);
//                        }

//                        if (height/width<0.01) {
//                            log.error("狭长面 Intersection 面: " + bsm_23 + "长宽比："+ height/width);
//                            return new Tuple2<>(bsm_23, mpp);
//                        }
                        //过滤部分狭长
//                        if(bsm_23.startsWith("330106211000024200_341245_0")){
//                            System.out.println(bsm_23+geometry.toText());
//                        }
//
//                        GeometrySnapper geometrySnapper = new GeometrySnapper(geometry);
//                        geometry = geometrySnapper.snapToSelf(0.001, true);
//                        if(bsm_23.startsWith("330106211000024200_341245_0")){
//                            System.out.println("snap"+bsm_23+geometry.toText());
//                        }
//                        geometry = geometry.norm();
//                        if(bsm_23.startsWith("330106211000024200_341245_0")){
//                            System.out.println("norm"+bsm_23+geometry.toText());
//                        }

//                        if(bsm_23.equals("330102211000000591_340_1")){
//                            log.error("330102211000001176_341_4 " + bsm_23 + "面积大小："+ geometry.getArea());
//                            log.error("330102211000001176_341_4 " + bsm_23 + "长宽比："+ height/width);
//                        }
//                        GeometryFactory gf = new GeometryFactory();
//                        if (geometry instanceof org.locationtech.jts.geom.Polygon) {
//                            mpp.setGeometry(new org.locationtech.jts.geom.MultiPolygon(new Polygon[]{(Polygon) geometry}, gf));
//                        } else if (geometry instanceof org.locationtech.jts.geom.MultiPolygon) {
//                            mpp.setGeometry((org.locationtech.jts.geom.MultiPolygon) geometry);
//                        }





                        MultiPolyline ml = in._2._2.get();
                        Geometry xw = ml.getGeometry();
//                        String kctbbh1 = ml.getAttribute("KCTBBH1").toString();
//                        String kctbbh2 = ml.getAttribute("KCTBBH2").toString();
                        String kcbsm1 = ml.getAttribute("KCBSM1").toString();
                        String kcbsm2 = ml.getAttribute("KCBSM2").toString();


//                        String kcdwdm1 = ml.getAttribute("KCTBDWDM1").toString().substring(0, 9);
//                        String kcdwdm2 = ml.getAttribute("KCTBDWDM2").toString();
                        //KCTBDWDM2 可能为null，不能直接取9位
//                        if (kctbbh2 == null) {
//                            kctbbh2 = "";
//                        }
//                        if (kcdwdm2 == null || kcdwdm2.equals("")) {
//                            kcdwdm2 = "";
//                        } else {
//                            kcdwdm2 = kcdwdm2.substring(0, 9);
//                        }

                        if (kcbsm1.equals(bsm_23) || kcbsm2.equals(bsm_23)) {
                            Double ratio = 1.0;
//                            if(mpp.getGeometry().intersects(xw)&&!xw.within(mpp.getGeometry())){
//                                Double ylength = xw.getLength();
//                                Geometry temp = mpp.getGeometry().intersection(xw);
//                                if(temp.getLength()>0){
//                                    xw = temp;
//                                    Double xlength = xw.getLength();
//                                    ratio = xlength/ylength;
//                                }
//                            }

//                            org.locationtech.jts.geom.Point point = xw.getCentroid();
                            //if (mpp.getGeometry().intersects(point.buffer(0.01))) {
                                Double kcbl = Double.valueOf(ml.getAttribute("KCBL").toString());
//                                if (mpp.getGeometry().getBoundary().buffer(0.01).contains(ml.getGeometry()) && kcbl == 1) {
//                                    kcbl = 0.5;
//                                }
                                Double xwmj = Double.valueOf(mpp.getAttribute("XWMJ").toString());
                                Double tbdlmj = Double.valueOf(mpp.getAttribute("TBDLMJ").toString());
                                Double cd = Double.valueOf(ml.getAttribute("CD").toString());
                                Double kd = Double.valueOf(ml.getAttribute("KD").toString());
                                String dlbm = ml.getAttribute("DLBM").toString();
                                String bsm = ml.getAttribute("BSM").toString();
                                String ybsm = ml.getAttribute("YBSM").toString();
                                if(ybsm.equals("37357")){
                                    System.out.println(bsm_23+bsm+"test37357");
                                }
                                List<Tuple4<String, Double, Geometry, String>> list = new ArrayList<>();
                                Double area = cd * kd * kcbl * ratio;
                                xwmj += area;
                                tbdlmj -= area;
                                Tuple4<String, Double, Geometry, String> tu = new Tuple4<>(dlbm, area, ml.getGeometry(), bsm);
                                list.add(tu);
                                Field field1 = mpp.getField("XWMJ_LIST");
                                Field field2 = mpp.getField("XWMJ");
                                Field field3 = mpp.getField("TBDLMJ");
                                LinkedHashMap<Field, Object> attrs = mpp.getAttributes();
                                attrs.put(field1, list);
                                attrs.put(field2, xwmj);
                                attrs.put(field3, tbdlmj);
                                mpp.setAttributes(attrs);
                                return new Tuple2<>(bsm_23 + "_" + bsm, mpp);
                            }
                        }

                    return new Tuple2<>(bsm_23, mpp);
                }
            }).reduceByKey(new Function2<MultiPolygon, MultiPolygon, MultiPolygon>() {
                @Override
                public MultiPolygon call(MultiPolygon multiPolygon, MultiPolygon multiPolygon2) throws Exception {
                    return multiPolygon;
                }
            }).mapToPair(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    String bsm_23 = in._2.getAttribute("BSM_23").toString();
                    List<Tuple4<String, Double, Geometry, String>> list = (List<Tuple4<String, Double, Geometry, String>>) in._2.getAttribute("XWMJ_LIST");
                    return new Tuple2<>(bsm_23, in._2);
                }
            }).groupByKey().mapToPair(new PairFunction<Tuple2<String, Iterable<MultiPolygon>>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, Iterable<MultiPolygon>> in) throws Exception {
                    MultiPolygon mp = new MultiPolygon();
                    int flag = 0;
                    Double toarea = 0.0;
                    Double tbdlmj = 0.0;
                    List<Tuple4<String, Double, Geometry, String>> list = new ArrayList<>();
                    for (MultiPolygon multiPolygon : in._2) {
                        if (flag == 0) {
                            mp = new MultiPolygon(multiPolygon);
                            toarea = Double.valueOf(mp.getAttribute("XWMJ").toString());
                            tbdlmj = Double.valueOf(mp.getAttribute("TBDLMJ").toString());
                            list = (List<Tuple4<String, Double, Geometry, String>>) mp.getAttribute("XWMJ_LIST");
                            flag = 1;
                            continue;
                        }
                        Double toarea2 = Double.valueOf(multiPolygon.getAttribute("XWMJ").toString());
                        List<Tuple4<String, Double, Geometry, String>> list2 = (List<Tuple4<String, Double, Geometry, String>>) multiPolygon.getAttribute("XWMJ_LIST");
                        if (list2 != null) {
                            for (int j = 0; j < list2.size(); j++) {
                                int flag1 = 0;
                                for (int i = 0; i < list.size(); i++) {
                                    if (list.get(i)._4().equals(list2.get(j)._4())) {
                                        flag1 = 1;
                                        continue;
                                    }
                                }
                                if (flag1 == 0) {
                                    list.add(list2.get(j));
                                    toarea += list2.get(j)._2();
                                    tbdlmj -= list2.get(j)._2();
                                }
                            }
                        }


                    }
                    Field field1 = mp.getField("XWMJ_LIST");
                    Field field2 = mp.getField("XWMJ");
                    Field field3 = mp.getField("TBDLMJ");
                    LinkedHashMap<Field, Object> attrs = new LinkedHashMap<>(mp.getAttributes());
                    attrs.put(field1, list);
                    attrs.put(field2, toarea);
                    attrs.put(field3, tbdlmj);
                    mp.setAttributes(attrs);
                    return new Tuple2<>(in._1, mp);
                }
            });

            JavaPairRDD<String, Geometry> flow = inter3.flatMapToPair(new PairFlatMapFunction<Tuple2<String, MultiPolygon>, String, Geometry>() {
                @Override
                public Iterator<Tuple2<String, Geometry>> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    MultiPolygon inter = in._2;
                    List<Tuple2<String, Geometry>> result = new ArrayList<>();
                    Double tkxs_3 = Double.valueOf(inter.getAttribute("TKXS_3").toString());
                    Double tkxs_2 = Double.valueOf(inter.getAttribute("TKXS_2").toString());
                    String dlbm_3 = String.valueOf(inter.getAttribute("DLBM_3").toString());
                    String dlbm_2 = String.valueOf(inter.getAttribute("DLBM_2").toString());
                    String zldwdm_3 = String.valueOf(inter.getAttribute("ZLDWDM_3").toString());
                    String dlbz_2 = String.valueOf(inter.getAttribute("DLBZ_2").toString());
                    String zzsxdm_3 = String.valueOf(inter.getAttribute("ZZSXDM_3").toString());
                    if (zzsxdm_3 == null || zzsxdm_3.equals("") || zzsxdm_3.equals("null") || zzsxdm_3.equals("NULL")) {
                        zzsxdm_3 = EMPTY_STRING;
                    }
                    if (dlbz_2 == null || dlbz_2.equals("") || dlbz_2.equals("null") || dlbz_2.equals("NULL")) {
                        dlbz_2 = EMPTY_STRING;
                    }

                    Double tbmj_3 = Double.valueOf(inter.getAttribute("TBMJ").toString());
                    Double flowArea = tbmj_3;
                    Double tbdlmj_2 = Double.valueOf(inter.getAttribute("TBDLMJ").toString());
                    String kc = "";
                    Tuple2<String, Geometry> flow = new Tuple2<>("", null);
                    List<Tuple4<String, Double, Geometry, String>> list_xw = (List<Tuple4<String, Double, Geometry, String>>) inter.getAttribute("XWMJ_LIST");
                    List<Tuple4<String, Double, Geometry, String>> list_lw = (List<Tuple4<String, Double, Geometry, String>>) inter.getAttribute("LWMJ_LIST");
                    Double flowmj_2 = tbdlmj_2 ;

                    if (dlbm_2.startsWith("01")) {
                        Double kcmj_2 = tbdlmj_2 * tkxs_2;
                        flowmj_2 = tbdlmj_2 - kcmj_2;
                        tbdlmj_2 -= kcmj_2;
                        if (dlbm_3.startsWith("01")) {
                            Double kcmj_3 = tbmj_3 * tkxs_3;
                            if (kcmj_2 >= kcmj_3) {
                                kc = String.format("123##1203##%s##%.9f##%s##%s", zldwdm_3, kcmj_3, EMPTY_STRING, EMPTY_STRING);
                                flow = new Tuple2<>(kc, null);
                                result.add(flow);
                                kc = String.format("123##%s##%s##%.9f##%s##%s", dlbm_3, zldwdm_3, kcmj_2 - kcmj_3, zzsxdm_3, EMPTY_STRING);
                                flow = new Tuple2<>(kc, null);
                                result.add(flow);
                                flowArea = flowArea - kcmj_2 ;
                                if (list_xw != null) {
                                    for (int i = 0; i < list_xw.size(); i++) {
                                        kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2(), list_xw.get(i)._4().split("_")[0] , "polyline");
                                        flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                        result.add(flow);
                                        flowArea = flowArea - list_xw.get(i)._2() ;
                                    }
                                }
                                if (list_lw != null) {
                                    for (int i = 0; i < list_lw.size(); i++) {
                                        kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2(), zzsxdm_3, "point");
                                        flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                        result.add(flow);
                                        flowArea = flowArea - list_lw.get(i)._2() ;
                                    }
                                }
                                kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2, zzsxdm_3, dlbz_2);
                                flow = new Tuple2<>(kc, inter.getGeometry());
                                result.add(flow);
                            } else if (kcmj_2 < kcmj_3) {
                                kc = String.format("123##1203##%s##%.9f##%s##%s", zldwdm_3,  kcmj_2, EMPTY_STRING, EMPTY_STRING);
                                flow = new Tuple2<>(kc, null);
                                result.add(flow);
                                Double kcarea = kcmj_3 - kcmj_2;
                                flowArea = flowArea - kcarea ;
                                if (list_xw != null) {
                                    for (int i = 0; i < list_xw.size(); i++) {

                                        if(kcarea>0) {
                                            if(kcarea >=list_xw.get(i)._2() * tkxs_3){
                                                kc = String.format("%s##1203##%s##%.9f##%s##%s", list_xw.get(i)._1(), zldwdm_3, list_xw.get(i)._2() * tkxs_3, list_xw.get(i)._4().split("_")[0], "polyline");
                                                flow = new Tuple2<>(kc, null);
                                                result.add(flow);
                                                kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2() * (1 - tkxs_3), list_xw.get(i)._4().split("_")[0], "polyline");
                                                flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                                result.add(flow);
                                                kcarea -= list_xw.get(i)._2() * tkxs_3;
                                                flowArea = flowArea - list_xw.get(i)._2() ;
                                            }else{
                                                kc = String.format("%s##1203##%s##%.9f##%s##%s", list_xw.get(i)._1(), zldwdm_3, kcarea, list_xw.get(i)._4().split("_")[0], "polyline");
                                                flow = new Tuple2<>(kc, null);
                                                result.add(flow);
                                                kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2() -kcarea, list_xw.get(i)._4().split("_")[0], "polyline");
                                                flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                                result.add(flow);
                                                kcarea -= list_xw.get(i)._2() * tkxs_3;
                                                flowArea = flowArea - list_xw.get(i)._2() ;
                                            }
                                        }
                                        else{
                                            kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2(), list_xw.get(i)._4().split("_")[0], "polyline");
                                            flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                            result.add(flow);
                                            flowArea = flowArea - list_xw.get(i)._2() ;
                                        }
                                    }
                                }
                                if (list_lw != null) {
                                    for (int i = 0; i < list_lw.size(); i++) {
                                        if(kcarea>0){
                                            if(kcarea >=list_lw.get(i)._2() * tkxs_3){
                                                kc = String.format("%s##1203##%s##%.9f##%s##%s", list_lw.get(i)._1(), zldwdm_3, list_lw.get(i)._2() * tkxs_3, EMPTY_STRING, "point");
                                                flow = new Tuple2<>(kc, null);
                                                result.add(flow);
                                                kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2() * (1 - tkxs_3), zzsxdm_3, "point");
                                                flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                                result.add(flow);
                                                kcarea -= list_lw.get(i)._2() * tkxs_3;
                                                flowArea = flowArea - list_lw.get(i)._2() ;
                                            }else{
                                                kc = String.format("%s##1203##%s##%.9f##%s##%s", list_lw.get(i)._1(), zldwdm_3, kcarea, EMPTY_STRING, "point");
                                                flow = new Tuple2<>(kc, null);
                                                result.add(flow);
                                                kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2() - kcarea, zzsxdm_3, "point");
                                                flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                                result.add(flow);
                                                kcarea -= list_lw.get(i)._2() * tkxs_3;
                                                flowArea = flowArea - list_lw.get(i)._2() ;
                                            }

                                        }
                                        else {
                                            kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2() , zzsxdm_3, "point");
                                            flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                            result.add(flow);
                                            flowArea = flowArea - list_lw.get(i)._2() ;
                                        }

                                    }
                                }
                                if(kcarea>0){//全留kcarea
                                    if(kcarea >=flowArea * tkxs_3){
                                        kc = String.format("%s##1203##%s##%.9f##%s##%s", dlbm_2, zldwdm_3, flowmj_2 * tkxs_3, EMPTY_STRING, dlbz_2);
                                        flow = new Tuple2<>(kc, null);
                                        result.add(flow);
                                        kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2 * (1 - tkxs_3), zzsxdm_3, dlbz_2);
                                        flow = new Tuple2<>(kc, inter.getGeometry());
                                        result.add(flow);
                                        kcarea -= flowArea * tkxs_3;
                                    }else{
                                        kc = String.format("%s##1203##%s##%.9f##%s##%s", dlbm_2, zldwdm_3, kcarea, EMPTY_STRING, dlbz_2);
                                        flow = new Tuple2<>(kc, null);
                                        result.add(flow);
                                        kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2 -kcarea, zzsxdm_3, dlbz_2);
                                        flow = new Tuple2<>(kc, inter.getGeometry());
                                        result.add(flow);
                                        kcarea -= flowArea * tkxs_3;
                                    }

                                }else {
                                    kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2 , zzsxdm_3, dlbz_2);
                                    flow = new Tuple2<>(kc, inter.getGeometry());
                                    result.add(flow);
                                }

                            }

                        } else if (!dlbm_3.startsWith("01")) {
                            kc = String.format("123##%s##%s##%.9f##%s##%s", dlbm_3, zldwdm_3, kcmj_2, zzsxdm_3, EMPTY_STRING);
                            flow = new Tuple2<>(kc, null);
                            result.add(flow);
                            flowArea = flowArea - kcmj_2 ;
                            if (list_xw != null) {
                                for (int i = 0; i < list_xw.size(); i++) {
                                    kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2(), list_xw.get(i)._4().split("_")[0] , "polyline");
                                    flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                    result.add(flow);
                                    flowArea = flowArea - list_xw.get(i)._2() ;
                                }
                            }
                            if (list_lw != null) {
                                for (int i = 0; i < list_lw.size(); i++) {
                                    kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2(), zzsxdm_3, "point");
                                    flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                    result.add(flow);
                                    flowArea = flowArea - list_lw.get(i)._2() ;
                                }
                            }
                            kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2, zzsxdm_3, dlbz_2);
                            flow = new Tuple2<>(kc, inter.getGeometry());
                            result.add(flow);
                        }

                    } else if (!dlbm_2.startsWith("01")) {
                        if (dlbm_3.startsWith("01")) {
                            Double kcarea = tbmj_3 * tkxs_3;
                            if (list_xw != null) {
                                for (int i = 0; i < list_xw.size(); i++) {
                                    if(kcarea>0) {
                                        if(kcarea>=list_xw.get(i)._2() * tkxs_3){
                                            kc = String.format("%s##1203##%s##%.9f##%s##%s", list_xw.get(i)._1(), zldwdm_3, list_xw.get(i)._2() * tkxs_3, list_xw.get(i)._4().split("_")[0], "polyline");
                                            flow = new Tuple2<>(kc, null);
                                            result.add(flow);
                                            kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2() * (1 - tkxs_3), list_xw.get(i)._4().split("_")[0], "polyline");
                                            flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                            result.add(flow);
                                            kcarea -=  list_xw.get(i)._2() * tkxs_3;
                                            flowArea = flowArea - list_xw.get(i)._2() ;
                                        }else{
                                            kc = String.format("%s##1203##%s##%.9f##%s##%s", list_xw.get(i)._1(), zldwdm_3, kcarea, list_xw.get(i)._4().split("_")[0], "polyline");
                                            flow = new Tuple2<>(kc, null);
                                            result.add(flow);
                                            kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2() - kcarea, list_xw.get(i)._4().split("_")[0], "polyline");
                                            flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                            result.add(flow);
                                            kcarea -=  list_xw.get(i)._2() * tkxs_3;
                                            flowArea = flowArea - list_xw.get(i)._2() ;
                                        }

                                    }else{
                                        kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2() , list_xw.get(i)._4().split("_")[0], "polyline");
                                        flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                        result.add(flow);
                                        flowArea = flowArea - list_xw.get(i)._2() ;
                                    }
                                }
                            }
                            if (list_lw != null) {
                                for (int i = 0; i < list_lw.size(); i++) {
                                    if(kcarea>0){
                                        if(kcarea>=list_lw.get(i)._2() * tkxs_3){
                                            kc = String.format("%s##1203##%s##%.9f##%s##%s", list_lw.get(i)._1(), zldwdm_3, list_lw.get(i)._2() * tkxs_3, EMPTY_STRING, "point");
                                            flow = new Tuple2<>(kc, null);
                                            result.add(flow);
                                            kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2() * (1 - tkxs_3), zzsxdm_3, "point");
                                            flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                            result.add(flow);
                                            kcarea -= list_lw.get(i)._2() * tkxs_3;
                                            flowArea = flowArea - list_lw.get(i)._2() ;
                                        }else{
                                            kc = String.format("%s##1203##%s##%.9f##%s##%s", list_lw.get(i)._1(), zldwdm_3, kcarea, EMPTY_STRING, "point");
                                            flow = new Tuple2<>(kc, null);
                                            result.add(flow);
                                            kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2() - kcarea, zzsxdm_3, "point");
                                            flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                            result.add(flow);
                                            kcarea -= list_lw.get(i)._2() * tkxs_3;
                                            flowArea = flowArea - list_lw.get(i)._2() ;
                                        }

                                    }else{
                                        kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2() , zzsxdm_3, "point");
                                        flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                        result.add(flow);
                                        flowArea = flowArea - list_lw.get(i)._2() ;
                                    }
                                }
                            }
                            if(kcarea>0){
                                if(kcarea>flowArea*tkxs_3){
                                    kc = String.format("%s##1203##%s##%.9f##%s##%s", dlbm_2, zldwdm_3, flowmj_2 * tkxs_3, EMPTY_STRING, dlbz_2);
                                    flow = new Tuple2<>(kc, null);
                                    result.add(flow);
                                    kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2 * (1 - tkxs_3), zzsxdm_3, dlbz_2);
                                    flow = new Tuple2<>(kc, inter.getGeometry());
                                    result.add(flow);
                                }else{
                                    kc = String.format("%s##1203##%s##%.9f##%s##%s", dlbm_2, zldwdm_3, kcarea, EMPTY_STRING, dlbz_2);
                                    flow = new Tuple2<>(kc, null);
                                    result.add(flow);
                                    kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2 - kcarea, zzsxdm_3, dlbz_2);
                                    flow = new Tuple2<>(kc, inter.getGeometry());
                                    result.add(flow);
                                }

                            }else{
                                kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2 , zzsxdm_3, dlbz_2);
                                flow = new Tuple2<>(kc, inter.getGeometry());
                                result.add(flow);
                            }

                        } else if (!dlbm_3.startsWith("01")) {
                            if (list_xw != null) {
                                for (int i = 0; i < list_xw.size(); i++) {
                                    kc = String.format("%s##%s##%s##%.9f##%s##%s", list_xw.get(i)._1(), dlbm_3, zldwdm_3, list_xw.get(i)._2(), list_xw.get(i)._4().split("_")[0] , "polyline");
                                    flow = new Tuple2<>(kc, list_xw.get(i)._3());
                                    result.add(flow);
                                    flowArea = flowArea - list_xw.get(i)._2() ;
                                }
                            }
                            if (list_lw != null) {
                                for (int i = 0; i < list_lw.size(); i++) {
                                    kc = String.format("%s##%s##%s##%.9f##%s##%s", list_lw.get(i)._1(), dlbm_3, zldwdm_3, list_lw.get(i)._2(), zzsxdm_3, "point");
                                    flow = new Tuple2<>(kc, list_lw.get(i)._3());
                                    result.add(flow);
                                    flowArea = flowArea - list_lw.get(i)._2() ;
                                }
                            }
                            kc = String.format("%s##%s##%s##%.9f##%s##%s", dlbm_2, dlbm_3, zldwdm_3, flowmj_2, zzsxdm_3, dlbz_2);
                            flow = new Tuple2<>(kc, inter.getGeometry());
                            result.add(flow);
                        }
                    }
                    return result.iterator();
                }
            });


            String fileName = this.arg.getGeomWriterConfig();
            FileWriter writer = new FileWriter(fileName);
            List<Tuple2<String, Geometry>> temp_list3 = flow.collect();
            for (int i = 0; i < temp_list3.size(); i++) {
                if(temp_list3.get(i)._1.split("##")[5].equals("polyline")) {
                    writer.write(temp_list3.get(i)._1  + "\n");//+ "#" + temp_list3.get(i)._2.toText()
                }
            }
            writer.close();
            JavaPairRDD<String,Geometry> flow_all = flow;
            if(flow_lmx!=null){
                flow_all = flow.union(flow_lmx);
            }

            // 写出统计结果
            JavaPairRDD<String, Double> flowAreaRDD = flow_all.mapToPair(new PairFunction<Tuple2<String, Geometry>, String, Double>() {
                @Override
                public Tuple2<String, Double> call(Tuple2<String, Geometry> in) throws Exception {
                    String[] fs = in._1.split("##");
                    String key = String.format("%s##%s##%s##%s##%s", fs[0], fs[1], fs[2], fs[4], fs[5]);
                    Double v = Double.valueOf(fs[3]);
                    return new Tuple2<>(key, v);
                }
            }).reduceByKey(new Function2<Double, Double, Double>() {
                @Override
                public Double call(Double v1, Double v2) throws Exception {
                    return v1 + v2;
                }
            });

            flowAreaRDD.cache();

            JavaRDD<Tuple2<String, LinkedHashMap<Field, Object>>> statResult = flowAreaRDD.map(x -> {
                LinkedHashMap<Field, Object> r = new LinkedHashMap<>();
                Field f = new Field("mj");
                f.setType(java.lang.Double.class);
                String[] fs = x._1.split("##");
                if (fs[3].equals(EMPTY_STRING) || fs[3] == null || fs[3].equals("null")) {
                    fs[3] = "";
                }
                if (fs[4].equals(EMPTY_STRING) || fs[4] == null || fs[4].equals("null")) {
                    fs[4] = "";
                }
                Field f2 = new Field("qdlbm");
                f2.setType(java.lang.String.class);
                Field f3 = new Field("hdlbm");
                f3.setType(java.lang.String.class);
                Field f4 = new Field("zldwdm");
                f4.setType(java.lang.String.class);
                Field f5 = new Field("dlbz");
                f5.setType(java.lang.String.class);
                Field f6 = new Field("zzsxdm");
                f6.setType(java.lang.String.class);
                r.put(f, x._2);
                r.put(f2, fs[0]);
                r.put(f3, fs[1]);
                r.put(f4, fs[2]);
                r.put(f5, fs[4]);
                r.put(f6, fs[3]);
                return new Tuple2<>(x._1, r);
            });

            StatLayer statLayer = new StatLayer(statResult);
            Field f = new Field("mj");
            f.setType(Double.class);
            statLayer.getMetadata().addAttribute(f, null);
            Field f2 = new Field("qdlbm");
            f2.setType(String.class);
            statLayer.getMetadata().addAttribute(f2, null);
            Field f3 = new Field("hdlbm");
            f3.setType(String.class);
            statLayer.getMetadata().addAttribute(f3, null);
            Field f4 = new Field("zldwdm");
            f4.setType(String.class);
            statLayer.getMetadata().addAttribute(f4, null);
            Field f5 = new Field("dlbz");
            f5.setType(String.class);
            statLayer.getMetadata().addAttribute(f5, null);
            Field f6 = new Field("zzsxdm");
            f6.setType(String.class);
            statLayer.getMetadata().addAttribute(f6, null);


            JavaPairRDD<String, Double> flowAreaRDDK = flowAreaRDD.mapToPair(new PairFunction<Tuple2<String, Double>, String, Double>() {
                @Override
                public Tuple2<String, Double> call(Tuple2<String, Double> in) throws Exception {
                    String[] fs = in._1.split("##");
                    if (fs[4].equals(EMPTY_STRING) || fs[4] == null || fs[4].equals("null")) {
                        fs[4] = "";
                    }
                    String dlbm = fs[0];
                    if (fs[4].equals("K") || fs[4].equals("k")) {
                        dlbm = dlbm + "k";
                    }
                    String key = String.format("%s##%s##%s", dlbm, fs[1], fs[2]);
                    Double v = in._2;
                    return new Tuple2<>(key, v);
                }
            }).reduceByKey((x1, x2) -> x1 + x2);
            JavaRDD<Tuple2<String, LinkedHashMap<Field, Object>>> statResultK = flowAreaRDDK.map(x -> {
                LinkedHashMap<Field, Object> r = new LinkedHashMap<>();
                Field fk = new Field("mj");
                fk.setType(java.lang.Double.class);
                String[] fs = x._1.split("##");
                Field f2k = new Field("qdlbm");
                f2k.setType(java.lang.String.class);
                Field f3k = new Field("hdlbm");
                f3k.setType(java.lang.String.class);
                Field f4k = new Field("zldwdm");
                f4k.setType(java.lang.String.class);
                r.put(fk, x._2);
                r.put(f2k, fs[0]);
                r.put(f3k, fs[1]);
                r.put(f4k, fs[2]);
                return new Tuple2<>(x._1, r);
            });

            StatLayer statLayerK = new StatLayer(statResultK);
            Field fk = new Field("mj");
            fk.setType(Double.class);
            statLayerK.getMetadata().addAttribute(fk, null);
            Field f2k = new Field("qdlbm");
            f2k.setType(String.class);
            statLayerK.getMetadata().addAttribute(f2k, null);
            Field f3k = new Field("hdlbm");
            f3k.setType(String.class);
            statLayerK.getMetadata().addAttribute(f3k, null);
            Field f4k = new Field("zldwdm");
            f4k.setType(String.class);
            statLayerK.getMetadata().addAttribute(f4k, null);


            LayerWriter statWriterK = LayerFactory.getWriter(ss, statsWriterConfig);
            statLayerK.inferFieldMetadata();
            statWriterK.write(statLayerK);

            PgLayerWriterConfig statsWriterConfigALL = (PgLayerWriterConfig) statsWriterConfig;
            statsWriterConfigALL.setTablename(statsWriterConfigALL.getTablename() + "_all");
            LayerWriter statWriter = LayerFactory.getWriter(ss, statsWriterConfigALL);
            statLayer.inferFieldMetadata();
            statWriter.write(statLayer);

        } catch (Exception e) {
            log.error("Run error:" + e.getMessage());
            logBuilder.append("计算失败！" + e.getMessage());
        } finally {
            // pgHelper.close();
        }
    }

    public JavaPairRDD<String, Geometry> lmx(String xzqdm , MultiPolygonLayer xzq3d ,MultiPolygonLayer xzq2d , MultiPolygonLayer tb3d ,MultiPolygonLayer tb2d ,MultiPolylineLayer xw2d ,MultiPointLayer lw2d) throws Exception{
//        try
//        {
        String s = "330206##330211##330212##330213##330225##330226##330281##330282##330303##330305##330326##330327##330381##330382##330424##330482##330902##330903##330921##330922##331002##331004##331022##331081##331082##331083";
        List<String> xzqs = Arrays.asList(s.split("##"));
        if(xzqs.contains(xzqdm))
        {
//            JavaPairRDD<String,Geometry> tb3dunion = tb3d.groupByKey().mapToPair(new PairFunction<Tuple2<String, Iterable<MultiPolygon>>, String, Geometry>() {
//                @Override
//                public Tuple2<String, Geometry> call(Tuple2<String, Iterable<MultiPolygon>> in) throws Exception {
//                    Geometry geometry = null;
//                    int flag = 0;
//                    for(MultiPolygon mp:in._2){
//                        if(flag==0){
//                            geometry = mp.getGeometry();
//                            flag = 1;
//                            continue;
//                        }
//                        //geometry = org.locationtech.jts.operation.union.CascadedPolygonUnion.union(geometry,mp.getGeometry());
//                        geometry = geometry.union(mp.getGeometry());
//
//                    }
//                    return new Tuple2<>("123",geometry);
//                }
//            }).groupByKey().mapToPair(new PairFunction<Tuple2<String, Iterable<Geometry>>, String, Geometry>() {
//                @Override
//                public Tuple2<String, Geometry> call(Tuple2<String, Iterable<Geometry>> in) throws Exception {
//                    Geometry geometry = null;
//                    int flag = 0;
//                    for(Geometry mp:in._2){
//                        if(flag==0){
//                            geometry = mp;
//                            flag = 1;
//                            continue;
//                        }
//                        geometry = geometry.union(mp);
//
//
//
//                    }
//                    return new Tuple2<>("123",geometry);
//                }
//            });
//
//            Map<String,Geometry> mp3d = tb3dunion.collectAsMap();
//            Geometry union3 = mp3d.get("123");
//
//            JavaPairRDD<String,Geometry> tb2dunion = tb2d.groupByKey().mapToPair(new PairFunction<Tuple2<String, Iterable<MultiPolygon>>, String, Geometry>() {
//                @Override
//                public Tuple2<String, Geometry> call(Tuple2<String, Iterable<MultiPolygon>> in) throws Exception {
//                    Geometry geometry = null;
//                    int flag = 0;
//                    for(MultiPolygon mp:in._2){
//                        if(flag==0){
//                            geometry = mp.getGeometry();
//                            flag = 1;
//                            continue;
//                        }
//                        geometry = geometry.union(mp.getGeometry());
//                    }
//                    return new Tuple2<>("123",geometry);
//                }
//            }).groupByKey().mapToPair(new PairFunction<Tuple2<String, Iterable<Geometry>>, String, Geometry>() {
//                @Override
//                public Tuple2<String, Geometry> call(Tuple2<String, Iterable<Geometry>> in) throws Exception {
//                    Geometry geometry = null;
//                    int flag = 0;
//                    for(Geometry mp:in._2){
//                        if(flag==0){
//                            geometry = mp;
//                            flag = 1;
//                            continue;
//                        }
//                        geometry = geometry.union(mp);
//                    }
//                    return new Tuple2<>("123",geometry);
//                }
//            });
//
//            Map<String,Geometry> mp2d = tb2dunion.collectAsMap();
//            Geometry union2 = mp2d.get("123");

            JavaPairRDD<String,MultiPolygon> tb3dunion = xzq3d.mapToLayer(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    String bsm = in._2.getAttribute("BSM").toString();
                    Geometry geometry = in._2.getGeometry();
                    Feature feature = new Feature();
                    geometry = feature.validedGeom(geometry);
                    MultiPolygon mp = new MultiPolygon(in._2);
                    mp.setGeometry((org.locationtech.jts.geom.MultiPolygon)geometry);
                    return new Tuple2<>(bsm,mp);
                }
            }).reduceByKey(new Function2<MultiPolygon, MultiPolygon, MultiPolygon>() {
                @Override
                public MultiPolygon call(MultiPolygon multiPolygon, MultiPolygon multiPolygon2) throws Exception {
                    return multiPolygon;
                }
            });




            List<Tuple2<String,MultiPolygon>> list_tb3d = tb3dunion.collect();
            Geometry[] list_3 = new Geometry[list_tb3d.size()];
            for(int i=0;i<list_tb3d.size();i++){
                list_3[i]=(list_tb3d.get(i)._2.getGeometry());
            }
            GeometryFactory gf = new GeometryFactory();
            GeometryCollection geometryCollection3 = new GeometryCollection(list_3,gf);
            Geometry union3g = geometryCollection3.union();
            Feature feature = new Feature();
//            GeometryFactory gf = new GeometryFactory();
//                        if (union3 instanceof org.locationtech.jts.geom.Polygon) {
//                            mpp.setGeometry(new org.locationtech.jts.geom.MultiPolygon(new Polygon[]{(Polygon) geometry}, gf));
//                        } else if (geometry instanceof org.locationtech.jts.geom.MultiPolygon) {
//                            mpp.setGeometry((org.locationtech.jts.geom.MultiPolygon) geometry);
//                        }
            Geometry union3s = feature.validedGeom(union3g);


            Geometry lineString = union3s.getBoundary().getGeometryN(0);
            org.locationtech.jts.geom.Polygon union3 =  gf.createPolygon((LinearRing) lineString);
            JavaPairRDD<String,MultiPolygon> tb2dunion = xzq2d.mapToLayer(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    String bsm = in._2.getAttribute("BSM").toString();
                    Geometry geometry = in._2.getGeometry();
                    Feature feature1 = new Feature();
                    geometry = feature1.validedGeom(geometry);
                    MultiPolygon mp = new MultiPolygon(in._2);
                    mp.setGeometry((org.locationtech.jts.geom.MultiPolygon)geometry);
                    return new Tuple2<>(bsm,mp);
                }
            }).reduceByKey(new Function2<MultiPolygon, MultiPolygon, MultiPolygon>() {
                @Override
                public MultiPolygon call(MultiPolygon multiPolygon, MultiPolygon multiPolygon2) throws Exception {
                    return multiPolygon;
                }
            });


            List<Tuple2<String,MultiPolygon>> list_tb2d = tb2dunion.collect();
            Geometry[] list_2 = new Geometry[list_tb2d.size()];
            for(int i=0;i<list_tb2d.size();i++){
                list_2[i]=(list_tb2d.get(i)._2.getGeometry());
            }
           // Geometry union2g = org.locationtech.jts.operation.union.CascadedPolygonUnion.union(list_2);
            GeometryCollection geometryCollection = new GeometryCollection(list_2,gf);
            Geometry union2g = geometryCollection.union();

            Geometry union2s = feature.validedGeom(union2g);

            Geometry lineString2 = union2s.getBoundary().getGeometryN(0);
            org.locationtech.jts.geom.Polygon union2 =  gf.createPolygon((LinearRing) lineString2);

            JavaPairRDD<String,MultiPolygon> tb3ddiff = tb3d.mapToPair(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    MultiPolygon mp = in._2;
                    Geometry geometry = mp.getGeometry().difference(union2);
                    if(geometry.isEmpty()){
                        return new Tuple2<>("EMPTY",null);
                    }
                    Double tbmj = Double.valueOf(mp.getAttribute("TBMJ").toString());
                    String bsm = mp.getAttribute("BSM").toString();
                    Double yarea = mp.getGeometry().getArea();
                    Double xarea = geometry.getArea();
                    Double xtbmj = xarea/yarea*tbmj;
                    LinkedHashMap<Field,Object> attrs = new LinkedHashMap<>(mp.getAttributes());
                    Field field = mp.getField("TBMJ");
                    attrs.put(field,xtbmj);
                    MultiPolygon mpp = new MultiPolygon(mp);
                    mpp.setGeometry((org.locationtech.jts.geom.MultiPolygon)geometry);
                    mpp.setAttributes(attrs);
                    return new Tuple2<>(bsm,mpp);
                }
            }).filter(new Function<Tuple2<String, MultiPolygon>, Boolean>() {
                @Override
                public Boolean call(Tuple2<String, MultiPolygon> in) throws Exception {
                    if(in._1.equals("EMPTY")){
                        return false;
                    }
                    return true;
                }
            }).reduceByKey(new Function2<MultiPolygon, MultiPolygon, MultiPolygon>() {
                @Override
                public MultiPolygon call(MultiPolygon multiPolygon, MultiPolygon multiPolygon2) throws Exception {
                    return multiPolygon;
                }
            });

            JavaPairRDD<String,MultiPolygon> tb2ddiff = tb2d.mapToPair(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    MultiPolygon mp = in._2;
                    Geometry geometry = mp.getGeometry().difference(union3);
                    if(geometry.isEmpty()){
                        return new Tuple2<>("EMPTY",null);
                    }
                    Double tbmj = Double.valueOf(mp.getAttribute("TBMJ").toString());
                    String bsm = mp.getAttribute("TBMJ").toString();
                    Double yarea = mp.getGeometry().getArea();
                    Double xarea = geometry.getArea();
                    Double xtbmj = xarea/yarea*tbmj;
                    LinkedHashMap<Field,Object> attrs = new LinkedHashMap<>(mp.getAttributes());
                    Field field = mp.getField("TBMJ");
                    attrs.put(field,xtbmj);
                    MultiPolygon mpp = new MultiPolygon(mp);
                    mpp.setGeometry((org.locationtech.jts.geom.MultiPolygon)geometry);
                    mpp.setAttributes(attrs);
                    return new Tuple2<>(bsm,mpp);
                }
            }).filter(new Function<Tuple2<String, MultiPolygon>, Boolean>() {
                @Override
                public Boolean call(Tuple2<String, MultiPolygon> in) throws Exception {
                    if(in._1.equals("EMPTY")){
                        return false;
                    }
                    return true;
                }
            }).reduceByKey(new Function2<MultiPolygon, MultiPolygon, MultiPolygon>() {
                @Override
                public MultiPolygon call(MultiPolygon multiPolygon, MultiPolygon multiPolygon2) throws Exception {
                    return multiPolygon;
                }
            }).mapToPair(new PairFunction<Tuple2<String, MultiPolygon>, String, MultiPolygon>() {
                @Override
                public Tuple2<String, MultiPolygon> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    return new Tuple2<>("123",in._2);
                }
            });

            JavaPairRDD<String,MultiPolyline> xw2ddiff = xw2d.mapToLayer(new PairFunction<Tuple2<String, MultiPolyline>, String, MultiPolyline>() {
                @Override
                public Tuple2<String, MultiPolyline> call(Tuple2<String, MultiPolyline> in) throws Exception {
                    MultiPolyline ml = in._2;
                    Geometry geometry = ml.getGeometry().difference(union3);
                    if(geometry.isEmpty()){
                        return new Tuple2<>("EMPTY",null);
                    }
                    Double cd = Double.valueOf(ml.getAttribute("CD").toString());
                    String bsm = ml.getAttribute("BSM").toString();
                    Double ylength = ml.getGeometry().getLength();
                    Double xlenhth = geometry.getLength();
                    Double xcd = xlenhth/ylength*cd;
                    LinkedHashMap<Field,Object> attrs = new LinkedHashMap<>(ml.getAttributes());
                    Field field = ml.getField("CD");
                    attrs.put(field,xcd);
                    MultiPolyline mll = new MultiPolyline(ml);
                    mll.setGeometry((org.locationtech.jts.geom.MultiLineString)geometry);
                    mll.setAttributes(attrs);
                    return new Tuple2<>(bsm,mll);
                }
            }).filter(new Function<Tuple2<String, MultiPolyline>, Boolean>() {
                @Override
                public Boolean call(Tuple2<String, MultiPolyline> in) throws Exception {
                    if(in._1.equals("EMPTY")){
                        return false;
                    }
                    return true;
                }
            }).reduceByKey(new Function2<MultiPolyline, MultiPolyline, MultiPolyline>() {
                @Override
                public MultiPolyline call(MultiPolyline multiPolyline, MultiPolyline multiPolyline2) throws Exception {
                    return multiPolyline;
                }
            }).mapToPair(new PairFunction<Tuple2<String, MultiPolyline>, String, MultiPolyline>() {
                @Override
                public Tuple2<String, MultiPolyline> call(Tuple2<String, MultiPolyline> in) throws Exception {
                    return new Tuple2<>("123",in._2);
                }
            });

            JavaPairRDD<String,MultiPoint> lw2ddiff = lw2d.mapToPair(new PairFunction<Tuple2<String, MultiPoint>, String, MultiPoint>() {
                @Override
                public Tuple2<String, MultiPoint> call(Tuple2<String, MultiPoint> in) throws Exception {
                    MultiPoint mp = in._2;
                    Geometry geometry = mp.getGeometry().difference(union3);
                    if(geometry.isEmpty()){
                        return new Tuple2<>("EMPTY",null);
                    }
                    MultiPoint mpp = new MultiPoint(mp);
                    String bsm = mp.getAttribute("BSM").toString();
                    return new Tuple2<>(bsm,mpp);
                }
            }).filter(new Function<Tuple2<String, MultiPoint>, Boolean>() {
                @Override
                public Boolean call(Tuple2<String, MultiPoint> in) throws Exception {
                    if(in._1.equals("EMPTY")){
                        return false;
                    }
                    return true;
                }
            }).reduceByKey(new Function2<MultiPoint, MultiPoint, MultiPoint>() {
                @Override
                public MultiPoint call(MultiPoint multiPoint, MultiPoint multiPoint2) throws Exception {
                    return multiPoint;
                }
            }).mapToPair(new PairFunction<Tuple2<String, MultiPoint>, String, MultiPoint>() {
                @Override
                public Tuple2<String, MultiPoint> call(Tuple2<String, MultiPoint> in) throws Exception {
                    return new Tuple2<>("123",in._2);
                }
            });

            JavaPairRDD<String, Geometry> flow3d = tb3ddiff.flatMapToPair(new PairFlatMapFunction<Tuple2<String, MultiPolygon>, String, Geometry>() {
                @Override
                public Iterator<Tuple2<String, Geometry>> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    MultiPolygon inter = in._2;
                    List<Tuple2<String, Geometry>> result = new ArrayList<>();
                    Double tkxs = Double.valueOf(inter.getAttribute("TKXS").toString());
                    String dlbm = String.valueOf(inter.getAttribute("DLBM").toString());
                    String zldwdm = String.valueOf(inter.getAttribute("ZLDWDM").toString());
                    String zzsxdm = String.valueOf(inter.getAttribute("ZZSXDM").toString());
                    if (zzsxdm == null || zzsxdm.equals("") || zzsxdm.equals("null") || zzsxdm.equals("NULL")) {
                        zzsxdm = EMPTY_STRING;
                    }
                    Double tbmj = Double.valueOf(inter.getAttribute("TBMJ").toString());

                    String kc = "";
                    Tuple2<String, Geometry> flow = new Tuple2<>("", null);
                    if(tkxs>0){
                        kc = String.format("-##1203##%s##%.9f##%s##%s", zldwdm, tbmj*tkxs, EMPTY_STRING, EMPTY_STRING);
                        flow = new Tuple2<>(kc,null);
                        result.add(flow);
                    }
                    kc = String.format("-##%s##%s##%.9f##%s##%s",dlbm, zldwdm, tbmj*(1-tkxs), zzsxdm, EMPTY_STRING);
                    flow = new Tuple2<>(kc,in._2.getGeometry());
                    result.add(flow);
                    return result.iterator();
                }
            });

            JavaPairRDD<String,MultiPolygon> tb2ddiff_xw = tb2ddiff.cogroup(xw2ddiff).flatMapToPair(new PairFlatMapFunction<Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPolyline>>>, String, MultiPolygon>() {
                @Override
                public Iterator<Tuple2<String, MultiPolygon>> call(Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPolyline>>> in) throws Exception {
                    List<Tuple2<String,MultiPolygon>> result = new ArrayList<>();
                    for(MultiPolygon mp : in._2._1){
                        MultiPolygon mpp = new MultiPolygon(mp);
                        String zldwdm = mpp.getAttribute("ZLDWDM").toString().substring(0,12);
                        String tbbh = mpp.getAttribute("TBBH").toString();
                        Double tbmj = Double.valueOf(mpp.getAttribute("TBMJ").toString());
                        Double tbdlmj = tbmj;
                        for(MultiPolyline ml : in._2._2){
                            String kctbdwdm1 = ml.getAttribute("KCTBDWDM1").toString().substring(0,12);
                            String kctbdwdm2 = ml.getAttribute("KCTBDWDM2").toString();
                            String kctbbh1 = ml.getAttribute("KCTBBH1").toString();
                            String kctbbh2 = ml.getAttribute("KCTBBH2").toString();
                            if(kctbdwdm2==null){
                                kctbdwdm2 = "";
                            }
                            if(kctbbh2==null){
                                kctbbh2 = "";
                            }
                            if(kctbdwdm2.length()>12){
                                kctbdwdm2 = kctbdwdm2.substring(0,12);
                            }
                            if((zldwdm.equals(kctbdwdm1)&&tbbh.equals(kctbbh1)) || (zldwdm.equals(kctbdwdm2)&&tbbh.equals(kctbbh2))){
                                Double cd = Double.valueOf(ml.getAttribute("CD").toString());
                                Double kd = Double.valueOf(ml.getAttribute("KD").toString());
                                Double kcbl = Double.valueOf(ml.getAttribute("KCBL").toString());
                                tbdlmj -= cd*kd*kcbl;
                            }
                        }

                        LinkedHashMap<Field,Object> attrs = new LinkedHashMap<>(mpp.getAttributes());
                        Field field = mpp.getField("TBDLMJ");
                        attrs.put(field,tbdlmj);
                        mpp.setAttributes(attrs);
                        result.add(new Tuple2<>(in._1,mpp));
                    }
                    return result.iterator();
                }
            });

            JavaPairRDD<String,MultiPolygon> tb2ddiff_xwlw = tb2ddiff_xw.cogroup(lw2ddiff).flatMapToPair(new PairFlatMapFunction<Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPoint>>>, String, MultiPolygon>() {
                @Override
                public Iterator<Tuple2<String, MultiPolygon>> call(Tuple2<String, Tuple2<Iterable<MultiPolygon>, Iterable<MultiPoint>>> in) throws Exception {
                    List<Tuple2<String,MultiPolygon>> result = new ArrayList<>();
                    for(MultiPolygon mp : in._2._1){
                        MultiPolygon mpp = new MultiPolygon(mp);
                        String zldwdm = mpp.getAttribute("ZLDWDM").toString().substring(0,12);
                        String tbbh = mpp.getAttribute("TBBH").toString();
                        Double tbdlmj = Double.valueOf(mpp.getAttribute("TBDLMJ").toString());
                        for(MultiPoint ml : in._2._2){
                            String zltbdwdm = ml.getAttribute("ZLTBDWDM").toString().substring(0,12);
                            String zltbbh = ml.getAttribute("ZLTBBH").toString();
                            if(zldwdm.equals(zltbdwdm)&&tbbh.equals(zltbbh)){
                                Double mj = Double.valueOf(ml.getAttribute("MJ").toString());
                                tbdlmj -= mj;
                            }
                        }
                        LinkedHashMap<Field,Object> attrs = new LinkedHashMap<>(mpp.getAttributes());
                        Field field = mpp.getField("TBDLMJ");
                        attrs.put(field,tbdlmj);
                        mpp.setAttributes(attrs);
                        result.add(new Tuple2<>(in._1,mpp));
                    }
                    return result.iterator();
                }
            });
            JavaPairRDD<String, Geometry> flow2dxw = xw2ddiff.flatMapToPair(new PairFlatMapFunction<Tuple2<String, MultiPolyline>, String, Geometry>() {
                @Override
                public Iterator<Tuple2<String, Geometry>> call(Tuple2<String, MultiPolyline> in) throws Exception {
                    MultiPolyline inter = in._2;
                    List<Tuple2<String, Geometry>> result = new ArrayList<>();
                    String dlbm = String.valueOf(inter.getAttribute("DLBM").toString());
                    String zldwdm = String.valueOf(inter.getAttribute("ZLDWDM").toString());
                    Double cd = Double.valueOf(inter.getAttribute("CD").toString());
                    Double kd = Double.valueOf(inter.getAttribute("KD").toString());
                    String kc = "";
                    Tuple2<String, Geometry> flow = new Tuple2<>("", null);
                    kc = String.format("%s##-##%s##%.9f##%s##%s",dlbm, zldwdm, cd*kd, EMPTY_STRING, EMPTY_STRING);
                    flow = new Tuple2<>(kc,in._2.getGeometry());
                    result.add(flow);
                    return result.iterator();
                }
            });

            JavaPairRDD<String, Geometry> flow2dlw = lw2ddiff.flatMapToPair(new PairFlatMapFunction<Tuple2<String, MultiPoint>, String, Geometry>() {
                @Override
                public Iterator<Tuple2<String, Geometry>> call(Tuple2<String, MultiPoint> in) throws Exception {
                    MultiPoint inter = in._2;
                    List<Tuple2<String, Geometry>> result = new ArrayList<>();
                    String dlbm = String.valueOf(inter.getAttribute("DLBM").toString());
                    String zldwdm = String.valueOf(inter.getAttribute("ZLDWDM").toString());
                    Double mj = Double.valueOf(inter.getAttribute("MJ").toString());
                    String kc = "";
                    Tuple2<String, Geometry> flow = new Tuple2<>("", null);
                    kc = String.format("%s##-##%s##%.9f##%s##%s",dlbm, zldwdm, mj, EMPTY_STRING, EMPTY_STRING);
                    flow = new Tuple2<>(kc,in._2.getGeometry());
                    result.add(flow);
                    return result.iterator();
                }
            });

            JavaPairRDD<String, Geometry> flow2d = tb2ddiff_xwlw.flatMapToPair(new PairFlatMapFunction<Tuple2<String, MultiPolygon>, String, Geometry>() {
                @Override
                public Iterator<Tuple2<String, Geometry>> call(Tuple2<String, MultiPolygon> in) throws Exception {
                    MultiPolygon inter = in._2;
                    List<Tuple2<String, Geometry>> result = new ArrayList<>();
                    String dlbm = String.valueOf(inter.getAttribute("DLBM").toString());
                    String zldwdm = String.valueOf(inter.getAttribute("ZLDWDM").toString());
                    String dlbz = String.valueOf(inter.getAttribute("DLBZ").toString());
                    Double tbdlmj = Double.valueOf(inter.getAttribute("TBDLMJ").toString());
                    Double tkxs = Double.valueOf(inter.getAttribute("TKXS").toString());
                    String kc = "";
                    Tuple2<String, Geometry> flow = new Tuple2<>("", null);

                    if(tkxs>0){
                        kc = String.format("123##-##%s##%.9f##%s##%s", zldwdm, tbdlmj*tkxs, EMPTY_STRING, EMPTY_STRING);
                        flow = new Tuple2<>(kc,null);
                        result.add(flow);
                    }
                    kc = String.format("%s##-##%s##%.9f##%s##%s",dlbm, zldwdm, tbdlmj*(1-tkxs), EMPTY_STRING, dlbz);
                    flow = new Tuple2<>(kc,in._2.getGeometry());
                    result.add(flow);
                    return result.iterator();
                }
            });
            JavaPairRDD<String,Geometry> flow = flow3d.union(flow2d).union(flow2dlw).union(flow2dxw);
            return flow;
        }
        return null;
    }

//    catch (Exception e) {
//            log.error("Run error:" + e.getMessage());
//        } finally {
//            // pgHelper.close();
//        }
//        return null;




    public static void main(String[] args) throws Exception {
        LandFlowAnalysisNew analysis = new LandFlowAnalysisNew(SparkSessionType.LOCAL, args);
        analysis.exec();
    }

}
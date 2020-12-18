package edu.zju.gis.hls.gisspark.example;

import lombok.extern.slf4j.Slf4j;
import org.geotools.geometry.jts.GeometryClipper;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;

import java.lang.management.MemoryUsage;

@Slf4j
public class IntersectionTest {

    public static void main(String[] args) {
        WKTReader2 wktReader = new WKTReader2();
        MultiPolygon polygon2D = null;
        MultiPolygon polygon3D = null;
        Polygon polygonError = null;
        try {
            polygon2D = (MultiPolygon) wktReader.read(polygon2D_WKT);
            polygon3D = (MultiPolygon) wktReader.read(polygon3D_WKT);
            polygonError = (Polygon) wktReader.read(errorIntersection_WKT);
        } catch (ParseException e) {
            e.printStackTrace();
            log.error("********************************** WKT错误 **********************************");
        }
        Geometry intersection = polygon2D.intersection(polygon3D);
        GeometrySnapper geometrySnapper = new GeometrySnapper(polygonError);
        Geometry snapSelf = geometrySnapper.snapToSelf(0.001,true);
        Geometry removeCollinear = JTS.removeCollinearVertices(polygonError,4);
        boolean theSame = snapSelf.equals(polygonError);
        int lose = polygonError.getNumPoints()-snapSelf.getNumPoints();
        log.info("snap wkt: " + snapSelf.toText());
        log.info("collinear wkt: " + removeCollinear.toText());

    }

    private final static String polygon2D_WKT = "MULTIPOLYGON (((40510132.6069458 3330055.52429928,40510137.9438213 3330030.73366693,40510132.6037857 3329863.70036898,40510133.4853301 3329859.5587724,40510115.862569 3329846.57342761,40510140.8724287 3329752.41497101,40510145.286221 3329647.95875466,40510152.6428256 3329600.88013252,40510162.4182149 3329589.53935324,40510168.3213686 3329523.7082624,40510158.9490943 3329517.61634344,40510160.2621504 3329515.3481708,40510160.7740865 3329495.88062547,40510196.6638249 3329479.92854271,40510213.0086074 3329482.38029621,40510296.3671533 3329516.70468512,40510382.1834075 3329521.19877801,40510427.3934709 3329515.36505303,40510447.8109334 3329509.53177436,40510465.6162362 3329490.61687349,40510455.3786749 3329468.32417371,40510443.5046545 3329424.60413325,40510435.4087435 3329382.50303301,40510435.4088183 3329366.31031134,40510441.9727055 3329358.64914527,40510452.2319363 3329338.41878911,40510456.2415447 3329323.38274772,40510480.3011528 3329183.54642298,40510487.3179707 3329146.45739702,40510491.3273026 3329115.38335293,40510496.8401437 3329084.8105247,40510502.3539166 3329039.70271233,40510503.3568336 3329010.13186007,40510503.365134 3329010.08925957,40510503.3733345 3329010.04675908,40510509.8730939 3328976.55133155,40510514.7482782 3328959.38267626,40510521.4013298 3328935.95346665,40510521.4015373 3328925.92930077,40510520.3991695 3328922.92225421,40510503.8509016 3328919.91343604,40510498.3466121 3328918.9126209,40510467.7737544 3328914.90305848,40510452.7346788 3328912.58956654,40510448.2271862 3328911.89613451,40510399.6113348 3328897.86347353,40510370.0408937 3328892.34999279,40510299.3721631 3328873.30444828,40510212.6658979 3328854.26026216,40510144.0887397 3328840.92557321,40510140.4939471 3328840.22667281,40510076.2775524 3328830.16444938,40510073.3337573 3328829.70315774,40510032.235848 3328821.18271665,40509996.2592173 3328814.6807658,40509990.636728 3328813.66464943,40509913.4528136 3328796.1240323,40509852.2361425 3328784.07272621,40509849.8010476 3328783.59331074,40509772.6170847 3328770.56380705,40509735.9329462 3328764.74823364,40509731.5191538 3328764.04838665,40509689.4188717 3328753.02154064,40509664.4823354 3328747.02192534,40509628.3954271 3328738.34032609,40509622.7600415 3328736.98458235,40509622.7599038 3328740.49298069,40509629.7764645 3328753.52388067,40509644.3105806 3328780.08686655,40509665.8643886 3328835.34614025,40509671.8750233 3328850.75633442,40509677.3877703 3328874.31326833,40509683.4019496 3328895.86501095,40509693.4261177 3328926.93892003,40509705.4552423 3328972.04616741,40509711.469509 3328990.08911665,40509717.9846835 3329011.1394953,40509729.5115073 3329046.22374619,40509750.0594949 3329103.36091564,40509759.5816837 3329132.43027522,40509767.600151 3329163.50423888,40509778.1255793 3329196.0818765,40509789.6535582 3329244.19709903,40509792.1595819 3329261.23789861,40509796.6695524 3329301.33352906,40509798.1722401 3329354.46032153,40509795.1649902 3329363.48178545,40509790.6542623 3329386.03562742,40509790.1530474 3329426.63250457,40509783.136474 3329453.19605461,40509773.613847 3329473.74554056,40509763.0886394 3329489.28314398,40509754.0670239 3329497.80378497,40509707.4555404 3329514.84481116,40509671.8704486 3329523.36528395,40509643.8032725 3329530.38261756,40509627.2637558 3329531.88667236,40509561.6070654 3329530.88459099,40509546.612781 3329529.4333402,40509546.0699815 3329529.38083507,40509519.5066351 3329524.36851203,40509503.4684834 3329519.85755135,40509479.5716409 3329514.49269775,40509478.9099424 3329514.3441907,40509455.8549854 3329510.33424552,40509440.7927128 3329507.76258527,40509435.3060228 3329506.82569717,40509424.2797815 3329501.3129043,40509411.2487079 3329498.80726536,40509397.7164504 3329494.79814101,40509382.680725 3329487.78165957,40509368.6472729 3329483.2712493,40509353.1102102 3329479.76313767,40509334.5661121 3329470.24014327,40509314.0172034 3329461.71952691,40509283.9456644 3329446.68341145,40509259.3871078 3329434.15305379,40509238.3369356 3329426.13373184,40509195.2345349 3329412.09971379,40509157.9791946 3329397.06474984,40509141.9409515 3329391.71909531,40509111.8693866 3329379.02277747,40509093.8264654 3329371.67178749,40509084.0275608 3329362.76068648,40509080.4941113 3329358.04925074,40509058.7039362 3329335.66978974,40509046.9253536 3329325.65799081,40509029.2575816 3329316.23511975,40509009.8230205 3329307.40101531,40508973.3097062 3329295.62212915,40508935.6185128 3329285.02188406,40508839.6238097 3329257.34369089,40508747.1632496 3329234.96351205,40508683.5598128 3329219.65191699,40508661.769769 3329214.3522213,40508645.642906 3329210.86596829,40508655.8610422 3329346.68763797,40508656.1108923 3329350.06796634,40508657.4277014 3329367.89137247,40508658.6729208 3329384.74797822,40508660.4848811 3329397.78959178,40508661.2147248 3329403.04279724,40508689.2057241 3329412.44289292,40508690.4133111 3329413.65070532,40508691.5014014 3329405.21872075,40508691.971002 3329395.82872999,40508703.2390227 3329384.56095575,40508703.4699265 3329384.21475841,40508707.3588264 3329384.21480006,40508713.6259297 3329383.90916733,40508723.8671988 3329386.81310527,40508730.1342581 3329390.63423035,40508737.3184583 3329390.63413195,40508747.8654471 3329391.7038402,40508754.4580538 3329391.1120584,40508778.3008867 3329397.43349696,40508779.4221467 3329391.82796867,40508828.5522601 3329399.9518121,40508843.6393813 3329407.30217044,40508842.3308323 3329411.88195438,40508862.111633 3329430.47625796,40508899.609022 3329440.83954067,40508922.9547322 3329458.55549722,40508936.4872941 3329462.11674061,40508959.2789408 3329467.10252426,40508962.127834 3329477.07384084,40508969.2501961 3329480.63474241,40508975.6604802 3329472.79996916,40509039.0496074 3329498.43884387,40509041.8983853 3329509.83476929,40509041.1861166 3329516.2450588,40509055.4308479 3329522.65530855,40509074.6613479 3329522.65551458,40509086.0573861 3329519.09453826,40509101.0143778 3329510.54770231,40509122.1384916 3329518.58008227,40509199.4834783 3329549.13671042,40509212.8516039 3329539.5876456,40509220.0652811 3329528.97901343,40509238.9750589 3329531.10906935,40509255.5130393 3329532.97285372,40509395.7423655 3329593.82670078,40509396.7969479 3329595.09231783,40509508.1913229 3329728.76305963,40509548.1108702 3329789.71681151,40509546.5535293 3329793.53969314,40509543.9150398 3329801.89436112,40509546.9931599 3329819.48373747,40509556.2277408 3329834.87436592,40509572.9375815 3329848.06631712,40509576.0158662 3329858.18009025,40509590.5273884 3329886.76233953,40509600.6412367 3329900.39382709,40509623.5063973 3329942.69575298,40509644.61296 3329970.83846665,40509647.6911223 3329983.15089432,40509652.9677883 3330009.97427099,40509653.8470808 3330029.32265844,40509650.3292523 3330031.96099653,40509642.8540267 3330015.69089671,40509633.1801352 3330005.57698543,40509627.7682663 3330002.68078781,40509617.4320625 3329986.68376865,40509559.2441804 3329897.29343338,40509512.7001701 3329841.93111557,40509503.196849 3329835.2981816,40509499.9945937 3329832.26816143,40509503.0208425 3329840.41522298,40509511.3757467 3329846.13186392,40509524.5678836 3329864.60081104,40509536.8805404 3329878.67222303,40509564.1439519 3329919.91850446,40509587.4490899 3329953.77684464,40509616.9102271 3329996.87015856,40509653.6733455 3330051.25546736,40509656.356892 3330046.92639952,40509665.4141729 3330048.72131769,40509683.7514343 3330052.35495008,40509873.314737 3330089.92032281,40510112.759996 3330058.15668568,40510132.6069458 3330055.52429928)))";
    private final static String polygon3D_WKT = "MULTIPOLYGON (((40510819.4068 3330922.8535,40510798.0325 3330904.8396,40510580.4127 3330660.762,40510578.9896 3330659.4183,40510524.7898 3330608.2433,40510448.8023 3330521.6204,40510410.166 3330472.4747,40510312.0053 3330342.2995,40510234.7469 3330215.8284,40510146.6404 3330058.6656,40510144.2592 3330048.6114,40510144.4249 3330044.237,40510145.47915 3329938.96135,40510145.8679 3329900.1445,40510146.4657 3329840.4406,40510146.9419 3329808.0556,40510148.8469 3329801.0706,40510156.9432 3329769.1617,40510162.0232 3329751.6992,40510165.0395 3329727.4104,40510167.0667 3329706.4044,40510172.3051 3329659.9377,40510180.2426 3329637.1835,40510196.1176 3329618.398,40510263.8511 3329573.4188,40510309.6241 3329543.2562,40510319.6783 3329542.4625,40510331.0554 3329547.4896,40510435.0368 3329605.698,40510451.9702 3329615.7522,40510517.0578 3329689.3065,40510548.8079 3329728.2003,40510568.0168 3329729.6592,40510583.8357 3329732.8232,40510626.384 3329731.3163,40510706.3192 3329728.4844,40510755.0081 3329725.9499,40510893.9283 3329717.3827,40510949.7555 3329712.6202,40511028.6015 3329706.7993,40511067.3425 3329704.4836,40511065.4857 3329700.1506,40511063.0541 3329694.4766,40511062.5925 3329693.3995,40511039.4826 3329693.697,40511022.6326 3329693.9141,40511020.6725 3329693.9393,40510995.7241 3329694.2605,40510978.7531 3329694.479,40510936.8329 3329695.0186,40510918.8283 3329695.7753,40510891.8989 3329696.907,40510846.9647 3329698.7964,40510802.0306 3329700.6864,40510757.0965 3329702.5756,40510718.7743 3329705.454,40510680.4522 3329708.3336,40510643.1434 3329711.1371,40510642.1301 3329711.2132,40510632.9544 3329709.0541,40510614.6029 3329695.0201,40510614.5964 3329695.0156,40510580.8666 3329696.0534,40510554.4083 3329675.945,40510498.3165 3329611.3866,40510459.1581 3329582.8115,40510402.008 3329543.6531,40510365.607 3329524.825,40510352.4341 3329524.662,40510338.4179 3329524.4885,40510319.7489 3329522.2025,40510300.0822 3329516.8993,40510284.0062 3329509.5954,40510162.4182 3329589.5394,40510152.6428 3329600.8801,40510145.2861 3329647.9588,40510140.8724 3329752.415,40510128.7838 3329797.9268,40510120.5864 3329828.7891,40510115.8626 3329846.5734,40510123.8116 3329852.4307,40510133.4852 3329859.5588,40510132.6038 3329863.7004,40510135.4542 3329952.8602,40510137.9437 3330030.7336,40510122.6388 3330045.4335,40510115.5675 3330052.2252,40510129.3259 3330074.4503,40510164.7854 3330110.4028,40510180.2457 3330139.0472,40510205.7346 3330186.2725,40510213.4978 3330200.656,40510266.7369 3330299.2959,40510300.7094 3330358.6685,40510316.5845 3330382.1635,40510356.5896 3330436.7736,40510368.8408 3330451.356,40510388.1801 3330474.3753,40510389.4311 3330475.8643,40510416.5518 3330508.1456,40510424.1042 3330517.1351,40510435.0122 3330530.1188,40510436.8688 3330532.3516,40510441.225 3330537.5905,40510511.5944 3330622.2186,40510517.9104 3330630.4569,40510573.7209 3330692.9415,40510621.6438 3330746.8251,40510637.5385 3330764.6967,40510707.3887 3330840.8968,40510771.5238 3330901.5394,40510804.806 3330937.5721,40510815.9847 3330926.3032,40510819.4068 3330922.8535)))";
    private final static String errorIntersection_WKT = "POLYGON ((40510135.4542 3329952.8602,40510137.9437 3330030.7336,40510122.6388 3330045.4335,40510115.5675 3330052.2252,40510118.747738 3330057.36250568,40510132.6069458 3330055.52429928,40510137.9438213 3330030.73366693,40510133.9253567 3329905.03836255,40510135.4542 3329952.8602))";
}

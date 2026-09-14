package io.turboio.enhanced;
import java.util.*;
public final class AmapNavigationTextTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  AmapNavigationText.Candidate c=AmapNavigationText.select("高德地图","前方 100 米右转","",Collections.emptyList(),"","","navigation",true);
  check(c!=null&&"前方 100 米右转".equals(c.content),"standard text guidance");
  c=AmapNavigationText.select("高德地图","","前方500米进入环岛",Collections.emptyList(),"","","",true);
  check(c!=null&&c.content.contains("环岛"),"big text guidance");
  c=AmapNavigationText.select("高德地图","","",Arrays.asList("沿当前道路直行","随后靠右"),"","","",true);
  check(c!=null&&"沿当前道路直行".equals(c.content),"text lines guidance");
  check(AmapNavigationText.select("高德地图","正在使用定位服务","",Collections.emptyList(),"","","",false)==null,"ignore generic location notice");
  check(AmapNavigationText.select("高德地图","优惠活动提醒","",Collections.emptyList(),"","","",false)==null,"ignore non-navigation AMap notice");
  check(AmapNavigationText.select("高德地图","离线地图下载中","",Collections.emptyList(),"","","",true)==null,"ignore unrelated ongoing notice");
  c=AmapNavigationText.select("高德地图正在导航","沿京藏高速向北行驶","",Collections.emptyList(),"","","",true);
  check(c!=null&&"沿京藏高速向北行驶".equals(c.content),"navigation context keeps road guidance");
  c=AmapNavigationText.select("高德地图","  前方\n100\u00a0米   左转  ","",Collections.emptyList(),"","","",false);
  check(c!=null&&"前方 100 米 左转".equals(c.content),"normalize whitespace");
  StringBuilder longText=new StringBuilder("直行");for(int i=0;i<600;i++)longText.append('路');
  c=AmapNavigationText.select("高德地图",longText.toString(),"",Collections.emptyList(),"","","",false);
  check(c!=null&&c.content.codePointCount(0,c.content.length())==480&&c.content.endsWith("…"),"bounded content");
  System.out.println("PASS "+checks+" AMap navigation notification text checks");
 }
}

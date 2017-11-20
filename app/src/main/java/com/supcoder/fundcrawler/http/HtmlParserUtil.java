package com.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class HtmlParserUtil {

    public static void main(String[] args) {

    }

    private HtmlParserUtil() {

    }

    private static HtmlParserUtil htmlParserUtil = new HtmlParserUtil();

    public static HtmlParserUtil getInstance() {
        synchronized (HtmlParserUtil.class) {
            if (htmlParserUtil == null) {
                htmlParserUtil = new HtmlParserUtil();
            }
            return htmlParserUtil;
        }
    }

    public List<ProcessMessege> queryProcessInfo2(String fundCode) {
        String url = "http://fund.eastmoney.com/" + fundCode + ".html?spm=aladin";
        Document doc;
        List<ProcessMessege> list = new ArrayList<ProcessMessege>();
        try {
            doc = Jsoup.connect(url).get();

            Element childNodeList = doc.getElementById("position_shares");

            Map<String, String> map = getFluctuate(fundCode);
            String[] text;
            for (int i = 1; i < 11; i++) {
                text = childNodeList.child(0).child(0).child(0).child(i).text().split(" ");
                list.add(new ProcessMessege(text[0], text[1], map.get(text[0])));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        list.add(queryUpOrDown(fundCode));
        return list;
    }

    public Map<String, String> getFluctuate(String fundCode) {
        String url = "http://nufm3.dfcfw.com/EM_Finance2014NumericApplication/JS.aspx?type=CT&cmd="
                + getStockCode(fundCode)
                + "&sty=E1OQCPZT&st=z&sr=&p=&ps=&cb=&js=var%20js_favstock={favif:[%28x%29]}&token=8a36403b92724d5d1dd36dc40534aec5"
                + "&rt=function%20random()%20{%20[native%20code]%20}";

        HttpRequester httpRequester = new HttpRequester();
        HttpRespons httpRespons;
        Map<String, String> map = new HashMap<String, String>();
        try {
            httpRespons = httpRequester.sendGet(url);
            String response = httpRespons.getContentCollection().toString();
            JSONObject jsonObject = JSONObject
                    .parseObject(response.substring(response.indexOf("=") + 1, response.length() - 1));
            JSONArray jsonArray = jsonObject.getJSONArray("favif");
            String[] string;
            for (int i = 0; i < jsonArray.size(); i++) {
                string = ((String) jsonArray.get(i)).split(",");
                map.put(string[2], string[4]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return map;

    }

    public String getStockCode(String fundCode) {
        String url = "http://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js?v=" + getFormatTime();
        InputStream inputStream = getResourceFromUrl(url);
        String js = new String(readInputStream(inputStream));
        js = js.substring(js.indexOf("stockCodes=[") + 12, js.indexOf("]")).replaceAll("\"", "");
        return js;
    }

    public String getFormatTime() {
        SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sf.format(new Date());
    }

    /**
     *
     * @param urlStr
     * @param fileName
     * @param savePath
     * @throws IOException
     */
    public InputStream getResourceFromUrl(String urlStr) {
        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
        }
        conn.setConnectTimeout(3 * 1000);
        conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");

        InputStream inputStream = null;
        try {
            inputStream = conn.getInputStream();
        } catch (IOException e) {
        }

        return inputStream;

    }

    /**
     * 从输入流中获取数据
     *
     * @param inStream
     *            输入流
     * @return
     * @throws Exception
     */
    public byte[] readInputStream(InputStream inStream) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int len = 0;
        try {
            if ((len = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }
            inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outStream.toByteArray();
    }


    public ProcessMessege queryUpOrDown(String fundCode) {
        String url = "http://fundgz.1234567.com.cn/js/" + fundCode + ".js?rt=1511162059983";
        InputStream inputStream = getResourceFromUrl(url);
        String js = new String(readInputStream(inputStream));
        js = js.substring(js.indexOf("gszzl\":\"") + "gszzl\":\"".length(),
                js.indexOf("gszzl\":\"") + "gszzl\":\"".length() + 4);
        return new ProcessMessege("888888", Double.parseDouble(js) / 100 + "", "");
    }
}
package ru.centralhardware.telegram.unbbGetMetaBot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Network {

    private static String URL_METAR = "https://tgftp.nws.noaa.gov/data/observations/metar/stations/UNBB.TXT";
    private static String URL_TAF   = "https://tgftp.nws.noaa.gov/data/forecasts/taf/stations/UNBB.TXT";

    public static String getMetar(){
       try{
           URL url = new URL(URL_METAR);
           HttpURLConnection con = (HttpURLConnection) url.openConnection();
           con.setRequestMethod("GET");
           BufferedReader in = new BufferedReader(
                   new InputStreamReader(con.getInputStream()));
           String inputLine;
           StringBuffer content = new StringBuffer();
           while ((inputLine = in.readLine()) != null) {
               content.append(inputLine);
           }
           in.close();
           return content.toString().substring(content.indexOf("U"));
       } catch (IOException e) {
           e.printStackTrace();
       }
       return "";
    }

    public static String getTaf(){
        try{
            URL url = new URL(URL_TAF);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            return content.toString().substring(content.indexOf("U"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

}

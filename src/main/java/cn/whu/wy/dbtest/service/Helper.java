package cn.whu.wy.dbtest.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * @Author WangYong
 * @Date 2020/08/27
 * @Time 17:23
 */
public class Helper {
    private static String lastBizNo;


    /**
     * 流水号生成器，17位时间戳 + 当前的微秒数和纳秒数
     * 一共17+6=23位
     */
    public static String getBizNo() {
        LocalDateTime now = LocalDateTime.now();
        long nanoTime = System.nanoTime();
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
            String timeStamp = formatter.format(now);
            String us = String.valueOf(nanoTime);
            return timeStamp + us.substring(us.length() - 7, us.length() - 1);
        } catch (Exception e) {
            System.out.println("now=" + now + ", nanoTime=" + nanoTime);
            return null;
        }

    }

    public static String getAccount(int i) {
        if (i > 999999) return "xxxxxx";
        return "0755" + String.format("%06d", i);
    }

}

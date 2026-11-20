package com.example.rpg_inventory.util;

// 大数字缩写工具，纯 Java 实现
public class NumberFormatUtil {

    private NumberFormatUtil() {}

    // 999 -> "999"；1000 -> "1k"；1500 -> "1k"（不保留小数）；999999 -> "999k"；1000000 -> "1M"
    public static String format(int amount) {
        if (amount < 1000) {
            return String.valueOf(amount);
        }
        if (amount < 1_000_000) {
            return amount / 1000 + "k";
        }
        return amount / 1_000_000 + "M";
    }
}
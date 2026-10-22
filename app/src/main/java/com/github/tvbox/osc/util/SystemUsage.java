package com.github.tvbox.osc.util;

import android.app.ActivityManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 车机播放页右上角的负载指标：系统 CPU 占用率 + 内存使用率。
 *
 * <p>CPU 取自 {@code /proc/stat} 的相邻两次采样差值（总量 - 空闲），因此是“整机”口径，
 * 单核跑满 8 核机器上只体现 12.5%。内存取自 {@code ActivityManager.MemoryInfo}
 * 的 {@code used / total}。两者都是 0-100，越接近 100 越卡，便于一眼判断。
 *
 * <p>只读文件与系统 API，无任何权限要求。
 */
public final class SystemUsage {

    private static long lastTotal;
    private static long lastIdle;

    private SystemUsage() {
    }

    /** 一次采样得到 {CPU%, 内存%}，失败项为 -1；两项共用同一次 /proc/stat 读取。 */
    public static int[] sample(Context context) {
        return new int[]{cpuPercent(), memoryPercent(context)};
    }

    public static String describe(int[] sample) {
        int cpu = sample == null || sample.length < 1 ? -1 : sample[0];
        int memory = sample == null || sample.length < 2 ? -1 : sample[1];
        return "CPU " + (cpu < 0 ? "--" : cpu + "%")
                + " · 内存 " + (memory < 0 ? "--" : memory + "%");
    }

    /**
     * 只输出真正取到的指标：取不到 CPU 就不显示 CPU，取不到内存就不显示内存；
     * 两项都取不到返回 null（调用方应隐藏整个徽标）。
     */
    public static String describeAvailable(int[] sample) {
        int cpu = sample == null || sample.length < 1 ? -1 : sample[0];
        int memory = sample == null || sample.length < 2 ? -1 : sample[1];
        StringBuilder builder = new StringBuilder();
        if (cpu >= 0) builder.append("CPU ").append(cpu).append('%');
        if (memory >= 0) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append("内存 ").append(memory).append('%');
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /** 最紧张的那一项（0-100），用于判断是否需要高亮告警。 */
    public static int worstPercent(int[] sample) {
        if (sample == null) return -1;
        int cpu = sample.length > 0 ? sample[0] : -1;
        int memory = sample.length > 1 ? sample[1] : -1;
        return Math.max(cpu, memory);
    }

    /** 整机 CPU 占用率 0-100；首次调用没有基准时返回 -1。 */
    public static int cpuPercent() {
        long total = 0L;
        long idle = 0L;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu")) return -1;
            String[] parts = line.trim().split("\\s+");
            // cpu user nice system idle iowait irq softirq steal
            for (int i = 1; i < parts.length && i <= 8; i++) {
                long value = Long.parseLong(parts[i]);
                total += value;
                if (i == 4 || i == 5) idle += value; // idle + iowait
            }
        } catch (Throwable error) {
            return -1;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
        if (total <= 0L) return -1;
        long totalDelta = total - lastTotal;
        long idleDelta = idle - lastIdle;
        lastTotal = total;
        lastIdle = idle;
        if (totalDelta <= 0L || idleDelta < 0L) return -1;
        long busy = totalDelta - idleDelta;
        if (busy < 0L) busy = 0L;
        return (int) Math.max(0L, Math.min(100L, Math.round(busy * 100d / totalDelta)));
    }

    /** 内存使用率 0-100。 */
    public static int memoryPercent(Context context) {
        if (context == null) return -1;
        try {
            ActivityManager manager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return -1;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            if (info.totalMem <= 0L) return -1;
            long used = info.totalMem - info.availMem;
            if (used < 0L) used = 0L;
            return (int) Math.max(0L, Math.min(100L, Math.round(used * 100d / info.totalMem)));
        } catch (Throwable error) {
            return -1;
        }
    }
}

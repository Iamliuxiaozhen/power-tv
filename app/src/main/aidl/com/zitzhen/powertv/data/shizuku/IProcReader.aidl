package com.zitzhen.powertv.data.shizuku;

interface IProcReader {
    void destroy() = 16777114; // Destroy method defined by Shizuku server
    String readCpuStat() = 1;
    String readProcessTable() = 2;
}

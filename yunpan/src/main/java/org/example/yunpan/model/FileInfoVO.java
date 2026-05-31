package org.example.yunpan.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FileInfoVO {

    private String fileName;
    private boolean isDirectory;
    private long size;
    private String lastModifiedTime;

    public FileInfoVO() {}

    public FileInfoVO(String fileName, boolean isDirectory, long size, Instant lastModifiedTime) {
        this.fileName = fileName;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModifiedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Shanghai"))
                .format(lastModifiedTime);
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean getIsDirectory() {
        return isDirectory;
    }

    public void setIsDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(String lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }
}

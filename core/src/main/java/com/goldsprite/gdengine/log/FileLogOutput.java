package com.goldsprite.gdengine.log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.goldsprite.gdengine.log.DLog.Level;
import com.goldsprite.gdengine.log.DLog.LogOutput;

/**
 * 将日志输出到文件的 LogOutput 实现。
 * 支持追加模式写入，自动创建父目录。
 */
public class FileLogOutput implements LogOutput {
    private final String filePath;
    private PrintWriter writer;

    public FileLogOutput(String filePath) {
        this.filePath = filePath;
        
        try {
            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            // Append mode, auto flush
            writer = new PrintWriter(new FileWriter(file, true), true);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[FileLogOutput] 无法创建日志文件: " + filePath + " (" + e.getMessage() + ")");
        }
    }

    @Override
    public void onLog(Level level, String tag, String msg) {
        if (writer == null) return;

        // 格式: [2023-10-27 10:00:00.123] [INFO] [Server] 消息内容
        // SimpleDateFormat 非线程安全，每次创建新实例以保证安全
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String time = dateFormat.format(new Date());
        String logLine = String.format("[%s] [%s] [%s] %s", time, level.name(), tag, msg);

        writer.println(logLine);
    }
    
    /**
     * 关闭日志文件流
     */
    public void close() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}

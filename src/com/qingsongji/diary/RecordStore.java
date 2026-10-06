package com.qingsongji.diary;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
/** Alternating app-private files; commit pointer only after verified durable write. */
public final class RecordStore {
    private final File dir;
    public RecordStore(File dir) { this.dir = dir; }
    private String active() throws IOException {
        File index = new File(dir, "active");
        if (!index.exists()) return null;
        String slot = new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8);
        if (!slot.equals("a") && !slot.equals("b")) throw new IOException("记录索引损坏，原文件已保留。");
        return slot;
    }
    public synchronized String read() throws IOException {
        String slot = active();
        if (slot == null) return null;
        File file = new File(dir, "records-" + slot + ".json");
        if (file.length() > 10 * 1024 * 1024) throw new IOException("记录文件超过 10 MB，已停止读取并保留原文件。");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
    public synchronized void write(String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 10 * 1024 * 1024) throw new IOException("记录不能超过 10 MB。");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法建立记录目录。");
        String old;
        try { old = active(); } catch (IOException e) { old = null; }
        // Normal application saves always read/validate first. Corrupt indices can
        // reach write only via explicitly confirmed reset from the shared core.
        String next = "a".equals(old) ? "b" : "a";
        File file = new File(dir, "records-" + next + ".json");
        durableWrite(file, bytes);
        if (!Arrays.equals(bytes, Files.readAllBytes(file.toPath()))) throw new IOException("记录写入校验失败。");
        File pending = new File(dir, "active.pending");
        durableWrite(pending, next.getBytes(StandardCharsets.UTF_8));
        Files.move(pending.toPath(), new File(dir, "active").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    private static void durableWrite(File file, byte[] bytes) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) { stream.write(bytes); stream.getFD().sync(); }
    }
}

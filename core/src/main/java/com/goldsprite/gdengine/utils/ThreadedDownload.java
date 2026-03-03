package com.goldsprite.gdengine.utils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.goldsprite.gdengine.log.DLog;

public class ThreadedDownload {

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(int percentage, String message);
	}

	public static class DownloadTask implements Runnable {
		private final String downloadUrl;
		private final String saveDirectory;
		private final ProgressListener listener;
		private final Runnable onFinish;

		public DownloadTask(String downloadUrl, String saveDirectory, ProgressListener listener, Runnable onFinish) {
			this.downloadUrl = downloadUrl;
			this.saveDirectory = saveDirectory;
			this.listener = listener;
			this.onFinish = onFinish;
		}

		@Override
		public void run() {
			Path tempZipFile = null;
			try {
				// 1. 下载部分保持不变...
				long fileSize = getFileSize(downloadUrl);
				if (fileSize <= 0) notifyError("无法获取文件大小");

				tempZipFile = Paths.get(saveDirectory, "download_temp.zip");
				Files.createDirectories(tempZipFile.getParent());

				long downloaded = 0;
				URL url = new URL(downloadUrl);
				try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
					 FileOutputStream fos = new FileOutputStream(tempZipFile.toFile())) {

					ByteBuffer buffer = ByteBuffer.allocate(32 * 1024);
					while (rbc.read(buffer) != -1) {
						buffer.flip();
						if (buffer.hasRemaining()) fos.getChannel().write(buffer);
						downloaded += buffer.position();
						buffer.clear();

						int progress = (int) ((downloaded * 100) / fileSize);
						notifyProgress(progress, "下载中: " + progress + "%");
						// 下载时不需要太频繁 sleep，这里为了平滑可以保留一点点，或者去掉
						Thread.sleep(50);
					}
				}

				// 2. 解压部分 (核心修改在这里)
				notifyProgress(0, "下载完成，准备解压...");
				extractZip(tempZipFile.toFile(), new File(saveDirectory));

				Files.deleteIfExists(tempZipFile);
				Thread.sleep(1000); // 暂停 1 秒, 有个反应时间
				notifyProgress(100, "任务完成！");

				if (onFinish != null) onFinish.run();

			} catch (Exception e) {
				try { if (tempZipFile != null) Files.deleteIfExists(tempZipFile); } catch (IOException ignored) {}
				notifyError("错误: " + e.getMessage());
				e.printStackTrace();
			}
		}

		private long getFileSize(String urlString) throws IOException {
			URL url = new URL(urlString);
			URLConnection conn = url.openConnection();
			return conn.getContentLengthLong();
		}

		/**
		 * 修改后的解压方法：
		 * 1. 基于总 Zip 文件大小计算进度
		 * 2. 每 10% 暂停 1 秒
		 */
		private void extractZip(File zipFile, File outputFolder) throws IOException {
			final long totalZipSize = zipFile.length(); // 获取压缩包总大小

			// 使用自定义流来统计已读取的字节数
			try (FileInputStream fis = new FileInputStream(zipFile);
				 BufferedInputStream bis = new BufferedInputStream(fis); // 加缓冲提升IO性能
				 CountingInputStream cis = new CountingInputStream(bis); // 包装计数流
				 ZipInputStream zis = new ZipInputStream(cis)) {

				ZipEntry entry;
				byte[] buffer = new byte[8192]; // 增大缓冲区到 8KB
				int nextPauseThreshold = 10; // 下一个暂停的百分比阈值 (10%, 20%...)

				while ((entry = zis.getNextEntry()) != null) {
					// --- 文件名处理 ---
					String entryName = entry.getName();
					int firstSlashIndex = entryName.indexOf('/');
					if (firstSlashIndex != -1) entryName = entryName.substring(firstSlashIndex + 1);
					if (entryName.isEmpty()) continue;

					File entryFile = new File(outputFolder, entryName);
					if (entry.isDirectory()) {
						entryFile.mkdirs();
						continue;
					}
					if (entryFile.getParentFile() != null) entryFile.getParentFile().mkdirs();

					// --- 写入文件 ---
					try (FileOutputStream fos = new FileOutputStream(entryFile)) {
						int len;
						while ((len = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, len);

							// --- 进度与暂停逻辑 ---
							// cis.getBytesRead() 获取的是读取的压缩包字节数，对应 totalZipSize
							long bytesRead = cis.getBytesRead();
							int currentPercent = (int) ((bytesRead * 100) / totalZipSize);

							// 如果当前进度达到了阈值 (10, 20, 30...)
							if (currentPercent >= nextPauseThreshold && currentPercent < 100) {
								notifyProgress(currentPercent, String.format(Locale.CHINESE, "解压中 %d%%...", currentPercent));

								try {
									Thread.sleep(100); // 暂停
								} catch (InterruptedException ignored) {}

								// 更新下一个阈值，确保每 10% 只停一次
								// 例如当前是 12%，暂停后，下一个目标设为 20%
								nextPauseThreshold = (currentPercent / 10 + 1) * 10;
							} else {
								// 没到暂停点时，正常刷新进度 (可以限制刷新频率以免刷屏，这里简单处理)
								if (bytesRead % (1024 * 50) == 0) { // 每读 50KB 刷新一次 UI，防止太频繁
									notifyProgress(currentPercent, "解压中: " + entryName);
								}
							}
						}
					}
					zis.closeEntry();
				}
			}
		}

		// --- 辅助类：用于统计读取字节数的流 ---
		private static class CountingInputStream extends FilterInputStream {
			private long bytesRead = 0;

			protected CountingInputStream(InputStream in) {
				super(in);
			}

			@Override
			public int read() throws IOException {
				int result = super.read();
				if (result != -1) bytesRead++;
				return result;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				int result = super.read(b, off, len);
				if (result != -1) bytesRead += result;
				return result;
			}

			public long getBytesRead() {
				return bytesRead;
			}
		}

		private void notifyProgress(int percentage, String msg) {
			if (listener != null) listener.onProgress(percentage, msg);
		}

		private void notifyError(String err) {
			if (listener != null) listener.onProgress(-1, err);
		}
	}

	/**
	 * 启动异步下载并解压任务。
	 * @param downloadUrl 下载地址
	 * @param savePath    解压保存目录
	 * @param onFinish    完成回调（可为 null）
	 */
	public static void download(String downloadUrl, String savePath, Runnable onFinish) {
		if (downloadUrl == null || downloadUrl.isEmpty() || savePath == null || savePath.isEmpty()) {
			DLog.logErrT("ZipDownLoader", "下载地址或保存路径为空，跳过下载");
			return;
		}

		ProgressListener listener = (percentage, message) -> {
			String progressBar = (percentage >= 0) ? "=".repeat(Math.max(0, percentage / 2)) + ">" : "ERROR";
			String msg = String.format(Locale.CHINESE, "%s [%-50s]", message, progressBar);

			if (percentage == -1) {
				DLog.logErrT("ZipDownLoader", "\n" + message);
			} else {
				DLog.logT("ZipDownLoader", msg);
			}
		};

		Thread thread = new Thread(new DownloadTask(downloadUrl, savePath, listener, onFinish));
		thread.start();
		DLog.logT("ZipDownLoader", "下载任务已启动...");
	}

	public static void main(String[] args) {
		download("https://example.com/test.zip", "./downloads", () -> System.out.println("=== 完成 ==="));
	}
}

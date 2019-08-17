package com.jwlee.netmanagement.util;

import com.jcraft.jsch.*;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author jianwuli
 * @time 2019/8/17 16:01
 */
public class LinuxConnectionHelper {
    //    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LinuxConnectionHelper.class);
    private static final int TIME_OUT = 5 * 60 * 1000; //设置超时为5分钟
    private static final Map<String, Session> cache = new HashMap<>(); //session缓存

    public static SessionMonitor sessionMonitor;

    /**
     * 创建 连接，需手动关闭
     *
     * @param host
     * @param userName
     * @param password
     * @param port
     * @throws JSchException
     */
    public static Session connect(String host, String userName, String password, int port) throws JSchException {
        //创建对象
        JSch jsch = new JSch();
        //创建会话
        Session session = jsch.getSession(userName, host, port);
        //输入密码
        session.setPassword(password);
        //配置信息
        Properties config = new Properties();
        //设置不用检查hostKey
        //如果设置成“yes”，ssh就不会自动把计算机的密匙加入“$HOME/.ssh/known_hosts”文件，
        //并且一旦计算机的密匙发生了变化，就拒绝连接。
        config.setProperty("StrictHostKeyChecking", "no");
//        //默认值是 “yes” 此处是由于我们SFTP服务器的DNS解析有问题，则把UseDNS设置为“no”
//        config.put("UseDNS", "no");
        session.setConfig(config);
        //过期时间
        session.setTimeout(TIME_OUT);
        //建立连接
        session.connect();

        return session;
    }

    /**
     * 创建  连接，无需手动关闭
     *
     * @param host
     * @param userName
     * @param password
     * @param port
     * @throws JSchException
     */
    public static Session longConnect(String host, String userName, String password, int port) throws JSchException {
        String key = host + userName + password + port;
        Session session = cache.get(key);
        if (session == null) {
            //创建对象
            JSch jsch = new JSch();
            //创建会话
            session = jsch.getSession(userName, host, port);
            //输入密码
            session.setPassword(password);
            //配置信息
            Properties config = new Properties();
            //设置不用检查hostKey
            //如果设置成“yes”，ssh就不会自动把计算机的密匙加入“$HOME/.ssh/known_hosts”文件，
            //并且一旦计算机的密匙发生了变化，就拒绝连接。
            config.setProperty("StrictHostKeyChecking", "no");
//        //默认值是 “yes” 此处是由于我们SFTP服务器的DNS解析有问题，则把UseDNS设置为“no”
//        config.put("UseDNS", "no");
            session.setConfig(config);
            //过期时间
            //session.setTimeout(TIME_OUT);
            //建立连接，此时会在linux上新建一个进程，timeout 并不会结束进程，只有调用disconnect()才会结束此进程
            session.connect();
            cache.put(key, session);
        } else {
            //判断session是否失效
            if (testSessionIsDown(key)) {
                //session is down
                //session 失去连接则清除
                closeLongSessionByKey(key);
                //重新生成session
                session = longConnect(host, userName, password, port);
            }
        }
        //创建定时器
        createSessionMonitor();
        return session;
    }

    /**
     * 销毁 session
     *
     * @param session
     */
    public static void close(Session session) {
        if (session != null) {
            session.disconnect();
        }
    }

    /**
     * 测试session是否失效
     *
     * @param key
     * @return
     */
    public static boolean testSessionIsDown(String key) {
        Session session = cache.get(key);
        if (session == null) {
            return true;
        }
        ChannelExec channelExec = null;
        try {
            channelExec = openChannelExec(session);
            channelExec.setCommand("true");
            channelExec.connect();
            return false;
        } catch (Throwable e) {
            //session is down
            return true;
        } finally {
            if (channelExec != null) {
                channelExec.disconnect();
            }
        }
    }

    /**
     * 销毁 session
     *
     * @param key
     */
    public static synchronized void closeLongSessionByKey(String key) {
        Session session = cache.get(key);
        if (session != null) {
            session.disconnect();
            cache.remove(key);
        }
    }

    /**
     * 销毁 session
     *
     * @param session
     */
    public static void closeLongSessionBySession(Session session) {
        Iterator iterator = cache.keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            Session oldSession = cache.get(key);
            if (session == oldSession) {
                session.disconnect();
                cache.remove(key);
                return;
            }
        }
    }

    /**
     * 创建一个 sftp 通道并建立连接
     *
     * @param session
     * @return
     * @throws Exception
     */
    public static ChannelSftp openChannelSftp(Session session) throws Exception {
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();
        return channelSftp;
    }

    /**
     * 关闭 sftp 通道
     *
     * @param channelSftp
     * @throws Exception
     */
    public static void closeChannelSftp(ChannelSftp channelSftp) {
        if (channelSftp != null) {
            channelSftp.disconnect();
        }
    }

    /**
     * 下载文件
     *
     * @param remoteFile 远程服务器的文件路径
     * @param localPath  需要保存文件的本地路径
     * @throws IOException
     * @throws SftpException
     */
    public static void downloadFile(Session session, String remoteFile, String localPath, String UUID) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            String remoteFilePath = remoteFile.substring(0, remoteFile.lastIndexOf("/"));
            String remoteFileName = remoteFile.substring(remoteFile.lastIndexOf("/") + 1, remoteFile.length());
            if (localPath.charAt(localPath.length() - 1) != '/') {
                localPath += '/';
            }
            File file = new File(localPath + UUID);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            OutputStream output = new FileOutputStream(file);
            try {
                channelSftp.cd(remoteFilePath);
//                LOGGER.info("远程服务器路径：" + remoteFilePath);
//                LOGGER.info("本地下载路径：" + localPath + remoteFileName);
                SftpATTRS attrs = channelSftp.lstat(remoteFile);
                channelSftp.get(remoteFile, output, new FileSftpProgressMonitor(attrs.getSize()));
            } catch (Exception e) {
                throw e;
            } finally {
                output.flush();
                output.close();
            }
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 上传文件
     *
     * @param localFile  本地文件路径
     * @param remotePath 远程服务器路径
     * @throws IOException
     * @throws SftpException
     */
    public static String uploadFile(Session session, String localFile, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        File file = new File(localFile);
        String fileName = file.getName();
        final InputStream input = new FileInputStream(file);
        try {
            channelSftp.cd(remotePath);
        } catch (SftpException e) {
            String tempPath = null;
            try {
                tempPath = remotePath.substring(0, remotePath.lastIndexOf("/"));
                channelSftp.cd(tempPath);
            } catch (SftpException e1) {
                channelSftp.mkdir(tempPath);
            }
            channelSftp.mkdir(remotePath);
            channelSftp.cd(remotePath);
        }
//        LOGGER.info("远程服务器路径：" + remotePath);
//        LOGGER.info("本地上传路径：" + localFile);
        try {
            channelSftp.put(input, fileName, new FileSftpProgressMonitor(file.length()));
        } catch (Exception e) {
            throw e;
        } finally {
            input.close();
            closeChannelSftp(channelSftp);
        }
        return fileName;
    }

    /**
     * 文件上传
     *
     * @param session
     * @param inputStream
     * @param fileName
     * @param remotePath
     * @throws Exception
     */
    public static void uploadFile(Session session, InputStream inputStream, String fileName, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        channelSftp.cd(remotePath);
//        LOGGER.info("远程服务器路径：" + remotePath);
        try {
            channelSftp.put(inputStream, fileName, new FileSftpProgressMonitor(inputStream.available()));
        } catch (Exception e) {
            throw e;
        } finally {
            inputStream.close();
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 获取远程服务器文件列表
     *
     * @param session
     * @param remotePath 远程服务器路径
     * @return
     * @throws SftpException
     */
    public static Vector listFiles(Session session, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            Vector vector = channelSftp.ls(remotePath);
            return vector;
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 删除文件
     *
     * @param session
     * @param remotePath
     * @param fileName
     * @throws Exception
     */
    public static void removeFile(Session session, String remotePath, String fileName) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            channelSftp.cd(remotePath);
            channelSftp.rm(fileName);
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 删除文件夹
     *
     * @param session
     * @param remotePath
     * @throws Exception
     */
    public static void removeDir(Session session, String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            if (remotePath.lastIndexOf("/") == remotePath.length() - 1) {
                remotePath = remotePath.substring(0, remotePath.length() - 1);
            }
            String parentDir = remotePath.substring(0, remotePath.lastIndexOf("/") + 1);
            String rmDir = remotePath.substring(remotePath.lastIndexOf("/") + 1, remotePath.length());
            channelSftp.cd(parentDir);
            channelSftp.rmdir(rmDir);
        } catch (Exception e) {
            throw e;
        } finally {
            closeChannelSftp(channelSftp);
        }
    }

    /**
     * 新建一个 exec 通道
     *
     * @param session
     * @return
     * @throws JSchException
     */
    public static ChannelExec openChannelExec(Session session) throws JSchException {
        ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
        return channelExec;
    }

    /**
     * 关闭 exec 通道
     *
     * @param channelExec
     */
    public static void closeChannelExec(ChannelExec channelExec) {
        if (channelExec != null) {
            channelExec.disconnect();
        }
    }

    /**
     * 执行 脚本
     *
     * @param session
     * @param cmd     执行 .sh 脚本
     * @param charset 字符格式
     * @return
     * @throws IOException
     * @throws JSchException
     */
    public static String[] execCmd(Session session, String cmd, String charset, int timeSleep) throws Exception {
        //打开通道
//        LOGGER.info("cmd {}",cmd);
        ChannelExec channelExec = openChannelExec(session);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        channelExec.setCommand(cmd);
        channelExec.setOutputStream(out);
        channelExec.setErrStream(error);
        channelExec.connect();
        //确保能够执行完成及响应所有数据
        Thread.sleep(timeSleep);
        String[] msg = new String[2];
        msg[0] = new String(out.toByteArray(), charset);
        msg[1] = new String(error.toByteArray(), charset);
        out.close();
        error.close();
        //关闭通道
        closeChannelExec(channelExec);
//        LOGGER.info("msg {}", msg);
        return msg;

    }

    /**
     * 创建一个交互式的 shell 通道
     *
     * @param session
     * @return
     * @throws JSchException
     */
    public static ChannelShell openChannelShell(Session session) throws JSchException {
        ChannelShell channelShell = (ChannelShell) session.openChannel("shell");
        return channelShell;
    }

    /**
     * 关闭 shell 通道
     *
     * @param channelShell
     */
    public static void closeChannelShell(ChannelShell channelShell) {
        if (channelShell != null) {
            channelShell.disconnect();
        }
    }

    /**
     * 执行命令
     *
     * @param cmds         命令参数
     * @param session
     * @param timeout      连接超时时间
     * @param sleepTimeout 线程等待时间
     * @return
     * @throws Exception
     */
    public static String execShellCmd(String[] cmds, Session session, int timeout, int sleepTimeout) throws Exception {
        //打开通道
        ChannelShell channelShell = openChannelShell(session);
        //设置输入输出流
        PipedOutputStream pipedOut = new PipedOutputStream();
        ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
        channelShell.setInputStream(new PipedInputStream(pipedOut));
        channelShell.setOutputStream(errorOut);
        channelShell.connect(timeout);
        for (String cmd : cmds) {
            pipedOut.write(cmd.getBytes("UTF-8"));
            //线程休眠，保证执行命令后能够及时返回响应数据
            Thread.sleep(sleepTimeout);

        }
        String msg = new String(errorOut.toByteArray(), "UTF-8");
//        LOGGER.info(msg);
        pipedOut.close();
        errorOut.close();
        //关闭通道
        closeChannelShell(channelShell);
        return msg;
    }

    /**
     * 创建定时器，清除timeout 的 session 连接
     */
    public static void createSessionMonitor() {
        if (sessionMonitor == null) {
            synchronized (SessionMonitor.class) {
                if (sessionMonitor == null) {
                    //定时器，定时清除失效的session
                    sessionMonitor = new SessionMonitor();
                    sessionMonitor.start();
                }
            }
        }
    }

    /**
     * 监控文件传输
     */
    static class FileSftpProgressMonitor extends TimerTask implements SftpProgressMonitor {

        private long progressInterval = 5 * 1000; // 默认间隔时候为5秒

        private boolean isEnd = false; // 记录传输是否停止

        private long transfered; // 记录已传输的数据总大小

        private long fileSize; // 记录文件总大小

        private Timer timer; // 定时器对象

        private boolean isScheduled = false; // 记录是否已启动timer定时器

        private NumberFormat df = NumberFormat.getInstance(); //格式化

        public FileSftpProgressMonitor(long fileSize) {
            this.fileSize = fileSize;
        }

        @Override
        public void run() {
            if (!isEnd()) {
                long transfered = getTransfered();
                if (transfered != fileSize) {
//                    LOGGER.info("Current transfered: " + transfered + " bytes");
                    sendProgressMessage(transfered);
                } else {
//                    LOGGER.info("transfered end.");
                    setIsEnd(true);
                }
            } else {
//                LOGGER.info("Transfering done. Cancel timer.");
                stop(); // 若是传输停止，停止timer记时器
            }
        }

        /**
         * 定时器关闭
         */
        public void stop() {
//            LOGGER.info("Try to stop progress monitor.");
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
                isScheduled = false;
            }
//            LOGGER.info("Progress monitor stoped.");
        }

        /**
         * 定时器启动
         */
        public void start() {
//            LOGGER.info("Try to start progress monitor.");
            if (timer == null) {
                timer = new Timer();
            }
            timer.schedule(this, 1000, progressInterval);
            isScheduled = true;
//            LOGGER.info("Progress monitor started.");
        }

        /**
         * 传输进度
         *
         * @param transfered
         */
        private void sendProgressMessage(long transfered) {
            if (fileSize != 0) {
                double d = ((double) transfered * 100) / (double) fileSize;
//                LOGGER.info("Sending progress message: " + df.format(d) + "%");
            } else {
//                LOGGER.info("Sending progress message: " + transfered);
            }
        }

        @Override
        public void init(int i, String s, String s1, long l) {
//            LOGGER.info("transfering start.");
        }

        @Override
        public boolean count(long l) {
            if (isEnd()) {
                return false;
            }
            if (!getIsScheduled()) {
                start();
            }
            add(l);
            return true;
        }

        @Override
        public void end() {
            setIsEnd(false);
//            LOGGER.info("transfering end.");
        }


        private synchronized void add(long count) {
            transfered = transfered + count;
        }

        public synchronized boolean isEnd() {
            return isEnd;
        }

        public synchronized void setIsEnd(boolean isEnd) {
            this.isEnd = isEnd;
        }

        public synchronized long getTransfered() {
            return transfered;
        }

        public synchronized void setTransfered(long transfered) {
            this.transfered = transfered;
        }


        public synchronized boolean getIsScheduled() {
            return isScheduled;
        }

        public synchronized void setIsScheduled(boolean isScheduled) {
            this.isScheduled = isScheduled;
        }


    }

    /**
     * 定时器，定时清除失效的session
     */
    static class SessionMonitor extends TimerTask {

        private Timer timer; // 定时器对象
        private long progressInterval = 30 * 1000; // 默认间隔时候为30秒

        @Override
        public void run() {
            if (!cache.isEmpty()) {
                Iterator iterator = cache.keySet().iterator();
                while (iterator.hasNext()) {
                    String key = (String) iterator.next();
                    //清除失效session
                    if (testSessionIsDown(key)) {
                        closeLongSessionByKey(key);
                    }
                }
            }
        }

        public void start() {
            if (timer == null) {
                timer = new Timer();
            }
            timer.schedule(this, 1000, progressInterval);
        }

        public void stop() {
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
            }
        }
    }
}

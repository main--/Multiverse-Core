package com.onarandombox.MultiverseCore.updating;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.plugin.java.PluginClassLoader;

import com.onarandombox.MultiverseCore.MultiverseCore;

/**
 * Our updater.
 */
public class Updater implements Runnable {
    private static final String UPDATE_CHECK_URL = "http://multiverse-plugin.appspot.com/updateCheck";

    private MultiverseCore core;
    private final URL updateCheckUrl;

    public Updater(MultiverseCore core) {
        this.core = core;
        try {
            // build JSON information
            StringBuilder urlBuilder = new StringBuilder(UPDATE_CHECK_URL);
            urlBuilder.append("?name=").append(
                    URLEncoder.encode(core.getDescription().getName(), "UTF-8"));
            urlBuilder.append("&version=").append(
                    URLEncoder.encode(core.getDescription().getVersion(), "UTF-8"));
            System.out.println("DEBUG: URL: " + urlBuilder.toString());
            updateCheckUrl = new URL(urlBuilder.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This is periodically executed by the Bukkit scheduler.
     */
    public void run() {
        try {
            URLConnection connection = updateCheckUrl.openConnection();
            connection.connect();
            // // read all text
            // InputStreamReader reader = new InputStreamReader(connection.getInputStream());
            // while (!reader.ready()) Thread.sleep(1L);
            //
            // StringBuilder read = new StringBuilder();
            // while (reader.ready()) {
            // read.append((char) reader.read());
            // }
            //
            // System.out.println("DEBUG: READ: " + read);
            // read it as yaml-config :D
            Configuration recvConfig = YamlConfiguration.loadConfiguration(connection
                    .getInputStream());
            boolean update = recvConfig.getBoolean("updateAvailable", false);
            if (!update)
                return;

            // there's an update, so let's download it!
            String updateUrl = recvConfig.getString("updateUrl");
            File myFile = getMyPluginFile();
            File jarFile = new File(myFile/*jar*/.getParentFile()/*plugins*/.getParentFile()/*server*/, "updates");
            downloadFile(updateUrl, jarFile);

            // hotswap :D
            PluginClassLoader loader = null;
            Configuration hotswapConfig;
            JarFile jar = new JarFile(jarFile);
            JarEntry entry = jar.getJarEntry("hotswapInfo.yml");

            if (entry == null) {
                throw new InvalidPluginException(new FileNotFoundException(
                        "Jar does not contain hotswapInfo.yml"));
            }

            InputStream stream = jar.getInputStream(entry);
            hotswapConfig = YamlConfiguration.loadConfiguration(stream);

            stream.close();
            jar.close();

            URL[] urls = new URL[1];
            urls[0] = jarFile.toURI().toURL();
            loader = new PluginClassLoader((JavaPluginLoader) core.getPluginLoader(), urls,
                    getClass().getClassLoader());
            Class<?> hotswapClass = Class.forName(hotswapConfig.getString("hotswapper"), true,
                    loader);
            Class<? extends Hotswapper> plugin = hotswapClass.asSubclass(Hotswapper.class);
            Constructor<? extends Hotswapper> constructor = plugin.getConstructor();
            Hotswapper hotswapper = constructor.newInstance();

            hotswapper.hotswap(core);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static final int BUFFER_SIZE = 4096;

    private void downloadFile(String urlString, File file) throws IOException {
        // we don't overwrite
        if (file.exists())
            throw new IOException("File already exists!");

        OutputStream fileOutputStream = new FileOutputStream(file);

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        int response = connection.getResponseCode();
        if (response == HttpURLConnection.HTTP_OK) {
            byte[] buffer = new byte[BUFFER_SIZE];
            InputStream stream = connection.getInputStream();
            int d;
            while ((d = stream.read(buffer)) > 0)
                fileOutputStream.write(buffer, 0, d);
            fileOutputStream.flush();
            fileOutputStream.close();
        } else {
            throw new IOException("Invalid HTTP response: " + response);
        }
    }

    private File getMyPluginFile() throws IOException {
        File pluginsDirectory = new File(this.core.getServerFolder(), "plugins");
        File[] pluginFiles = pluginsDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jar") && name.contains("multiverse")
                        && name.contains("core");
            }
        });
        if (pluginFiles.length != 1)
            throw new IOException("Couldn't find the Multiverse-JAR!");
        else
            return pluginFiles[0];
    }
}

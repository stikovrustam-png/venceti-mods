package com.venceti.mods;

import android.content.Context;

import rikka.shizuku.Shizuku;

public class ModInstaller {

    private static final String SOURCE = "/storage/emulated/0/Download/Venceti Mods/Files";

    public static String install(Context ctx, String mode) {
        String pkg = "apk".equals(mode) ? "com.grand.cr" : "com.grand.launcher";
        String dest = "/storage/emulated/0/Android/data/" + pkg + "/files";

        if (!Shizuku.pingBinder()) {
            return "Shizuku Not Working";
        }

        try {
            sh("mkdir -p '" + dest + "'");
            String out = sh("cp -rf '" + SOURCE + "/.' '" + dest + "/'");
            if (out.startsWith("ERR")) return out;
            return "OK";
        } catch (Throwable t) {
            return "ERROR: " + t.getMessage();
        }
    }

    private static String sh(String cmd) {
        try {
            String[] command = {"sh", "-c", cmd};
            Process p = Shizuku.newProcess(command, null, null);

            java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");

            java.io.BufferedReader er = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getErrorStream()));
            StringBuilder err = new StringBuilder();
            while ((line = er.readLine()) != null) err.append(line).append("\n");

            p.waitFor();

            if (err.length() > 0) return "ERR: " + err.toString().trim();
            return sb.toString().trim();

        } catch (Throwable t) {
            return "ERR: " + t.getMessage();
        }
    }
}

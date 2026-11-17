package com.sugardeicing.localserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

/** 开发者选项：试验功能、环境自检、日志与重置。 */
public class DevActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Logger.init(this);
        Ui.statusBar(this);
        final Activity a = this;
        LinearLayout c = Ui.scaffold(this, "开发者选项", this::finish);

        Ui.head(a, c, "试验功能");
        LinearLayout c1 = Ui.card(a, c);
        Ui.row(a, c1, "把当前页面放到桌面",
                "在桌面生成入口，点开直接进入指定页面。部分系统桌面可能拦截", "试验", v ->
                        Toast.makeText(a, "这个功能还在做，下一版提供", Toast.LENGTH_SHORT).show());

        Ui.head(a, c, "诊断");
        LinearLayout c2 = Ui.card(a, c);
        Ui.row(a, c2, "环境自检", "8 项，启动时自动执行一次", "›",
                v -> startActivity(new Intent(a, SelfCheckActivity.class)));
        Ui.row(a, c2, "导出日志", "写入系统下载目录 LocalHttp/", "›", v -> {
            String p = Logger.exportToPublic(a);
            Toast.makeText(a, p == null ? "导出失败，先看日志" : "已导出：" + p, Toast.LENGTH_LONG).show();
        });
        Ui.row(a, c2, "清空日志", "只清日志，不影响导入的文件", "›", v ->
                new AlertDialog.Builder(a).setTitle("清空日志")
                        .setMessage("日志会被全部清掉，导出过的文件不受影响。")
                        .setPositiveButton("清空", (d, w) -> {
                            Logger.clear();
                            Toast.makeText(a, "已清空", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null).show());

        Ui.head(a, c, "危险操作");
        LinearLayout c3 = Ui.card(a, c);
        Ui.row(a, c3, "关闭开发者选项", "关闭后入口隐藏。连点「版本」7 次可重新开启", "›", v -> {
            Prefs.setDev(a, false);
            Logger.log("开发者选项已关闭");
            finish();
        });
        Ui.row(a, c3, "恢复出厂", "清除导入的文件、授权、设置与日志", "›", v ->
                new AlertDialog.Builder(a).setTitle("恢复出厂")
                        .setMessage("清除导入的文件、存储授权、设置与日志，回到初次安装的状态。此操作不可撤销。")
                        .setPositiveButton("清除", (d, w) -> {
                            try {
                                FileStore.wipe(a);
                                Prefs.clear(a);
                                Logger.clear();
                                Toast.makeText(a, "已恢复出厂设置", Toast.LENGTH_SHORT).show();
                                finishAffinity();
                            } catch (Throwable t) {
                                Logger.log("恢复出厂失败：" + t);
                                Toast.makeText(a, "清除失败，请查看日志", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null).show());

        Ui.hint(a, c, "以上均为试验功能，可能不稳定，不影响本地网页浏览。");
    }
}

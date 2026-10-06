package com.qingsongji.diary;
import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int EXPORT = 10, IMPORT = 11;
    private WebView web;
    private RecordStore records;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private byte[] pendingExport;
    private volatile String pendingImport;
    private boolean pickerBusy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        records = new RecordStore(new File(getFilesDir(), "diary"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xfff7f8f4);
        web = new WebView(this); root.addView(web, new LinearLayout.LayoutParams(-1, -1)); setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
            });
            root.requestApplyInsets();
        } else root.setFitsSystemWindows(true);
        getWindow().setStatusBarColor(0xfff7f8f4); getWindow().setNavigationBarColor(0xfff7f8f4);
        web.setBackgroundColor(0xfff7f8f4);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setGeolocationEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled(false);
        web.addJavascriptInterface(new Bridge(), "NativeDiary");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return asset(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost()) && "https".equals(uri.getScheme())) return false;
                if (request.isForMainFrame() && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this,"没有可打开链接的浏览器",Toast.LENGTH_LONG).show(); }
                }
                return true;
            }
        });
        web.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            Uri uri = Uri.parse(url);
            if (HOST.equals(uri.getHost()) && uri.getPath().endsWith("/install-qr.png")) new Bridge().exportQr();
            else if ("https".equals(uri.getScheme())) try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) {}
        });
        web.loadUrl("https://" + HOST + "/assets/index.html");
    }
    private WebResourceResponse asset(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost()) || !uri.getPath().startsWith("/assets/")) return denied();
        String path = uri.getPath().substring(8);
        if (path.contains("..") || path.contains("\\") || path.startsWith("/")) return denied();
        String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".html") ? "text/html" : path.endsWith(".svg") ? "image/svg+xml" : path.endsWith(".png") ? "image/png" : path.endsWith(".json") || path.endsWith(".webmanifest") ? "application/json" : "text/plain";
        try { return new WebResourceResponse(mime, "UTF-8", 200, "OK", Collections.singletonMap("Cache-Control","no-store"), getAssets().open("web/"+path)); }
        catch (IOException e) { return denied(); }
    }
    private WebResourceResponse denied() { return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), new ByteArrayInputStream(new byte[0])); }
    private String response(Object value, Exception error) {
        try { JSONObject json = new JSONObject(); json.put("ok", error == null); json.put("value", value == null ? JSONObject.NULL : value); if(error!=null)json.put("error",error.getMessage()==null?"本机操作失败":error.getMessage()); return json.toString(); }
        catch (JSONException e) { return "{\"ok\":false,\"error\":\"结果编码失败\"}"; }
    }
    private void signal(String name, String detail) {
        runOnUiThread(() -> { if (!isFinishing()) web.evaluateJavascript("window.dispatchEvent(new CustomEvent("+JSONObject.quote(name)+",{detail:"+detail+"}));", null); });
    }
    private void exportResult(String message) { signal("native-export-result", JSONObject.quote(message)); }
    public final class Bridge {
        @JavascriptInterface public String readRecords() { try { return response(records.read(), null); } catch(Exception e) { return response(null,e); } }
        @JavascriptInterface public String writeRecords(String text) { try { records.write(text); return response(null,null); } catch(Exception e) { return response(null,e); } }
        @JavascriptInterface public void exportText(String text, String filename, String mime) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if(bytes.length>30*1024*1024){exportResult("导出文件超过 30 MB，请缩小备份。");return;}
            startExport(bytes, filename, mime);
        }
        @JavascriptInterface public void exportQr() {
            io.execute(() -> { try { startExport(readLimited(getAssets().open("web/install-qr.png"), 1024*1024), "轻松记-APK下载二维码.png", "image/png"); } catch(Exception e){exportResult("二维码保存失败。");} });
        }
        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> {
                if(pickerBusy){exportResult("请先完成当前文件选择。");return;} pickerBusy=true;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
                try { startActivityForResult(intent, IMPORT); } catch(ActivityNotFoundException e){pickerBusy=false;exportResult("系统文件选择器不可用。");}
            });
        }
        @JavascriptInterface public synchronized String takeImportResult() { String result=pendingImport; pendingImport=null;return result==null?response(null,new IOException("没有待导入文件。")):result; }
        @JavascriptInterface public void copyText(String text) { runOnUiThread(() -> ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("轻松记下载链接",text))); }
        @JavascriptInterface public void finishApp() { runOnUiThread(() -> finish()); }
    }
    private void startExport(byte[] bytes, String filename, String mime) {
        runOnUiThread(() -> {
            if(pickerBusy){exportResult("请先完成当前文件选择。");return;} pickerBusy=true; pendingExport=bytes;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime.equals("image/png")?mime:mime.equals("text/csv;charset=utf-8")?"text/csv":"application/json");intent.putExtra(Intent.EXTRA_TITLE, filename.replaceAll("[/\\\\]","_"));
            try { startActivityForResult(intent, EXPORT); } catch(ActivityNotFoundException e){pickerBusy=false;pendingExport=null;exportResult("系统文件选择器不可用。");}
        });
    }
    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request,code,data);
        if(request!=EXPORT && request!=IMPORT)return;
        if(code!=RESULT_OK || data==null || data.getData()==null){pickerBusy=false;pendingExport=null;exportResult("已取消文件选择，记录未更改。");return;}
        Uri uri=data.getData();
        if(request==EXPORT){final byte[] bytes=pendingExport;pendingExport=null;io.execute(() -> {try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null||bytes==null)throw new IOException();out.write(bytes);out.flush();exportResult("文件已保存。");}catch(Exception e){exportResult("文件保存失败，请重新选择位置。");}finally{runOnUiThread(()->pickerBusy=false);}});}
        else io.execute(() -> {try{String text=new String(readLimited(getContentResolver().openInputStream(uri),10*1024*1024),StandardCharsets.UTF_8);pendingImport=response(text,null);}catch(Exception e){pendingImport=response(null,new IOException("读取失败或文件超过 10 MB，原始记录未更改。"));}finally{runOnUiThread(()->pickerBusy=false);signal("native-import-ready","null");}});
    }
    private byte[] readLimited(InputStream stream,int limit) throws IOException {
        if(stream==null)throw new IOException("无法读取文件。");
        try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("文件过大。");out.write(buffer,0,count);}return out.toByteArray();}
    }
    @Override public void onBackPressed() { web.evaluateJavascript("window.dispatchEvent(new Event('native-back'));",null); }
    @Override protected void onDestroy(){web.removeJavascriptInterface("NativeDiary");web.destroy();io.shutdownNow();super.onDestroy();}
}
